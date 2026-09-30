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
     * <p>This went 12-34, then 22-62, then 36-86, and every one of those was too tight: at any
     * of them the widget read as part of the crosshair rather than as a frame around it. The
     * range is now wide enough to hold a weapon with a genuinely enormous cone without clamping
     * it against the top of the scale, because the design has weapons coming that are wider
     * than anything currently in the table.</p>
     */
    static final int MIN_HALF_SPAN = 46;
    static final int MAX_HALF_SPAN = 150;

    /**
     * The widest fraction of the screen the brackets may reach.
     *
     * <p>A hard cap, and the reason the span cannot be a pure pixel number: at GUI scale 4 on a
     * small window, 150 pixels is most of the screen and the brackets would leave it. Capping
     * against the actual width means a wide cone always stays visible, and the cost is that the
     * widget is proportionally smaller on a small screen, which is better than being cut off.</p>
     */
    static final float MAX_SCREEN_FRACTION = 0.42F;

    /** Cone half-angle window the span is stretched across, in degrees. */
    static final float ANGLE_FLOOR = 10.0F;
    static final float ANGLE_CEILING = 100.0F;

    /**
     * Alpha at the bracket end of the bar, and the value it fades from at the crosshair end.
     *
     * <p>The crosshair end is <b>zero on purpose</b>, and that is the answer to "the brackets
     * still feel glued to the crosshair". A line that begins fully transparent has no visible
     * start at all: the eye first sees it about a third of the way out, so the separation is
     * bought with the gradient instead of with a bigger number, and it costs nothing at any
     * screen size.</p>
     *
     * <p>An earlier version refused to go below a hundred and sixty, on the theory that a low
     * floor made the bar look like scattered dots. That was a misdiagnosis: the dots came from
     * the second brightness level, the unfilled socket, which has since been deleted. With one
     * gradient there is nothing to break up, so the floor can go to zero.</p>
     */
    public static final int ALPHA_NEAR_BRACKET = 210;
    public static final int ALPHA_NEAR_CROSSHAIR = 0;

    private ConeGauge() {
    }

    /**
     * The alpha of one pixel column of the bar, from the crosshair side outward.
     *
     * <p>One effect: a gradient along the bar, bright at the bracket and soft at the crosshair,
     * eased rather than linear. A linear ramp reads as a ramp with visible steps; an eased one
     * reads as a fade. The bar is drawn from the crosshair out to the moving front, so the
     * player watches one continuous line grow, and it is brightest exactly where the eye goes
     * to check whether the weapon is ready.</p>
     *
     * <p><b>There is no unfilled socket, and there is no soft leading edge.</b> Both were
     * tried, and each broke exactly what the other fixed. A dim socket plus a soft front either
     * put a full-brightness pixel next to the crosshair on an unready weapon, or dimmed the last
     * columns of a fully charged bar down to about fourteen, which is the precise opposite of
     * what the transparency is for. The bar is one line, its transparency is the gradient, and
     * the only thing the cooldown changes is where the line stops.</p>
     *
     * <p>The reach is not lost by dropping the socket: the brackets sit at the reach-scaled
     * distance on every frame, cooldown or not, so how far the weapon reaches is always on
     * screen. The bar is left with one job, which is how far along the cooldown is.</p>
     *
     * @param i      pixel column, 0 at the crosshair and {@code length - 1} at the bracket
     * @param length total length in columns
     * @param filled how many columns are charged
     * @return 0 for a column past the moving front, which is not drawn
     */
    public static int barAlpha(int i, int length, int filled) {
        if (length <= 0 || i < 0 || i >= filled) {
            return 0;
        }
        // 0 at the crosshair, 1 at the bracket.
        float t = length <= 1 ? 1.0F : (float) i / (length - 1);
        return Math.round(ALPHA_NEAR_CROSSHAIR
                + (ALPHA_NEAR_BRACKET - ALPHA_NEAR_CROSSHAIR) * smoothstep(t));
    }

    /** The classic ease. Linear ramps look like steps; this one looks like a fade. */
    private static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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
     * instead of on the extremes.</p>
     *
     * <p>Capped against the screen so a very wide cone cannot push the brackets off the edge at
     * a high GUI scale. Only the cap depends on the screen; the mapping is the same everywhere,
     * so two players on different scales see the same weapon ranked against the same neighbours.</p>
     *
     * @param guiWidth the GUI width in pixels, used only for the cap
     */
    public static int halfSpan(float horizontalAngle, int guiWidth) {
        float t = (horizontalAngle - ANGLE_FLOOR) / (ANGLE_CEILING - ANGLE_FLOOR);
        t = Math.max(0.0F, Math.min(1.0F, t));
        int span = MIN_HALF_SPAN + Math.round(t * t * (MAX_HALF_SPAN - MIN_HALF_SPAN));
        if (guiWidth <= 0) {
            return span;
        }
        return Math.min(span, Math.round(guiWidth * MAX_SCREEN_FRACTION));
    }

    /** As above, for callers that do not care about the screen cap. */
    public static int halfSpan(float horizontalAngle) {
        return halfSpan(horizontalAngle, 0);
    }
}
