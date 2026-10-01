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
     *
     * <p>This is the gradient's shape. How loud the whole widget is, is {@link #WIDGET_ALPHA},
     * and keeping the two apart is the point: the shape answers "where does the line fade" and
     * the multiplier answers "how present is this thing at all".</p>
     */
    public static final int ALPHA_NEAR_BRACKET = 175;
    public static final int ALPHA_NEAR_CROSSHAIR = 0;

    /**
     * How present the widget is, and the reason it stopped being annoying.
     *
     * <p>The playtest called the brackets and the bar "too intrusive", and the interesting part
     * is not the brightness. It is that this widget is on screen <b>every frame of every
     * session</b>, in the same place, and the crosshair is also there. Anything permanent competes
     * with the thing you aim with, and a competing element does not become familiar, it becomes
     * noise that you have to consciously ignore.</p>
     *
     * <p>So the widget now sits clearly below the crosshair, which is the invariant that
     * matters: <b>the brightest thing in the middle of the screen is the thing the player is
     * aiming with</b>. Brackets and bar share this one number because they are one object at one
     * place, and a widget whose two halves dim by different amounts reads as two unrelated
     * things.</p>
     *
     * <p>It stays above zero on purpose, and it stays above the noise floor of a mid-grey wall at
     * every GUI scale: these are a spatial reading, and position survives dimming far better
     * than brightness does. A faint marker in the right place still tells you your spacing.</p>
     */
    public static final float WIDGET_ALPHA = 0.55F;

    /**
     * How present the brackets are, which is lower than the bar's peak and it is not a fudge.
     *
     * <p>The playtest called the whole widget "too intrusive" and then, once this landed, said
     * the bar was exactly right and the brackets still looked solid. That is not a smaller
     * version of the same problem: it is two shapes with different averages. The bar is a
     * gradient that reaches zero at the crosshair, so its average presence sits far below its
     * peak, while a bracket is a flat mark held at one alpha all the way across. One number
     * cannot serve both without one of them reading wrong, and matching the peaks made the
     * brackets about three times louder than the bar in perceived terms.</p>
     *
     * <p>Two numbers, still one decision. Both sit below the crosshair, which is the invariant
     * that matters: <b>the brightest thing in the middle of the screen is the thing the player
     * is aiming with.</b> This widget is on screen every frame of every session in the same
     * place, and a permanent element that competes with the aim point does not become familiar,
     * it becomes noise to consciously ignore.</p>
     *
     * <p>Well above nothing on purpose, because these are a spatial reading and position
     * survives dimming far better than brightness does: a faint mark in the right place still
     * says how far the weapon reaches, which is the only thing in this widget that is not a
     * moment-to-moment reading.</p>
     *
     * <p><b>The number barely moved and the brackets got much more visible, because the bug was
     * never this constant.</b> It used to be multiplied by the sprite's own alpha, and the art
     * was redrawn: the old file peaked at 102 of 255, the new one at 38, so a constant that had
     * been producing 28 of 255 was quietly producing 11. Eleven of 255 is not a bracket, it is a
     * rumour, and no amount of tuning this number could have found it.
     * {@link BracketMask} now divides the art's alpha by the art's own peak -- the same split the
     * bar's gradient already used -- so this is a real opacity and the art decides only shape.</p>
     *
     * <p>Thirty percent, which is a little under the bar's peak, and the reason it is not lower
     * is the art: the old bracket was a ten column solid mark and this one is a thin outline with
     * gaps in it. A thinner mark needs more alpha to read at the same weight, so restoring the old
     * 28 would have left the new bracket under-reading rather than matching it. This sits close to
     * the ceiling the test below sets, and that ceiling is deliberate -- the player called this
     * widget "perfecto" at a fraction of the bar's brightness, and nothing about the new art is a
     * reason to start competing with the crosshair.</p>
     */
    public static final float BRACKET_ALPHA = 0.30F;

    /** The alpha the brackets are drawn at. One definition, so the layer and the test agree. */
    public static float bracketAlpha() {
        return BRACKET_ALPHA;
    }

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
        int shape = Math.round(ALPHA_NEAR_CROSSHAIR
                + (ALPHA_NEAR_BRACKET - ALPHA_NEAR_CROSSHAIR) * smoothstep(t));
        // The widget's own presence, applied after the shape so that changing how loud it is
        // never changes where it fades. See WIDGET_ALPHA.
        return Math.round(shape * WIDGET_ALPHA);
    }

    /**
     * The brightest the bar ever gets, which is the shape's peak scaled by the widget's presence.
     *
     * <p>One definition rather than two. The shape constant says where the gradient tops out; this
     * says what that is worth on screen once the widget has been dialled down, and the layer, the
     * test and any future tuning all read the same number instead of each re-deriving the
     * product.</p>
     */
    public static int peakBarAlpha() {
        return Math.round(ALPHA_NEAR_BRACKET * WIDGET_ALPHA);
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

    /**
     * How far short of the brackets the bar stops, in pixels.
     *
     * <p>The gap between the end of the line and the ink. It is a gap and not a measurement of
     * the art because the art's extent is {@code BracketMask}'s to report, and this class has no
     * business knowing how wide a bracket is.</p>
     */
    public static final int BAR_INSET = 5;

    /**
     * Where the right bracket's ink sits, given where the left one's does.
     *
     * <p><b>This is the whole "descentrados" bug, and it is one pixel.</b> The two anchors were
     * each measured as the middle column of their own ink, and the ink is four columns wide, so
     * both rounded down. For a pair to be an exact mirror in an image of {@code width}, the two
     * anchors have to sum to {@code width - 1}: four and eighteen for a twenty-three wide file.
     * Measuring them independently gave four and seventeen, and seventeen is <em>not</em> the
     * mirror of four -- so the right bracket sat one pixel nearer the centre than the left one,
     * and the bar, which is symmetric, ended up two pixels from one bracket and three from the
     * other.</p>
     *
     * <p>Nothing about that reads as a bug in a screenshot. It reads as "the brackets are not
     * quite centred on the bars", which is what the playtest said, three rounds running, while
     * every number in the diagnostic was individually correct.</p>
     */
    public static int mirrorAnchor(int leftAnchor, int spriteWidth) {
        return spriteWidth - 1 - leftAnchor;
    }

    /**
     * Every number the widget draws, in one value.
     *
     * <p>It exists because of a specific failure: the playtest reported twice that the brackets
     * were off centre with respect to the bars, and the only way to answer that is to print the
     * numbers. The first version of the printout recomputed them, which is a second copy of the
     * arithmetic and therefore a second thing that can be wrong -- and a diagnostic that lies is
     * worse than none, because it sends the next hour of the search in the wrong direction.</p>
     *
     * <p>So the layer draws from this and the diagnostic reads the same one. If they ever
     * disagree, the disagreement is a bug in one function rather than in two.</p>
     */
    public record Geometry(int halfSpan, int barInner, int barOuter, int barLength) {

        /** Where the bar stops on the right, measured from the crosshair. */
        public int barEnd() {
            return barOuter + barLength;
        }

        /** The gap between the end of the line and the bracket's centre, which is what the eye reads. */
        public int gapToBracket() {
            return halfSpan - barEnd();
        }
    }

    /**
     * The widget's geometry for a live screen.
     *
     * @param horizontalAngle the cone's half-angle, which is what sets {@code halfSpan}
     * @param crosshairReach  how far the crosshair currently extends; the bar starts outside it
     */
    public static Geometry geometry(float horizontalAngle, int guiWidth, int guiHeight,
                                    double fov, int crosshairReach) {
        int halfSpan = halfSpan(horizontalAngle, guiHeight, fov, guiWidth);
        int inner = halfSpan - BAR_INSET;
        int length = Math.max(0, inner - crosshairReach);
        return new Geometry(halfSpan, inner, crosshairReach, length);
    }
}
