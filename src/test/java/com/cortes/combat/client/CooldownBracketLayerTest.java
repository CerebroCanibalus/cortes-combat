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
        assertEquals(36, ConeGauge.halfSpan(10.0F), "narrowest cone, floor");
        assertEquals(86, ConeGauge.halfSpan(80.0F), "widest cone, ceiling");
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
        // The crosshair is 15px and grows on a hit. If a bracket can land inside the crosshair's
        // own silhouette the two merge into an unreadable blob at exactly the moment the player
        // is trying to aim. Twice its width, so they read as two things and not as one.
        for (float angle = 0.0F; angle <= 90.0F; angle += 1.0F) {
            int span = ConeGauge.halfSpan(angle);
            assertTrue(span > 30,
                    "a bracket at " + span + "px is too close to the 15px crosshair, cone " + angle);
        }
    }

    @Test
    @DisplayName("the drawn part of the bar is one continuous line, never a gap")
    void barIsContinuous() {
        // The symptom the player reported: the bar broke up into scattered dots near the
        // crosshair. There is no unfilled socket any more, so every column that is drawn has to
        // be solidly drawn, with nothing between two drawn columns that could look like a break.
        for (int length = 8; length <= 60; length++) {
            for (float ratio : new float[] {0.25F, 0.5F, 0.75F, 1.0F}) {
                int filled = Math.round(length * ratio);
                int drawn = 0;
                for (int i = 0; i < length; i++) {
                    int alpha = ConeGauge.barAlpha(i, length, filled);
                    if (alpha == 0) {
                        continue;
                    }
                    drawn++;
                    assertTrue(alpha >= 160,
                            "column " + i + " of " + length + " at " + ratio
                                    + " is " + alpha + ", which reads as a gap");
                }
                assertTrue(drawn > 0, "a " + ratio + " charged bar drew nothing at all");
            }
        }
    }

    @Test
    @DisplayName("the bar never steps: no column is more than a soft gradient from the next")
    void noSteps() {
        // A gradient in even a handful of bands reads as bands. This is the smoothness the
        // player asked for, checked as a bound on the largest single-column jump.
        int length = 40;
        int biggest = 0;
        for (int i = 0; i < length - 1; i++) {
            biggest = Math.max(biggest,
                    Math.abs(ConeGauge.barAlpha(i + 1, length, length)
                            - ConeGauge.barAlpha(i, length, length)));
        }
        assertTrue(biggest <= 14,
                "the gradient bands instead of fading, biggest single-column jump was " + biggest);
    }

    @Test
    @DisplayName("the bar gets monotonically brighter from the crosshair to the bracket")
    void barIsMonotonic() {
        // A gradient that dips anywhere stops reading as a fade and starts reading as texture.
        int length = 40;
        int previous = -1;
        for (int i = 0; i < length; i++) {
            int alpha = ConeGauge.barAlpha(i, length, length);
            assertTrue(alpha >= previous,
                    "a full bar dipped at column " + i + ": " + previous + " -> " + alpha);
            previous = alpha;
        }
    }

    @Test
    @DisplayName("the bar is brightest at the bracket, not at the crosshair")
    void brightEndIsTheBracket() {
        // The whole point of the gradient. An earlier version with a soft leading edge dropped
        // the last columns to about fourteen, which is the exact inverse of this and made a
        // ready weapon look like the one that had just been swung.
        int length = 40;
        int atBracket = ConeGauge.barAlpha(length - 1, length, length);
        int atCrosshair = ConeGauge.barAlpha(0, length, length);
        assertTrue(atBracket > atCrosshair,
                "the bracket end must be brighter, was " + atBracket + " and " + atCrosshair);
        assertEquals(ConeGauge.ALPHA_NEAR_BRACKET, atBracket, 1,
                "the bracket end must reach full brightness");
    }

    @Test
    @DisplayName("an unready weapon draws nothing next to the crosshair")
    void unreadyDrawsNothing() {
        // The bug a socket always risked: with nothing charged, a full-brightness pixel next to
        // the crosshair reads as ready. With no socket there is nothing to be bright.
        assertEquals(0, ConeGauge.barAlpha(0, 30, 0), "an empty bar must draw no column at all");
        assertEquals(0, ConeGauge.barAlpha(15, 30, 0));
    }

    @Test
    @DisplayName("the bar only ever grows: drawn columns keep their brightness and gain one more")
    void fillOnlyGrows() {
        // The bar has to get better, not worse, as the weapon gets ready. "Better" here is
        // strictly one more column: no column that was already lit may change brightness, or the
        // player watches the line they are tracking shift under them.
        int length = 30;
        for (int filled = 1; filled <= length; filled++) {
            for (int i = 0; i < filled - 1; i++) {
                assertEquals(ConeGauge.barAlpha(i, length, filled - 1),
                        ConeGauge.barAlpha(i, length, filled),
                        "lit column " + i + " changed brightness when the fill grew to " + filled);
            }
            // The newly drawn one, and nothing past it.
            assertTrue(ConeGauge.barAlpha(filled - 1, length, filled) > 0,
                    "the new column at " + (filled - 1) + " must actually be drawn");
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
