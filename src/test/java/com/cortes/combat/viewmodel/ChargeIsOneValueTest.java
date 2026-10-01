package com.cortes.combat.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Properties the charge has to have for the wind-up, the animation and the field of view to be
 * one thing instead of three that happen to be near each other.
 *
 * <p>Each of these broke silently at some point: the tests were green while the fist was visibly
 * stuttering, while the world zoomed in and out on every tap, and while a two-tick click drew a
 * sliver of charge bar. None of the assertions below is a number somebody typed.</p>
 */
class ChargeIsOneValueTest {

    private static final int TICKS = ViewModelMotion.STRIKE_TICKS;

    /** Charges for a while, releases, and returns the motion. */
    private static ViewModelMotion releasedAt(float charge) {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, 1.0F);
        }
        motion.onStrike(charge, TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F);
        return motion;
    }

    @Test
    @DisplayName("a tap reports no charge at all, even though the swing still has a floor")
    void aTapIsNotACharge() {
        // The bug in one assertion. `released` is how far the item travels and it has a floor so
        // an ordinary click still swings. `classified` is what happened. They were one field, so
        // the floor travelled into the field of view and every tap zoomed the world in and back
        // out, which is what the playtest reported in almost those words.
        ViewModelMotion tap = releasedAt(0.0F);

        assertEquals(ViewModelMotion.TAP_FLOOR, tap.released(), 1.0E-6F,
                "a tap still swings, so the amplitude keeps its floor");
        assertEquals(0.0F, tap.currentCharge(), 0.0F,
                "but a tap has no charge in it, and anything reading the charge has to see zero");
        assertEquals(0.0F, tap.classifiedCharge(), 0.0F);
    }

    @Test
    @DisplayName("the reported charge is the classification, at every charge, un-floored")
    void theChargeIsTheClassificationAndNotTheAmplitude() {
        for (float charge : new float[] {0.0F, 0.1F, 0.25F, 0.5F, 1.0F}) {
            ViewModelMotion motion = releasedAt(charge);

            assertEquals(charge, motion.currentCharge(), 1.0E-6F,
                    "charge " + charge + " must be reported as itself, not floored");
            assertEquals(ViewModelMotion.TAP_FLOOR + (1.0F - ViewModelMotion.TAP_FLOOR) * charge,
                    motion.released(), 1.0E-6F,
                    "and the amplitude is a separate number about a separate question");
        }
    }

    @Test
    @DisplayName("a hold below the threshold moves the wind-up and not the charge")
    void theWindupIsRawAndTheChargeIsNot() {
        // The same hold at two resolutions, and they disagree on purpose.
        //
        // The wind-up is raw so the weapon starts moving on frame one: the playtest said the
        // cocking "starts very late, only in the last part of the charge", and a wind-up that
        // sits at zero for four ticks does exactly that.
        //
        // The charge is thresholded so the camera stays still during a tap: a two tick click is
        // one twenty-fourth of a charge and the camera used to close on it. That is what "any
        // attack starts the charge" turned out to be, on both sides. One number cannot answer
        // both questions, and reading the raw one where the thresholded one belongs is exactly
        // the mistake worth pinning down.
        ViewModelMotion motion = new ViewModelMotion();
        motion.tick(true, 0.08F, 0.0F, false, 1.0F);

        assertTrue(motion.snapshot(0.0F, 0.0F).windup() > 0.0F,
                "the wind-up has to move on a hold this short, or the press gets no feedback");
        assertEquals(0.0F, motion.currentCharge(), 0.0F,
                "and that same hold must not register as a charge, or the camera closes on every "
                        + "ordinary click");
    }

    @Test
    @DisplayName("the charge climbs only once the hold passes the threshold")
    void theChargeRampsAfterTheThresholdAndNotBefore() {
        ViewModelMotion below = new ViewModelMotion();
        below.tick(true, 0.20F, 0.0F, false, 1.0F);
        assertEquals(0.0F, below.currentCharge(), 0.0F,
                "below the threshold there is no charge at all");

        ViewModelMotion above = new ViewModelMotion();
        above.tick(true, 0.40F, 0.5F, false, 1.0F);
        assertTrue(above.currentCharge() > 0.0F, "above it, there is");
    }

    @Test
    @DisplayName("the draw-back moves every frame, not once a tick")
    void theWindupIsNotATwentyHertzStaircase() {
        // The charge only advances on the tick. Reading it straight made the item's draw-back
        // climb in twenty visible steps a second, which is what the fist was reported as doing:
        // "does not animate properly until the end of the charge and shakes strangely in between".
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 10; i++) {
            motion.tick(true, (i + 1) / 24.0F, (i + 1) / 24.0F, false, 1.0F);
        }

        Set<Float> withinOneTick = new HashSet<>();
        for (int sub = 0; sub < 4; sub++) {
            withinOneTick.add(motion.snapshot(sub / 4.0F, 0.0F).windup());
        }

        assertEquals(4, withinOneTick.size(),
                "one tick of a rising charge has to give four different frames. A staircase gives "
                        + withinOneTick.size() + ", and a staircase is read as a stutter rather "
                        + "than as motion.");
    }

    @Test
    @DisplayName("the draw-back never jumps up on the frame the swing starts")
    void theHandOffNeverJumpsUp() {
        // Continuity, without a tolerance. A jump *up* at the hand-off is the bug: the swing used
        // to unwind from the floored amplitude, so a two tick click went from nothing to most of
        // the draw-back in one frame. A hair of downward discontinuity is invisible, so asserting
        // the direction is enough and no number is needed.
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, 1.0F);
        }
        float endOfHold = motion.snapshot(0.99F, 0.0F).windup();

        motion.onStrike(1.0F, TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F);
        float startOfStrike = motion.snapshot(0.0F, 0.0F).windup();

        assertTrue(startOfStrike <= endOfHold + 1.0E-5F,
                "the strike has to unwind from where the hold got to, so it went up from "
                        + endOfHold + " to " + startOfStrike);
    }

    @Test
    @DisplayName("a tap has no draw-back to unwind, so it does not grow one on release")
    void aTapDoesNotGrowADrawBackItNeverHad() {
        ViewModelMotion tap = releasedAt(0.0F);
        for (int sub = 0; sub < 4; sub++) {
            assertEquals(0.0F, tap.snapshot(sub / 4.0F, 0.0F).windup(), 0.0F,
                    "the wind-up scales by the classified charge, so a tap has none of it");
        }
    }

    @Test
    @DisplayName("the charge is gone when the swing is")
    void theChargeIsFinishedWhenTheSwingIs() {
        ViewModelMotion motion = releasedAt(1.0F);
        for (int tick = 0; tick <= motion.strikeTicks(); tick++) {
            assertFalse(motion.currentCharge() < 0.0F, "a charge is never negative");
            motion.tick(false, 0.0F, 0.0F, false, 1.0F);
        }
        assertEquals(0.0F, motion.currentCharge(), 0.0F,
                "once the follow-through ends the charge is over with it, or the field of view "
                        + "never reopens");
    }
}
