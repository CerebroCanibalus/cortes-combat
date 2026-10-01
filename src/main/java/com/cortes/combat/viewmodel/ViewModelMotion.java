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
     * How long the strike lasts, in ticks, when nothing says otherwise.
     *
     * <p>Only a fallback. The real length is per weapon and comes in with {@link #onStrike}, because
     * a fixed one makes a sword and a fist weigh exactly the same and the playtest asked for both
     * to be heavier.</p>
     */
    public static final int STRIKE_TICKS = 6;

    /**
     * The floor and the ceiling on a swing's length.
     *
     * <p>The ceiling exists so that a sword with a very slow attack speed does not turn one swing
     * into a second of animation; at twelve ticks the follow-through is long enough to read and
     * short enough that the player is not waiting for the weapon to come back.</p>
     */
    public static final int MIN_STRIKE_TICKS = 4;
    public static final int MAX_STRIKE_TICKS = 12;

    /**
     * This swing's length, in ticks.
     *
     * <p>The heaviest single lever there is, and the one that was missing. The playtest kept
     * saying the swing had "very little weight" while the amplitudes were already past vanilla's
     * 80 degrees, and it was right: a big angle travelled in the same 300 milliseconds is still a
     * flick. Weight is mostly how long the weapon is committed to the arc.</p>
     *
     * <p>It cannot be one global number, because the weapons disagree about how much time they
     * have. A diamond sword may swing every twelve ticks and a fist every four, so a follow
     * through longer than a weapon's own interval gets cut off by the next swing. Deriving the
     * length from the interval means a heavy weapon looks heavy and a fast one looks quick
     * <em>for the same reason</em>, which is the one thing a fixed number could never do.</p>
     */
    private int strikeTicks = STRIKE_TICKS;

    /**
     * How much of the swing a tap gets, 0..1.
     *
     * <p>Not a damage number and never sent anywhere. It is the amplitude of the motion, and it
     * exists so that every attack swings and so that an ordinary swing is not a twitch.</p>
     *
     * <p>This number has moved three times and the reasons are worth keeping, because each move
     * was a correction of the one before.</p>
     *
     * <p><b>Zero</b>, on the reading that "a tap is the vanilla swing and the charge is what is
     * different". That left the weapon dead on four attacks out of five, which is not a subtle
     * difference, it is a prop.</p>
     *
     * <p><b>A third</b>, because a tap should read as lighter than a charge. The playtest then
     * said the ordinary swing "feels like very little weight" and asked for it to be "almost at
     * the level of the charge", which is the same complaint one round later: the tap was too
     * light. Worth noticing that a tap arriving as a <em>fraction</em> of a charge was called a
     * bad charge at a fifth and a weak swing at a third. The fraction was never the problem; the
     * absolute size was.</p>
     *
     * <p><b>So: 0.78.</b> What tells a tap from a charge is no longer this offset. It is the
     * wind-up the player watched build, the charge bar, the field of view, the tremble and the
     * damage number. The swing itself is the same gesture at two weights, because it is the same
     * gesture, and shrinking it to a twitch made the most common attack in the game the least
     * convincing one.</p>
     */
    public static final float TAP_FLOOR = 0.78F;

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

    /**
     * How big this swing moves, 0..1. A <b>rendering</b> number and nothing else.
     *
     * <p>Captured rather than read live, because the live charge is gone by the time the
     * follow-through plays. A swing has one amount of charge in it, decided at the release, and
     * the whole motion has to agree on which one that was.</p>
     *
     * <p>It has a floor, {@link #TAP_FLOOR}, so an ordinary click still swings. That floor is a
     * drawing decision and it is the reason this field is not the same thing as
     * {@link #classified}: when both were one value, the floor leaked into the field of view, and
     * every tap in the game zoomed the world in and back out. One number meaning two things is
     * how that happened.</p>
     */
    private float released;

    /**
     * The charge the last release carried, exactly as classified, 0 for a tap.
     *
     * <p>This is the fact. The field of view, the wind-up's hand-off and anything else that needs
     * to know how much charge there really was reads this, and it is not floored, because a tap
     * has no charge in it and pretending otherwise is what made the zoom fire on every click.</p>
     */
    private float classified;

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

    /**
     * Where the wind-up was a tick ago, so the hold can be interpolated between the two.
     *
     * <p>This is the second half of a bug that took three rounds of playtesting to name. The
     * charge rises once per <b>tick</b>, and the item's wind-up used to be read straight off it, so
     * the draw-back advanced in twenty visible steps a second: on a twenty-four tick charge, a jump
     * of one twenty-fourth every fifty milliseconds. The report was that the fist "does not
     * animate properly until the end of the charge and shakes strangely in between", and both
     * halves of that sentence were this. It is a staircase, and a staircase is read as a stutter
     * rather than as motion.</p>
     *
     * <p>The strike half was already interpolated, which is why the cut looked fine and only the
     * hold looked broken: the fix for the twenty-hertz swing was applied to the strike's clock and
     * not to the hold's, and the hold is where the player spends a second watching.</p>
     */
    private float windupPrev;

    /**
     * The same hold, at the threshold: zero until the swing counts as charged.
     *
     * <p>Two resolutions of one quantity, kept apart on purpose. The wind-up is raw because it is
     * the feedback that says "the press was heard", and it has to move on the very first tick.
     * The charge is thresholded because it is a claim about what the swing <em>is</em>, and a tap
     * is not a charged swing however briefly it was held.</p>
     *
     * <p>They were one field for a while and the field of view read the raw one, which is how
     * every ordinary click in the game nudged the camera: the wind-up is raw on purpose and the
     * camera is not about wind-ups.</p>
     */
    private float charge;

    /** The thresholded charge a tick ago, for the same interpolation the wind-up gets. */
    private float chargePrev;

    /**
     * How far the guard is up, 0..1, and how far it was a tick ago.
     *
     * <p>A ramp out of a boolean, for the same reason the wind-up is interpolated: the guard
     * changes on the tick and a flag would put the entire stance on one frame, which is a pop and
     * not an animation. It is also the only channel here that can go <b>down</b> while nothing
     * else is happening, which is why it holds an age rather than a phase.</p>
     */
    private float guard;
    private float guardPrev;

    /**
     * Ticks since the guard broke, or -1 when it has not.
     *
     * <p>A one-shot, like {@code strikeAge}, and for the same reason a channel exists instead of
     * a boolean: the break is a motion, and a motion has a shape.</p>
     */
    private int breakAge = -1;

    /**
     * The held item's remaining cooldown, 0 when ready.
     *
     * <p>Passed in rather than read, for the same reason the charge is: this class is pure and
     * knows nothing about items, and a number that arrives is a number that can be tested.</p>
     *
     * <p><b>Zero means ready, not one.</b> {@code getCooldownPercent} returns the fraction
     * <em>remaining</em> and returns zero outright when the item has no cooldown at all, which is
     * the opposite of the assumption that produced a guard pose that never appeared: every idle
     * tick read as "fully staggered", the guard channel was forced to zero sixty times a second,
     * and the collapse's edge fired on every tick of a descending counter. The two facts are in
     * this paragraph because both were got wrong the same way, and reading them off the method
     * name would have got them wrong again.</p>
     */
    private float stagger = 0.0F;

    /** Ticks since the stance last changed, and what the stance was before this tick. */
    private int guardAge;
    private boolean guarding;

    /**
     * The guard broke.
     *
     * <p>Called from the item's vanilla cooldown starting, which is the same clock the stagger
     * runs on. That is not a shortcut: it means the animation cannot be a different length than
     * the stun, and the two cannot be re-tuned apart by accident.</p>
     */
    public void onGuardBreak() {
        breakAge = 0;
    }

    /**
     * How far through the collapse the guard is, part way through a frame.
     *
     * <p>A bump, not a hold, and <b>fast</b>: a marker, not a motion. See
     * {@link #BREAK_TICKS} for why it stopped being the item cooldown's length.</p>
     */
    private float breakAt(float offset) {
        if (breakAge < 0) {
            return 0.0F;
        }
        float phase = clamp01((breakAge + clamp01(offset)) / (float) BREAK_TICKS);
        if (phase >= 1.0F) {
            return 0.0F;
        }
        // Out hard, back soft. A collapse that leaves and returns at the same speed is a shove
        // travelling out and coming back along the same line, which is the thing "no weight" has
        // meant every time the playtest has said it. The strike gets the same treatment from
        // vanilla's own `sin(sqrt(f) * PI)`, and it is not a coincidence.
        return (float) Math.sin(Math.sqrt(phase) * Math.PI * 0.5F);
    }

    /** Public so a test can pin the shape without reaching into the private field. */
    public float guardBreak() {
        return breakAt(0.0F);
    }
    /**
     * How long the guard takes to come up and to drop, in ticks.
     *
     * <p><b>The rise is two, and the number it replaced was eleven.</b> That is the playtest
     * overruling a decision that sounded like a good one, and the reason it was wrong is worth
     * keeping because the argument for it was not silly.</p>
     *
     * <p>The old reasoning was that raising a weapon is a decision and dropping it is a reflex,
     * so the two should be different speeds. The observation is real. What it missed is
     * <b>when</b> the guard actually goes up: it goes up the instant the server reads the press,
     * which is one packet. So an eleven tick ramp was not weight, it was eleven ticks of the
     * animation disagreeing with the gameplay, and the player feels that as the game not
     * listening -- "se siente poco responsivo", verbatim.</p>
     *
     * <p>Two ticks is a hundred milliseconds, which is about the round trip itself, so the pose
     * lands with the guard instead of trailing it. This is the same lesson as the release, where
     * a channel's length has to be chosen for the motion and not borrowed from another system's
     * clock; eleven was borrowed from a theory about weight that the real timing did not
     * support.</p>
     *
     * <p>The fall stays at six and stays slower than the rise, and now for a reason that is
     * actually true: letting go is a decision too, and the weapon settling is the one moment
     * where a little weight reads as weight rather than as lag.</p>
     */
    public static final int GUARD_RISE_TICKS = 2;
    public static final int GUARD_FALL_TICKS = 6;

    /**
 * How long the collapse lasts, in ticks.
 *
 * <p>Seven. Deliberately its own number, and the reason is worth writing down because it was
 * <b>wrong</b> before and the fix looked like elegance.</p>
 *
 * <p>The first version timed the collapse by the item's own vanilla cooldown, on the argument
 * that the animation and the stagger could then never drift apart. That is true and it is the
 * wrong question: a sword's stagger is a hundred ticks. Five seconds of the weapon sliding
 * backwards is not a marker saying "your guard broke", it is a fifth animation the player has
 * to wait out, and the playtest read it in one word -- it "is a piece of crap".</p>
 *
 * <p>A collapse is a <b>punch</b>: something happens fast, it is over, and then the weapon is at
 * rest while the gameplay consequence plays out. Seven ticks is about a third of a second, which
 * is short enough to read as a hit landing and long enough to be seen at all. The stagger does
 * not need the animation to be told about it -- it has its own HUD element and its own sound.</p>
 */
    public static final int BREAK_TICKS = 7;

    /**
 * Feed the held item's remaining cooldown, once a tick, and fire the collapse on the edge.
 *
 * <p>This lives here rather than in the caller for two reasons, and the second one is the
 * embarrassing one.</p>
 *
 * <p><b>One.</b> The rule "a break is a transition, not a state" belongs next to the state it
 * guards.</p>
 *
 * <p><b>Two.</b> It was in the caller, and the tests were green anyway. The motion's own collapse
 * was correctly bounded to seven ticks; what made it a five second drift was the caller firing
 * the edge on <em>every descending tick of the cooldown</em>, restarting a seven tick bump a
 * hundred times. A component test cannot catch a bug in the wiring, which is the whole limit of
 * component tests and worth being blunt about rather than learning again.</p>
 *
 * <p>The edge is "nothing was on cooldown and now something is", because that is what a break
 * <em>is</em>. Not "the number went down", which is what a descending counter looks like.</p>
 *
 * @param staggerPercent the item's remaining cooldown, 0 when ready
 */
    public void observeStagger(float staggerPercent) {
        float now = clamp01(staggerPercent);
        if (lastObservedStagger <= 0.0F && now > 0.0F) {
            breakAge = 0;
        }
        lastObservedStagger = now;
    }

    /** What {@link #observeStagger} last saw, so the edge can be a transition. */
    private float lastObservedStagger = 0.0F;

    /** Client ticks elapsed. The only clock, so the tremble and the swing cannot disagree. */
    private int ticks;

    /** Whether the button was down as of the last tick. Decides which of the two wind-ups applies. */
    private boolean holding;

    /**
     * The strike just resolved, and how charged it was.
     *
     * <p>The charge is passed in rather than read from the wind-up, and it is the single most
     * important argument in this class. Scaling the whole strike by it is what makes a tap read
     * as a tap: before, the strike channel was the same size for every release, so a two tick
     * click played the same thirty degree swing as a full charge and looked like a charge that
     * had gone wrong.</p>
     *
     * <p>It also makes the viewmodel a readout of the rule. The caller passes the same value
     * the server will use, threshold included, so what the player sees is the classification
     * their swing is about to get rather than a guess at it.</p>
     *
     * @param charge 0 for a tap, up to 1 for a full charge
     */
    public void onStrike(float charge, int durationTicks) {
        // A tap is a real cut, and it is nearly as big a one as a charge.
        //
        // This floor was zero, then a third, and the playtest moved it twice. It was zero because
        // "a tap is the vanilla swing and the charge is what is different", which left the weapon
        // dead on four attacks out of five. It was a third because a tap should read as lighter,
        // which turned out to be wrong in the other direction: the report was that the ordinary
        // swing "feels like very little weight" and should be "almost at the level of the charge".
        //
        // So the tap is close to the full thing. What still tells them apart is the wind-up, the
        // charge bar, the field of view and the damage number, and none of those are in this
        // offset. The swing is the one motion that should be legible as the same gesture at two
        // different weights, because it is the same gesture.
        released = TAP_FLOOR + (1.0F - TAP_FLOOR) * clamp01(charge);
        // The same number, unfloored. One value for what happened and one for how far the item
        // travels, because they answer different questions and the day they shared one, the floor
        // shipped itself into the field of view.
        classified = clamp01(charge);
        strikeTicks = Math.max(MIN_STRIKE_TICKS, Math.min(MAX_STRIKE_TICKS, durationTicks));
        pendingStrike = true;
        // An integer LCG rather than Random: one multiply and one add, no shared global state,
        // and the only thing asked of it is that consecutive swings differ while the same swing
        // stays reproducible, which any advancing integer gives.
        seed = seed * 1664525 + 1013904223;
    }

    /** The swing length this motion is currently playing, in ticks. For tests and debug output. */
    public int strikeTicks() {
        return strikeTicks;
    }

    /** Whatever was held is no longer held, and there is no strike to play. */
    public void onCancel() {
        strikeAge = -1;
        pendingStrike = false;
        released = 0.0F;
        classified = 0.0F;
    }

    /**
     * One client tick of state.
     *
     * @param holding      whether the attack button is down
     * @param rawCharge    the hold's raw progress, 0..1. This is what the wind-up reads.
     * @param chargedRatio the same hold, thresholded. This is what anything that needs to know
     *                     whether the swing is a <em>charge</em> reads
     * @param guarding     whether the guard is up right now
     * @param stagger      the held item's remaining cooldown, 0 when ready. Only decides whether the
     *                     guard is up, because that is the one thing the stagger and the stance
     *                     have to agree about
     */
    public void tick(boolean holding, float rawCharge, float chargedRatio, boolean guarding,
                     float stagger) {
        ticks++;
        this.stagger = clamp01(stagger);
        // Its own clock, not the item's. See BREAK_TICKS: tying the collapse to the stagger made
        // a five second marker out of a five second stun.
        if (breakAge >= 0 && ++breakAge >= BREAK_TICKS) {
            breakAge = -1;
        }
        if (holding) {
            // A new press abandons whatever the last one was doing, and zeroes the charge it
            // released with. Otherwise a fast second click would inherit the first one's charge
            // and the wind-up would jump to a value the player never charged to.
            strikeAge = -1;
            pendingStrike = false;
            released = 0.0F;
            classified = 0.0F;
        } else if (pendingStrike) {
            strikeAge = 0;
            pendingStrike = false;
        } else if (strikeAge >= 0 && ++strikeAge >= strikeTicks) {
            // The strike is over, so the charge it carried is over with it.
            //
            // Not zeroing this is what left the FOV narrowed forever: `currentCharge` falls back
            // to the released charge when the button is up, so a value that was set once and
            // never cleared kept the world zoomed in for the rest of the session, and only
            // grabbing a different item cleared it. A follow-through that ends is the end of
            // everything the charge was driving, including the field of view.
            strikeAge = -1;
            released = 0.0F;
            classified = 0.0F;
        }
        float next = targetWindup(holding, rawCharge);
        // The hold's two ends, so the frame between them can be interpolated. Without this the
        // draw-back is a twenty-hertz staircase, which is what the fist's "shakes strangely"
        // turned out to be. See windupPrev.
        //
        // Except on the press itself, which is a discontinuity and not a ramp: the new draw-back
        // starts from nothing, and interpolating towards it would leave the item drawn at the
        // PREVIOUS swing's wind-up for one frame, which is a pop on exactly the frame the player
        // pressed. A test caught this one, and it was right to.
        windupPrev = holding != this.holding ? next : windup;
        chargePrev = holding != this.holding ? chargedRatio : charge;
        this.holding = holding;
        windup = next;
        charge = chargedRatio;

        // The guard is the one ramp that can go down, so it counts ticks since the stance changed
        // rather than reading a phase off a strike. Rise and fall are different lengths; see
        // GUARD_RISE_TICKS.
        //
        // A staggered item drops the stance <b>at once</b>, and that is not the same thing as
        // letting go. The guard fell over six ticks because someone let go; it is gone because
        // something knocked it down, and a knock that takes a third of a second to land is not a
        // knock. The collapse itself is the BREAK channel's motion, so between the two of them
        // the weapon is off line on the same tick and back before the player can act again.
        float before = guard;
        boolean staggered = this.stagger > 0.0F;
        boolean up = guarding && !staggered;
        boolean changed = up != this.guarding;
        if (staggered) {
            // Checked before the button, because a stagger is a stronger statement than a
            // release. The guard fell over six ticks because someone let go; it is gone because
            // something knocked it down, and a knock that takes a third of a second to land is
            // not a knock. The collapse is the BREAK channel's motion, so between the two of them
            // the weapon is off line on this tick.
            //
            // Ordering matters and getting it wrong is invisible: `changed` is true on this same
            // tick whenever the player was holding, and it used to win, so the stance hung there
            // fully raised for the whole stagger with the collapse played on top of it.
            guardAge = 0;
            guard = 0.0F;
        } else if (changed) {
            // The press itself is the first tick of the rise, not tick zero of one that has not
            // started. Counting from zero made GUARD_RISE_TICKS lie: holding for exactly eleven
            // ticks reached nine elevenths, and a constant that does not mean what it says is a
            // constant that gets re-tuned by accident.
            guardAge = 1;
            guard = up
                    ? Math.min(1.0F, guardAge / (float) GUARD_RISE_TICKS)
                    : before;
        } else {
            guardAge++;
            guard = up
                    ? Math.min(1.0F, guardAge / (float) GUARD_RISE_TICKS)
                    : Math.max(0.0F, 1.0F - guardAge / (float) GUARD_FALL_TICKS);
        }
        // Interpolation is skipped across the frame the stance starts or stops. Interpolating
        // there would leave the weapon a third of a stance behind for one frame, on the exact
        // frame the player pressed or released.
        guardPrev = changed ? guard : before;
        this.guarding = guarding;
    }

    private float targetWindup(boolean holding, float charge) {
        if (holding) {
            return charge;
        }
        if (strikeAge < 0) {
            return 0.0F;
        }
        return 1.0F - (float) strikeAge / (float) strikeTicks;
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
    /**
     * One client tick of the clock only.
     *
     * <p>Separate from {@link #tick} because the item renderer reads this on every frame and
     * the swing state only advances on ticks. A reader that advanced the clock would make the
     * tremble run at the frame rate.</p>
     */
    public void advanceClock() {
        ticks++;
    }

    public ViewModelInput snapshot(float partialTick, float cooldownRunning) {
        float offset = clamp01(partialTick);
        return new ViewModelInput(
                windupAt(offset),
                strikeAt(offset),
                recoverAt(offset),
                cooldownRunning,
                guardAt(offset),
                breakAt(offset),
                holding,
                (ticks + offset) / 20.0F,
                seed);
    }

    /** How far the guard is up, part way through a frame. */
    public float guardAt(float offset) {
        float t = clamp01(offset);
        return guardPrev + (guard - guardPrev) * t;
    }

    /**
     * How far through the swing we are, 0..1.
     *
     * <p>Absent the hold the swing is over, and reading it as one rather than as a
     * divide-by-zero is a small thing to be explicit about: a channel whose denominator is
     * "the number of ticks the swing lasts" has to answer a question when the swing is not
     * happening, and the answer is zero, not a division.</p>
     */
    private float phaseAt(float offset) {
        return strikeAge < 0 ? 0.0F : (strikeAge + offset) / (float) strikeTicks;
    }

    /**
     * The weight beat, 0..1: the motion that goes down and back to where the hand is.
     *
     * <p>This is the beat that was missing, and the playtest is what named it: the weight is not
     * part of the attack, it is a separate motion. So it gets its own channel that starts after
     * the cut has already arrived, instead of being a property of the cut's own curve.</p>
     *
     * <p>It is a bump over the tail, so it contributes nothing while the blade is still going out
     * and it is back at rest when the swing ends. <b>Nothing here holds</b>: the item is exactly
     * at the vanilla pose on the last frame, which is the invariant the whole channel design
     * exists to keep. The channel it replaced held its peak and snapped, and the playtest read
     * the result as "no weight" rather than as a pop, because the pop came at the very end of a
     * motion the eye had already stopped following.</p>
     */
    private float recoverAt(float offset) {
        if (strikeAge < 0 || released <= 0.0F) {
            return 0.0F;
        }
        float phase = phaseAt(offset);
        if (phase <= RECOVER_AT || phase >= 1.0F) {
            return 0.0F;
        }
        float local = (phase - RECOVER_AT) / (1.0F - RECOVER_AT);
        // A bump over the tail: zero at the hand-off, one at the end of the fall, back to zero.
        return released * (1.0F - Math.abs(2.0F * local - 1.0F));
    }

    /**
     * Where the weight beat starts, as a fraction of the swing.
     *
     * <p>Late enough that the cut has already arrived. Starting it earlier averages the two
     * motions into one shove, which is the thing three rounds of playtesting kept reporting.</p>
     */
    public static final float RECOVER_AT = 0.45F;

    /**
     * The wind-up halfway between two ticks.
     *
     * <p>While the button is down this is the charge, <b>interpolated between the last two
     * ticks</b>. The charge only moves once a tick, and reading it raw made the draw-back climb in
     * twenty visible steps: on a twenty-four tick charge, a jump of one twenty-fourth every fifty
     * milliseconds. That is a staircase, and a staircase is read as a stutter rather than as
     * motion, which is exactly what the fist was reported as doing.</p>
     *
     * <p>Once the swing has resolved it is the unwind, and the phase is taken from the strike's
     * age so the two move as one number rather than as two that have to be kept in step by hand.
     * It unwinds from the <b>classified</b> charge, not from the amplitude, so the hand-off is
     * continuous: a tap has no charge to unwind, so there is nothing to hand off, and a full
     * charge unwinds from the one the player watched build. Scaling this by the amplitude instead
     * made a two-tick click jump from nothing to most of the draw-back in a single frame.</p>
     */
    private float windupAt(float offset) {
        float t = clamp01(offset);
        if (holding) {
            return windupPrev + (windup - windupPrev) * t;
        }
        if (strikeAge < 0) {
            return 0.0F;
        }
        return classified * clamp01(1.0F - (strikeAge + offset) / (float) strikeTicks);
    }

    /**
     * The cut, on <b>vanilla's own curve</b>, as a bump scaled by the released charge.
     *
     * <p>The shape is {@code sin(sqrt(f) * PI)}, read off {@code applyItemArmAttackTransform} in
     * the 1.21.1 jar. The square root is the whole point and it is not a detail: it makes the
     * curve rise almost immediately and return over the rest of the swing, so the motion is
     * <b>fast out, slow back</b>. A triangle peaks in the middle and a plain sine peaks at a
     * quarter, so both of those leave and come back at the same speed, which is a machine.
     * Asymmetry is what an arm swinging looks like.</p>
     *
     * <p>It also <b>ends at zero</b>, which the channel it replaced did not. That one held its
     * peak for the rest of the swing, on the theory that a bump "arrives and undoes itself in the
     * same instant", and the consequence was that the item was still rotated seventy odd degrees
     * when the swing ended and <b>snapped</b> to the vanilla pose in a single frame. Holding was a
     * fix for a misdiagnosis: what made the cut read as a shove was that it was a translation in
     * the wrong frame, and once that was fixed the hold was only ever in the way.</p>
     *
     * <p>Scaled by the released charge, so a tap is a small jab rather than a full swing played
     * quickly.</p>
     */
    private float strikeAt(float offset) {
        if (strikeAge < 0) {
            return 0.0F;
        }
        float phase = clamp01((strikeAge + offset) / (float) strikeTicks);
        if (phase >= 1.0F) {
            return 0.0F;
        }
        return released * (float) Math.sin(Math.sqrt(phase) * Math.PI);
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

    /** The recovery bump, 0..1. See {@link Channel#RECOVER}. */
    public float recover() {
        return recoverAt(0.0F);
    }

    /**
     * The charge driving everything right now, 0..1, or 0 when nothing is happening.
     *
     * <p>The <b>classified</b> charge and not the swing's amplitude. Those were one value, and the
     * amplitude has a floor of {@link #TAP_FLOOR} so an ordinary click still swings, so every tap
     * in the game reported a charge of 0.78 to the field of view. The playtest called it "the
     * sword zooms in and out on every attack, because any attack starts the charge", and it was
     * right down to the mechanism: a tap has no charge in it, and the only thing that said
     * otherwise was a rendering constant.</p>
     *
     * @param offset the partial tick, so a reader that runs per frame gets a curve rather than
     *               the twenty-hertz staircase the tick produces
     */
    public float chargeAt(float offset) {
        if (holding) {
            float t = clamp01(offset);
            return chargePrev + (charge - chargePrev) * t;
        }
        return strikeAge < 0 ? 0.0F : classified;
    }

    /** {@link #chargeAt} at the current tick. */
    public float currentCharge() {
        return chargeAt(0.0F);
    }

    /** The charge the last strike was classified as, 0 for a tap. Not the amplitude. */
    public float classifiedCharge() {
        return classified;
    }

    /** The tremble pattern for the swing in progress. Advances per strike, never per frame. */
    public int seed() {
        return seed;
    }

    /** How big the last swing moved, 0..1. A rendering number, floored by {@link #TAP_FLOOR}. */
    public float released() {
        return released;
    }

    /** Test hook: back to a clean rest. */
    public void reset() {
        strikeAge = -1;
        pendingStrike = false;
        windup = 0.0F;
        windupPrev = 0.0F;
        charge = 0.0F;
        chargePrev = 0.0F;
        guard = 0.0F;
        guardPrev = 0.0F;
        guardAge = 0;
        guarding = false;
        released = 0.0F;
        classified = 0.0F;
        seed = 1;
        ticks = 0;
        holding = false;
    }
}
