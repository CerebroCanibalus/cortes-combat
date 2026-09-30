package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tremble's contract: bounded, reproducible, three independent axes, and smooth.
 *
 * <p>Smooth is the one that costs something to get and is the reason the noise interpolates.
 * A tremble sampled one value per bucket is cheaper and looks like a glitch: a held weapon that
 * jumps between two positions sixty times a second is twitching, not shaking. Interpolation buys
 * the difference for one extra hash and a lerp.</p>
 */
class ViewModelNoiseTest {

    private static final float HZ = 14.0F;

    /**
     * The rate the motion is actually drawn at, which is the frame rate and not the tick rate.
     *
     * <p>Getting this wrong is what makes a tremble look like a buzz: sampled twenty times a
     * second at fourteen hertz the signal moves almost a whole bucket per sample, which leaves the
     * interpolation nothing to smooth.</p>
     */
    private static final float FRAME_STEP = 1.0F / 60.0F;

    @Test
    @DisplayName("stays inside minus one to one on every axis and every bucket")
    void bounded() {
        for (int seed = 1; seed <= 40; seed++) {
            for (int frame = 0; frame < 120; frame++) {
                float time = frame / 20.0F;
                for (int axis = 0; axis < 6; axis++) {
                    float value = ViewModelNoise.axis(seed, time, HZ, axis);
                    assertTrue(value >= -1.0F && value <= 1.0F,
                            "axis " + axis + " left the range at seed " + seed + ": " + value);
                }
            }
        }
    }

    @Test
    @DisplayName("the same seed, time and axis always give the same number")
    void deterministic() {
        for (int i = 0; i < 50; i++) {
            float time = i * 0.0173F;
            for (int axis = 0; axis < 6; axis++) {
                assertEquals(ViewModelNoise.axis(11, time, HZ, axis),
                        ViewModelNoise.axis(11, time, HZ, axis), 0.0,
                        "the tremble must be reproducible or the same swing looks different");
            }
        }
    }

    @Test
    @DisplayName("consecutive frames are close together, which is what makes it a tremble")
    void smoothNotStepped() {
        // Stepped noise jumps by the whole difference between two buckets the moment it crosses
        // one. Interpolated noise can only move as far as the phase advanced, times the full
        // range, so that product is the bound this asserts against. Writing a number there
        // instead would measure the frequency rather than the interpolation, and would have to be
        // rewritten the first time somebody changes the rate.
        float bound = 2.0F * HZ * FRAME_STEP + 1.0E-3F;
        float worst = 0.0F;
        float total = 0.0F;
        float previous = ViewModelNoise.axis(5, 0.0F, HZ, 0);
        for (int frame = 1; frame <= 600; frame++) {
            float value = ViewModelNoise.axis(5, frame * FRAME_STEP, HZ, 0);
            float step = Math.abs(value - previous);
            worst = Math.max(worst, step);
            total += step;
            previous = value;
        }
        assertTrue(worst <= bound, "the tremble jumped " + worst + " in a frame, past what "
                + "interpolation can do (" + bound + ")");

        // And the average has to sit well under what a stepped signal would give, which is the
        // difference between a shake and a buzz and the whole reason for interpolating.
        float mean = total / 600.0F;
        assertTrue(mean < 0.15F,
                "the tremble averaged " + mean + " per frame, which is close to stepped");
    }

    @Test
    @DisplayName("the three axes are not the same signal")
    void axesAreIndependent() {
        int identical = 0;
        for (int frame = 0; frame < 60; frame++) {
            float time = frame / 20.0F;
            float x = ViewModelNoise.axis(3, time, HZ, 0);
            float y = ViewModelNoise.axis(3, time, HZ, 1);
            float z = ViewModelNoise.axis(3, time, HZ, 2);
            if (x == y && y == z) {
                identical++;
            }
        }
        assertEquals(0, identical, "the axes must not share a value or the item moves on a diagonal");
    }

    @Test
    @DisplayName("different seeds give different trebles, or a repeat charge looks like a loop")
    void seedsDiffer() {
        int identical = 0;
        for (int frame = 0; frame < 60; frame++) {
            float time = frame / 20.0F;
            if (ViewModelNoise.axis(1, time, HZ, 0) == ViewModelNoise.axis(2, time, HZ, 0)) {
                identical++;
            }
        }
        assertTrue(identical < 5,
                "two seeds produced the same tremble on " + identical + " of 60 frames");
    }

    @Test
    @DisplayName("a frequency of zero is silence, not a division by zero")
    void zeroFrequencyIsSilence() {
        assertEquals(0.0F, ViewModelNoise.axis(1, 1.0F, 0.0F, 0), 0.0);
        assertEquals(0.0F, ViewModelNoise.axis(1, 1.0F, -3.0F, 0), 0.0);
    }

    @Test
    @DisplayName("it actually moves, rather than sitting at a constant")
    void notConstant() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int frame = 0; frame < 60; frame++) {
            float value = ViewModelNoise.axis(9, frame / 20.0F, HZ, 0);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        assertFalse(max - min < 0.5F,
                "over a second of shaking the signal only spanned " + (max - min));
    }
}
