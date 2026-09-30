package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the strike is scaled by the charge, which is the fix for a tap looking like a charge.
 *
 * <p>The report was that an ordinary attack looked "like a charge done badly". The cause was not
 * the wind-up, which was correctly tiny for a tap, but the strike: it was the same amplitude for
 * every release, so a two tick click and a full charge played the same thirty degree swing and
 * differed only in how long it lasted. A shorter version of the wrong motion is still the wrong
 * motion.</p>
 *
 * <p>There is no floor and that is the second half of the fix. A tap used to arrive at the
 * viewmodel as a fifth of a charged animation, which the playtest described twice and in the
 * same breath: it "looks like a charge done badly" and it "should look like the vanilla swing
 * animation". A tap is not a small charge. It is the vanilla arm swing, and the input code plays
 * that instead of a viewmodel strike.</p>
 */
class ChargeScaledStrikeTest {

    private static final float EXACT = 1.0E-6F;

    /** Drives a full charge, releases at the given ratio, and returns the peak strike. */
    private static float peakStrikeFor(float released) {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / 25.0F));
        }
        motion.onStrike(released);
        float peak = 0.0F;
        for (int tick = 0; tick <= ViewModelMotion.STRIKE_TICKS; tick++) {
            motion.tick(false, 0.0F);
            for (int sub = 0; sub < 4; sub++) {
                peak = Math.max(peak, motion.snapshot(sub / 4.0F, 0.0F, false).strike());
            }
        }
        return peak;
    }

    @Test
    @DisplayName("a tap gets no strike at all: it is the vanilla arm swing and nothing else")
    void tapIsNothing() {
        // There was a floor here once, so a click arrived at the viewmodel as a fifth of a
        // charged animation, and the playtest said both of the things that means: it looked
        // "like a charge done badly" and it "should look like the vanilla swing animation".
        // Those are the same complaint, and no amount of tuning answers it. A tap is not a small
        // charge, it is not a charge at all.
        assertEquals(0.0F, peakStrikeFor(0.0F), 0.0F,
                "a tap must contribute nothing, or it is a small charge wearing a disguise");
    }

    @Test
    @DisplayName("the strike scales with the charge, roughly linearly")
    void scalesWithCharge() {
        assertEquals(0.5F, peakStrikeFor(0.5F), 0.02F);
        assertEquals(0.75F, peakStrikeFor(0.75F), 0.02F);
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
            motion.tick(true, 1.0F);
        }
        motion.onStrike(1.0F);
        motion.tick(false, 0.0F);
        // Tap straight away, without waiting for the follow-through to finish.
        motion.tick(true, 0.0F);
        assertEquals(0.0F, motion.released(), EXACT,
                "a new press must zero the previous swing's charge");
    }

    @Test
    @DisplayName("a cancelled swing leaves no charge behind")
    void cancelClearsTheCharge() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onStrike(1.0F);
        motion.tick(false, 0.0F);
        motion.onCancel();
        motion.tick(false, 0.0F);
        assertEquals(0.0F, motion.released(), 0.0F);
    }

    @Test
    @DisplayName("the recorded charge is clamped, so a bad value cannot blow up the motion")
    void clampsTheCharge() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onStrike(5.0F);
        assertEquals(1.0F, motion.released(), EXACT);
        motion.onStrike(-3.0F);
        assertEquals(0.0F, motion.released(), EXACT);
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
            motion.tick(true, 1.0F);
        }
        motion.onStrike(1.0F);
        motion.tick(false, 0.0F);
        assertTrue(motion.currentCharge() > 0.0F, "the charge should still be live mid-strike");

        for (int i = 0; i <= ViewModelMotion.STRIKE_TICKS; i++) {
            motion.tick(false, 0.0F);
        }
        assertEquals(0.0F, motion.currentCharge(), 0.0F,
                "once the strike is over the charge must be zero, or the FOV never reopens");
        assertEquals(0.0F, motion.released(), 0.0F);
    }

    @Test
    @DisplayName("a new press drops the previous swing's charge before the new one starts")
    void pressClearsThePreviousCharge() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onStrike(1.0F);
        motion.tick(false, 0.0F);
        motion.tick(true, 0.0F);
        assertEquals(0.0F, motion.currentCharge(), EXACT);
    }
}
