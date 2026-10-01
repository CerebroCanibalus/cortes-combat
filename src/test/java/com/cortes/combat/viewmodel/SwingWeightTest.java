package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the cut leaves fast and comes back slow, and that nothing is left holding at the end.
 *
 * <p>Three rounds of playtesting described the same charged sword as "patÃ©tica", then a little
 * push, then not there at all. Two causes, and this class exists for the second one.</p>
 *
 * <p>The first was that the cut was a translation applied in the wrong frame, so a rigid item was
 * slid rather than swung. Fixed in {@code ItemInHandRendererMixin}, and about rotation versus
 * translation in {@code CutIsARotationTest}.</p>
 *
 * <p>The second was the shape. A channel named {@code THRUST} was added on the theory that a bump
 * "arrives and undoes itself in the same instant" and that holding the peak would give the motion
 * somewhere to have arrived. It did not: a channel that holds never comes back, so the item was
 * still turned seventy odd degrees when the swing ended and <b>snapped</b> to the vanilla pose in
 * a single frame. And the playtest called the result "no weight" rather than "a pop at the end",
 * because the pop arrived after the eye had stopped following the swing.</p>
 *
 * <p>What is here now is vanilla's own curve, {@code sin(sqrt(f) * PI)}, whose square root is the
 * whole point: it rises almost at once and returns over the rest of the swing. The invariant worth
 * keeping is the asymmetry, and the other one is that every channel is back at zero on the last
 * frame.</p>
 */
class SwingWeightTest {

    /** Charges to full, releases, and returns the cut at each tick of the follow-through. */
    private static float[] strikeTrace() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / 25.0F), Math.min(1.0F, (i + 1) / 25.0F), false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        float[] trace = new float[ViewModelMotion.STRIKE_TICKS + 1];
        for (int tick = 0; tick <= ViewModelMotion.STRIKE_TICKS; tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
            trace[tick] = motion.snapshot(0.0F, 0.0F).strike();
        }
        return trace;
    }

    @Test
    @DisplayName("the cut leaves far faster than it comes back")
    void theCutIsAsymmetric() {
        float[] trace = strikeTrace();
        float peak = 0.0F;
        int peakAt = -1;
        for (int i = 0; i < trace.length; i++) {
            if (trace[i] > peak) {
                peak = trace[i];
                peakAt = i;
            }
        }
        assertTrue(peak > 0.95F, "the cut should reach full extension, peaked at " + peak);
        assertTrue(peakAt <= 2,
                "the cut should be most of the way out within two ticks, peaked at tick " + peakAt
                        + ". A cut that arrives late leaves and returns at the same speed, and a "
                        + "machine leaving and returning at the same speed is a machine.");

        // The return has to take longer than the departure, or there is no weight in it at all.
        int ticksOut = peakAt;
        int ticksBack = trace.length - 1 - peakAt;
        assertTrue(ticksBack > ticksOut,
                "and the way back has to take longer than the way out: " + ticksOut + " out, "
                        + ticksBack + " back. That asymmetry is the whole thing.");
    }

    @Test
    @DisplayName("the cut is back at zero on the last frame, with nothing left holding")
    void nothingHoldsAtTheEnd() {
        // The snap. A channel that holds its peak leaves the item rotated when the swing ends, and
        // the frame after that one puts it back at the vanilla pose, which reads as a glitch
        // rather than as a recovery.
        float[] trace = strikeTrace();
        assertEquals(0.0F, trace[trace.length - 1], 0.0F,
                "the swing has to end at the vanilla pose, and the only way to guarantee that is "
                        + "for every channel to reach zero on its own");
    }

    @Test
    @DisplayName("the weight beat starts after the cut has arrived, and it is a separate beat")
    void weightFollowsTheCut() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / 25.0F), Math.min(1.0F, (i + 1) / 25.0F), false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);

        // The recovery must contribute nothing while the cut is still travelling, or the two
        // average into a single shove. That shove was what three rounds called "no weight".
        assertEquals(0.0F, motion.snapshot(0.0F, 0.0F).recover(), 0.0F);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        assertEquals(0.0F, motion.snapshot(0.0F, 0.0F).recover(), 0.0F,
                "the weight must not start while the cut is still on its way out");

        float recoveryPeak = 0.0F;
        int recoveryPeakAt = -1;
        int cutArrivedAt = -1;
        for (int tick = 0; tick <= ViewModelMotion.STRIKE_TICKS; tick++) {
            ViewModelInput frame = motion.snapshot(0.0F, 0.0F);
            if (frame.recover() > recoveryPeak) {
                recoveryPeak = frame.recover();
                recoveryPeakAt = tick;
            }
            if (cutArrivedAt < 0 && frame.strike() > 0.9F) {
                cutArrivedAt = tick;
            }
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        }
        assertTrue(recoveryPeak > 0.7F,
                "the weight should reach its peak, at " + recoveryPeak
                        + ". Sampled on ticks, so a bump over a short tail only gets to within "
                        + "one tick's rise.");
        assertTrue(recoveryPeakAt > cutArrivedAt,
                "the weight has to peak after the cut arrives, or they are one shove: "
                        + recoveryPeakAt + " against " + cutArrivedAt);
    }

    @Test
    @DisplayName("a tap is a tap: no cut, no weight, nothing")
    void tapIsSilent() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, 1.0F, false, false);
        }
        motion.onStrike(0.0F, ViewModelMotion.STRIKE_TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        ViewModelInput frame = motion.snapshot(0.0F, 0.0F);
        assertEquals(0.0F, frame.strike(), 0.0F);
        assertEquals(0.0F, frame.recover(), 0.0F);
    }
}
