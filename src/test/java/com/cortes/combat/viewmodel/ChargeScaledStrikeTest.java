package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the strike is scaled by the charge, and that a tap still swings.
 *
 * <p>This class has been wrong twice, in opposite directions, and both times because the test
 * encoded a decision instead of a property.</p>
 *
 * <p>First there was no scaling at all: every release played the same swing, so a two tick click
 * and a full charge differed only in duration. Then the floor was set to zero, on the reading
 * that a tap is "not a small charge" and therefore contributes nothing. The playtest overruled
 * that: four attacks out of five are taps, and a weapon that does not move on them is a prop.</p>
 *
 * <p>What is actually being protected here is not a number. It is that the motion grows
 * monotonically with the charge, never exceeds it, and that both ends of the range are far
 * enough apart for a player to tell them apart by looking. Those hold whatever the floor is, and
 * they are what a retune must not break.</p>
 */
class ChargeScaledStrikeTest {

    private static final float EXACT = 1.0E-6F;

    /** Drives a full charge, releases at the given ratio, and returns the peak strike. */
    private static float peakStrikeFor(float released) {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / 25.0F), Math.min(1.0F, (i + 1) / 25.0F), false, 1.0F, false, false);
        }
        motion.onStrike(released, ViewModelMotion.STRIKE_TICKS);
        float peak = 0.0F;
        for (int tick = 0; tick <= ViewModelMotion.STRIKE_TICKS; tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
            for (int sub = 0; sub < 4; sub++) {
                peak = Math.max(peak, motion.snapshot(sub / 4.0F, 0.0F).strike());
            }
        }
        return peak;
    }

    @Test
    @DisplayName("a tap swings almost as big as a charge, and a charge is still the heavier one")
    void tapSwingsAndChargeIsStillHeavier() {
        float tap = peakStrikeFor(0.0F);
        float charged = peakStrikeFor(1.0F);

        assertTrue(tap > 0.6F,
                "an ordinary click has to be a real swing, it is " + tap
                        + " of a full one. This floor was zero and then a third, and the playtest "
                        + "rejected both: a weapon that does not move on four attacks out of five "
                        + "is a prop, and one that twitches on them is worse.");
        assertTrue(charged > tap,
                "and a full charge has to be the heavier of the two, " + charged + " against " + tap);
    }

    @Test
    @DisplayName("the strike grows with the charge, and never past it")
    void scalesWithCharge() {
        // Monotone and bounded rather than linear, because the floor makes the map deliberately
        // non-linear: TAP_FLOOR + (1 - TAP_FLOOR) * charge. A test that demands linearity here
        // would be a test that forbids the very thing the floor is for.
        float previous = -1.0F;
        for (float charge = 0.0F; charge <= 1.0001F; charge += 0.1F) {
            float peak = peakStrikeFor(charge);
            assertTrue(peak >= previous,
                    "the strike must never shrink as the charge grows, " + peak
                            + " came after " + previous);
            assertTrue(peak <= 1.0F + EXACT,
                    "and must never exceed a full charge, was " + peak);
            previous = peak;
        }
    }

    @Test
    @DisplayName("a full charge still gets the whole strike")
    void fullChargeIsUnchanged() {
        assertEquals(1.0F, peakStrikeFor(1.0F), EXACT);
    }

    @Test
    @DisplayName("a second press does not inherit the previous swing's charge")
    void pressResetsTheCharge() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        // Tap straight away, without waiting for the follow-through to finish.
        motion.tick(true, 0.0F, 0.0F, false, 1.0F, false, false);
        assertEquals(0.0F, motion.released(), EXACT,
                "a new press must zero the previous swing's charge");
    }

    @Test
    @DisplayName("a cancelled swing leaves no charge behind")
    void cancelClearsTheCharge() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        motion.onCancel();
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        assertEquals(0.0F, motion.released(), 0.0F);
    }

    @Test
    @DisplayName("the recorded charge is clamped, so a bad value cannot blow up the motion")
    void clampsTheCharge() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onStrike(5.0F, ViewModelMotion.STRIKE_TICKS);
        assertEquals(1.0F, motion.released(), EXACT);
        // A negative charge is still a tap, and a tap still swings. The floor is a floor.
        motion.onStrike(-3.0F, ViewModelMotion.STRIKE_TICKS);
        assertEquals(ViewModelMotion.TAP_FLOOR, motion.released(), EXACT,
                "clamped to the tap amplitude, not below it");
    }

    @Test
    @DisplayName("the charge dies with the follow-through, or the FOV stays closed forever")
    void chargeEndsWithTheStrike() {
        // The bug this catches: `released` was set once on the release and never cleared, and
        // everything that read a charge after the fact went on reading it. The field of view
        // stayed narrowed for the rest of the session, and only grabbing a different item cleared
        // it. A follow-through that has ended is the end of everything the charge was driving.
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        assertTrue(motion.currentCharge() > 0.0F, "the charge should still be live mid-strike");

        for (int i = 0; i <= ViewModelMotion.STRIKE_TICKS; i++) {
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        }
        assertEquals(0.0F, motion.currentCharge(), 0.0F,
                "once the strike is over the charge must be zero, or the FOV never reopens");
        assertEquals(0.0F, motion.released(), 0.0F);
    }

    @Test
    @DisplayName("a new press drops the previous swing's charge before the new one starts")
    void pressClearsThePreviousCharge() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        motion.tick(true, 0.0F, 0.0F, false, 1.0F, false, false);
        assertEquals(0.0F, motion.currentCharge(), EXACT);
    }
}
