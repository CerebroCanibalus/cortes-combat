package com.cortes.combat.client;

import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.net.PlayerCombatState;
import com.cortes.combat.pose.PoseSolver;
import com.cortes.combat.pose.PoseState;
import com.cortes.combat.thirdperson.BodyPose;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelInput;
import com.cortes.combat.viewmodel.ViewModelMotion;
import com.cortes.combat.viewmodel.ViewModelProfile;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * What every <em>other</em> player on screen is doing: one clock each, driven by what the server said.
 *
 * <h2>The clock is the client's, and it has to be</h2>
 *
 * <p>{@code ItemViewModel.MOTION} is a static clock belonging to <em>this</em> client. There is no
 * server-side copy of it, and adding one would be a second clock for one animation &mdash; the thing
 * this project vetoes outright, and the reason the wire carries state rather than the thirteen
 * channels the clock produces.</p>
 *
 * <p>So this holds <b>one {@link ViewModelMotion} per visible player</b>, each driven by the state
 * that player sent. Every client runs the same function on the same numbers, so every client draws
 * the same pose &mdash; and a client with eighty milliseconds of lag draws the same pose
 * <em>late</em>, which the interpolation below turns into smooth rather than into an arm
 * teleporting to a finished position.</p>
 *
 * <h2>The four events, and why they are not fields</h2>
 *
 * <p>{@code onStrike}, {@code onSweep}, {@code onCancel} and {@code onGuardBreak} are called on
 * this clock the same way {@code ItemViewModel} calls them for the local player, from the payloads
 * that already exist. A chop is born on the <em>release</em> with the charge it was released at,
 * and no amount of state recovers that: the clock is told, because "the release frame has to show
 * the item still back" is the reason {@code pendingStrike} exists.</p>
 *
 * <p>Each remote clock therefore also needs its own seed, and it gets one. <b>A tremble is noise
 * and noise that is identical on every client stops being noise</b> &mdash; two clients drawing the
 * same player see the same motion and a different shake.</p>
 */
public final class RemotePoseStates {

    /** One clock per player, by network entity id. */
    private static final Map<Integer, RemoteMotion> CLOCKS = new HashMap<>();

    private RemotePoseStates() {
    }

    /**
     * One clock, the last two poses, and the three profiles that do not travel.
     *
     * <p><b>Read off the entity once per packet, not once per tick.</b> The charge profile and the
     * guard profile are data components on the item the player is holding, and the game has already
     * synced that hotbar &mdash; so they live here rather than on the wire. Holding the entity
     * instead would mean a world lookup per player per tick, twenty times a second, to read a
     * component that only changed when they changed item.</p>
     *
     * <p>Which means they can go stale, and that is stated rather than solved: <b>a change of
     * weapon is itself a state change</b>, because the weapon's profile is one of the nine fields.
     * A player who swaps weapons and then stands still still sends, since the state is sent on a
     * rate and not only on changes.</p>
     */
    private static final class RemoteMotion {
        private final ViewModelMotion motion = new ViewModelMotion();
        private BodyPose previous = BodyPose.NONE;
        private BodyPose current = BodyPose.NONE;
        private boolean hadAny;

        /** The state this player last described themselves with. */
        private PlayerCombatState state = PlayerCombatState.STILL;

        private ChargeProfile charge;
        private GuardProfile guard;
        private boolean vanillaShield;
        private float cooldownRatio = 1.0F;

        /** A state has arrived that this tick has not consumed yet. */
        private boolean pending;
    }

    /**
     * One client tick for every remote clock.
     *
     * <p><b>Once a tick, and once per player.</b> The motion's {@code tick} is a game tick: it
     * advances the strike's age, the break's age and the guard's ramp by one. Calling it per frame
     * would run the whole animation at sixty times the rate, and calling it for the local player
     * twice would double its own.</p>
     */
    public static void tick() {
        for (RemoteMotion remote : CLOCKS.values()) {
            advance(remote);
        }
    }

    /**
     * The pose for one player part way through the frame, or {@link BodyPose#NONE} for a player this
     * client has heard nothing about.
     *
     * @param partialTick 0 at the start of the tick, approaching 1 at the end
     */
    public static BodyPose snapshot(int entityId, float partialTick) {
        RemoteMotion remote = CLOCKS.get(entityId);
        if (remote == null || !remote.hadAny) {
            return BodyPose.NONE;
        }
        if (remote.previous.isZero()) {
            // First frame of a player. Interpolating from nothing drags the arm out of the model
            // over one frame, which is its own artefact -- `BodyPoseDispatcher#snapshot` makes the
            // same choice for the same reason.
            return remote.current;
        }
        // **A clock that is a tick behind its last packet interpolates from itself.**
        //
        // `previous` and `current` are the same pose on a tick where no new state arrived, and
        // lerping one against the other is the identity -- so this is not a special case to
        // detect, it is the arithmetic already being right. It is written down because the
        // alternative sounds plausible and is not: "skip the interpolation when a packet was
        // missed" would be true of the frames between packets and false of the frame a packet
        // landed on, and that is a step.
        return remote.previous.lerp(remote.current, partialTick);
    }

    /** Whether this client has any state for that player. */
    public static boolean knows(int entityId) {
        return CLOCKS.containsKey(entityId);
    }

    /**
     * A state arrived. The pose becomes the far end of an interpolation and the clock is armed.
     *
     * <p><b>The clock does not step here.</b> {@link #tick} steps it, once per game tick, the same
     * as the local player's -- advancing in both places would run the whole animation at double
     * speed for everybody else, which is the sort of thing that looks fine until somebody counts.
     * It is also the ordering rule {@code ClientTasks} already documents for the local player: the
     * body's poses read the viewmodel's frame, so the frame has to have been advanced first.</p>
     *
     * @param player the entity that id names. <b>Never null</b>: the caller resolves it and calls
     *               {@link #forget} for an id that does not name a player. A clock with no profiles
     *               on it still produces a pose, and that one would silently have no charge and no
     *               guard -- a player rendered half-animated reads as intentional.
     */
    public static void accept(PlayerCombatState state, AbstractClientPlayer player) {
        RemoteMotion remote = CLOCKS.computeIfAbsent(state.entityId(), id -> new RemoteMotion());
        remote.state = state;
        remote.pending = true;

        // **The three reads out of the world, once per packet rather than once per tick.**
        //
        // The charge profile and the guard profile are components on the item the player is holding
        // and the hotbar is synced by the game, so sending them would be sending something the
        // receiver can look up. The shield flag is the one that is a question rather than a lookup,
        // and `BodyPoseDispatcher` asks it the same way for the local player.
        ItemStack hand = player.getMainHandItem();
        remote.charge = WeaponStats.of(hand).charge();
        remote.guard = GuardStats.best(player).profile();
        remote.vanillaShield = hand.is(Items.SHIELD);
        remote.cooldownRatio = state.cooldownRatio();
        remote.hadAny = true;
    }

    /** The swing resolved for that player: the one event the clock cannot recover from state. */
    public static void strike(int entityId, float charge, int durationTicks) {
        RemoteMotion remote = CLOCKS.get(entityId);
        if (remote != null) {
            remote.motion.onStrike(charge, durationTicks);
        }
    }

    /** The right click swept. An event with twenty ticks of life and no field to remember to clear. */
    public static void sweep(int entityId) {
        RemoteMotion remote = CLOCKS.get(entityId);
        if (remote != null) {
            remote.motion.onSweep();
        }
    }

    /** Whatever was held was released without a swing. */
    public static void cancel(int entityId) {
        RemoteMotion remote = CLOCKS.get(entityId);
        if (remote != null) {
            remote.motion.onCancel();
        }
    }

    /** A player left the world, or stopped being visible. */
    public static void forget(int entityId) {
        CLOCKS.remove(entityId);
    }

    public static void clear() {
        CLOCKS.clear();
    }

    /** How many players have a clock. For the debug readout. */
    public static int size() {
        return CLOCKS.size();
    }

    /** Whether that player's weapon can do the thing that channel is for. */
    public static boolean reads(int entityId, Channel channel) {
        RemoteMotion remote = CLOCKS.get(entityId);
        return remote != null && ViewModelDefaults.of(remote.state.weapon()).reads(channel);
    }

    /**
     * One tick of one remote clock, and the pose that comes out of it.
     *
     * <p><b>The seven arguments are the same seven the local player uses</b>, in the same order,
     * and two of them are computed here rather than sent. That is the whole design: the server says
     * "the button has been down for thirty-one ticks" and the reading of that against the weapon's
     * own charge profile happens on the client, through
     * {@link com.cortes.combat.attack.AttackTiming} &mdash; the same function, the same numbers.</p>
     */
    private static void advance(RemoteMotion remote) {
        PlayerCombatState state = remote.state;

        // **The two poses are always consecutive ticks, and this line is what makes that true.**
        //
        // The first version pushed the old pose aside only when a packet arrived, so a lost packet
        // left `previous` frozen while `current` kept advancing with the clock: the interpolation
        // stretched between two poses further and further apart, which is extrapolation wearing an
        // interpolator's clothes. A dropped packet should cost one tick of staleness, not a
        // growing gap between where the arm was and where it is.
        remote.previous = remote.current;

        remote.motion.observeStagger(state.stagger());
        remote.motion.observeParry(state.parryActive(), state.parryCooldown());

        ViewModelProfile profile = ViewModelDefaults.of(state.weapon());

        remote.motion.tick(state.holding(),
                rawCharge(remote),
                classifiedCharge(remote),
                state.guarding(),
                state.stagger(),
                // **The parry and the lunge are gated on the profile here too**, and not at the
                // call site, for the reason `ItemViewModel.tick` documents: the channels are global
                // and the state that drives them is the player's, so a player who was parried and
                // then picked up a sword had that sword animate the sabre's lockout for the five
                // seconds it had left.
                profile.reads(Channel.PARRY) && state.parryActive(),
                profile.reads(Channel.LUNGE) && state.lunging());

        remote.current = PoseSolver.body(stateFor(remote, state, profile));
        remote.pending = false;
    }

    /**
     * The charge as the player sees it: the raw ratio, for the wind-up.
     *
     * <p><b>Ticks over the weapon's own full-charge length.</b> The numerator travels; the
     * denominator is a data component on the item the client already has, and sending the ratio
     * instead would send a number the receiver can compute exactly.</p>
     *
     * <p>A weapon with no charge profile answers zero whatever the button is doing, and that is the
     * same answer the local player gives &mdash; {@code predictedRatio} divides by
     * {@code fullChargeTicks} and returns zero when it is not positive. A guard profile and a
     * charge profile are different objects, so asking the wrong one here would be a silent
     * division by the wrong number rather than an error.</p>
     */
    private static float rawCharge(RemoteMotion remote) {
        ChargeProfile charge = remote.charge;
        if (!remote.state.holding() || charge == null || charge.fullChargeTicks() <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) remote.state.holdTicks() / (float) charge.fullChargeTicks());
    }

    /**
     * The same reading, thresholded: whether this swing <em>is</em> a charge rather than a tap.
     *
     * <p><b>{@link AttackTiming#chargeRatioFor}, called with the weapon's own answer.</b> The three
     * argument form takes the {@code canCharge} flag, so a weapon that cannot charge at all returns
     * zero here even with the button down for a second &mdash; which is the case the project already
     * paid for, when a gate was forgotten in the call sites and a dagger reported a full charge on
     * a one second hold.</p>
     */
    private static float classifiedCharge(RemoteMotion remote) {
        ChargeProfile charge = remote.charge;
        if (!remote.state.holding() || charge == null) {
            return 0.0F;
        }
        return AttackTiming.chargeRatioFor(remote.state.holdTicks(), charge.fullChargeTicks(),
                charge.canCharge());
    }

    /** The pose state, with the three fields the payload does not carry. */
    private static PoseState stateFor(RemoteMotion remote, PlayerCombatState state,
                                      ViewModelProfile profile) {
        // **The snapshot is taken at zero partial tick.** `BodyPoseDispatcher` interpolates between
        // two of these per frame, and interpolating the clock *and* the pose would smooth the same
        // motion twice -- which is what a twenty-hertz animation inside a sixty-hertz renderer
        // looks like when nobody notices and everybody can feel it.
        return new PoseState(state.weapon(), remote.charge, remote.guard, remote.vanillaShield,
                remote.motion.snapshot(0.0F, 1.0F - remote.cooldownRatio), profile);
    }
}