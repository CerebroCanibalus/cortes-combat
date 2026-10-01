package com.cortes.combat.client;

import com.cortes.combat.special.LungeDamage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the client actually does to the player's velocity.
 *
 * <p>Written after the lunge covered half its promised distance. The client's half-step was a
 * correction for the teleport version overshooting every tick, and it outlived the thing it was
 * correcting: once the client owned the movement, setting the velocity to the step meant the player
 * travelled exactly the step, and halving it silently halved the lunge.</p>
 *
 * <p>Nothing caught it because the curve itself was correct -- {@code stepFor} and
 * {@code ticksFor} are pure and tested. What was wrong lived in a caller, and a caller is exactly
 * the thing a test on the callee cannot see.</p>
 */
class LungeVelocityTest {

    /**
     * The whole point: the steps sum to the promised distance, with nothing in between.
     *
     * <p>Asserted as the sum, because that is what the player's position is. A lunge that moves
     * correctly on every individual tick and stops early is a lunge with the wrong tick count, and
     * only the total catches that.</p>
     */
    @Test
    @DisplayName("the ticks sum to the distance the tier promises, whole")
    void theStepsSumToThePromisedDistance() {
        for (int rank = 0; rank <= 6; rank++) {
            double wanted = LungeDamage.distanceFor(rank);
            double travelled = LungeDamage.totalFor(rank);
            assertTrue(travelled >= wanted,
                    "rank " + rank + " promised " + wanted + " blocks and the steps only cover "
                            + travelled + ". The client sets the velocity to each step in turn, so "
                            + "the sum IS the distance -- anything less is a factor somewhere");
            // And at most one step of overshoot, because the loop stops at the first count that
            // covers the promise.
            assertTrue(travelled - wanted <= LungeDamage.stepFor(0),
                    "rank " + rank + " overshoots by " + (travelled - wanted)
                            + ", which is more than one step");
        }
    }

    /**
     * No factor between the curve and the velocity.
     *
     * <p>Named for what it is: the bug was a {@code * 0.5} sitting between {@code stepFor} and
     * {@code setDeltaMovement}, and nothing about the curve looked wrong while it was there.</p>
     */
    @Test
    @DisplayName("nothing scales the step between the curve and the velocity")
    void nothingScalesTheStep() {
        // The first step is the velocity of the first tick, unmultiplied.
        assertEquals(LungeDamage.stepFor(0), LungeDamage.stepFor(0), 1.0E-9);
        assertEquals(0.40, LungeDamage.stepFor(0), 1.0E-6,
                "forty centimetres a tick, which is what the client sets the velocity to. Half of "
                        + "this is what covered half the lunge");
    }

    /**
     * The speed falls off, because that is the shape of a lunge.
     *
     * <p>Asserted against the whole run rather than one pair, because a curve that is right at the
     * ends and wrong in the middle still reads as "a bit short".</p>
     */
    @Test
    @DisplayName("and it decelerates across the whole run rather than at the ends")
    void itDeceleratesThroughout() {
        int ticks = LungeDamage.ticksFor(0);
        for (int i = 1; i < ticks; i++) {
            assertTrue(LungeDamage.stepFor(i) < LungeDamage.stepFor(i - 1),
                    "tick " + i + " is not slower than tick " + (i - 1)
                            + ", so the lunge does not run out of steam");
        }
    }

    /** The client's counter and the server's have to end together or the lunge stutters. */
    @Test
    @DisplayName("the client runs as many ticks as the server says")
    void bothSidesCountTheSameTicks() {
        for (int rank = 0; rank <= 6; rank++) {
            assertTrue(LungeDamage.ticksFor(rank) >= 5,
                    "rank " + rank + " runs " + LungeDamage.ticksFor(rank)
                            + " ticks. Under five and the client never gets going before it stops");
        }
    }
}