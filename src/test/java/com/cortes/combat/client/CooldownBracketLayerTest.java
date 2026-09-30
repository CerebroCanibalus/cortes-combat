package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far the cooldown brackets sit for a given cone.
 *
 * <p>Pure arithmetic over a cone angle, so it runs without the game even though the widget that
 * uses it is a GUI layer. The failure this guards against is not a crash: it is a widget that
 * quietly stops telling the player anything, which is exactly the kind of bug a test has to be
 * told about by a human looking at the screen.</p>
 */
class CooldownBracketLayerTest {

    private static final float SWORD = 35.0F;
    private static final float DAGGER = 45.0F;
    private static final float AXE = 45.0F;
    private static final float SABRE = 75.0F;
    private static final float RAPIER = 12.0F;

    @Test
    @DisplayName("a wider cone always draws wider")
    void monotonic() {
        // The one invariant the whole widget rests on. If this ever inverts, the indicator is
        // actively lying: a sabre would look tighter than a dagger.
        int previous = 0;
        for (float angle = 0.0F; angle <= 90.0F; angle += 0.5F) {
            int span = ConeGauge.halfSpan(angle);
            assertTrue(span >= previous,
                    "span shrank going from " + (angle - 0.5F) + " to " + angle
                            + " degrees: " + previous + " -> " + span);
            previous = span;
        }
    }

    @Test
    @DisplayName("angles outside the window are clamped, not extrapolated")
    void clamped() {
        assertEquals(ConeGauge.halfSpan(0.0F), ConeGauge.halfSpan(-40.0F),
                "a negative angle must clamp to the floor, not draw off the bracket");
        assertEquals(ConeGauge.halfSpan(180.0F), ConeGauge.halfSpan(200.0F),
                "an absurd angle must clamp to the ceiling rather than leaving the screen");
    }

    @Test
    @DisplayName("the common weapons are told apart from each other")
    void commonWeaponsAreDistinct() {
        // The band that matters. Sword, axe and pickaxe live within a few degrees of each
        // other, and a linear spread put all of them on the same span, so the four weapons a
        // player actually carries drew identically.
        int sword = ConeGauge.halfSpan(SWORD);
        int dagger = ConeGauge.halfSpan(DAGGER);
        assertTrue(dagger - sword >= 4,
                "a 45 degree cone must be visibly wider than a 35 degree one, was "
                        + sword + " vs " + dagger);
    }

    @Test
    @DisplayName("the extremes use the full range")
    void usesTheRange() {
        assertEquals(46, ConeGauge.halfSpan(10.0F), "narrowest cone, floor");
        assertEquals(150, ConeGauge.halfSpan(100.0F), "widest cone, ceiling");
    }

    @Test
    @DisplayName("an enormous cone still gets a large radius rather than clamping to the top")
    void enormousConesGetRoom() {
        // The design has weapons coming that are wider than anything in the table now. A cone
        // of 120 degrees has to be visibly wider than one of 100, or those weapons all draw the
        // same frame and the indicator stops describing them.
        int at100 = ConeGauge.halfSpan(100.0F);
        int at120 = ConeGauge.halfSpan(120.0F);
        int at60 = ConeGauge.halfSpan(60.0F);
        assertTrue(at120 >= at100, "120 degrees drew tighter than 100");
        assertTrue(at100 > at60, "100 degrees drew tighter than 60");
    }

    @Test
    @DisplayName("the rapier is the tightest and the sabre the widest, as designed")
    void extremesMatchTheDesign() {
        int rapier = ConeGauge.halfSpan(RAPIER);
        int sabre = ConeGauge.halfSpan(SABRE);
        assertTrue(rapier < ConeGauge.halfSpan(SWORD),
                "the rapier has a 12 degree cone and must draw tighter than a 35 degree sword");
        assertTrue(sabre > ConeGauge.halfSpan(AXE),
                "the sabre has a 75 degree cone and must draw wider than a 45 degree axe");
    }

    @Test
    @DisplayName("the brackets never sit on top of the crosshair")
    void clearsTheCrosshair() {
        // The crosshair is 15px and grows on a hit. A bracket that can land inside its silhouette
        // makes the two merge into one blob at exactly the moment the player is trying to aim.
        // Three times its width, because the bar between them starts invisible and the eye
        // needs room before it finds anything at all.
        for (float angle = 0.0F; angle <= 90.0F; angle += 1.0F) {
            int span = ConeGauge.halfSpan(angle);
            assertTrue(span > 45,
                    "a bracket at " + span + "px is too close to the 15px crosshair, cone " + angle);
        }
    }

    @Test
    @DisplayName("the bar is fully transparent at the crosshair and brightest at the bracket")
    void gradientRunsFromNothingToFull() {
        // The requirement as stated: the end nearest the aim point must be invisible, so the line
        // has no visible start and the widget reads as a frame rather than as part of the
        // crosshair. The separation is bought here rather than with a bigger span.
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
        for (int length = 30; length <= 140; length += 5) {
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
        // lengths the widget can actually produce, which is about 30 to 140 pixels: the narrowest
        // cone with the crosshair and its bracket subtracted, through to the widest.
        for (int length = 30; length <= 140; length += 5) {
            int biggest = 0;
            for (int i = 0; i < length - 1; i++) {
                biggest = Math.max(biggest,
                        Math.abs(ConeGauge.barAlpha(i + 1, length, length)
                                - ConeGauge.barAlpha(i, length, length)));
            }
            // A smoothstep over this range peaks at about 210 * 1.5 / (length - 1), so the
            // bound is set just above the worst case rather than at an arbitrary round number.
            assertTrue(biggest <= 12,
                    "a " + length + " long bar bands instead of fading, biggest jump was "
                            + biggest);
        }
    }

    @Test
    @DisplayName("the bar's first visible pixel is a real distance from the crosshair")
    void firstVisiblePixelIsAwayFromTheCrosshair() {
        // The point of starting at zero: on a long bar, nothing at all shows for the first
        // stretch, so the widget cannot be mistaken for part of the crosshair. On the shortest
        // bar the effect is weaker, which is the honest trade for a tight cone.
        int length = 150;
        int firstVisible = -1;
        for (int i = 0; i < length; i++) {
            if (ConeGauge.barAlpha(i, length, length) > 24) {
                firstVisible = i;
                break;
            }
        }
        assertTrue(firstVisible >= 20,
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
        // The bar has to get better, not worse, as the weapon gets ready. "Better" here is
        // strictly one more column: no column that was already lit may change brightness, or the
        // player watches the line they are tracking shift under them.
        //
        // The new column is allowed to be invisible, because the crosshair end of the bar is
        // fully transparent by design. What must hold is the extent: the bar stops growing the
        // moment it reaches the front, and not before.
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

    @Test
    @DisplayName("a very wide cone still fits on the screen")
    void screenCap() {
        // The reason the cap exists. At GUI scale four on a small window, an uncapped 150 pixels
        // is most of the screen and the brackets would leave it.
        for (int guiWidth : new int[] {320, 480, 640, 960, 1920}) {
            int span = ConeGauge.halfSpan(100.0F, guiWidth);
            assertTrue(span <= guiWidth * ConeGauge.MAX_SCREEN_FRACTION,
                    "span " + span + " exceeds the cap on a " + guiWidth + " wide GUI");
            assertTrue(span * 2 < guiWidth, "the brackets must not meet across the screen");
        }
    }

    @Test
    @DisplayName("the screen cap never makes a wide cone tighter than a narrow one")
    void capPreservesOrder() {
        // A cap that reorders the weapons would be worse than no cap: the indicator would show a
        // halberd tighter than a sword, which is a straight lie.
        for (int guiWidth : new int[] {200, 320, 480, 960, 1920}) {
            int previous = 0;
            for (float angle = 0.0F; angle <= 120.0F; angle += 1.0F) {
                int span = ConeGauge.halfSpan(angle, guiWidth);
                assertTrue(span >= previous,
                        "on a " + guiWidth + " wide GUI, " + angle
                                + " degrees drew tighter than a narrower cone: "
                                + previous + " -> " + span);
                previous = span;
            }
        }
    }
}
