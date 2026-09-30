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
     * Overall size of the widget, as a fraction of the cone's true projected width.
     *
     * <p>One, because the projection already lands in a sensible range once the angle is
     * compressed below. Anything else is a fudge factor on top of real geometry, and it is the
     * fudge factors that have to be re-tuned every time a screen resolution shows up.</p>
     */
    static final double SCALE = 1.0;

    /**
     * Where the angle response flattens out.
     *
     * <p>The true projection is {@code tan(angle)}, and between the tightest and widest cone in
     * the design that spans seventeen to one: a widget with a seventeen to one range cannot be
     * read, because the tight end collapses into the crosshair and the wide end leaves the
     * screen. Dividing the tangent by {@code 1 + tangent / SATURATION} keeps the ordering and
     * the smoothness and brings it to about seven to one.</p>
     *
     * <p><b>It has to be applied to the angle, not to the projected pixels.</b> A power of the
     * pixel count looks equivalent and is not: the square root of a doubled projection is only
     * one and a half times bigger, so the widget would stop growing with the screen. Compressing
     * the angle instead keeps {@code span} exactly proportional to the screen height, which is
     * the property the whole class exists to guarantee.</p>
     */
    static final double SATURATION = 2.0;

    /**
     * The widest fraction of the screen the brackets may reach.
     *
     * <p>A backstop, not the sizing rule. On a narrow window with a very wide cone the brackets
     * would otherwise run off the edge, and a bracket drawn off the screen is worse than one
     * drawn at the margin.</p>
     */
    static final float MAX_SCREEN_FRACTION = 0.42F;

    /**
     * Below this, the widget would sit inside the crosshair's own silhouette.
     *
     * <p>The crosshair is 15 pixels and grows on a hit, so a bracket has to clear about twice
     * that even before the bar, which starts fully transparent, becomes visible at all.</p>
     */
    static final int MIN_HALF_SPAN = 42;

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
    public static final int ALPHA_NEAR_BRACKET = 175;
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
     * <p><b>The size is the cone's projected width on screen.</b> A cone of half-angle
     * {@code a}, seen with a vertical field of view {@code f}, subtends
     * {@code (height / 2) * tan(a) / tan(f / 2)} pixels from the centre line, and that
     * expression is what makes the widget correct on a 1080p screen and on a 2K one, at any GUI
     * scale, at any FOV the player chose, with no magic number per resolution.</p>
     *
     * <p>Note that the screen <em>width</em> does not appear in it, and that is not an
     * oversight. Pixels per radian of horizontal angle is set by the vertical FOV and the
     * height, so a wider window at the same FOV shows more world to the sides without
     * stretching the indicator. The width is used only for the backstop.</p>
     *
     * <p>The tangent is then saturated, because a literal 75 degree cone really does fill the
     * screen and a widget that fills the screen is not a reading of anything. The saturation
     * divides the tangent rather than the result, which is what keeps the span exactly
     * proportional to the screen height.</p>
     *
     * @param horizontalAngle the cone's half-angle, in degrees
     * @param guiHeight       the scaled screen height in pixels
     * @param fovDegrees      the player's vertical field of view, in degrees
     * @param guiWidth        the scaled screen width, used only for the backstop
     */
    public static int halfSpan(float horizontalAngle, int guiHeight, double fovDegrees, int guiWidth) {
        if (guiHeight <= 0 || fovDegrees <= 0.0 || fovDegrees >= 180.0) {
            return MIN_HALF_SPAN;
        }
        double fovHalf = Math.toRadians(fovDegrees) / 2.0;
        // Pixels per radian of horizontal angle. A wider FOV means more world in view, so fewer
        // pixels for any given angle, which is exactly right: zoom out and the cone really does
        // get smaller on screen.
        double pixelsPerRadian = (guiHeight / 2.0) / Math.tan(fovHalf);
        // Clamped below a right angle: a half-angle past 90 degrees has a negative tangent and
        // would draw the brackets inwards. No cone in the design is that wide, but a config
        // override is not obliged to be sensible.
        double angle = Math.min(Math.max(horizontalAngle, 0.0F), 89.0F);
        double tangent = Math.tan(Math.toRadians(angle));
        double compressed = tangent / (1.0 + tangent / SATURATION);
        double span = pixelsPerRadian * compressed * SCALE;
        if (!Double.isFinite(span) || span <= 0.0) {
            return MIN_HALF_SPAN;
        }
        int result = (int) Math.round(span);
        if (guiWidth > 0) {
            result = Math.min(result, Math.round(guiWidth * MAX_SCREEN_FRACTION));
        }
        return Math.max(MIN_HALF_SPAN, result);
    }

    /**
     * A default 1080p-at-scale-2 call, for tests and for anywhere the window is not available.
     *
     * <p>Every one of these numbers is only a stand-in. The real widget always passes the live
     * screen and FOV, which is the entire point of the formula.</p>
     */
    public static int halfSpan(float horizontalAngle) {
        return halfSpan(horizontalAngle, 540, 90.0, 960);
    }
}
