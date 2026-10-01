package com.cortes.combat.special;

import com.cortes.combat.state.LungeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunge's movement, as numbers a bare JVM can hold.
 *
 * <p>Written after the lunge failed three times in ways that all looked identical from inside the
 * game. Twice the player did not move at all -- which turned out to be a fact about vanilla rather
 * than about this code, since a server-side velocity does not move a player -- and once it moved as
 * a single blink, which was this design's fault.</p>
 *
 * <p>The three designs these numbers encode are the ones that did not work, and they are named
 * because each one looked reasonable and each one is arithmetically impossible:</p>
 */
class LungeMovementTest {

    /**
     * Every tier covers the distance it promises.
     *
     * <p>The distance is solved from {@link LungeDamage#ticksFor} rather than tabulated beside it,
     * so a retune of the step or the decay cannot leave a promise the player does not keep. The
     * tolerance is one step rather than a float epsilon, because the step count is an integer and
     * the last step overshoots by design.</p>
     */
    @Test
    @DisplayName("it covers the distance the material promises")
    void itCoversThePromisedDistance() {
        for (int rank = 0; rank <= 6; rank++) {
            double wanted = LungeDamage.distanceFor(rank);
            double actual = LungeDamage.totalFor(rank);
            assertTrue(actual >= wanted && actual - wanted <= LungeDamage.stepFor(0),
                    "rank " + rank + " promised " + Math.round(wanted * 100) / 100.0
                            + " blocks and travelled " + Math.round(actual * 100) / 100.0
                            + ". The overshoot has to be at most one step");
        }
    }

    /**
     * A better weapon takes more steps, and the same ones.
     *
     * <p>This is the design, and it is the assertion that would have caught the two dead ends.</p>
     *
     * <p>Scaling the step size by tier gave a netherite first step of 1.3 blocks -- eight steps of
     * more than a metre, which is not a lunge but a sequence of teleports. Scaling the decay ran
     * into a ceiling nobody had drawn: a geometric series converges, so the total has a maximum of
     * {@code FIRST_STEP / (1 - decay)}, and at any decay that looked right that maximum was under
     * three blocks. A netherite lunge of six and a fifth was not reachable by any setting.</p>
     *
     * <p>So the step is a constant and the tier buys <em>count</em>. Half a second of lunge against
     * one and a half, same motion, which is what a better weapon should feel like.</p>
     */
    @Test
    @DisplayName("a better weapon lunges longer, never faster")
    void theTierBuysTimeNotSpeed() {
        for (int rank = 0; rank <= 6; rank++) {
            assertEquals(LungeDamage.stepFor(0), LungeDamage.stepFor(0),
                    "the first step is a constant");
        }
        // And that constant is small enough that even the longest lunge never reads as a teleport.
        assertTrue(LungeDamage.FIRST_STEP < 0.6,
                "the first step is " + LungeDamage.FIRST_STEP + " blocks. Above about 0.6 the "
                        + "client sees a teleport, and a thirty step lunge of them is worse than the "
                        + "single jump this replaced");

        int wooden = LungeDamage.ticksFor(0);
        int iron = LungeDamage.ticksFor(3);
        int netherite = LungeDamage.ticksFor(6);
        assertTrue(iron > wooden && netherite > iron,
                "more material means more steps: " + wooden + ", " + iron + ", " + netherite);
        assertTrue(netherite >= wooden * 2,
                "and the difference has to be big enough to feel. A netherite lunge of " + netherite
                        + " ticks against a wooden " + wooden + " is " + (netherite / 20.0)
                        + " seconds against " + (wooden / 20.0));
    }

    /** The steps shrink, so it decelerates rather than sliding at one speed. */
    @Test
    @DisplayName("it starts fast and runs out of steam")
    void itDecelerates() {
        double first = LungeDamage.stepFor(0);
        double middle = LungeDamage.stepFor(LungeDamage.ticksFor(6) / 2);
        double last = LungeDamage.stepFor(LungeDamage.ticksFor(6) - 1);

        assertTrue(middle < first, "the tail is slower than the start");
        assertTrue(last < middle, "and slower still at the end: " + last + " against " + middle);
        assertTrue(last > 0.0, "and the last step still moves. A zero would leave a tick where "
                + "the player is teleported nowhere while the weapon is extended");
    }

    /**
     * The direction is committed once, and read from the state rather than from the camera.
     *
     * <p>Re-reading the look direction every tick turned the lunge into a steerable flight, and
     * holding it in a static would have made it one lunge for the whole server: two players
     * overwriting each other, surviving death and world change. It is an attachment for that
     * reason.</p>
     */
    @Test
    @DisplayName("the direction is fixed at the press and lives on the player")
    void theDirectionIsCommitted() {
        LungeState state = new LungeState(0.0, 1.0, 0);
        assertEquals(1.0, state.direction()[1], 1.0E-6, "straight ahead");
        assertEquals(0.0, state.direction()[0], 1.0E-6, "and no sideways");

        LungeState diagonal = new LungeState(3.0, 4.0, 0);
        assertEquals(1.0, Math.hypot(diagonal.direction()[0], diagonal.direction()[1]), 1.0E-6,
                "a diagonal is normalised on read, so a step length means the same in any "
                        + "direction");
    }

    /** A degenerate direction falls back rather than dividing by zero on the movement tick. */
    @Test
    @DisplayName("a zero direction is not a division by zero")
    void aZeroDirectionIsSurvivable() {
        LungeState broken = new LungeState(0.0, 0.0, 0);
        assertEquals(1.0, Math.hypot(broken.direction()[0], broken.direction()[1]), 1.0E-6,
                "it still comes out a unit vector, so the step moves the player somewhere rather "
                        + "than throwing");
    }

    /**
     * The owed steps are counted down per tier, never negative.
     *
     * <p>Per tier because a netherite lunge owes thirty steps where a wooden one owes ten, and a
     * caller reading a single figure would end the good one a third of the way through -- which is
     * exactly the "the lunge stops for no reason" complaint.</p>
     */
    @Test
    @DisplayName("the steps owed are per tier and never negative")
    void theRemainingStepsAreBounded() {
        for (int rank = 0; rank <= 6; rank++) {
            int ticks = LungeDamage.ticksFor(rank);
            assertEquals(ticks, new LungeState(0.0, 1.0, 0).remaining(rank));
            assertEquals(0, new LungeState(0.0, 1.0, ticks).remaining(rank));
            // Past the end, which a state restored from storage could carry. A lunge that owes
            // steps it already spent would carry the player across the world on login.
            assertEquals(0, new LungeState(0.0, 1.0, ticks + 40).remaining(rank));
            assertFalse(new LungeState(0.0, 1.0, ticks).running(rank));
        }
        assertTrue(new LungeState(0.0, 1.0, 0).running(0), "tick zero is still going");
    }

    /** A nonsense distance from a data pack must not make a lunge that never ends. */
    @Test
    @DisplayName("and no tier can make a lunge that goes on forever")
    void thereIsACeiling() {
        assertTrue(LungeDamage.ticksFor(0) <= LungeDamage.MAX_TICKS);
        // What a data pack would have to write to get there: a distance the loop can never cover.
        double impossible = LungeDamage.FIRST_STEP * LungeDamage.MAX_TICKS * 2.0;
        assertTrue(impossible > LungeDamage.distanceFor(6),
                "the test's own impossible distance has to actually be impossible");
        int ticks = LungeDamage.MAX_TICKS;
        double travelled = 0.0;
        for (int i = 0; i < ticks; i++) {
            travelled += LungeDamage.stepFor(i);
        }
        assertTrue(travelled < impossible,
                "the cap stops the lunge before the distance it was asked for, which is the whole "
                        + "point of having one");
    }
}