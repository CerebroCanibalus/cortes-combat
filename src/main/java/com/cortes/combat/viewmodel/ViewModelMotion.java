package com.cortes.combat.viewmodel;

/**
 * The client's view of how far through a swing the item is.
 *
 * <p>Nothing here reads the game. It is handed a charge ratio and a pair of booleans and turns
 * them into the {@link Channel#WINDUP} and {@link Channel#STRIKE} values, which keeps the
 * library's one hard part, "what does a swing look like over time", out of the renderer and in
 * something a test can drive a hundred frames at a time.</p>
 *
 * <h2>The hand-off</h2>
 *
 * <p>The interesting part is the moment of release, and getting it wrong is very visible. The
 * item is drawn all the way back and then it has to come forward and hit. Two naive versions
 * both look broken:</p>
 * <ul>
 *   <li>Charge snaps to zero on release. The item teleports to rest on the frame of the strike
 *       and <em>then</em> lunges forward, so the swing has a visible hitch at its fastest point
 *       and the two halves read as two separate motions.</li>
 *   <li>Charge stays at one and the strike adds on top. The item goes forward twice as far as it
 *       should and never comes back, because nothing ever undid the wind-up.</li>
 * </ul>
 *
 * <p>What this does instead is hand over: the wind-up unwinds across the strike while the strike
 * pushes forward through it, so the item travels out of the wound-back position in one
 * continuous move and the peak of the follow-through happens while the wind-up is already
 * halfway gone. Both channels are then at rest, so the item returns to the vanilla pose without
 * anybody writing an undo track.</p>
 *
 * <h2>Ticks advance the state, the render reads it interpolated</h2>
 *
 * <p>{@link #tick} moves the clock one game tick. {@link #snapshot} takes a partial tick and
 * produces the frame. Those are two different jobs and separating them is not bookkeeping: a
 * viewmodel evaluated only once per tick is a twenty hertz animation, which at sixty frames a
 * second repeats each position three times and reads as a machine stepping rather than as an
 * arm moving. Vanilla interpolates its own swing with the partial tick for exactly this reason.
 */
public final class ViewModelMotion {

    /**
     * How long the strike lasts, in ticks.
     *
     * <p>Long enough that the follow-through reads as a motion rather than as a jump, short
     * enough that a fast weapon's next swing does not start while this one is still travelling.
     * The bare hand has the shortest interval in the game at four ticks, so a follow-through
     * longer than that overlaps on that weapon alone, and that is a balance question rather than
     * a rendering one.</p>
     */
    public static final int STRIKE_TICKS = 6;

    /** Ticks since the last strike, or -1 when no strike is playing. */
    private int strikeAge = -1;

    /**
     * A strike that has been announced but has not been shown yet.
     *
     * <p>Without this the release frame is missed. The tick after the release is where the naive
     * version's teleport happens, because by then the age has already been stepped forward and the
     * wind-up has already dropped a sixth of the way. The frame the player let go on has to still
     * show the item drawn all the way back.</p>
     */
    private boolean pendingStrike;

    private int seed = 1;

    /**
     * Where the wind-up currently is.
     *
     * <p>Assigned straight from the charge rather than eased towards it. The charge is already
     * something the player watches move on a meter, and an item that lags behind its own meter is
     * a lag players notice and cannot name. Anything that wants easing belongs in a track's
     * curve, where it is per weapon and visible in the data.</p>
     */
    private float windup;

    /** Client ticks elapsed. The only clock, so the tremble and the swing cannot disagree. */
    private int ticks;

    /** Whether the button was down as of the last tick. Decides which of the two wind-ups applies. */
    private boolean holding;

    /** The strike just resolved. Starts the follow-through and re-rolls the tremble pattern. */
    public void onStrike() {
        pendingStrike = true;
        // An integer LCG rather than Random: one multiply and one add, no shared global state,
        // and the only thing asked of it is that consecutive swings differ while the same swing
        // stays reproducible, which any advancing integer gives.
        seed = seed * 1664525 + 1013904223;
    }

    /** Whatever was held is no longer held, and there is no strike to play. */
    public void onCancel() {
        strikeAge = -1;
        pendingStrike = false;
    }

    /**
     * One client tick of state.
     *
     * @param holding whether the attack button is down
     * @param charge  the predicted charge ratio, 0..1
     */
    public void tick(boolean holding, float charge) {
        ticks++;
        this.holding = holding;
        if (holding) {
            strikeAge = -1;
            pendingStrike = false;
        } else if (pendingStrike) {
            strikeAge = 0;
            pendingStrike = false;
        } else if (strikeAge >= 0 && ++strikeAge >= STRIKE_TICKS) {
            strikeAge = -1;
        }
        windup = targetWindup(holding, charge);
    }

    private float targetWindup(boolean holding, float charge) {
        if (holding) {
            return charge;
        }
        if (strikeAge < 0) {
            return 0.0F;
        }
        return 1.0F - (float) strikeAge / (float) STRIKE_TICKS;
    }

    /**
     * The frame to evaluate, between two ticks.
     *
     * <p>The state is advanced once per tick and read many times per frame, with the partial tick
     * in between. Evaluating only at whole ticks would make every motion in the game a twenty
     * hertz step, which is the difference between an arm moving and a machine stuttering.</p>
     *
     * <p>The charge and the guard are left for the caller to fill in: they are already tracked
     * for the HUD and duplicating them here would be two copies of one value.</p>
     *
     * @param partialTick 0 at the start of the tick, approaching 1 at the end
     */
    public ViewModelInput snapshot(float partialTick, float cooldownRunning, boolean guarding) {
        float offset = clamp01(partialTick);
        return new ViewModelInput(
                windupAt(offset),
                strikeAt(offset),
                cooldownRunning,
                guarding,
                (ticks + offset) / 20.0F,
                seed);
    }

    /**
     * The wind-up halfway between two ticks.
     *
     * <p>While the button is down this is the charge, which the tick already has. Once the swing
     * has resolved it is the unwind, and the phase is taken from the strike's age so the two move
     * as one number rather than as two that have to be kept in step by hand.</p>
     */
    private float windupAt(float offset) {
        if (holding) {
            return windup;
        }
        if (strikeAge < 0) {
            return 0.0F;
        }
        return clamp01(1.0F - (strikeAge + offset) / (float) STRIKE_TICKS);
    }

    private float strikeAt(float offset) {
        if (strikeAge < 0) {
            return 0.0F;
        }
        float phase = clamp01((strikeAge + offset) / (float) STRIKE_TICKS);
        return 1.0F - Math.abs(2.0F * phase - 1.0F);
    }

    private static float clamp01(float t) {
        return t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
    }

    /** How far back the item is drawn, 0..1, at the current tick. */
    public float windup() {
        return windup;
    }

    /**
     * The follow-through, as a bump: 0, up to 1 in the middle, back to 0.
     *
     * <p>Returning to zero is the whole reason for the shape. A channel that stayed at one would
     * leave the item permanently displaced once the motion ended, so every profile would need a
     * second track to undo its own first one, and the two would have to stay in agreement.</p>
     */
    public float strike() {
        return strikeAt(0.0F);
    }

    /** The tremble pattern for the swing in progress. Advances per strike, never per frame. */
    public int seed() {
        return seed;
    }

    /** Test hook: back to a clean rest. */
    public void reset() {
        strikeAge = -1;
        pendingStrike = false;
        windup = 0.0F;
        seed = 1;
        ticks = 0;
        holding = false;
    }
}
