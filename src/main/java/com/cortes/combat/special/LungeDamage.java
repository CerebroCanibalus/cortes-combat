package com.cortes.combat.special;

/**
 * The ropera's lunge, as numbers.
 *
 * <p>Everything here is arithmetic on floats and ints: how far the step carries, how much the
 * damage grows per enemy it passes through, and what the multiplier is on the last of them. The
 * part that touches the world -- the velocity, the collision and the sweep box -- lives in
 * {@link Lunge}, and this class is the part a test can open.</p>
 *
 * <p>The split exists because of what went wrong twice. The lunge was a number in a method that
 * also moved a player, so nothing about it could be asserted; then it was a teleport, so it moved
 * without momentum; then it was a cone, so it hit where the camera looked instead of where the body
 * went. Each of those was invisible in a build with green tests, because the tests could not load
 * the class that held the decision.</p>
 */
public final class LungeDamage {

    /**
     * How much each enemy passed through makes the next one hurt.
     *
     * <p>A quarter, so the fifth enemy in line takes two and a half times what the first did. Near
     * enough that a lunge through a crowd is the best thing the ropera does, and far enough that
     * one big number is not the whole answer.</p>
     */
    public static final float STEP_PER_TARGET = 0.25F;

    /**
     * The multiplier the last target in a lunge can be hit for.
     *
     * <p>Three times the weapon's base, and it is a ceiling rather than a consequence of how many
     * enemies there happen to be. A lunge through a crowd of ten would otherwise deal thirty times
     * base, which is not a lunge that rewards reading the room -- it is a lunge that rewards
     * standing in a room.</p>
     */
    public static final float MAX_MULTIPLIER = 3.0F;

    /** How many enemies it takes to reach {@link #MAX_MULTIPLIER}, for the numbers to agree. */
    public static final int TARGETS_TO_CAP = 8;

    private LungeDamage() {
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
     * <p>Multiplies the weapon's own base and nothing else: not the charge, not the weapon's charge
     * bonus, not a knockback number. The lunge is flat by design -- its value is the distance it
     * bought and the ground it covered, and a special that also scaled on damage would be
     * competing with the swing on the one axis the swing is already best at.</p>
     */
    public static float damageFor(float baseDamage, int index) {
        return baseDamage * multiplierFor(index);
    }

    /**
     * How far a lunge of this tier carries the player, in blocks.
     *
     * <p>Read from the material because reach-by-tier is a property of the thing being held: a
     * wooden ropera and a netherite one are the same weapon, and the step is where the difference
     * between them is felt.</p>
     */
    public static double distanceFor(int materialRank) {
        return BASE_DISTANCE + Math.max(0, materialRank) * PER_TIER_DISTANCE;
    }

    /** The sweep's reach for this tier, which grows with the distance it carries. */
    public static double reachFor(int materialRank) {
        return BASE_REACH + Math.max(0, materialRank) * PER_TIER_REACH;
    }

    /** Blocks a lunge carries at the lowest material. */
    public static final double BASE_DISTANCE = 3.2;

    /** Extra blocks per material rank, wooden being zero and netherite being six. */
    public static final double PER_TIER_DISTANCE = 0.5;

    /** Sweep reach at the lowest material. */
    public static final double BASE_REACH = 2.0;

    /** Extra reach per material rank. */
    public static final double PER_TIER_REACH = 0.4;

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
     * <p>Set on every step rather than once, because anything that touches the player's damage state
     * mid-dash -- a fall, a fire tick, another mod's i-frame -- resets it, and a lunge that quietly
     * stops being a dodge halfway through is worse than one that never was.</p>
     *
     * <p>It is vanilla's own field and not a new mechanic: the same number that makes an arrow miss
     * you. Using it means every damage rule in the game reads the lunge correctly without knowing the
     * lunge exists, and the couple of leftover ticks after it ends are grace nobody notices and
     * everybody would miss.</p>
     */
    public static final int INVULNERABLE_TICKS = 6;

    // ------------------------------------------------------------ the steering --
    //
    // Lives here rather than in Lunge for the same reason the movement does: Lunge touches Entity
    // and a bare JVM cannot load it. This is the arithmetic behind "if I go backwards it still
    // pulls me forward", and it has to be a test rather than a comment.

    /**
     * How much of the direction the player can still change mid-lunge.
     *
     * <p><b>Zero point three, and both of the extremes shipped.</b> Fully locked to the press was
     * reported as "very hardcoded forward: if I go backwards it still pulls me forward" -- a
     * commitment the player cannot influence is not a commitment, it is a rail. Fully re-read from
     * the camera every tick turned it into a steerable flight, which is a double jump nobody asked
     * for. So the lunge leans: a little turn follows a little, and the press still decides most of
     * it.</p>
     */
    public static final float STEER_SHARE = 0.3F;

    /**
     * The direction of one step: where the lunge was committed, leaned toward where the player
     * looks now.
     *
     * <p>Pure. Unit in, unit out, because a step length multiplied by a vector longer than one makes
     * a diagonal lunge faster than a straight one -- the bug a blended direction always has.</p>
     */
    public static double[] steered(double[] committed, double[] look) {
        double dot = committed[0] * look[0] + committed[1] * look[1];
        // Looking straight backwards would blend to a fraction of the committed vector, and
        // renormalising would hand back a full one -- which reads as the lunge ignoring the player
        // entirely. Damped instead, so turning around fights the lunge rather than instantly
        // reversing it.
        double share = STEER_SHARE * Math.max(0.0, 1.0 - dot) * 0.5;
        double x = committed[0] * (1.0 - share) + look[0] * share;
        double z = committed[1] * (1.0 - share) + look[1] * share;
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-6) {
            return committed;
        }
        return new double[]{x / length, z / length};
    }

    // ------------------------------------------------- the shape of the movement --
    //
    // These four live here rather than in Lunge for one reason: Lunge touches Entity, so a bare
    // JVM cannot load it, and the arithmetic that decides how far the player moves would have no
    // test at all. This class is the part a test can open, and it is the part that was wrong three
    // times in a row.

    /** How many position updates a lunge is made of. One third of a second. */
    /**
     * How many position updates a lunge is made of.
     *
     * <p>One third of a second. The count is what decides whether this reads as travel or as a
     * blink, and it is the reason a better material gets more steps rather than longer ones.</p>
     */
    // These four live here rather than in Lunge for one reason: Lunge touches Entity, so a bare
    //
    // These live here rather than in Lunge for one reason: Lunge touches Entity, so a bare JVM
    // cannot load it, and the arithmetic that decides how far the player moves would have no test
    // at all. This class is the part a test can open, and it is the part that was wrong three times.

    /**
     * The first step, in blocks, and the SAME for every weapon.
     *
     * <p>This is the number the whole design hangs on, and it was arrived at backwards. Three
     * attempts failed before it:</p>
     *
     * <ul>
     *   <li>Scaling the step size by tier gave a netherite first step of 1.3 blocks. Thirty steps
     *       of more than a metre are not a lunge -- they are thirty teleports, worse than the
     *       single jump this replaced.</li>
     *   <li>Scaling the decay by tier ran into a wall nobody had drawn: a geometric series
     *       converges, so the total distance has a ceiling of {@code FIRST_STEP / (1 - decay)}.
     *       At the decay that looks right the ceiling was under three blocks, which made the
     *       netherite ropera's six and a fifth arithmetically impossible.</li>
     * </ul>
     *
     * <p>What works is the obvious thing: <b>a better weapon takes more steps.</b> A wooden lunge
     * is ten of them and a netherite one is thirty, all the same size. The player sees an identical
     * motion -- same start, same shape -- and the better weapon simply keeps going for longer. Half
     * a second against one and a half, which is a difference a player feels, and it costs the first
     * step nothing.</p>
     */
    public static final double FIRST_STEP = 0.40;

    /**
     * How much of each step survives to the next.
     *
     * <p>Nought five. Flat enough that the series has no ceiling inside the range this game asks
     * for, which is the property the two previous attempts were missing, and shaped enough that the
     * steps are still visibly shrinking: the last one is half the first.</p>
     */
    public static final float RETENTION = 0.95F;

    /**
     * A cap on how long any lunge can last.
     *
     * <p>Not reachable by any material in the game -- netherite takes thirty -- and here so that a
     * bad distance from a data pack produces a long lunge rather than a player who cannot stop
     * moving.</p>
     */
    public static final int MAX_TICKS = 60;

    /**
     * How many steps this tier's lunge is made of.
     *
     * <p>Solved from the distance rather than tabulated, because the table it would be tabulated
     * from is {@link #distanceFor} and writing both means one of them is going to be wrong. The
     * loop stops at the first count that covers the promise, so a lunge never travels further than
     * the tier says.</p>
     */
    public static int ticksFor(int materialRank) {
        double wanted = distanceFor(materialRank);
        double travelled = 0.0;
        for (int n = 1; n <= MAX_TICKS; n++) {
            travelled += stepFor(n - 1);
            if (travelled >= wanted) {
                return n;
            }
        }
        return MAX_TICKS;
    }

    /** How far the nth step carries the player, in blocks. The same step, every time. */
    public static double stepFor(int elapsed) {
        int n = Math.max(0, elapsed);
        return FIRST_STEP * Math.pow(RETENTION, n);
    }

    /**
     * The whole distance a lunge of this tier covers.
     *
     * <p>The SUM of the steps rather than a number written down beside them, so retuning the decay
     * cannot leave a promised distance the player no longer travels.</p>
     */
    public static double totalFor(int materialRank) {
        int ticks = ticksFor(materialRank);
        double total = 0.0;
        for (int i = 0; i < ticks; i++) {
            total += stepFor(i);
        }
        return total;
    }

    /**
     * How many steps a lunge of this tier still owes at this tick, never negative.
     *
     * <p>Asks the tier rather than using one number, because a netherite lunge owes thirty steps
     * where a wooden one owes ten, and a caller that read a single figure would end the good one
     * early.</p>
     */
    public static int remaining(int materialRank, int tick) {
        return Math.max(0, ticksFor(materialRank) - Math.max(0, tick));
    }
}
