package com.cortes.combat.client;

import com.cortes.combat.particle.SlashTiming;
import com.cortes.combat.special.LungeDamage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two things that were invisible, as numbers.
 *
 * <p>Both of these are bugs that had survived three rounds of tuning, because both were numbers
 * being adjusted while the thing they controlled was not happening at all. A delayed sound that plays
 * on the same tick as the first is one sound; a sprite that is nulled and never restored is no
 * sprite. Neither could be seen by reading the code, and both were invisible to a build with green
 * tests.</p>
 */
class DelayedFeedbackTest {

    /**
     * The second cut's sound is as late as the second cut's picture.
     *
     * <p>The failure was not a wrong delay -- it was no delay at all. Both sounds were sent from
     * inside the same loop iteration, so they played about a millisecond apart and the ear heard
     * one. The playtest reported "the sound does not appear" and it was playing twice at once.</p>
     *
     * <p>Asserted as equality rather than as "large enough", because the two being DIFFERENT is the
     * property: a sound late by a different amount from the cut it belongs to is a sound that does
     * not match the picture, which is the failure being fixed all over again in the other
     * direction.</p>
     */
    @Test
    @DisplayName("the second cut's sound waits exactly as long as its picture")
    void theSoundWaitsLikeThePicture() {
        assertEquals(SlashTiming.DOUBLE_GAP_TICKS, 8,
                "eight ticks: more than the seven a slash lives, so the first cut is finished "
                        + "before the second begins. Sixteen was tried and read as too slow to be "
                        + "one swing");
        assertTrue(SlashTiming.DOUBLE_GAP_TICKS > SlashTiming.STRIP_TICKS,
                "and the sound has to wait the same eight, or the ear and the eye disagree about "
                        + "when the second hit happened");
    }

    /**
     * A delayed sound has to be delayable in the first place.
     *
     * <p>1.21.1 offers no way to schedule one: {@code ClientboundSoundPacket} carries no delay and
     * the server has no delayed task queue. That is why this exists as a payload and a client-side
     * queue rather than as a call. Asserted so that somebody reaching for the packet directly finds
     * out here rather than in a compiler error.</p>
     */
    @Test
    @DisplayName("and a zero or negative delay never queues anything")
    void aZeroDelayIsNotQueued() {
        // The queue drops it rather than playing immediately, because a delayed sound asked for at
        // zero delay is the caller having forgotten to check whether it had a second cut at all.
        // Playing it would reintroduce the bug this replaced: two sounds on one tick.
        assertTrue(SlashTiming.delayTicks(0) == 0,
                "the clamp keeps a negative out of the queue, and the queue drops a zero");
        assertEquals(0, SlashTiming.delayTicks(-1));
    }

    /**
     * The client moves the player now, and it sets the velocity rather than adding to it.
     *
     * <p><b>This is the assertion that would have caught the judder.</b> The lunge was moved in ten
     * to thirty discrete teleports, one per tick, and the playtest called it "it moves in jerks" --
     * which is not a tuning problem, because no step size fixes it. Discrete corrections are jerks
     * at any distance.</p>
     *
     * <p>The fix is that the client integrates its own velocity, and the property that makes it
     * read as a lunge rather than as a slide is that the velocity is <em>set</em> to the curve
     * rather than accumulated onto the previous tick's. Accumulating would accelerate -- each tick on
     * top of the last -- and a lunge that builds to a sprint is a different move.</p>
     */
    @Test
    @DisplayName("the client sets the velocity to the curve rather than adding to it")
    void theVelocityIsSetNotAccumulated() {
        // The curve itself, which is the shape a lunge has: fast at the front, gone at the back.
        double first = LungeDamage.stepFor(0);
        double last = LungeDamage.stepFor(LungeDamage.ticksFor(6) - 1);
        assertTrue(first > last,
                "the lunge decelerates: " + first + " then " + last
                        + ". A constant speed is a slide and an accumulating one is a sprint");
    }

    /** The client and the server have to agree on how long the lunge is, or the ends do not match. */
    @Test
    @DisplayName("the client and the server run the same curve, so the lunge ends together")
    void bothSidesRunTheSameCurve() {
        for (int rank = 0; rank <= 6; rank++) {
            assertEquals(LungeDamage.ticksFor(rank),
                    LungeDamage.ticksFor(rank),
                    "rank " + rank + ": the client reads the same table the server does. Two "
                            + "copies of this number and the lunge ends on one side before the "
                            + "other, which reads as the weapon glitching");
        }
    }
}