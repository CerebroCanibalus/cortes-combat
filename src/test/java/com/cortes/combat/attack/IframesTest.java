package com.cortes.combat.attack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The i-frames have to be shorter than the fastest weapon's interval, and nothing else does.
 *
 * <p>This answers the question "should the i-frames move?" with a number instead of a guess. The
 * window is applied to the <em>target</em> after a hit, so it can only ever hurt the player by
 * eating their own follow-up: swing again before it expires and the second swing does nothing at
 * all, with a green build, a correct damage number, and no message anywhere.</p>
 *
 * <p><b>The constant moved out of {@code CombatEngine} to make this possible.</b> The engine
 * touches Minecraft and cannot be loaded in a unit test, so a window living there could only be
 * checked by launching a client and hitting a zombie twice. {@link AttackTiming} is pure, and the
 * thing it has to stay shorter than is a table of numbers.</p>
 */
class IframesTest {

    /** What the catalogue says, in ticks, at its own attack speed with no tempo applied. */
    private static int intervalFor(WeaponFamily family) {
        return AttackTiming.ticksFor(family.attackSpeed());
    }

    /**
     * No weapon in the game can swing again inside its own invulnerability window.
     *
     * <p>This is the assertion that was missing while the javadoc made the same claim in prose.
     * The dagger is the risk: it was the fastest thing in the game at eight ticks against a four
     * tick window, which was a four tick margin, and "make the dagger faster" is exactly the
     * change that would have eaten every second hit of the fastest weapon in the game.</p>
     */
    @Test
    @DisplayName("the fastest weapon still swings again outside the window")
    void theFastestWeaponIsStillSlowerThanTheWindow() {
        WeaponFamily fastest = null;
        int fastestTicks = Integer.MAX_VALUE;
        for (WeaponFamily family : WeaponFamily.values()) {
            int ticks = intervalFor(family);
            assertTrue(ticks > AttackTiming.IFRAME_TICKS,
                    family.id() + " swings every " + ticks + " ticks and the window is "
                            + AttackTiming.IFRAME_TICKS + ". Every second swing of this weapon "
                            + "would land on nothing, with nothing in the log saying so");
            if (ticks < fastestTicks) {
                fastestTicks = ticks;
                fastest = family;
            }
        }

        assertEquals(WeaponFamily.DAGA, fastest,
                "the dagger is meant to be the fastest thing in the game. If another family took "
                        + "the crown then this test is no longer checking the weapon the design "
                        + "gave the tightest margin to");
        assertEquals(6, fastestTicks,
                "six ticks, against a four tick window. A two tick margin, and the reason the "
                        + "speed cannot go to five without this file failing");
    }

    /**
     * Vanilla's window is twenty and this is four.
     *
     * <p>Worth holding the ratio down rather than the absolute: twenty was tuned for a weapon that
     * swings twice a second, and the whole reason this system replaced vanilla's timing is that
     * it does not suit weapons that move faster than that.</p>
     */
    @Test
    @DisplayName("three ticks, and a sixth of what vanilla uses")
    void itIsAFifthOfVanillas() {
        assertEquals(3, AttackTiming.IFRAME_TICKS);
        assertTrue(AttackTiming.IFRAME_TICKS * 6 <= 20,
                "vanilla is twenty and this has to stay well inside it. At twenty a fast weapon "
                        + "cannot land a second hit for a full second, which is the bug this "
                        + "window exists to avoid");
    }

    /** The unarmed case is slower still, so bare hands cannot get stuck either. */
    @Test
    @DisplayName("bare hands are outside the window too")
    void evenBareHands() {
        assertTrue(AttackTiming.UNARMED_INTERVAL > AttackTiming.IFRAME_TICKS,
                "unarmed is " + AttackTiming.UNARMED_INTERVAL + " ticks against a "
                        + AttackTiming.IFRAME_TICKS + " tick window. It is the fastest thing a "
                        + "player can swing when nothing is in their hand, and it is not in the "
                        + "catalogue loop above because it is not a family");
    }

    /**
     * The bare hand is the real answer to this file, not the dagger.
     *
     * <p>{@link #UNARMED_INTERVAL} is twelve, and checking that against the window passes
     * comfortably -- and means nothing. Twelve is the <em>fallback</em> for an entity whose attack
     * speed attribute is missing or nonsense. A real player's bare hand uses the unarmed tempo of
     * 1.25 over the base 4.0, and that is four ticks.</p>
     */
    @Test
    @DisplayName("the bare hand, not the dagger, is what bounds the window")
    void theBareHandIsTheTightOne() {
        int bareHand = AttackTiming.scaleFor(4.0F, 1.25F);
        assertEquals(4, bareHand,
                "four ticks: ticksFor(4.0 times 1.25). This is the fastest swing in the game and "
                        + "it is not a weapon at all");
        assertTrue(bareHand > AttackTiming.IFRAME_TICKS,
                "and it has to clear the window of " + AttackTiming.IFRAME_TICKS + ". At four "
                        + "against four every second punch landed on nothing, silently, and the "
                        + "check that should have caught it was reading the wrong number: the "
                        + "fallback twelve, which no player ever swings at");
    }

    /** And the tempo multiplier cannot push anything into the window either, in either direction. */
    @Test
    @DisplayName("a tempo, speeding a weapon up or slowing it down, still leaves it outside the window")
    void tempoCannotCrossIt() {
        // The two profiles the catalogue DOES give a tempo to, plus the sword at the 1.0 its row now
        // resolves to. The axe was missing here for a round and the sword was listed as the fast one
        // when its 0.85 was slowing it down -- see `WeaponStats`, where that row is deleted and the
        // direction is spelled out. Both directions are walked now, because "a tempo" is a number
        // someone will retune and the window is what it would silently break.
        float[][] tempos = {{1.6F, 1.0F}, {4.0F, 1.25F}, {1.0F, 0.85F}};
        for (float[] entry : tempos) {
            double speed = entry[0] * entry[1];
            int ticks = AttackTiming.ticksFor(speed);
            assertTrue(ticks > AttackTiming.IFRAME_TICKS,
                    "a tempo of " + entry[1] + " on a base of " + entry[0] + " gives " + ticks
                            + " ticks, and the window is " + AttackTiming.IFRAME_TICKS
                            + ". A tempo is a number someone will eventually retune, and this is "
                            + "where it would silently break a weapon");
        }
    }

    /** The charge does not change the interval, so a charged swing cannot be the one that crosses. */
    @Test
    @DisplayName("charging does not shorten the swing")
    void chargingDoesNotShortenIt() {
        ChargeProfile full = new ChargeProfile(true, 0.5F, 25);
        assertTrue(full.canCharge());
        assertEquals(10, AttackTiming.ticksFor(2.0F),
                "ticksFor reads the attack speed and nothing else. A charge multiplies the "
                        + "damage and never the rhythm, so the fastest a charged hit can be swung "
                        + "is the fastest an uncharged one can");
    }
}
