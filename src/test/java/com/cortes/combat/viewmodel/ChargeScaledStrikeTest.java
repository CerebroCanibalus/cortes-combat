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
 * <p>The floor is the other half. A tap with a literal zero strike pops the item from wherever it
 * is to rest in one frame, which is a visible jump on every ordinary click, so an uncharged swing
 * gets a small real follow-through.</p>
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
    @DisplayName("a tap gets a small strike, not a full one")
    void tapIsSmall() {
        float tap = peakStrikeFor(0.0F);
        float charged = peakStrikeFor(1.0F);
        assertTrue(tap < charged * 0.35F,
                "a tap peaked at " + tap + " against a full charge's " + charged);
    }

    @Test
    @DisplayName("a tap still moves, because a strike of exactly zero pops the item to rest")
    void tapIsNotZero() {
        assertTrue(peakStrikeFor(0.0F) >= ViewModelMotion.TAP_FLOOR - EXACT,
                "a tap must still have a follow-through, peaked at " + peakStrikeFor(0.0F));
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
        assertEquals(ViewModelMotion.TAP_FLOOR, motion.released(), EXACT);
    }
}
