package com.cortes.combat.client;

/**
 * The geometry of the cooldown brackets, as pure arithmetic.
 *
 * <p>Separate from {@link CooldownBracketLayer} for the same reason {@code ConeMath} is separate
 * from {@code Reach}: the layer implements {@code LayeredDraw.Layer}, which does not exist
 * without the game on the classpath, so any test that touches it dies with a class loading error
 * before a single assertion runs. The numbers live here so they can be checked on a bare JVM.</p>
 *
 * <p>What it answers is one question: given a cone's half-angle, how far from the crosshair do
 * the brackets sit. Everything the widget draws is then measured from that one number.</p>
 */
public final class ConeGauge {

    /**
     * Where the brackets sit at the narrowest cone, and how much further out the widest goes.
     *
     * <p>Deliberately not a percentage of the screen. A percentage looks correct at one window
     * size and crowds the crosshair at another, and the crosshair is the one thing on screen
     * that must never be crowded. Pixels keep a fixed relationship to the crosshair's own 15,
     * which is the reference the player's eye is already calibrated on.</p>
     */
    static final int MIN_HALF_SPAN = 22;
    static final int MAX_HALF_SPAN = 62;

    /** Cone half-angle window the span is stretched across, in degrees. */
    static final float ANGLE_FLOOR = 10.0F;
    static final float ANGLE_CEILING = 80.0F;

    private ConeGauge() {
    }

    /**
     * How far the brackets sit from the centre, for a cone of the given half-angle.
     *
     * <p>A cone angle, not a weapon. Anything between {@link #ANGLE_FLOOR} and
     * {@link #ANGLE_CEILING} degrees gets a proportional span and anything outside is clamped,
     * so a modded weapon with a wider profile is drawn as "wider" rather than as "wrong".</p>
     *
     * <p>The curve is not linear, and that is the whole point. Linear spread made the common
     * weapons — the 30 to 45 degree band where sword, axe, dagger and pickaxe all live — land
     * within a few pixels of each other, so the four weapons a player actually carries all drew
     * identically. Squaring the parameter spends the range on the band where the weapons differ
     * instead of on the extremes nobody uses.</p>
     */
    public static int halfSpan(float horizontalAngle) {
        float t = (horizontalAngle - ANGLE_FLOOR) / (ANGLE_CEILING - ANGLE_FLOOR);
        t = Math.max(0.0F, Math.min(1.0F, t));
        return MIN_HALF_SPAN + Math.round(t * t * (MAX_HALF_SPAN - MIN_HALF_SPAN));
    }
}
