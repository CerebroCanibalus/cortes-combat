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
     * How far the parry has come up, 0..1, and how far it was a tick ago.
     *
     * <p>The same shape as the guard, because both are stances a player turns on and off, and for
     * the same reason it is interpolated rather than flagged: it changes on the tick and a flag
     * would put the whole blade on one frame.</p>
     *
     * <p>Kept apart from the guard because the two move on different clocks and end differently.
     * The guard follows a button; the parry follows a twelve tick window that runs out whether or
     * not anything hit it, and it starts the blade going out on its own rather than on a hold.</p>
     */
    private float parry;
    private float parryPrev;

    /**
     * How far the lunge has gone, and how far it was a tick ago.
     *
     * <p>Interpolated and ramped exactly like the parry, for the same two reasons: the state
     * changes on the tick and a flag would put the whole step on one frame, and a ramp that can
     * only fall from up is the one that rests at zero.</p>
     */
    private float lunge;
    private float lungePrev;

    /** Whether the step is happening, which is what the ramp is built from. */
    private boolean lunging;

    /** Ticks since the step started. */
    private int lungeAge;

    // **The arcabuz's three.** Held is a flag rather than an age, because it is a state and not a
    // movement; the other two are an age each, and they are the only channels here whose length is
    // decided by the server rather than by a constant on this class.

    /** Whether the weapon is loaded and ready. Interpolated for the same reason as everything. */
    private float held;

    /** The same, a tick ago. */
    private float heldPrev;

    /** Ticks since the reload went out, or -1 when it has not. */
    private int reloadAge;

    /** The reload itself. */
    private float reload;

    /** The same, a tick ago. */
    private float reloadPrev;

    /** Ticks left on the fuse, or -1 when nothing is lit. */
    private int shootAge;

    /** How far through the fuse we are, 0..1. */
    private float shoot;

    /** The same, a tick ago. */
    private float shootPrev;

    /**
     * The whole length of the shot, in ticks, as the server last reported it.
     *
     * <p>Not {@link #SWEEP_TICKS} and not any other constant here: the fuse is a weapon property
     * and it is a number someone will eventually want to tune without recompiling the animation.
     * The recoil and the visible burn are read off the same counter, so they cannot be two
     * numbers that disagree about how long the shot is.</p>
     */
    private int shootLength = 1;

    /**
     * How far the sweep has gone, and how far it was a tick ago.
     *
     * <p>A one-shot bump like the strike, and interpolated for the same reason everything here is:
     * the motion is advanced on the tick and drawn sixty times a second, so without the previous
     * value the arc would step.</p>
     */
    private float sweep;

    /** The same, a tick ago. */
    private float sweepPrev;

    /** Ticks since the chop started, and its value a tick ago. See {@link #chopAt}. */
    private float chopPrev;

    private float chop;
    /** Ticks since the sweep went out, or -1 when it has not. */
    private int sweepAge;

    /** Whether the server has the parry window open, which is what the ramp is built from. */
    private boolean parrying;

    /** Ticks since the window opened, for the same reason the guard holds an age. */
    private int parryAge;

    /**
     * Ticks since the guard broke, or -1 when it has not.
     *
     * <p>A one-shot, like {@code strikeAge}, and for the same reason a channel exists instead of
     * a boolean: the break is a motion, and a motion has a shape.</p>
     */
    private int breakAge = -1;

    /**
     * The parry lockout, as a second collapse on the same channel, and how long it lasts.
     *
     * <p><b>The same bump as the guard breaking, stretched to the lockout.</b> The player already
     * knows what an off-line weapon means -- they watched it happen when a shield came down -- so
     * the sabre reusing that shape is a word they already speak. The length carries the number: the
     * blade drops away on the press and comes back as the five seconds run out, and watching it
     * come back <em>is</em> the recharge readout, without a single pixel of HUD for it.</p>
     *
     * <p>A separate clock and not a shared one, so the two cannot fight. They also cannot happen
     * at once -- the sabre's guard profile is NONE, so it never goes up and never breaks -- but
     * "cannot happen" is a property of a decision someone can reverse, and a bump that stopped
     * being drawn because someone re-enabled the guard would be a bug report nobody could
     * reproduce.</p>
     */
    private int parryBreakAge = -1;

    /** How many ticks this lockout bump lasts, which is the server's own cooldown length. */
    private int parryBreakTicks;

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

    /**
     * The parry lockout, as the guard's collapse with the guard's own rise and fall.
     *
     * <p><b>Not the collapse curve stretched. The guard's two lengths, verbatim.</b> This was the
     * first version and it was wrong in a way the numbers hid: {@code sin(sqrt(f) * PI / 2)} is
     * fast-out-slow-in <em>over its own span</em>, so running it over a hundred ticks made the
     * departure take fifty of them. The blade drifted off line over half a second, which is not
     * the motion it is supposed to be borrowing -- the guard leaves in two ticks and anything
     * slower than that reads as a weapon being carried, not as one being knocked aside.</p>
     *
     * <p>So it is a ramp with a hold: out in {@link #GUARD_RISE_TICKS}, off the line for the
     * middle, back in over {@link #GUARD_FALL_TICKS}. The hold is what the player reads as "not
     * right now" and the fall is what they read as "soon" -- and the total is the server's own
     * number, so the weapon is off line for exactly as long as the sabre is locked and not one
     * tick longer.</p>
     *
     * <p>Taken as the <b>further</b> of the two collapses and not their sum. This channel is a
     * displacement, and two displacements on one axis resolve to the further one -- adding them
     * would make a guard break during a parry lockout throw the weapon off the screen, which is a
     * compound of two small things into one large one, and the sum of two reasons to be knocked
     * off line is not twice as knocked off line.</p>
     */
    private float parryBreakAt(float offset) {
        if (parryBreakAge < 0 || parryBreakTicks <= 0) {
            return 0.0F;
        }
        int fallStart = parryBreakTicks - GUARD_FALL_TICKS;
        int age = parryBreakAge + (int) clamp01(offset);
        if (age >= parryBreakTicks) {
            return 0.0F;
        }
        if (age < GUARD_RISE_TICKS) {
            return age / (float) GUARD_RISE_TICKS;
        }
        if (age < fallStart) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (age - fallStart) / (float) GUARD_FALL_TICKS);
    }

    /**
     * The server's parry answer, and the edge off which the lockout bump starts.
     *
     * <p><b>Only an opening starts it.</b> The first version inferred a press from the cooldown
     * going <em>up</em>, and that is wrong twice over. A parry that <em>connects</em> sends the full
     * length a second time, because {@code consume()} closes the window without touching the
     * cooldown -- so the bump restarted at the moment of the hit and the weapon came back a
     * hundred ticks after the parry landed instead of ninety-five. And the client's own countdown
     * runs against the server's, so ordinary jitter between the two clocks also reads as "the
     * number went up" on a close that nobody pressed.</p>
     *
     * <p>The server is the only thing that knows a press happened, and it says so by opening the
     * window. A close carries the remainder as information and starts nothing.</p>
     *
     * <p>An edge, for the same reason {@link #observeStagger} is one: a bump driven off a
     * descending counter restarts on every tick of that counter, and a hundred tick bump held at
     * its first hundredth is a weapon parked off line with no way back.</p>
     */
    public void observeParry(boolean active, int cooldown) {
        if (active && cooldown > 0) {
            parryBreakAge = 0;
            parryBreakTicks = cooldown;
        }
    }

    /**
     * The weapon in hand is not the one the parry belongs to, so the lockout stops.
     *
     * <p>A separate method and not a zero cooldown, because a zero only stops a <em>new</em> bump
     * from starting -- a bump already running would sail the rest of its hundred ticks onto a
     * weapon that has nothing to do with it. Switching from the sabre to a sword half way through
     * the lockout is exactly the moment that was visible, and the sword's own {@code BREAK} track
     * is the one that drew it.</p>
     */
    public void clearParry() {
        parryBreakAge = -1;
        parryBreakTicks = 0;
    }

    /** The step is over, on the same terms as {@link #clearParry()}. */
    public void clearLunge() {
        lunge = 0.0F;
        lungePrev = 0.0F;
        lungeAge = 0;
        lunging = false;
    }

    /**
     * The lunge, part way through a frame.
     *
     * <p>Not interpolated through a field that holds a value the caller set: the step is four
     * ticks out of twenty, and a caller that passed a ratio would be reporting a progress bar
     * nobody asked for.</p>
     */
    public float lungeAt(float offset) {
        float t = clamp01(offset);
        return lungePrev + (lunge - lungePrev) * t;
    }

    /**
     * Throw the sweep, and start its clock.
     *
     * <p>A bump rather than a flag, on the same terms as the strike: it starts here and is over in
     * {@link #SWEEP_TICKS}, resting at zero the rest of the time, so no profile needs a track whose
     * only job is undoing the last one.</p>
     *
     * <p>Restarting an arc that is already going is the interesting case. Pressing the button twice
     * inside twelve ticks should read as <em>a wider, faster swing</em>, not as two arcs fighting
     * over one channel, so the press pulls the age back to one rather than queueing a second.</p>
     */
    public void onSweep() {
        sweepAge = 1;
    }

    /**
     * The sweep is not this weapon's, on the same terms as {@link #clearParry()}.
     *
     * <p>The caller asks the profile whether it reads the channel first, and a profile that does
     * not gets told to forget it. Without that a weapon the player swaps to mid-arc inherits a
     * signal meant for the halberd -- which is how the sabre once spent five seconds drawn
     * collapsed because a global channel was filtered to the wrong item.</p>
     */
    public void clearSweep() {
        sweep = 0.0F;
        sweepPrev = 0.0F;
        sweepAge = 0;
    }

    /**
     * The chop's own clock, in ticks, and it is NOT the strike's.
     *
     * <p><b>This is the fix for "it still starts from rest and then hits".</b> The chop was read
     * off {@code strikeAge}, and {@code strikeAge} is not set on the tick of the release: the
     * release only announces the strike ({@code pendingStrike}) and the age starts on the tick
     * after. Correct for the strike bump, which is a shape that begins at zero anyway -- and wrong
     * for the chop, which is a HEIGHT. One frame at zero is one frame of the arm hanging at the
     * player's side, and then it is at the top, and the player sees a weapon that was resting and
     * then struck.</p>
     *
     * <p>So the chop gets its own age, set to zero by {@code onStrike} on the release itself, and
     * the height is already high on the first frame. The rest of the motion is the same clock's
     * business; only the first frame is the fix.</p>
     */
    private int chopAge = -1;

    /**
     * The build-up, in ticks: two, which is a tenth of a second.
     *
     * <p><b>"It needs a tiny build-up so it does not feel like it hits suddenly."</b> A weapon that
     * is at full height on frame zero and falling on frame one has no weight, because weight is
     * the two hundred milliseconds before something moves. Two ticks is the smallest length that
     * is visible at all -- one tick is a third of a frame at sixty hertz -- and it is the number
     * that was asked for by name rather than one that looked right.</p>
     */
    public static final int CHOP_BUILDUP_TICKS = 2;

    /**
     * The fall, in ticks: four.
     *
     * <p><b>A quarter of a second in total with the build-up, and that is the impact.</b> Impact is
     * speed over distance, and the distance was never the problem -- the blade travels from fully
     * raised to fully down either way. Six ticks of fall borrowed from the strike's clock put a
     * weight that is supposed to feel heavy into a tenth of a second of nothing happening, and the
     * playtest read it as "too soft and with no impact".</p>
     *
     * <p><b>Four is not a guess against six.</b> It is what is left after saying the two halves are
     * two ticks of anticipation and two of arrival, which is the shape of a swing: a beat of
     * gathering and a beat of letting go.</p>
     */
    public static final int CHOP_FALL_TICKS = 4;

    /**
    /**
     * How high the chop is on its very first frame, out of the full height.
     *
     * <p><b>And it is not a taste number: it is the continuity of the hand-off.</b> The wind-up
     * leaves the arm at {@code CHARGE_ARM_PITCH}, and the chop has to start where the wind-up left
     * it or the arm steps on the tick the button comes up. So this is the wind-up's height over
     * the chop's, and the two are chosen together -- 0.85 of 122 is 103.7 against a wind-up of
     * 104, which is a third of a degree.</p>
     *
     * <p><b>Starting at zero is what the playtest rejected.</b> "It still starts from rest and then
     * hits" is a weapon that was hanging at the player's side and then struck, and it was the
     * whole of the first complaint about the chop. Starting high means the blade is already up
     * before the build-up adds anything.</p>
     */
    static final float CHOP_START = 0.743F;

    /**
     * The chop: already high on the release, two ticks of build-up, then the fall.
     *
     * <p><b>Three parts and each one is a different complaint answered.</b> It opens at
     * {@link #CHOP_START} so the weapon is up before anything moves; it rises to the full height
     * over {@link #CHOP_BUILDUP_TICKS} so there is weight; and it falls as the square of what is
     * left, front-loaded, so the arm settles rather than stops.</p>
     *
     * <p><b>Scaled by the swing's own amplitude, and this is not optional.</b> The first version was
     * the fall on its own and a test caught the consequence at once: a full charge and a tap
     * travelled exactly the same distance, so the swing had stopped being a swing. The strike
     * bump was always {@code released * sin(...)} and dropping the amplitude is how a charged
     * weapon ends up looking identical to an ordinary click.</p>
     */
    public float chopAt(float offset) {
        if (chopAge < 0 || released <= 0.0F) {
            return 0.0F;
        }
        float t = chopAge + offset;
        if (t < 0.0F) {
            t = 0.0F;
        }

        if (t < CHOP_BUILDUP_TICKS) {
            float rise = CHOP_START + (1.0F - CHOP_START) * ((t + 1.0F) / CHOP_BUILDUP_TICKS);
            return released * Math.min(1.0F, rise);
        }
        float falling = t - CHOP_BUILDUP_TICKS;
        // **Its own length, and it is the shortest clock in the library.**
        //
        // The chop used to borrow `strikeTicks`, which is six -- a tenth of a second of falling for
        // a weapon described as heavy. Impact is speed over distance, and the distance was fine, so
        // the speed is what had to change: four ticks is a sixth of a second, and together with the
        // build-up the whole motion is a quarter of one.
        float span = Math.max(1, CHOP_FALL_TICKS);
        if (falling >= span) {
            return 0.0F;
        }
        float through = falling / span;
        // **Smoothstep, inverted, and the previous curve was exactly backwards.**
        //
        // The old one was `(1 - t)^2`, whose slope is steepest at t=0 and flat at t=1: the blade
        // leaves the top at full speed and coasts to a stop. That is a weapon being *thrown*, and
        // the playtest called it "too soft and with no impact" -- which is what a decelerating fall
        // looks like, because the fastest part of the motion is over before the eye has caught up.
        //
        // `1 - (3t^2 - 2t^3)` is flat at both ends and steepest in the middle. **The blade is slow
        // off the mark, fastest through the arrival, and settles into rest**, and the arrival is
        // where the eye is. That is the whole difference between a swing and a chop, and it is the
        // same reason a bullet leaves the barrel fast rather than accelerating into the target.
        float eased = through * through * (3.0F - 2.0F * through);
        return released * (1.0F - eased);
    }

    /** The chop right now, high on the release and zero once the swing is over. */
    public float chop() {
        return chopAt(0.0F);
    }

    /** The chop's value a tick ago, for the interpolation. Public so a test can read the pair. */
    public float chopPrev() {
        return chopPrev;
    }

    /** The sweep, part way through a frame. */
    public float sweepAt(float offset) {
        float t = clamp01(offset);
        return sweepPrev + (sweep - sweepPrev) * t;
    }

    // ---------------------------------------------------------------- the arcabuz

    /**
     * The fuse is lit, and this is how long the whole shot is.
     *
     * <p><b>The length comes in with the call and is not a constant here.</b> The recoil and the
     * visible burn are both read off this one counter, so "how long the gun is held up" and "how
     * far through the fuse the sprite is" cannot become two numbers that disagree.</p>
     */
    public void observeFuse(boolean lit, int lengthTicks) {
        if (!lit) {
            shootAge = -1;
            shoot = 0.0F;
            return;
        }
        // A length of zero or less would divide by nothing, and a shot that has no length would
        // jump the ramp from nothing to full on one frame.
        shootLength = Math.max(1, lengthTicks);
        if (shootAge < 0) {
            shootAge = shootLength;
        }
    }

    /** The weapon is loaded and waiting for a fuse, or it is not. */
    public void observeLoaded(boolean loaded) {
        held = loaded ? 1.0F : 0.0F;
    }

    /** The reload was thrown. */
    public void onReload() {
        reloadAge = 1;
    }

    /** The arcabuz is not this weapon, so the client forgets all three. */
    public void clearHeld() {
        held = 0.0F;
        heldPrev = 0.0F;
        reload = 0.0F;
        reloadPrev = 0.0F;
        reloadAge = 0;
        shoot = 0.0F;
        shootPrev = 0.0F;
        shootAge = -1;
    }

    public float heldAt(float offset) {
        float t = clamp01(offset);
        return heldPrev + (held - heldPrev) * t;
    }

    public float reloadAt(float offset) {
        float t = clamp01(offset);
        return reloadPrev + (reload - reloadPrev) * t;
    }

    public float shootAt(float offset) {
        float t = clamp01(offset);
        return shootPrev + (shoot - shootPrev) * t;
    }

    /** Public so a test can pin the shapes without reaching into the private fields. */
    public float held() {
        return held;
    }

    public float reload() {
        return reload;
    }

    public float shoot() {
        return shoot;
    }

    /** Public so a test can pin the shape without reaching into the private field. */
    public float sweep() {
        return sweep;
    }

    /** Public so a test can pin the shape without reaching into the private field. */
    public float guardBreak() {
        return Math.max(breakAt(0.0F), parryBreakAt(0.0F));
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
     * How fast the blade comes out for a parry, and how fast it comes back, in ticks.
     *
     * <p>Three and three, and neither number is the guard's. The guard rose in two because a
     * stance the player is holding up has to be up by the time they decide to swing. The parry
     * cannot borrow that argument, because the blade going out is not a consequence of a decision
     * the player is still making: it is the announcement, and it is three ticks because the window
     * is twelve and a quarter of the window spent getting there would make the parry feel like it
     * started late.</p>
     *
     * <p>It falls in three as well, which is the asymmetry that matters. The guard's fall is six
     * because settling under your own weight reads as weight. This one has no weight to settle
     * under -- the player is being slowed to a crawl the entire time the blade is out, and the
     * relief of it coming back is the only fast thing about the exchange. A slow return would
     * leave the player waiting out a wind-down while already being free to move.</p>
     */
    public static final int PARRY_RISE_TICKS = 3;
    public static final int PARRY_FALL_TICKS = 3;

    /**
     * How long the ropera's lunge takes to go out and to come back, in ticks.
     *
     * <p><b>Four and six, and the rise is not the strike's.</b> The strike is a ten tick weapon
     * and its thrust is already at the limit of what ten ticks can carry, so a lunge ramped at the
     * same rate as the strike would reach full extension on the same frame and the two would be
     * one motion. Four puts the point at full depth while the strike is still coming back, which
     * is the difference between a step and a longer swing.</p>
     *
     * <p>The fall is longer than the rise on purpose, and for the same reason the guard's is: a
     * step that snapped back would read as a teleport that undid itself. The player is somewhere
     * new and the weapon is catching up.</p>
     */
    public static final int LUNGE_RISE_TICKS = 4;
    public static final int LUNGE_FALL_TICKS = 6;

    /**
     * How long the halberd's sweep takes, in ticks.
     *
     * <p><b>Twelve, and it is long where the strike is short.</b> The strike leaves and returns in
     * six, because a cut is a snap. A sweep is the whole body going round and the blade arriving on
     * the other side, and twelve is what makes it read as travelling across the screen instead of
     * as a cut that happened to be wide.</p>
     *
     * <p>It also has to be longer than the weapon's own cooldown is short. The sweep costs four
     * swings to throw and the player is standing still for three quarters of a second after it,
     * so an arc that finished in six ticks would be over before they could see where it had gone.</p>
     */
    public static final int SWEEP_TICKS = 9;

    /**
     * How long the arcabuz's reload takes, in ticks.
     *
     * <p><b>Ten, and it is half a second because that is what was asked for.</b> It is a bump, so it
     * runs out and back over those ten rather than sitting down for them: a reload that held the
     * weapon down for half a second and then put it back reads as a pause in the game, and one that
     * sat down would need the player to press again.</p>
     *
     * <p>On a constant here rather than on the server, unlike the fuse. The reload is the same for
     * every arcabuz and nobody has asked for it to vary; the fuse is a per-shot length that arrives
     * with the payload.</p>
     */
    public static final int RELOAD_TICKS = 10;

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
        // **The chop starts HERE, on the release, and not on the next tick.**
        //
        // `pendingStrike` exists so the strike bump's first frame is not drawn at the previous
        // swing's wind-up, and a bump starts at zero anyway so a frame of nothing costs nothing.
        // The chop is a HEIGHT: a frame of zero is a frame of the arm hanging at the player's side,
        // and that is the playtest's report that it still starts from rest and then hits.
        chopAge = 0;
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
        chopAge = -1;
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
                     float stagger, boolean parrying, boolean lunging) {
        ticks++;
        this.stagger = clamp01(stagger);
        // Its own clock, not the item's. See BREAK_TICKS: tying the collapse to the stagger made
        // a five second marker out of a five second stun.
        if (breakAge >= 0 && ++breakAge >= BREAK_TICKS) {
            breakAge = -1;
        }
        // The parry lockout bump, on its own clock and its own length. Bounded by the server's
        // number rather than by a constant here, so the blade is still coming back on the tick
        // the sabre can parry again and not one tick before or after.
        if (parryBreakAge >= 0 && ++parryBreakAge >= parryBreakTicks) {
            parryBreakAge = -1;
        }
        if (holding) {
            // A new press abandons whatever the last one was doing, and zeroes the charge it
            // released with. Otherwise a fast second click would inherit the first one's charge
            // and the wind-up would jump to a value the player never charged to.
            strikeAge = -1;
            pendingStrike = false;
            released = 0.0F;
        chopAge = -1;
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
        chopAge = -1;
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
        } else if (up) {
            guardAge++;
            guard = Math.min(1.0F, guardAge / (float) GUARD_RISE_TICKS);
        } else if (guard != 0.0F) {
            // You can only fall from up. Without this the else branch also caught the case
            // "the guard has never been up", and a motion sitting at zero computed
            // `1 - 1/6` on its first tick: the sword was drawn off line for the first six ticks
            // of every session, which reads as a weapon flying in when you join a world.
            guardAge++;
            guard = Math.max(0.0F, 1.0F - guardAge / (float) GUARD_FALL_TICKS);
        }
        // Interpolation is skipped across the frame the stance starts or stops. Interpolating
        // there would leave the weapon a third of a stance behind for one frame, on the exact
        // frame the player pressed or released.
        guardPrev = changed ? guard : before;
        this.guarding = guarding;

        // The parry ramps on the same shape, and reads the SERVER's answer rather than the press.
        //
        // That difference is the whole reason this is not just the guard again. The press is not
        // a prediction here: the server decides whether the five second cooldown is up, and a
        // blade that came out for a parry that was refused would be the game showing the player a
        // thing that did not happen. The cost of being right is that the animation starts one
        // packet late, which at a normal ping is inside the three tick rise anyway.
        float parryBefore = parry;
        boolean parryUp = parrying;
        boolean parryChanged = parryUp != this.parrying;
        if (parryChanged) {
            parryAge = 1;
            parry = parryUp
                    ? Math.min(1.0F, parryAge / (float) PARRY_RISE_TICKS)
                    : parryBefore;
        } else if (parryUp) {
            parryAge++;
            parry = Math.min(1.0F, parryAge / (float) PARRY_RISE_TICKS);
        } else if (parry != 0.0F) {
            parryAge++;
            parry = Math.max(0.0F, 1.0F - parryAge / (float) PARRY_FALL_TICKS);
        } else {
            // **You can only fall from up.** The last branch is the one that was missing, and
            // without it a motion that has never parried computes `1 - 1/3` on its very first
            // tick and starts life two thirds out of line. The guard ramp had the same missing
            // branch and the same symptom: for the first GUARD_FALL_TICKS of every session the
            // sword was drawn off line, which is a weapon flying in when you join a world.
            parry = 0.0F;
        }
        parryPrev = parryChanged ? parry : parryBefore;
        this.parrying = parrying;

        // The lunge ramp, same shape as the parry's, and with the same missing branch filled in:
        // a ramp that can only fall from up is the one that rests at zero.
        float lungeBefore = lunge;
        boolean lungeUp = lunging;
        boolean lungeChanged = lungeUp != this.lunging;
        if (lungeChanged) {
            lungeAge = 1;
            lunge = lungeUp
                    ? Math.min(1.0F, lungeAge / (float) LUNGE_RISE_TICKS)
                    : lungeBefore;
        } else if (lungeUp) {
            lungeAge++;
            lunge = Math.min(1.0F, lungeAge / (float) LUNGE_RISE_TICKS);
        } else if (lunge != 0.0F) {
            lungeAge++;
            lunge = Math.max(0.0F, 1.0F - lungeAge / (float) LUNGE_FALL_TICKS);
        } else {
            lunge = 0.0F;
        }
        lungePrev = lungeChanged ? lunge : lungeBefore;
        this.lunging = lungeUp;

        // The sweep's own bump, on the strike's terms rather than the parry's.
        //
        // The parry and the lunge are ramps because they are states the player is *in* for as long
        // as they want: the window stays open, the step stays out. A sweep is not a state. It is
        // twelve ticks and then it is over whatever the player does next, which is the strike's
        // shape, so it gets the strike's age and its resting-at-zero guarantee.
        float sweepBefore = sweep;
        if (sweepAge > SWEEP_TICKS) {
            // Cleared only once the age is already past the end, so the twelfth tick still draws.
            //
            // **Three orders of this block were wrong before it was right, and all three produced
            // the same symptom: one tick short.**
            //
            // `age++; sweep = 1 - age/T` reaches zero ON the twelfth tick and then holds it for a
            // frame. `sweep = 1 - (age-1)/T; age++` fixes the zero but zeroes the twelfth tick
            // anyway, because the reset runs on the same tick as the last value. So the guard has
            // to come first and the value has to be drawn from the age the press set: ages one
            // through twelve all draw, from 1.00 down to 0.083, and the thirteenth is zero.
            sweepAge = 0;
            sweep = 0.0F;
        } else if (sweepAge > 0) {
            sweep = 1.0F - (float) (sweepAge - 1) / (float) SWEEP_TICKS;
            sweepAge++;
        } else {
            sweep = 0.0F;
        }
        sweepPrev = sweepBefore;
        // CHOP is read off the strike's clock rather than kept, so the two can never last
        // different times. `chopPrev` is the previous tick's value and the snapshot interpolates
        // between the two, because a chop that stepped twenty times a second inside a sixty frame
        // renderer is the same staircase the whole viewmodel library was built to avoid.
        float chopBefore = chop;
        chop = chopAge < 0 ? 0.0F : chopAt(0.0F);
        chopPrev = chopBefore;
        if (chopAge >= 0 && ++chopAge > CHOP_BUILDUP_TICKS + CHOP_FALL_TICKS) {
            chopAge = -1;
        }

        // The arcabuz's three, and they are three different shapes on purpose.

        // HELD is a state, so it is a flag with the same interpolating previous value as everything
        // else here -- the difference is what feeds it, which is a packet and not a clock.
        float heldBefore = held;
        heldPrev = heldBefore;

        // RELOAD is a bump on the strike's terms: out and back over RELOAD_TICKS, zero the rest of
        // the time. The order matters the same way the sweep's did -- compute from the age the
        // press set, then advance, and let the guard come first -- or the last tick of the motion
        // is spent drawing a reset instead of a pose.
        float reloadBefore = reload;
        if (reloadAge > RELOAD_TICKS) {
            reloadAge = 0;
            reload = 0.0F;
        } else if (reloadAge > 0) {
            reload = Math.abs(0.5F - reloadAge / (float) RELOAD_TICKS) * 2.0F;
            reloadAge++;
        } else {
            reload = 0.0F;
        }
        reloadPrev = reloadBefore;

        // SHOOT is a ramp held for as long as the fuse burns, counted DOWN, which is the only way
        // to hold a ramp at a length the server decides. Going up from a length we guessed would be
        // a second copy of the number that the payload already sent.
        float shootBefore = shoot;
        if (shootAge < 0) {
            shoot = 0.0F;
        } else if (shootAge <= 0) {
            shootAge = -1;
            shoot = 0.0F;
        } else {
            shoot = Math.min(1.0F, shootAge / (float) shootLength);
            shootAge--;
        }
        shootPrev = shootBefore;
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
                Math.max(breakAt(offset), parryBreakAt(offset)),
                parryAt(offset),
                lungeAt(offset),
                sweepAt(offset),
                chopAt(offset),
                heldAt(offset),
                reloadAt(offset),
                shootAt(offset),
                holding,
                (ticks + offset) / 20.0F,
                seed);
    }

    /** How far the guard is up, part way through a frame. */
    public float guardAt(float offset) {
        float t = clamp01(offset);
        return guardPrev + (guard - guardPrev) * t;
    }

    /** How far the parry has come up, part way through a frame. */
    public float parryAt(float offset) {
        float t = clamp01(offset);
        return parryPrev + (parry - parryPrev) * t;
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
        chopAge = -1;
        classified = 0.0F;
        seed = 1;
        ticks = 0;
        holding = false;
    }
}
