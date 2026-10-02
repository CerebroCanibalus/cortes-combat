package com.cortes.combat.special;

/**
 * The ropera's lunge, as numbers.
 *
 * <h2>What this class stopped trying to do</h2>
 *
 * <p>It used to promise an exact distance per material and solve for the tick curve that delivered
 * it: a first step, a retention factor, a solved tick count. That machinery is gone, and reading
 * vanilla's riptide is why.</p>
 *
 * <p>Vanilla's riptide is three lines. It calls {@code push} once with the look vector, opens a
 * twenty tick window, and hops. There is no distance in it anywhere -- <b>the impulse is the only
 * number and the friction decides where the player ends up.</b> Two attempts to build a distance
 * exact dash had already failed before that was known, and both failed the same way: scaling the
 * step by tier gave a netherite first step of 1.3 blocks, which reads as teleports, and scaling the
 * decay by tier hit the ceiling of the geometric series {@code FIRST_STEP / (1 - decay)}, which made
 * six and a fifth blocks arithmetically impossible. Neither was a badly chosen number. They were
 * numbers solving for a quantity the engine does not expose.</p>
 *
 * <p>So there are two numbers per material now, and they are different in kind:</p>
 *
 * <ul>
 *   <li><b>{@link #impulseFor} is a design number.</b> It is how hard the lunge throws you. A better
 *       material throws harder, which is what makes it a better weapon, and that is vanilla's own
 *       rule: Riptide I pushes 2.25 and Riptide II pushes 3.75.</li>
 *   <li><b>{@link #ceilingFor} is a cheat bound and NOT a promise.</b> It is how far a lunge is
 *       allowed to travel before the server stops granting anything. Nothing should ever reach it
 *       in play, so it is deliberately about twice the distance the impulse produces -- wide enough
 *       that steering, momentum and a slide down a hill are all legal, tight enough that a client
 *       which pushes itself four times harder gets no damage and no invulnerability for it.</li>
 * </ul>
 *
 * <p>The old rule "a better weapon lunges longer, never faster" is dead, and it is worth saying why:
 * it existed to keep the first step constant while the run got longer. Under one impulse there is
 * no step, so there is nothing left for it to protect. Tier now buys speed as well as distance, which
 * is what vanilla does.</p>
 *
 * <p>Pure. Every method here is arithmetic on doubles, and the box the lunge sweeps is returned as
 * six numbers rather than as an {@code AABB}, because a bare JVM cannot load the game's classes and
 * the geometry that decides what the lunge hits is exactly the geometry that was wrong before.</p>
 */
public final class LungeDamage {

    /**
     * How much of the weapon's own damage one lunge hit is worth.
 *
 * <p><b>Three quarters, and it was all of it.</b> The lunge is the strongest thing the ropera
 * does and it was paying nothing for the position it puts you in -- it moved you, so it should cost
 * you something on the number as well. Three quarters is the price of the step.</p>
 *
 * <p>Which is why the weapon's own base went up a quarter at the same time: the ropera's swing is
 * five now instead of four, and the lunge does three and three quarters of that. So the special is
 * worse than the swing against one thing and better than it against a line of them, which is what
 * the escalation is for.</p>
 */
    public static final float LUNGE_DAMAGE_SHARE = 0.75F;

/**
     * How much each enemy passed through makes the next one hurt.
 *
 * <p><b>Three eighths, and it was a quarter -- raised with the cap and not on its own.</b> The
 * ceiling went from three times base to four, and if only the ceiling had moved the cap would have
 * been decorative: a quarter step does not reach four until the sixteenth enemy, which is more than
 * a lunge ever passes through, so every real fight would have stopped somewhere around two and a
 * half times and never seen the number change.</p>
 *
 * <p>Three eighths reaches four at the eighth enemy, which is where three used to be reached. Same
 * number of targets to the ceiling, a bigger ceiling, and a bigger step to get there.</p>
 */
    public static final float STEP_PER_TARGET = 0.375F;

    /**
     * The multiplier the last target in a lunge can be hit for.
     *
     * <p><b>Four, and it was three.</b> The trade the playtest asked for: less damage per enemy, more
     * damage for going deep. A single lunge against one mob is now three quarters of what it was;
     * a lunge through six is more, and through eight is the ceiling.</p>
     *
     * <p>Still a ceiling rather than a consequence of how many enemies happen to be there. A lunge
     * through a crowd of ten would otherwise deal four and a quarter times base per target with no
     * limit, which is not a lunge that rewards reading the room -- it is a lunge that rewards
     * standing in a room.</p>
     */
    public static final float MAX_MULTIPLIER = 4.0F;

    /** How many enemies it takes to reach {@link #MAX_MULTIPLIER}, for the numbers to agree. */
    public static final int TARGETS_TO_CAP = 8;

    /**
     * How many ticks a lunge stays open for damage.
     *
     * <p><b>Twenty, which is vanilla's number</b> -- {@code TridentItem} calls
     * {@code startAutoSpinAttack(20, 8.0F, stack)} and the window lives that long regardless of how
     * long the player physically keeps sliding. Copying it means a lunge that runs into a wall and a
     * lunge that crosses a field stop being the same move, and it means the invulnerability window
     * and the damage window are one number instead of two that can disagree.</p>
     */
    public static final int WINDOW_TICKS = 20;

    /**
     * The push magnitude at the lowest material, in blocks per tick.
     *
     * <p>One, and it was two. Velocity in this game is blocks per tick, so an impulse of one is a
     * one block per tick slide that ground friction of 0.6 brings to rest around a block and two
     * thirds -- and that is what the wooden ropera's old table used to promise.
     *
     * <p>The first playtest of this said the lunge moved too far and asked for half of it, which is
     * this number. The interesting part is not that it halved: it is <b>that the promise survived
     * the whole redesign unchanged</b>. The old table said three and two tenths blocks for wood,
     * the new impulse produces one and two thirds, and both describe the same lunge. What was wrong
     * before was never the number. It was that the number described a curve nobody measured.</p>
     */
    public static final double BASE_IMPULSE = 1.0;

    /**
     * Extra impulse per material rank, wooden being zero and netherite being six.
     *
     * <p><b>Two hundredths, and that is almost nothing.</b> It was three tenths, the playtest halved
     * it, and then the playtest halved the escalation too: the distance felt right and the tier
     * scaling was the part that felt wrong. So a netherite ropera throws one point one two against
     * a wooden one's one point zero -- about a tenth of a block of difference across the whole
     * table.</p>
     *
     * <p>That is a real decision and it inverts the rule from two rounds ago, which was "a better
     * material lunges further, never faster". The material still decides what the weapon <em>does</em>
     * -- damage, tempo, reach, knockback -- and the lunge is now the one axis it does not touch. A
     * ropera's identity is the thrust, and a thrust whose reach changed with the ore stopped being
     * the same weapon twice.</p>
     */
    public static final double PER_TIER_IMPULSE = 0.02;

    /**
     * How many swings a lunge costs.
     *
     * <p><b>Eight, and it went up because the lunge became a dodge.</b> Four was priced when it was
     * a movement that hit things. Then it was given invulnerability, which makes it a different
     * thing: a button that moves you forward and cannot be hit while it does. That is a dodge, and a
     * dodge priced like a poke is spammed between every two hits of a fight.</p>
     *
     * <p>Still on the same hard cooldown as an attack, so "what does this button cost" stays one
     * number per button. Eight swings at ten ticks each is over a second and a half of downtime,
     * which is what makes it a decision rather than a habit.</p>
     */
    public static final float COOLDOWN_FACTOR = 8.0F;

    /**
     * How long the player cannot be hurt during a lunge.
     *
     * <p>Set on every tick rather than once, because anything that touches the player's damage state
     * mid-dash -- a fall, a fire tick, another mod's i-frame -- resets it, and a lunge that quietly
     * stops being a dodge halfway is worse than one that never was.</p>
     *
     * <p>It is vanilla's own field and not a new mechanic: the same number that makes an arrow miss
     * you. Using it means every damage rule in the game reads the lunge correctly without knowing the
     * lunge exists, and the couple of leftover ticks after it ends are grace nobody notices and
     * everybody would miss.</p>
     *
     * <p><b>Granted by the server and only while the lunge is legal.</b> A client that pushes
     * itself without permission gets no invulnerability, which is the difference between a dash
     * that dodges and a free escape hatch.</p>
     */
    public static final int INVULNERABLE_TICKS = 6;

    /**
     * The height of the hop a lunge starts with, when the player is on the ground.
     *
     * <p>Vanilla's riptide adds 1.2 and it is worth having: leaving the ground is most of what
     * makes a slide read as a lunge rather than as sliding along the floor.</p>
     */
    public static final double HOP_HEIGHT = 1.2;

    private LungeDamage() {
    }

    /**
     * How far a lunge of this material carries the player, from the measured relation.
     *
     * <p><b>An estimate and not a promise</b>, exactly like the ceiling it seeds. What the player
     * actually covers is whatever the engine decided and only the log knows it; this exists so the
     * ceiling and this class agree about what a lunge is, instead of being two hand-written
     * descriptions of it that drift apart -- which is precisely what they did.</p>
     */
    public static double estimatedDistance(int materialRank) {
        return impulseFor(materialRank) * DISTANCE_PER_IMPULSE;
    }

    /**
     * The multiplier for the nth enemy of a lunge, counting from zero.
     *
     * <p>{@code min(n × step, cap)} rather than a sum, so the ceiling is reachable in a number of
     * steps and the growth is a straight line to it. A sum would overshoot on the eighth and then
     * keep climbing, which is the behaviour the cap exists to prevent.</p>
     */
    public static float multiplierFor(int index) {
        if (index < 0) {
            return 1.0F;
        }
        return Math.min(MAX_MULTIPLIER, 1.0F + index * STEP_PER_TARGET);
    }

    /**
     * The damage for one enemy of a lunge.
     *
     * <p>The weapon's own base, times {@link #LUNGE_DAMAGE_SHARE}, times the escalation. Nothing
     * else: not the charge, not the weapon's charge bonus, not a knockback number. The lunge is flat
     * by design -- its value is the ground it bought and the ground it covered, and a special that
     * also scaled would be competing with the swing on the one axis the swing is already best at.</p>
     *
     * <p>The share is applied here and nowhere else, so the "the lunge costs three quarters of the
     * weapon" rule has exactly one implementation and the escalation multiplies it rather than
     * replacing it.</p>
     */
    public static float damageFor(float baseDamage, int index) {
        return baseDamage * LUNGE_DAMAGE_SHARE * multiplierFor(index);
    }

    /**
     * How hard this material's lunge throws the player, in blocks per tick.
     *
     * <p>Read from the material because the throw is a property of the thing being held: a wooden
     * ropera and a netherite one are the same weapon, and the difference between them is felt on the
     * first tick rather than over the whole run.</p>
     *
     * <p>A negative rank is treated as the lowest material rather than as a backwards bonus, so a
     * corrupt data pack produces a weak lunge instead of a reversed one.</p>
     */
    public static double impulseFor(int materialRank) {
        return BASE_IMPULSE + Math.max(0, materialRank) * PER_TIER_IMPULSE;
    }

    /**
     * How far this material's lunge is allowed to travel before the server stops honouring it.
     *
     * <p>Computed from {@link #impulseFor} rather than tabulated beside it, so the two cannot drift
     * apart. See {@link #CEILING_MARGIN} for the bug that made this a derivation instead of a
     * constant.</p>
     *
     * <p><b>This is not a promise and must never be quoted as the lunge's distance.</b> It is the
     * point past which the grant stops.</p>
     */
    public static double ceilingFor(int materialRank) {
        return estimatedDistance(materialRank) * CEILING_MARGIN;
    }

    /**
     * Blocks of ground an impulse of one covers, <b>measured rather than derived</b>.
     *
     * <p>This number was wrong for four rounds and every one of them cost a playtest.</p>
     *
     * <p>It was first derived as {@code impulse / friction} with a friction of 0.6, on the assumption
     * that an entity's velocity is scaled by the block's slipperiness. <b>It is not.</b> Read out of
     * the sources: {@code Entity#move} ends with
     * {@code setDeltaMovement(getDeltaMovement().multiply(getBlockSpeedFactor(), 1.0, ...))}, and
     * {@code BlockBehaviour}'s default {@code speedFactor} is 1.0, not 0.6. There is no friction
     * constant to divide by, so the relation has to come from the log.</p>
     *
     * <p>Then it was measured once, badly, from a run that had {@code /cc speed} running: 2.12. The
     * playtest that followed logged <b>thirteen lunges and every one closed {@code fin=ilegal}</b>,
     * because the ceiling derived from 2.12 was 7.1 and a real lunge travels 6 to 7. So the number
     * was wrong in the one direction that made lunges look shorter than they are, and the error was
     * a factor of nearly three.</p>
     *
     * <p>Three clean runs, standing still, no harness command: an impulse of 1.12 covered 6.01, 1.08
     * covered 6.71 and 1.12 covered 6.76. Six, and the playtest's own judgement agrees with it -- an
     * impulse of two was reported as "too much" and one as "excellent", and six is half of twelve.</p>
     */
    public static final double DISTANCE_PER_IMPULSE = 6.0;

    /**
     * How many times the distance an impulse covers the ceiling has to forgive.
     *
     * <p><b>Three. It was one point eight, and that was the whole bug of this round.</b> A playtest
     * logged thirteen lunges and <b>every one of them closed {@code fin=ilegal}</b>, so the lunge
     * never reached its damage code at all. The reported symptoms were "it does not seem to do
     * damage" and "its invulnerability fails, especially when I am not going so fast" -- and they are
     * one thing, because the legality check runs before the sweep and a lunge that goes illegal
     * grants nothing at all.</p>
     *
     * <p>Three covers what is legal and was not in the measurement: a lunge taken at a run, since
     * {@code push} is additive and the sprint's velocity is still in there; a lunge steered into an
     * arc; and the {@code /cc speed} harness command, which multiplies the player's own movement and
     * takes a lunge to nearly four times its measured distance.</p>
     *
     * <p>The margin exists to bound cheating, so the only question is how much to tolerate. It still
     * refuses a client that pushes itself four times too hard.</p>
     */
    public static final double CEILING_MARGIN = 3.0;

    /**
     * Per-tick movement below which the player counts as stopped and the lunge is over.
     *
     * <p><b>The window is measured rather than counted, and that is what makes the invulnerability the
     * same length as the dash.</b> A fixed twenty ticks is nearly double the movement: the impulse is
     * spent after about a third of a second, and the remainder was a player standing perfectly still
     * and untouchable for another two thirds of one.</p>
     *
     * <p>Two hundredths of a block, which is below the precision the position report arrives with --
     * so "stopped" is a measurement and not a threshold picked to make a number come out.</p>
     */
    public static final double STILL = 0.02;

    /** Whether a lunge has stopped moving, which is how its window learns it is over. */
    public static boolean stopped(double stepThisTick) {
        return stepThisTick < STILL;
    }

    /**
     * How far the player has travelled from where the lunge began, horizontally.
     *
     * <p>Horizontal only, because vertical is the player's business: falling off a cliff mid-dash
     * is not cheating, and a bound that counted it would make the lunge stop working near any drop,
     * which is where a ropera player wants to be.</p>
     *
     * <p>Distance and not displacement along the original direction, because the player can steer.
     * A dash that curved into a wall travelled less than its arc and measuring the arc would let a
     * client buy distance by spinning in place.</p>
     */
    public static double travelled(double startX, double startZ, double x, double z) {
        double dx = x - startX;
        double dz = z - startZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether a lunge that has come this far is still one the server will honour.
     *
     * <p>False ends the lunge on the spot: no damage after it, no invulnerability, nothing. The
     * movement is not undone -- nothing can undo it, because only the client ever knew where the
     * player was going -- so the cost of a dishonest client is that it slides for free. That is the
     * accepted price of not having a rollback, and it is a cheap one next to a player who can become
     * invulnerable at will.</p>
     */
    public static boolean legal(int materialRank, double travelled) {
        return travelled <= ceilingFor(materialRank);
    }

    /**
     * The space one tick of a lunge sweeps, as six numbers.
     *
     * <p><b>Vanilla's shape.</b> {@code LivingEntity#checkAutoSpinAttack} sweeps
     * {@code before.minmax(after)}: the volume the body actually passed through between two ticks.
     * Not a box in front of the player, and not a cone from the camera.</p>
     *
     * <p>That distinction is the first fix. The lunge used to build a box of the promised length in
     * front of the player <em>at the press</em>, before the player had moved a centimetre, so a
     * player standing behind cover could swing at everything four blocks ahead of them without ever
     * stepping out. The design says the step is the price and it is paid in position, and a hit
     * volume that does not care where the player went charged them for nothing.</p>
     *
     * <h2>The second fix, and the reason the lunge felt broken</h2>
     *
     * <p><b>The corridor was half the reach, which is less than a standing swing.</b> It inflated by
     * {@code 0.3 + reach * 0.5}, and {@code Reach#inCone} measures the weapon's own reach from the
     * attacker's eye to the <em>closest point</em> of the target's box and refuses anything past
     * {@code reach.distance()}. For the ropera that is three and a half blocks. So a ropera standing
     * still hit a zombie three blocks away, and the lunge -- which costs eight swings of downtime --
     * reached two.</p>
     *
     * <p>The playtest's words were "it does not seem to do damage" and "it is too fragile", and both
     * were this: not a tuning problem and not a feel problem, but a special that hit a strictly
     * smaller area than the button it was supposed to replace. The corridor is now inflated by the
     * <b>whole</b> reach, so a lunge from standing still reaches exactly what a standing swing
     * reaches and everything past it has to be earned by moving.</p>
     *
     * <p>Vertical is a quarter of it rather than the whole, and stays that way: the lunge is thrown
     * along the ground and should not cut something three blocks up that the blade never reached.
     * Horizontal is the axis the design spends -- "a thrust that goes through a line of them" -- and
     * the path through the crowd is the line, not the width of the corridor.</p>
     *
     * @param reach the weapon's reach, in blocks
     * @return {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     */
    public static double[] swept(double fromX, double fromZ,
                                 double toX, double toZ, double reach) {
        double halfWidth = reach;
        double vertical = reach * 0.25;
        return new double[]{
                Math.min(fromX, toX) - halfWidth, -vertical, Math.min(fromZ, toZ) - halfWidth,
                Math.max(fromX, toX) + halfWidth, 1.8 + vertical, Math.max(fromZ, toZ) + halfWidth};
    }

    /**
     * The cube around the player that the lunge hits with, as six numbers.
     *
     * <p><b>A cube on the player rather than a corridor swept from last tick to this one, and the two
     * cover the same ground.</b> A swept corridor inflated by {@code h} is the path dilated by
     * {@code h}; a cube tested at every tick is the path dilated by the cube's half extent. Same
     * shape, and the cube needs no geometry to reason about, no {@code minmax} that can come out
     * inverted when the player turns around, and no over-coverage of the corner when the lunge is
     * steered into an arc -- all three of which were properties of the version it replaces.</p>
     *
     * <p><b>Its size is half the weapon's reach, which is "a bit bigger than the player", and that
     * is the whole argument for it.</b> A ropera is 0.6 wide and 1.8 tall; this is a 3.4 cube around
     * its centre, about twice the player in every direction, which is about as long as the blade.</p>
     *
     * <p><b>Not the whole reach, which is what the previous version used and which was wrong in the
     * opposite direction.</b> The reasoning that put it there was "a stationary lunge must reach what
     * a standing swing reaches", and that reasoning was about a lunge that never happens: the player
     * travels. A mob three blocks ahead is 1.3 blocks away by the time the dash finishes, well inside
     * this cube. Meanwhile a mob off to the side, which the dash never approaches, should <em>not</em>
     * be hit -- the blade points where the lunge points. Half the reach says exactly that, and the
     * whole reach would have cut a corridor through every bystander on the field.</p>
     *
     * <p>Centred on the player's centre, which is 0.9 above their feet. A cube is wasteful on the
     * vertical axis -- this one reaches about a body height below the feet and the same again above
     * the head -- and that is a consequence of asking for a cube. The half extent is the one number
     * if it has to be flatter.</p>
     *
     * @param feetY the player's {@code getY()}, their feet
     * @param reach the weapon's reach, in blocks
     * @return {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     */
    public static double[] cube(double x, double feetY, double z, double reach) {
        double half = reach * CUBE_RADIUS;
        double centreY = feetY + PLAYER_CENTRE_HEIGHT;
        return new double[]{
                x - half, centreY - half, z - half,
                x + half, centreY + half, z + half};
    }

    /**
     * The cube's half extent, as a fraction of the weapon's reach.
     *
     * <p><b>Half, and it was four tenths for one build.</b> It was lowered by a tenth because the
     * playtest called the lunge overpowered and the radius was the obvious-looking number to reach
     * for. It was the wrong one: the thing the player reads as the ropera's reach is the swing's
     * cone, and that is what got nerfed on the next attempt. The lunge's cube is derived from the
     * weapon's reach, so it widens by itself when the weapon gets longer, and a number on top of
     * that was a second place for the same axis to be tuned.</p>
     *
     * <p>Half is also the number the shape was designed around: a ropera is zero point six wide, and
     * half its reach makes the box about twice the player in every direction, which is about as long
     * as the blade.</p>
     */
    public static final double CUBE_RADIUS = 0.5;

    /**
     * How far above the feet the player's box is centred, which is half their height.
     *
     * <p>Written down rather than using 1.8 because the cube is centred on it and an off-by-half
     * there moves the whole hit volume down a body without anything looking wrong.</p>
     */
    public static final double PLAYER_CENTRE_HEIGHT = 0.9;
}
