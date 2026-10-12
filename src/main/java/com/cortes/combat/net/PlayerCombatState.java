package com.cortes.combat.net;
import com.cortes.combat.core.weapon.WeaponProfile;
import io.netty.buffer.ByteBuf;

/**
 * One other player's <b>combat state</b>, as it crosses the wire. Nine fields.
 *
 * <h2>State, and not the channels it produces</h2>
 *
 * <p><b>This class was a set of thirteen channels and it had to stop being one.</b> The channels
 * &mdash; wind-up, strike, chop, parry &mdash; are the <em>output</em> of
 * {@link com.cortes.combat.viewmodel.ViewModelMotion}, which is a clock. A clock does not live on
 * the server: {@code ItemViewModel.MOTION} is a static instance belonging to the client, and there
 * is no server-side copy to read channels out of. So sending channels would have meant either giving
 * the server its own clock &mdash; a second one, for the same animation, which this project vetoes
 * outright &mdash; or not being able to send them at all.</p>
 *
 * <p>What the server <em>does</em> have is state without a clock, and it is what the player did:
 * whether the button is down, how many ticks it has been down, whether the guard is up, how much
 * cooldown is left on the item, whether the parry window is open. Nine numbers, and every one of
 * them is a fact about the player rather than a picture of a motion.</p>
 *
 * <h2>So the receiving client runs the same clock</h2>
 *
 * <p>Each client drives one {@code ViewModelMotion} per visible player, from this state. That is
 * the same function running on the same numbers, so the pose is the pose &mdash; and the lag shows
 * up as interpolating between two <em>poses</em>, which is what the local player has always done,
 * rather than as an arm teleporting to a finished position.</p>
 *
 * <p><b>Two of the clock's inputs are not sent and are computed here instead.</b> {@code rawCharge}
 * and {@code chargedRatio} are a reading of {@code holdTicks} against the weapon's own charge
 * profile, which is a data component on the item &mdash; and the item in another player's hand is
 * already in this client's world, because the hotbar is synced by the game. Sending a number the
 * receiver can look up is bandwidth for nothing.</p>
 *
 * <p>And {@link #weapon()} travels even though the item is visible, because it is the one field
 * that cannot be derived: it is the server's <em>decision</em> about what that item is, a weapon
 * from another mod resolves to something only the server can name, and two clients with different
 * mod sets still have to agree.</p>
 *
 * <h2>What this class deliberately does not carry</h2>
 *
 * <p><b>Three events, not three more fields.</b> {@code onStrike(charge, durationTicks)},
 * {@code onSweep()} and {@code onCancel()} are moments, not values, and the clock cannot recover
 * them: a chop is born on the <em>release</em>, and when the button goes up the clock is told how far
 * it had got, because "the release frame has to show the item still back" is the whole reason
 * {@code pendingStrike} exists. State alone would produce a strike with no charge on it, which is a
 * tap, forever. {@link CombatEventPayload} carries the three.</p>
 *
 * <p><b>Three and not four.</b> {@code ViewModelMotion#onGuardBreak} exists, is public, is part of
 * the clock's API and has <b>no callers</b> &mdash; the guard's collapse is derived from
 * {@code stagger}, which is one of the fields below. Asking who calls a method settles it; designing
 * from first principles would have invented a fourth event for something already carried.</p>
 *
 * <h2>Why this class names no {@code StreamCodec}</h2>
 *
 * <p><b>A codec field would stop the tests from running.</b> A {@code static final StreamCodec} is
 * verified when the class is first touched, so the field's type resolves before any assertion and
 * every test in the package dies with {@code NoClassDefFoundError} &mdash; not a compile error, not
 * an assertion, and nothing in the message that names a test. Same shape as this project's
 * {@code StringRepresentable} lesson.</p>
 *
 * <p>So the two halves are plain methods on {@link ByteBuf}, which is Netty and is a test
 * dependency, and the payload wraps them on the one line that needs a real codec.</p>
 */
public record PlayerCombatState(int entityId, WeaponProfile weapon,
                                boolean holding, int holdTicks,
                                boolean guarding, float stagger,
                                boolean parryActive, int parryCooldown,
                                boolean lunging, float cooldownRatio,
                                float specialCharge) {
    /**
     * Nothing happening, for a player this client has heard nothing about.
     *
     * <p>And the reason there is no {@code sweepActive}: the sweep is an event with a twenty-tick
     * life, and a <em>state</em> field would be a flag the sender has to remember to clear.
     * {@code onSweep} is called once and the clock runs it down.</p>
     */
    public static final PlayerCombatState STILL = new PlayerCombatState(
            -1, WeaponProfile.NONE, false, 0, false, 0.0F, false, 0, false, 1.0F, 0.0F);
    /** No tremble clock and no state: the clock's own version of this record. */
    public PlayerCombatState {
        if (weapon == null) {
            // **A profile this build does not know is not an error.** A client without a mod that
            // adds a weapon must still draw the player, and `NONE` is the profile that has no
            // tracks -- so the weapon sits still instead of drawing somebody else's motion.
            weapon = WeaponProfile.NONE;
        }
        if (holdTicks < 0) {
            holdTicks = 0;
        }
        if (parryCooldown < 0) {
            // **The lockout is a countdown and a countdown cannot be negative.** A negative one
            // would make `observeParry` see a window that closes before it opens, so the sabre
            // would report itself parryable during the five seconds it is meant to be refusing.
            parryCooldown = 0;
        }
        if (stagger < 0.0F) {
            stagger = 0.0F;
        } else if (stagger > 1.0F) {
            stagger = 1.0F;
        }
        // **La carga del boton derecho, clampeada igual que las otras dos.**
        //
        // Y viaja como <b>fraccion</b> y no como ticks, que es lo contrario de `holdTicks` -- y la
        // asimetria es a proposito. La duracion de la carga del ataque vive en el `ChargeProfile`
        // del arma, que el cliente YA tiene, asi que mandar los ticks no agrega nada. La de una
        // carga especial vive en una regla del encantamiento, y re-derivarla en el cliente seria una
        // segunda copia de "cuanto dura" que se desincroniza la primera vez que se tunea.
        if (specialCharge < 0.0F) {
            specialCharge = 0.0F;
        } else if (specialCharge > 1.0F) {
            specialCharge = 1.0F;
        }
        if (cooldownRatio < 0.0F) {
            cooldownRatio = 0.0F;
        } else if (cooldownRatio > 1.0F) {
            // **One means ready, and ready is the resting value.** This is readiness rather than
            // progress -- the same direction `CooldownState#ratio` returns and the same
            // `CombatStatePayload` sends for the local player's own bar, so the two agree. A
            // cooldown channel is spent as it runs, which is why its resting value is one rather
            // than zero: a channel that rested at zero would need a special case in every track
            // that reads it, and "what is the resting value of this channel" has to have one
            // answer.
            cooldownRatio = 1.0F;
        }
    }
    /**
     * Writes the nine fields.
     *
     * <p><b>Order is a contract and the two ends of it are written independently.</b> A swap
     * compiles, runs, and animates the wrong thing &mdash; not a crash and not a wrong value,
     * nine plausible numbers each in its neighbour's field. It is invisible with one player on the
     * screen, because nothing decodes a second player's state until there is a second player.</p>
     *
     * <p><b>The weapon is an ordinal, and that is a wire contract</b>: inserting a profile
     * renumbers the ones after it, so an old client reading a new server's payload decodes a
     * different weapon. The cost is accepted rather than hidden &mdash; the fifteen profiles live
     * in one enum, they are not extended at runtime, and changing that list is a protocol change
     * exactly as moving a field in this record would be. {@link WeaponProfile#byOrdinal} returns
     * null for one this build does not have and the constructor turns that into {@code NONE}, so a
     * version skew draws a still weapon instead of throwing inside a packet handler.</p>
     */
    public static void encode(ByteBuf buf, PlayerCombatState state) {
        buf.writeInt(state.entityId());
        buf.writeByte(state.weapon().ordinal());
        buf.writeBoolean(state.holding());
        buf.writeInt(state.holdTicks());
        buf.writeBoolean(state.guarding());
        buf.writeFloat(state.stagger());
        buf.writeBoolean(state.parryActive());
        buf.writeInt(state.parryCooldown());
        buf.writeBoolean(state.lunging());
        buf.writeFloat(state.cooldownRatio());
        buf.writeFloat(state.specialCharge());
    }
    /** The same, in the same order, which is the whole contract. */
    public static PlayerCombatState decode(ByteBuf buf) {
        int entityId = buf.readInt();
        WeaponProfile weapon = WeaponProfile.byOrdinal(buf.readByte());
        boolean holding = buf.readBoolean();
        int holdTicks = buf.readInt();
        boolean guarding = buf.readBoolean();
        float stagger = buf.readFloat();
        boolean parryActive = buf.readBoolean();
        int parryCooldown = buf.readInt();
        boolean lunging = buf.readBoolean();
        float cooldownRatio = buf.readFloat();
        float specialCharge = buf.readFloat();
        return new PlayerCombatState(entityId, weapon, holding, holdTicks, guarding, stagger,
                parryActive, parryCooldown, lunging, cooldownRatio, specialCharge);
    }
}