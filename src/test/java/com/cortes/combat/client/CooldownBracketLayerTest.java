package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cooldown brackets: how far out they sit, and how the bar between them looks.
 *
 * <p>Both halves of the widget are here, and both are pure arithmetic over a cone angle, so they
 * run without the game. That matters more than usual for this class: every number in it has to
 * be right on a screen the author does not own, at a GUI scale the author does not use, with a
 * field of view the player chose. Those are exactly the conditions under which looking at it
 * once proves nothing.</p>
 *
 * <p>The geometry lives in {@link ConeGauge}, not in the layer, because the layer implements
 * {@code LayeredDraw.Layer} and a test that touches it dies with a class loading error before
 * a single assertion runs.</p>
 */
class CooldownBracketLayerTest {

    /** The reference screen the one-argument calls stand in for: 1080p at GUI scale two. */
    private static final int REF_HEIGHT = 540;
    private static final int REF_WIDTH = 960;
    private static final double REF_FOV = 90.0;

    @Test
    @DisplayName("the widget covers the same fraction of the screen at any resolution")
    void resolutionIndependent() {
        // The bug the whole sizing rule was rewritten for. A fixed pixel span is unreadably
        // small on a big screen, because the player is looking at a bigger picture and the
        // widget does not grow with it. Same GUI scale, same FOV, a taller screen, and the
        // widget has to grow by exactly the same ratio or the same weapon reads two ways.
        int guiScale = 2;
        int smallHeight = 1080 / guiScale;
        int largeHeight = 1440 / guiScale;
        double ratio = (double) largeHeight / smallHeight;
        for (float angle : new float[] {12, 35, 55, 75}) {
            int small = ConeGauge.halfSpan(angle, smallHeight, REF_FOV, 1920 / guiScale);
            int large = ConeGauge.halfSpan(angle, largeHeight, REF_FOV, 2560 / guiScale);
            assertEquals(small * ratio, large, 2,
                    "a " + angle + " degree cone must grow with the screen, was "
                            + small + " scaled pixels at 1080p and " + large + " at 1440p");
        }
    }

    @Test
    @DisplayName("the widget covers the same physical size at every GUI scale")
    void guiScaleIndependent() {
        // The other half of the same requirement. A GUI pixel is a different number of screen
        // pixels at each scale, so the span in GUI pixels has to shrink as the scale rises for
        // the widget to stay put.
        int reference = ConeGauge.halfSpan(35.0F, 1080, REF_FOV, 1920);
        for (int guiScale : new int[] {1, 2, 3, 4}) {
            int span = ConeGauge.halfSpan(35.0F, 1080 / guiScale, REF_FOV, 1920 / guiScale);
            assertEquals(reference, span * guiScale, 2,
                    "at GUI scale " + guiScale + " a 35 degree cone covers "
                            + (span * guiScale) + " screen pixels, expected " + reference);
        }
    }

    @Test
    @DisplayName("the widget shrinks when the player zooms out, because the cone really does")
    void followsTheFov() {
        // Not a preference. At a wider FOV the same cone covers fewer pixels, and an indicator
        // that ignored that would be claiming the weapon reaches further than the view shows.
        int at70 = ConeGauge.halfSpan(35.0F, REF_HEIGHT, 70.0, REF_WIDTH);
        int at90 = ConeGauge.halfSpan(35.0F, REF_HEIGHT, 90.0, REF_WIDTH);
        int at110 = ConeGauge.halfSpan(35.0F, REF_HEIGHT, 110.0, REF_WIDTH);
        assertTrue(at70 > at90, "at 70 FOV the indicator should be wider than at 90, was "
                + at70 + " and " + at90);
        assertTrue(at90 > at110, "at 110 FOV the indicator should be narrower than at 90, was "
                + at90 + " and " + at110);
    }

    @Test
    @DisplayName("a wider cone always draws wider, at every screen and FOV")
    void monotonic() {
        // The one invariant the widget rests on. If it ever inverts the indicator is lying: a
        // sabre would draw tighter than a dagger.
        for (int height : new int[] {360, 540, 720}) {
            for (double fov : new double[] {60, 90, 120}) {
                int previous = 0;
                for (float angle = 0.0F; angle <= 140.0F; angle += 1.0F) {
                    int span = ConeGauge.halfSpan(angle, height, fov, height * 2);
                    assertTrue(span >= previous,
                            "at height " + height + " and " + fov + " FOV, " + angle
                                    + " degrees drew tighter than a narrower cone: "
                                    + previous + " -> " + span);
                    previous = span;
                }
            }
        }
    }

    @Test
    @DisplayName("the tightest and widest cones in the design are told apart")
    void designExtremes() {
        // The twelve degree rapier, the thirty five degree sword, the fifty five degree halberd
        // and the seventy five degree sabre, at the reference screen.
        int rapier = ConeGauge.halfSpan(12.0F);
        int sword = ConeGauge.halfSpan(35.0F);
        int halberd = ConeGauge.halfSpan(55.0F);
        int sabre = ConeGauge.halfSpan(75.0F);
        assertTrue(rapier < sword && sword < halberd && halberd < sabre,
                "the four must rank in order, was " + rapier + ", " + sword + ", "
                        + halberd + ", " + sabre);
        assertTrue(sabre > rapier * 2,
                "the widest cone should be clearly wider than the tightest, was "
                        + rapier + " and " + sabre);
    }

    @Test
    @DisplayName("the common weapons are told apart from each other")
    void commonWeaponsAreDistinct() {
        // The band that matters. Sword, axe and pickaxe live within a few degrees of each other,
        // and a linear spread put all of them on the same span, so the four weapons a player
        // actually carries drew identically.
        int sword = ConeGauge.halfSpan(35.0F);
        int dagger = ConeGauge.halfSpan(45.0F);
        assertTrue(dagger - sword >= 4,
                "a 45 degree cone must be visibly wider than a 35 degree one, was "
                        + sword + " vs " + dagger);
    }

    @Test
    @DisplayName("a very wide cone still fits on the screen")
    void screenCap() {
        // The backstop. A wide cone on a narrow window would otherwise run the brackets off the
        // edge, and a bracket drawn off the screen is worse than one at the margin.
        for (int guiWidth : new int[] {320, 480, 640, 960, 1920}) {
            int span = ConeGauge.halfSpan(89.0F, guiWidth / 2, REF_FOV, guiWidth);
            int cap = Math.round(guiWidth * ConeGauge.MAX_SCREEN_FRACTION);
            assertTrue(span <= cap,
                    "span " + span + " exceeds the cap of " + cap + " on a " + guiWidth
                            + " wide GUI");
            assertTrue(span * 2 < guiWidth, "the brackets must not meet across the screen");
        }
    }

    @Test
    @DisplayName("the screen cap never makes a wide cone tighter than a narrow one")
    void capPreservesOrder() {
        // A cap that reordered the weapons would be worse than no cap: the indicator would show
        // a halberd tighter than a sword, which is a straight lie.
        for (int guiWidth : new int[] {200, 320, 480, 960, 1920}) {
            int previous = 0;
            for (float angle = 0.0F; angle <= 140.0F; angle += 1.0F) {
                int span = ConeGauge.halfSpan(angle, guiWidth / 2, REF_FOV, guiWidth);
                assertTrue(span >= previous,
                        "on a " + guiWidth + " wide GUI, " + angle
                                + " degrees drew tighter than a narrower cone: "
                                + previous + " -> " + span);
                previous = span;
            }
        }
    }

    @Test
    @DisplayName("the brackets never sit on top of the crosshair")
    void clearsTheCrosshair() {
        // The crosshair is 15 pixels and grows on a hit. A bracket inside its silhouette makes
        // the two merge into one blob at exactly the moment the player is trying to aim.
        for (float angle = 0.0F; angle <= 90.0F; angle += 1.0F) {
            int span = ConeGauge.halfSpan(angle);
            assertTrue(span > 30,
                    "a bracket at " + span + "px is too close to the 15px crosshair, cone " + angle);
        }
    }

    @Test
    @DisplayName("a degenerate screen or FOV falls back instead of dividing by zero")
    void degenerateInputs() {
        assertEquals(ConeGauge.MIN_HALF_SPAN, ConeGauge.halfSpan(35.0F, 0, REF_FOV, REF_WIDTH));
        assertEquals(ConeGauge.MIN_HALF_SPAN, ConeGauge.halfSpan(35.0F, 540, 0.0, REF_WIDTH));
        assertEquals(ConeGauge.MIN_HALF_SPAN, ConeGauge.halfSpan(35.0F, 540, 180.0, REF_WIDTH));
        assertTrue(ConeGauge.halfSpan(0.0F) >= ConeGauge.MIN_HALF_SPAN,
                "a zero angle cone must not collapse into the crosshair");
    }

    @Test
    @DisplayName("the bar is fully transparent at the crosshair and brightest at the bracket")
    void gradientRunsFromNothingToFull() {
        // The end nearest the aim point must be invisible, so the line has no visible start and
        // the widget reads as a frame rather than as part of the crosshair. The separation is
        // bought here rather than with a bigger span.
        int length = 60;
        assertEquals(0, ConeGauge.barAlpha(0, length, length),
                "the bar must begin fully transparent at the crosshair");
        assertEquals(ConeGauge.ALPHA_NEAR_BRACKET,
                ConeGauge.barAlpha(length - 1, length, length), 1,
                "the bar must reach full strength at the bracket");
    }

    @Test
    @DisplayName("the bar brightens monotonically the whole way")
    void barIsMonotonic() {
        // A gradient that dips anywhere stops reading as a fade and starts reading as texture.
        for (int length = 30; length <= 150; length += 5) {
            int previous = -1;
            for (int i = 0; i < length; i++) {
                int alpha = ConeGauge.barAlpha(i, length, length);
                assertTrue(alpha >= previous,
                        "a " + length + " long bar dipped at column " + i
                                + ": " + previous + " -> " + alpha);
                previous = alpha;
            }
        }
    }

    @Test
    @DisplayName("the bar never steps: no column is more than a soft gradient from the next")
    void noSteps() {
        // A gradient in even a handful of bands reads as bands. Checked across the range of
        // lengths the widget can produce, from the tightest cone with the crosshair and its
        // bracket subtracted, through to the widest.
        for (int length = 25; length <= 150; length += 5) {
            int biggest = 0;
            for (int i = 0; i < length - 1; i++) {
                biggest = Math.max(biggest,
                        Math.abs(ConeGauge.barAlpha(i + 1, length, length)
                                - ConeGauge.barAlpha(i, length, length)));
            }
            // A smoothstep peaks at about ALPHA * 1.5 / (length - 1), so the bound sits just
            // above the worst case rather than at an arbitrary round number.
            assertTrue(biggest <= 14,
                    "a " + length + " long bar bands instead of fading, biggest jump was "
                            + biggest);
        }
    }

    @Test
    @DisplayName("the bar's first visible pixel is a real distance from the crosshair")
    void firstVisiblePixelIsAwayFromTheCrosshair() {
        // The point of starting at zero: on a long bar nothing shows for the first stretch, so
        // the widget cannot be mistaken for part of the crosshair.
        int length = 150;
        int firstVisible = -1;
        for (int i = 0; i < length; i++) {
            if (ConeGauge.barAlpha(i, length, length) > 24) {
                firstVisible = i;
                break;
            }
        }
        assertTrue(firstVisible >= 15,
                "the bar should not be visible until well away from the crosshair, was column "
                        + firstVisible);
    }

    @Test
    @DisplayName("an unready weapon draws nothing at all")
    void unreadyDrawsNothing() {
        assertEquals(0, ConeGauge.barAlpha(0, 30, 0), "an empty bar must draw no column at all");
        assertEquals(0, ConeGauge.barAlpha(15, 30, 0));
    }

    @Test
    @DisplayName("the bar only ever grows: drawn columns keep their brightness and gain one more")
    void fillOnlyGrows() {
        // The bar has to get better, not worse, as the weapon gets ready. "Better" is strictly
        // one more column: no column that was already lit may change brightness, or the player
        // watches the line they are tracking shift under them.
        int length = 40;
        for (int filled = 1; filled <= length; filled++) {
            for (int i = 0; i < filled - 1; i++) {
                assertEquals(ConeGauge.barAlpha(i, length, filled - 1),
                        ConeGauge.barAlpha(i, length, filled),
                        "lit column " + i + " changed brightness when the fill grew to " + filled);
            }
            assertEquals(0, ConeGauge.barAlpha(filled, length, filled),
                    "the bar must stop at the front, not run past it");
        }
    }

    @Test
    @DisplayName("a zero length or empty bar draws nothing instead of crashing")
    void degenerateBars() {
        assertEquals(0, ConeGauge.barAlpha(0, 0, 0));
        assertEquals(0, ConeGauge.barAlpha(5, -3, 2));
        assertEquals(0, ConeGauge.barAlpha(0, 10, 0));
    }
}
