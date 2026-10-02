package com.cortes.combat.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The streak's pitch ladder: half a tone to two, one step at a time.
 *
 * <p>The escalation has been visible on the damage numbers and on nothing else, so this ladder is
 * the only thing that tells a player their streak is running. That makes it a rule rather than a
 * garnish, and every number here is one a test can hold.</p>
 */
class StreakPitchTest {

    private static final double STEP_SEMITONES = 3.0;

    /**
     * The two ends are the design's, and they are vanilla's too.
     *
     * <p>{@code NoteBlock#getPitchFromNote} is {@code 2^((note - 12) / 12)}, so note 0 is 0.5 and
     * note 24 is 2.0 -- the whole two-octave sweep of the note block's own pitch slider. The
     * numbers are pinned rather than computed because they are the two a playtest will listen
     * for, and a ladder that lands between them is not the ladder that was asked for.</p>
     */
    @Test
    @DisplayName("the first hit sounds at 0.5 and the ninth at 2.0")
    void theEndsAreTheDesignsOnes() {
        assertEquals(0.5F, StreakState.stackPitchFor(1), 1.0E-6F,
                "the floor. A deep pluck on the first landed hit, which is the opposite of where "
                        + "an escalating combo usually starts and therefore the clearest possible "
                        + "signal that something is about to climb");
        assertEquals(2.0F, StreakState.stackPitchFor(StreakState.FULL_STACK_HITS), 1.0E-6F,
                "the ceiling, on the ninth hit");
    }

    /**
     * The middle is exactly 1.0, which is why the count is nine.
     *
     * <p>Twenty-four semitones over eight steps is three each, and the fifth hit lands on the
     * untouched pitch. Any other hit count either misses 1.0 or spends four of its steps getting
     * there. That is the whole argument for the number, so it is asserted rather than described.</p>
     */
    @Test
    @DisplayName("the ladder divides evenly and the middle is an unshifted pitch")
    void theLadderDividesEvenly() {
        int steps = StreakState.FULL_STACK_HITS - 1;
        assertEquals(0, 24 % steps,
                "twenty four semitones split into " + steps + " whole steps. A ladder with a "
                        + "partial step at the top spends its last jump on a fraction of a tone, "
                        + "which the ear reads as the ladder stopping early");

        assertEquals(1.0F, StreakState.stackPitchFor(5), 1.0E-6F,
                "the fifth hit is exactly where the recording already is. The climb starts low, "
                        + "crosses the unshifted sound in the middle and ends high, which is what "
                        + "makes it read as one sweep instead of as a dimmer");

        float[] pitches = new float[StreakState.FULL_STACK_HITS];
        for (int hits = 1; hits <= StreakState.FULL_STACK_HITS; hits++) {
            pitches[hits - 1] = StreakState.stackPitchFor(hits);
            double note = STEP_SEMITONES * (hits - 1);
            // The -12 is the note block's own offset: 2^((note - 12) / 12) is 0.5 at note 0, not
            // 1.0. Without it the first hit would be "one semitone up" and the ladder would be a
            // ninth of an octave short of the floor it promises.
            assertEquals(Math.pow(2.0, (note - 12) / 12.0), pitches[hits - 1], 1.0E-6,
                    "hit " + hits + " sits at note " + (int) note + ", which is "
                            + (hits - 1) * STEP_SEMITONES + " semitones up: a minor third each, "
                            + "wide enough to hear and narrow enough that nine of them are still "
                            + "a scale rather than a siren");
        }
    }

    /** It only ever goes up. A ladder that dips is a shape the player has to learn. */
    @Test
    @DisplayName("every hit is higher than the one before")
    void itOnlyGoesUp() {
        for (int hits = 2; hits <= StreakState.FULL_STACK_HITS; hits++) {
            assertTrue(StreakState.stackPitchFor(hits) > StreakState.stackPitchFor(hits - 1),
                    "hit " + hits + " must be above hit " + (hits - 1));
        }
        assertNotEquals(StreakState.stackPitchFor(1), StreakState.stackPitchFor(2),
                "the first two hits have to be distinguishable, or the whole ladder is a "
                        + "one-event signal that says 'a hit landed'");
    }

    /**
     * Past the ninth it holds, and before the first it starts at the floor.
     *
     * <p>The escalation has no cap by design, so an unbroken streak can run past anything this
     * counts. Holding at the top is what keeps the ladder a readout: a number that keeps climbing
     * forever is a number the player has stopped reading.</p>
     */
    @Test
    @DisplayName("the ceiling holds instead of running off the end")
    void theCeilingHolds() {
        float top = StreakState.stackPitchFor(StreakState.FULL_STACK_HITS);
        assertEquals(top, StreakState.stackPitchFor(StreakState.FULL_STACK_HITS + 1), 1.0E-6F);
        assertEquals(top, StreakState.stackPitchFor(400), 1.0E-6F,
                "the streak is uncapped, so this state is reachable and a formula that grows with "
                        + "it would eventually exceed what a pitch can be");

        assertEquals(StreakState.stackPitchFor(1), StreakState.stackPitchFor(0), 1.0E-6F,
                "no hits yet is the floor, not an error. The sound only ever plays after a hit, so "
                        + "this is the shape a caller gets if it asks before anything landed");
        assertEquals(StreakState.stackPitchFor(1), StreakState.stackPitchFor(-5), 1.0E-6F,
                "and a negative count clamps rather than producing a negative exponent");
    }

    /** The record reads its own state, so no caller can pass a count that disagrees with it. */
    @Test
    @DisplayName("the streak answers for its own pitch")
    void itReadsItsOwnCount() {
        assertEquals(StreakState.stackPitchFor(1), new StreakState(1, 0).stackPitch(), 1.0E-6F);
        assertEquals(StreakState.stackPitchFor(6), new StreakState(6, 40).stackPitch(), 1.0E-6F,
                "and how long ago it was is not part of the question. The window decides whether "
                        + "the streak is alive, not how high the last note was");
        assertEquals(StreakState.stackPitchFor(1), StreakState.EMPTY.stackPitch(), 1.0E-6F);
    }
}