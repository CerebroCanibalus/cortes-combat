package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The normalisation invariant, which is the one the whole library leans on.
 *
 * <p>If {@code f(1) != 1} a track overshoots its own promise, and if {@code f(0) != 0} a weapon
 * never comes to rest and the item drifts away from the vanilla pose over a long session. Both
 * are invisible frame by frame and obvious in a fight, so they are checked here rather than
 * discovered there.</p>
 */
class CurveTest {

    private static final float EXACT = 1.0E-6F;

    @ParameterizedTest
    @EnumSource(Curve.class)
    @DisplayName("every curve is zero at zero, so a track contributes nothing at rest")
    void zeroAtZero(Curve curve) {
        assertEquals(0.0F, curve.apply(0.0F), EXACT, curve + " should be 0 at t=0");
    }

    @ParameterizedTest
    @EnumSource(Curve.class)
    @DisplayName("every curve is one at one, so a track delivers what it promises")
    void oneAtOne(Curve curve) {
        assertEquals(1.0F, curve.apply(1.0F), EXACT, curve + " should be 1 at t=1");
    }

    @ParameterizedTest
    @EnumSource(Curve.class)
    @DisplayName("every curve clamps outside 0..1 instead of extrapolating")
    void clampsOutOfRange(Curve curve) {
        assertEquals(0.0F, curve.apply(-5.0F), EXACT, curve + " should clamp below to 0");
        assertEquals(0.0F, curve.apply(-0.0001F), EXACT, curve + " should clamp just below 0");
        assertEquals(1.0F, curve.apply(7.0F), EXACT, curve + " should clamp above to 1");
    }

    @Test
    @DisplayName("a curve that overshoots is still coming back down at t=0.99, not still rising")
    void overshootingCurvesComeBackDown() {
        // Written down because getting it wrong twice is easy: it looks like a monotonicity
        // assertion, and it cannot be one. An overshoot is supposed to peak in the middle of the
        // motion and settle, so the value near the end is below the value in the middle. A test
        // that demands the opposite is asserting that the follow-through has no follow-through.
        for (Curve curve : new Curve[]{Curve.BACK, Curve.BOUNCE, Curve.ELASTIC}) {
            float peak = 0.0F;
            for (int i = 0; i <= 200; i++) {
                peak = Math.max(peak, curve.apply(i / 200.0F));
            }
            assertTrue(curve.apply(0.99F) < peak,
                    curve + " should be settling back at t=0.99, but 0.99 gave "
                            + curve.apply(0.99F) + " against a peak of " + peak);
        }
    }

    @Test
    @DisplayName("SMOOTH is the slow-settle curve: slower than linear at both ends")
    void smoothSettlesAtBothEnds() {
        assertTrue(Curve.SMOOTH.apply(0.25F) < 0.25F,
                "SMOOTH should start slower than linear");
        assertTrue(Curve.SMOOTH.apply(0.75F) > 0.75F,
                "SMOOTH should end faster than linear, so it settles at the top");
    }

    @Test
    @DisplayName("EXPO is the strike curve: nearly all of the travel in the first fifth")
    void expoFrontLoads() {
        assertTrue(Curve.EXPO.apply(0.2F) > 0.5F,
                "EXPO should have covered most of the distance by t=0.2, was " + Curve.EXPO.apply(0.2F));
        assertTrue(Curve.EXPO.apply(0.1F) > Curve.SMOOTH.apply(0.1F),
                "EXPO should lead SMOOTH early, that is the whole difference between the two");
    }

    @Test
    @DisplayName("BACK overshoots past its target and comes back, which is the follow-through")
    void backOvershoots() {
        float peak = 0.0F;
        float peakAt = 0.0F;
        for (int i = 0; i <= 200; i++) {
            float t = i / 200.0F;
            float value = Curve.BACK.apply(t);
            if (value > peak) {
                peak = value;
                peakAt = t;
            }
        }
        assertTrue(peak > 1.05F, "BACK should overshoot by a visible amount, peaked at " + peak);
        assertTrue(peakAt > 0.4F && peakAt < 0.8F,
                "BACK should peak after the midpoint, peaked at t=" + peakAt);
    }

    @Test
    @DisplayName("BOUNCE dips below its target on the way, which is what a bounce is")
    void bounceDips() {
        boolean dipped = false;
        for (int i = 1; i < 100; i++) {
            if (Curve.BOUNCE.apply(i / 100.0F) < 0.99F) {
                dipped = true;
                break;
            }
        }
        assertTrue(dipped, "BOUNCE should be below 1 somewhere before the end");
    }

    @Test
    @DisplayName("ELASTIC is a decaying ring, so it goes above 1 and comes back")
    void elasticRings() {
        float peak = 0.0F;
        for (int i = 0; i <= 200; i++) {
            peak = Math.max(peak, Curve.ELASTIC.apply(i / 200.0F));
        }
        assertTrue(peak > 1.0F, "ELASTIC should overshoot, peaked at " + peak);
    }
}
