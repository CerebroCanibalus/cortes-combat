package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-off, which is the part of the swing that is easy to get wrong and hard to see in code.
 *
 * <p>Two implementations both look plausible and both look broken on screen. A charge that snaps
 * to zero on release teleports the item to rest and then lunges, so the fastest point of the
 * swing has a visible hitch. A charge that stays at one and adds the strike on top sends the item
 * twice as far as it should and never brings it back. These tests pin down the property that
 * rules out both.</p>
 */
class ViewModelMotionTest {

    private ViewModelMotion motion;

    @BeforeEach
    void setUp() {
        motion = new ViewModelMotion();
    }

    /** Drive the clock the way the client tick does. */
    private void tick(boolean holding, float charge) {
        motion.tick(holding, charge, charge, false, 1.0F, false, false);
    }

    @Test
    @DisplayName("holding follows the charge exactly")
    void windUpFollowsTheCharge() {
        tick(true, 0.25F);
        assertEquals(0.25F, motion.windup(), 1.0E-6F);
        tick(true, 0.5F);
        assertEquals(0.5F, motion.windup(), 1.0E-6F);
        tick(true, 1.0F);
        assertEquals(1.0F, motion.windup(), 1.0E-6F);
    }

    @Test
    @DisplayName("at rest both channels are zero")
    void restIsRest() {
        tick(false, 0.0F);
        assertEquals(0.0F, motion.windup(), 0.0F);
        assertEquals(0.0F, motion.strike(), 0.0F);
    }

    @Test
    @DisplayName("on the frame of the release the item is still wound all the way back")
    void releaseStartsFromTheWoundBackPosition() {
        tick(true, 1.0F);
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        tick(false, 0.0F);
        // The frame after the release, so the strike has not advanced yet. If this is not still
        // 1, the item is already snapping to rest on the frame the player let go, which is the
        // hitch.
        assertEquals(1.0F, motion.windup(), 1.0E-6F,
                "the wind-up must still be full on the frame after the release");
    }

    @Test
    @DisplayName("the wind-up unwinds monotonically across the strike")
    void windUpUnwindsMonotonically() {
        tick(true, 1.0F);
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        float previous = Float.POSITIVE_INFINITY;
        // One more tick than the strike is long: the release frame is itself a frame of the
        // motion, and the strike is only over once its age has run past the end.
        for (int i = 0; i <= ViewModelMotion.STRIKE_TICKS; i++) {
            tick(false, 0.0F);
            float windup = motion.windup();
            assertTrue(windup <= previous + 1.0E-6F,
                    "the wind-up went back up at tick " + i + ": " + windup + " after " + previous);
            assertTrue(windup >= 0.0F, "the wind-up went negative at tick " + i);
            previous = windup;
        }
        assertEquals(0.0F, motion.windup(), 1.0E-6F, "the wind-up should be gone by the end");
    }

    @Test
    @DisplayName("the strike is a bump: it rises, peaks, and comes back to nothing")
    void strikeIsABump() {
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        float peak = 0.0F;
        for (int i = 0; i <= ViewModelMotion.STRIKE_TICKS; i++) {
            tick(false, 0.0F);
            float value = motion.strike();
            assertTrue(value >= 0.0F && value <= 1.0F, "the strike left 0..1 at tick " + i);
            peak = Math.max(peak, value);
        }
        assertTrue(peak > 0.9F, "the strike should reach its peak, peaked at " + peak);
        assertEquals(0.0F, motion.strike(), 0.0F,
                "the strike must end at zero, or the item stays displaced forever");
    }

    @Test
    @DisplayName("the item is resting again once the strike is over")
    void everythingReturnsToRest() {
        tick(true, 1.0F);
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        for (int i = 0; i < ViewModelMotion.STRIKE_TICKS + 2; i++) {
            tick(false, 0.0F);
        }
        assertEquals(0.0F, motion.windup(), 0.0F);
        assertEquals(0.0F, motion.strike(), 0.0F);
    }

    @Test
    @DisplayName("a press during a follow-through cancels it rather than layering on top")
    void pressCancelsAnUnfinishedStrike() {
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        tick(false, 0.0F);
        tick(false, 0.0F);
        assertTrue(motion.strike() > 0.0F, "the strike should be running by now");
        tick(true, 0.0F);
        assertEquals(0.0F, motion.strike(), 0.0F,
                "a new wind-up should not sit on top of a follow-through that never finished");
    }

    @Test
    @DisplayName("consecutive strikes get different tremble seeds")
    void seedAdvancesPerStrike() {
        int first = motion.seed();
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        int second = motion.seed();
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        int third = motion.seed();
        assertFalse(first == second, "two strikes in a row should not shake identically");
        assertFalse(second == third, "the seed should keep advancing");
    }

    @Test
    @DisplayName("a strike is reproducible: the same sequence gives the same seed every time")
    void seedIsReproducible() {
        ViewModelMotion other = new ViewModelMotion();
        for (int i = 0; i < 5; i++) {
            motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
            other.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
            assertEquals(other.seed(), motion.seed(),
                    "two runs of the same swings should end on the same tremble pattern");
        }
    }

    @Test
    @DisplayName("cancelling drops the swing without pretending one happened")
    void cancelDropsEverything() {
        tick(true, 1.0F);
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        tick(false, 0.0F);
        motion.onCancel();
        tick(false, 0.0F);
        assertEquals(0.0F, motion.windup(), 0.0F);
        assertEquals(0.0F, motion.strike(), 0.0F);
    }

    @Test
    @DisplayName("a charge that was never released leaves nothing running")
    void releasingWithoutAStrikeIsQuiet() {
        tick(true, 1.0F);
        tick(false, 0.0F);
        assertEquals(0.0F, motion.windup(), 0.0F, "letting go with no strike must not hold a wind-up");
        assertEquals(0.0F, motion.strike(), 0.0F);
    }
}
