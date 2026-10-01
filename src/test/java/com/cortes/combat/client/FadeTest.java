package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage overlay's fade, and the one property the broken version did not have.
 *
 * <p>The playtest reported two symptoms as separate -- the vignette strobes, and it feels laggy --
 * and they are one bug. The fade was {@code alpha + (target - alpha) * dt * SPEED}: correct at
 * sixty frames a second, and wrong the moment a frame takes longer than a sixth of a second, because
 * the factor passes one and the alpha <b>overshoots its target</b>. It corrects on the next frame
 * and overshoots again after that, and because the oscillation only happens during a hitch, a
 * stutter causes the strobe and the strobe reads as more stutter.</p>
 *
 * <p>No speed constant fixes that, because the fault is the shape of the interpolation rather than
 * its rate. Three rounds of tuning the rate would have moved it without ever removing it.</p>
 */
class FadeTest {

    /** The bound that matters: the result stays between the two inputs, for ANY delta. */
    @Test
    @DisplayName("never passes the target, however long the frame was")
    void neverOvershoots() {
        float[][] pairs = {{0.0F, 1.0F}, {1.0F, 0.0F}, {0.3F, 0.9F}, {0.9F, 0.1F}};
        // From a sixty frame hitch all the way to a tab that was in the background for a minute.
        double[] deltas = {1.0 / 60.0, 1.0 / 30.0, 0.2, 0.5, 1.0, 5.0, 60.0};

        for (float[] pair : pairs) {
            for (double dt : deltas) {
                float result = Fade.toward(pair[0], pair[1], 6.0F, dt);
                float low = Math.min(pair[0], pair[1]) - 1.0E-4F;
                float high = Math.max(pair[0], pair[1]) + 1.0E-4F;
                assertTrue(result >= low && result <= high,
                        "from " + pair[0] + " toward " + pair[1] + " with a " + dt
                                + " second frame gave " + result + ", which is outside the "
                                + "interval. That is the strobe");
            }
        }
    }

    @Test
    @DisplayName("decays to zero without ever going negative")
    void neverGoesNegative() {
        for (double dt : new double[]{1.0 / 60.0, 0.5, 3.0, 30.0}) {
            float result = Fade.decay(0.8F, 5.0F, dt);
            assertTrue(result >= 0.0F && result <= 0.8F,
                    "0.8 decayed by " + dt + " gave " + result);
        }
        // A value that is already zero stays zero rather than going negative and being clamped
        // back, which is what a subtraction based fade does.
        assertEquals(0.0F, Fade.decay(0.0F, 5.0F, 1.0 / 60.0));
        assertEquals(0.0F, Fade.decay(-1.0F, 5.0F, 1.0 / 60.0));
    }

    /**
     * The fade takes the same wall-clock time whatever the frame rate.
     *
     * <p>This is the property a per-frame interpolation does not have and the reason the whole class
     * exists: at thirty frames a second the old version moved twice as far per frame and the vignette
     * reached its target twice as fast, so the screen darkened at a different rate on different
     * machines.</p>
     */
    @Test
    @DisplayName("takes the same real time at thirty frames as at two hundred")
    void frameRateIndependent() {
        float atTwoHundred = 0.0F;
        for (int i = 0; i < 40; i++) {
            atTwoHundred = Fade.toward(atTwoHundred, 1.0F, 6.0F, 1.0 / 200.0);
        }
        float atThirty = 0.0F;
        for (int i = 0; i < 6; i++) {
            atThirty = Fade.toward(atThirty, 1.0F, 6.0F, 1.0 / 30.0);
        }
        // Both are two tenths of a second of wall clock.
        assertEquals(atTwoHundred, atThirty, 0.01,
                "after the same real time at two different frame rates the values are " + atTwoHundred
                        + " and " + atThirty + ". A fade that runs at a different rate on different "
                        + "machines is a fade somebody will retune on their hardware");
    }

    /** A zero or nonsense delta leaves the value alone rather than snapping it. */
    @Test
    @DisplayName("a zero or broken delta changes nothing")
    void aZeroDeltaIsANoOp() {
        assertEquals(0.4F, Fade.toward(0.4F, 1.0F, 6.0F, 0.0), 1.0E-6);
        assertEquals(0.4F, Fade.toward(0.4F, 1.0F, 6.0F, -1.0), 1.0E-6);
        assertEquals(0.4F, Fade.toward(0.4F, 1.0F, 6.0F, Double.NaN), 1.0E-6);
        assertEquals(0.4F, Fade.decay(0.4F, 5.0F, 0.0), 1.0E-6);
        assertEquals(0.4F, Fade.decay(0.4F, 5.0F, Double.POSITIVE_INFINITY), 1.0E-6,
                "an infinite delta is a stall, not a fade: it goes to zero rather than to NaN");
    }

    /**
     * The threshold the layer uses to decide whether to draw at all.
     *
     * <p>Asserted because it is what stops the overlay paying for a full screen blit every frame to
     * draw a quad that is indistinguishable from nothing -- which is every frame of every fight
     * except the parts where the overlay is actually showing.</p>
     */
    @Test
    @DisplayName("and it knows when there is nothing worth drawing")
    void itKnowsWhenToStopDrawing() {
        assertTrue(Fade.visible(0.5F, 0.001F));
        assertTrue(!Fade.visible(0.0005F, 0.001F),
                "below the threshold the whole layer is skipped, which is the point of it");
        assertTrue(!Fade.visible(0.0F, 0.001F));
    }
}