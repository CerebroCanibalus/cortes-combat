package com.cortes.combat.net;

import com.cortes.combat.core.weapon.WeaponProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That {@link PlayerCombatState}'s wire format says what it means, field for field.
 *
 * <h2>The bug this exists for</h2>
 *
 * <p><b>A swapped field compiles, runs, and animates the wrong thing.</b> It is not a crash and not
 * a wrong value: nine plausible numbers come out and go back in, each one landing in its
 * neighbour. A player who is mid-parry animates as though they were guarding; a charge of thirty
 * one ticks renders as a cooldown of thirty one, which is a weapon waiting rather than a weapon
 * being cocked.</p>
 *
 * <p>And it is invisible in the only way this project has ever tested, because nothing decodes a
 * second player's state until there is a second player.</p>
 *
 * <p><b>So this reads the raw buffer one field at a time</b> and checks each against its own name.
 * An encode-and-decode round trip cannot catch this &mdash; it agrees with itself whichever way the
 * fields are ordered.</p>
 */
class PlayerCombatStateTest {

    /** Nine fields, each one carrying a number no other field could carry. */
    private static PlayerCombatState everyFieldItsOwnNumber() {
        return new PlayerCombatState(4242, WeaponProfile.SABRE,
                true, 31, true, 0.37F, true, 97, true, 0.62F, 0.44F);
    }

    private static PlayerCombatState roundTrip(PlayerCombatState state) {
        ByteBuf buf = Unpooled.buffer();
        PlayerCombatState.encode(buf, state);
        PlayerCombatState back = PlayerCombatState.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "the codec left " + buf.readableBytes() + " bytes unread. A codec that writes more "
                        + "than it reads grows the buffer on every packet until the connection "
                        + "stalls, and nothing about it throws");
        return back;
    }

    /**
     * Each field, read straight off the wire.
     *
     * <p><b>Read one, check it, and the order of the checks is the order on the wire.</b> Fixed
     * widths are what make this possible: every field is at a known offset, so a transposition is a
     * named mismatch rather than something that has to be inferred from a decoded record.</p>
     */
    @Test
    @DisplayName("each field lands on the wire where its own name says it should")
    void eachFieldLandsWhereItsNameSays() {
        ByteBuf buf = Unpooled.buffer();
        PlayerCombatState.encode(buf, everyFieldItsOwnNumber());

        assertEquals(4242, buf.readInt(), "entityId");
        assertEquals(WeaponProfile.SABRE.ordinal(), buf.readByte(), "weapon");
        assertTrue(buf.readBoolean(), "holding");
        assertEquals(31, buf.readInt(), "holdTicks");
        assertTrue(buf.readBoolean(), "guarding");
        assertEquals(0.37F, buf.readFloat(), "stagger");
        assertTrue(buf.readBoolean(), "parryActive");
        assertEquals(97, buf.readInt(), "parryCooldown");
        assertTrue(buf.readBoolean(), "lunging");
        assertEquals(0.62F, buf.readFloat(), "cooldownRatio");
        assertEquals(0.44F, buf.readFloat(), "specialCharge");
        assertEquals(0, buf.readableBytes(), "and nothing after it");
    }

    /** The decoder puts each field back where the encoder wrote it. */
    @Test
    @DisplayName("the decoder puts every field back on the one it came from")
    void theDecoderPutsEveryFieldBack() {
        PlayerCombatState state = everyFieldItsOwnNumber();
        PlayerCombatState back = roundTrip(state);

        assertEquals(state.entityId(), back.entityId(), "entityId");
        assertEquals(state.weapon(), back.weapon(), "weapon");
        assertEquals(state.holding(), back.holding(), "holding");
        assertEquals(state.holdTicks(), back.holdTicks(), "holdTicks");
        assertEquals(state.guarding(), back.guarding(), "guarding");
        assertEquals(state.stagger(), back.stagger(), "stagger");
        assertEquals(state.parryActive(), back.parryActive(), "parryActive");
        assertEquals(state.parryCooldown(), back.parryCooldown(), "parryCooldown");
        assertEquals(state.lunging(), back.lunging(), "lunging");
        assertEquals(state.cooldownRatio(), back.cooldownRatio(), "cooldownRatio");
    }

    /**
     * Nothing happening, and it says so.
     *
     * <p><b>A player standing still is the frame most of a fight is made of.</b> Every field is
     * false or zero, the weapon is {@code NONE}, and a client that receives it draws a player with
     * their arms down rather than an arm frozen in whatever the last swing was.</p>
     */
    @Test
    @DisplayName("a player who is doing nothing decodes to all false and all zero")
    void aStillPlayerDecodesToNothing() {
        PlayerCombatState back = roundTrip(PlayerCombatState.STILL);

        assertEquals(WeaponProfile.NONE, back.weapon(),
                "and the weapon is NONE, which is the profile with no tracks -- so nothing draws it "
                        + "rather than the previous weapon's motion continuing on its own");
        assertFalse(back.holding(), "the button is up");
        assertEquals(0, back.holdTicks(), "and it has been up for no ticks");
        assertFalse(back.guarding(), "the guard is down");
        assertEquals(0.0F, back.stagger(), "nothing is staggering them");
        assertFalse(back.parryActive(), "no parry window");
        assertEquals(0, back.parryCooldown(), "and no lockout running");
        assertFalse(back.lunging(), "no lunge");
        assertEquals(1.0F, back.cooldownRatio(),
                "and the readiness is one, which is what 'can swing' means. A channel that rested at "
                        + "zero would need a special case in every track that reads it");
    }

    /**
     * A weapon this build does not have is a still weapon, not a crash.
     *
     * <p><b>The version-skew case, and the reason {@code byOrdinal} returns null.</b> A server with
     * a weapon this client does not have sends an ordinal past the end of the enum. Throwing there
     * drops the packet and takes the connection with it; the honest answer is the profile with no
     * tracks, so the player stands still rather than vanishing from the world.</p>
     */
    @Test
    @DisplayName("a weapon from a newer build is read as a weapon that does nothing")
    void anUnknownWeaponBecomesNone() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(7);
        buf.writeByte(200);
        buf.writeBoolean(false);
        buf.writeInt(0);
        buf.writeBoolean(false);
        buf.writeFloat(0.0F);
        buf.writeBoolean(false);
        buf.writeInt(0);
        buf.writeBoolean(false);
        buf.writeFloat(1.0F);
        buf.writeFloat(0.0F);

        PlayerCombatState back = PlayerCombatState.decode(buf);

        assertEquals(WeaponProfile.NONE, back.weapon(),
                "an ordinal this build does not have is the profile with no tracks. A player whose "
                        + "weapon we cannot name is still a player: the honest reading is a weapon "
                        + "that does nothing, and the alternative is a packet handler that throws "
                        + "and takes the connection with it");
    }

    /**
     * The state is clamped on the way in, because the clock divides by it.
     *
     * <p><b>{@code holdTicks} and {@code stagger} are read by arithmetic that assumes a range.</b>
     * A negative tick count makes the wind-up run backwards and a stagger above one is a cooldown
     * at one hundred and ten percent, neither of which any producer can send and both of which a
     * hand-written or corrupted packet can. The clamp lives in the constructor so both the network
     * reader and a test get it, and that is the point: a guard only in the packet handler is a
     * guard the test cannot reach.</p>
     */
    @Test
    @DisplayName("a nonsense tick count or stagger is clamped rather than believed")
    void nonsenseIsClamped() {
        PlayerCombatState back = roundTrip(
                new PlayerCombatState(7, WeaponProfile.SABRE, true, -50, true, 4.2F,
                        false, -3, false, 9.0F, -2.0F));

        assertEquals(0, back.holdTicks(),
                "a negative hold is not a hold. The wind-up divides by the charge length and "
                        + "multiplies by this, so a negative number makes the cock run backwards");
        assertEquals(1.0F, back.stagger(),
                "and a stagger over one is a cooldown at a hundred and ten percent. It feeds the "
                        + "guard's collapse channel, where anything above one is past the end of "
                        + "the animation");
        assertEquals(0, back.parryCooldown(),
                "a negative lockout would make the sabre parryable before the window it is in");
        assertEquals(1.0F, back.cooldownRatio(),
                "and a readiness over one is a weapon that is more than ready, which makes the "
                        + "strike's follow-through play with the arm still cocked");
    }
}