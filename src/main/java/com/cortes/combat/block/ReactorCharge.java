package com.cortes.combat.block;

/**
 * The nether reactor's numbers, below the Minecraft line.
 *
 * <p><b>Pure on purpose.</b> A bare-JVM test can read every number here, and the block, the block
 * entity and the data generator read the same constants -- so raising the maximum moves the
 * inventory cap, the light curve and the scan budget together instead of leaving one of them a
 * patch behind. A number a test needs cannot name a Minecraft type, or the test stops compiling
 * before it can assert anything.</p>
 *
 * <p><b>Every number is a balance number and is meant to be tuned in playtest.</b> The design fixed
 * the shape -- one firestone is one charge, the radius is three blocks per charge, a charge lasts
 * exactly one hour -- and the values are the first pass at that shape, not a verdict. The one that
 * matters most is {@link #HOUR_TICKS}: too short and the reactor is a toy, too long and charging it
 * is pointless.</p>
 */
public final class ReactorCharge {

    /**
     * Blocks of activation radius per firestone. Thirty charges reach ninety blocks, which is the
     * design's headline number.
     */
    public static final double RADIUS_PER_CHARGE = 1.5;

    /**
     * The most a hopper or any other automation may deposit, and <b>the number that separates a
     * normal reactor from an overloaded one.</b> Above it the block is being force-fed by hand, and
     * that is the state phase two turns into smoke, fire and, at the top, an explosion.
     */
    public static final int MAX_NORMAL = 30;

    /**
     * The absolute ceiling, reachable only by hand. {@link #MAX_NORMAL} is where automation stops;
     * this is where the block itself stops, and it is the slot limit the manual insert obeys.
     */
    public static final int MAX_OVERLOAD = 40;

    /**
     * One charge, in ticks: twenty ticks a second for an hour.
     *
     * <p><b>The design says an hour exactly and this is that number, spelled out.</b> Only the
     * active reactor's clock runs, so a lever left down for an hour costs one firestone and the
     * radius drops one step.</p>
     */
    public static final int HOUR_TICKS = 72_000;

    /** Vanilla's light ceiling. Fifteen is the brightest a block can be. */
    public static final int LIGHT_MAX = 15;

    /**
     * The redstone strength the reactor presents, which is full power.
     *
     * <p>The wire's own attenuation is what turns fifteen into a gradient; the reactor is not a
     * dimmer and presents the maximum. It is the same number as {@link #LIGHT_MAX} by coincidence of
     * vanilla's scale and is kept separate so the two can move apart without one dragging the
     * other.</p>
     */
    public static final int SIGNAL_STRENGTH = 15;

    private ReactorCharge() {
    }

    /** Any charge, forced into {@code [0, MAX_OVERLOAD]}. Every other number here reads through it. */
    public static int clamp(int charge) {
        if (charge < 0) {
            return 0;
        }
        return Math.min(charge, MAX_OVERLOAD);
    }

    /**
     * The activation radius, in blocks, for a charge.
     *
     * <p>Three per firestone: zero at zero, ninety at thirty, one hundred and twenty at the overload
     * ceiling. The radius is what the scan uses and what the registry stores, so this is the one
     * definition of "how far the reactor reaches".</p>
     */
    public static int radius(int charge) {
        // **Rounded, not truncated, and the radius is an integer for a reason that is not style:**
        // the sweep and the distance test are both integer math (radius * radius, and `>> 4` for the
        // chunk bounds), so a half block of reach is a rounding rule here rather than a type change
        // in three files.
        return (int) Math.round(RADIUS_PER_CHARGE * clamp(charge));
    }

    /**
     * The light the block emits: <b>one level per firestone, capped at vanilla's
     * {@link #LIGHT_MAX}</b>.
     *
     * <p>Zero firestones is zero light, fifteen is the ceiling, and the overload band is clamped to
     * the same ceiling rather than pushed past it -- a block cannot be brighter than fifteen, and
     * vanilla's scale stops there: <b>there is no sixteen to aim at.</b> The step is one full level
     * per firestone because the design's words were "sube con cada carga" and the player reads them
     * literally. An earlier curve spread the same climb across the whole normal band -- half a
     * level per firestone, so firestone two moved nothing on screen -- and a step that does not
     * move is a step the player reports as broken. <b>Adjustable to playtest:</b> if fifteen
     * arrives too early, the fix is a curve here, not a new constant at the call site.</p>
     */
    public static int lightFor(int charge) {
        int c = clamp(charge);
        if (c <= 0) {
            return 0;
        }
        return Math.min(LIGHT_MAX, c);
    }

    /** Whether the block is holding any firestone at all. Drives the {@code charged} block state. */
    public static boolean isCharged(int charge) {
        return charge > 0;
    }

    /** Whether the charge is above the automation ceiling, which is the overload band. */
    public static boolean isOverloaded(int charge) {
        return clamp(charge) > MAX_NORMAL;
    }

    /**
     * Whether the reactor is lit: <b>it needs at least one firestone and NOT a redstone signal.</b>
     *
     * <p><b>The signal switches it OFF, it does not switch it ON.</b> The design's lever is a
     * master switch over a reactor that runs on its charge: put firestone in and the field lights
     * up, and a lever beside it is the way to stop it. Reading it the other way round -- signal
     * required to run -- makes a reactor that does nothing at all until a lever is placed, which
     * reads as broken rather than as switched off. A reactor switched off consumes nothing and,
     * phase two, does not overload.</p>
     */
    public static boolean isActive(boolean powered, int charge) {
        return isCharged(charge) && !powered;
    }
}
