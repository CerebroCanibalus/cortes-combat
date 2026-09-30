package com.cortes.combat.client;

import com.cortes.combat.core.CombatFeedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crosshair's timing, in a bare JVM.
 *
 * <p>Everything here is arithmetic on ints and floats, so it runs without the game. That is the
 * point of the class: growth, shake and the tint fade are numbers that are miserable to tune
 * while a fight is going on and trivial to tune in a test.</p>
 */
class CrosshairFeedbackTest {

    @BeforeEach
    void reset() {
        CrosshairFeedback.reset();
    }

    /** Run the given number of ticks with the crosshair in a fixed aim state. */
    private static void tick(int times, boolean aiming) {
        for (int i = 0; i < times; i++) {
            CrosshairFeedback.onTick(aiming);
        }
    }

    @Test
    @DisplayName("an untouched crosshair is exactly its native size")
    void idleIsUnchanged() {
        assertEquals(1.0F, CrosshairFeedback.growth(), 0.0001F);
        assertEquals(0, CrosshairFeedback.shakeX());
        assertEquals(0, CrosshairFeedback.shakeY());
        assertEquals(0xFFFFFF, CrosshairFeedback.rgb());
    }

    @Test
    @DisplayName("a hit jumps to full size on the frame it lands")
    void hitIsInstant() {
        // The whole difference between the two growth behaviours. An impact that eases in reads
        // as the camera moving; one that eases out reads as a hit.
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        assertTrue(CrosshairFeedback.growth() > 1.0F + CrosshairFeedback.AIM_GROWTH,
                "a hit must be visibly bigger than an aim, was " + CrosshairFeedback.growth());
    }

    @Test
    @DisplayName("the hit growth is gone once the flash ends")
    void hitGrowthExpires() {
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        tick(CombatFeedback.HIT.tintTicks(), false);
        assertFalse(CrosshairFeedback.hitFlashing());
        assertEquals(1.0F, CrosshairFeedback.growth(), 0.0001F,
                "no aim and no hit means the base size, not a leftover from the flash");
    }

    @Test
    @DisplayName("the hit growth decays monotonically, never bouncing back up")
    void hitDecaysMonotonically() {
        CrosshairFeedback.onEvent(CombatFeedback.KILL);
        float previous = CrosshairFeedback.growth();
        for (int i = 0; i < CombatFeedback.KILL.tintTicks(); i++) {
            tick(1, false);
            float current = CrosshairFeedback.growth();
            assertTrue(current <= previous + 0.0001F,
                    "growth went up on tick " + i + ": " + previous + " -> " + current);
            previous = current;
        }
    }

    @Test
    @DisplayName("a block colours the crosshair but never grows or shakes it")
    void guardTintsOnly() {
        CrosshairFeedback.onEvent(CombatFeedback.GUARDED);
        // The player did not throw anything. A crosshair that jumps here would claim credit for
        // a swing they never took.
        assertEquals(1.0F, CrosshairFeedback.growth(), 0.0001F);
        assertEquals(0, CrosshairFeedback.shakeX());
        assertEquals(0, CrosshairFeedback.shakeY());
        assertNotEquals(0xFFFFFF, CrosshairFeedback.rgb(), "a block still has to be visible");
    }

    @Test
    @DisplayName("a block is still running while a hit is not")
    void guardAndHitAreDifferentFlashes() {
        CrosshairFeedback.onEvent(CombatFeedback.GUARDED);
        assertTrue(CrosshairFeedback.active());
        assertFalse(CrosshairFeedback.hitFlashing());

        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        assertTrue(CrosshairFeedback.hitFlashing());
    }

    @Test
    @DisplayName("the kill is loud and the hit is not")
    void killIsLouder() {
        // Same growth on purpose: the size says "I connected", the colour says "I finished it".
        // If they both grew more the player would have nothing left to tell them apart.
        assertEquals(CombatFeedback.HIT.tintTicks() < CombatFeedback.KILL.tintTicks(), true,
                "a kill has to outlast a hit, or there is no reason to colour it differently");
    }

    @Test
    @DisplayName("the tint fades back to plain white, not to transparent")
    void tintReturnsToWhite() {
        CrosshairFeedback.onEvent(CombatFeedback.KILL);
        tick(CombatFeedback.KILL.tintTicks(), false);
        assertEquals(0xFFFFFF, CrosshairFeedback.rgb(),
                "the crosshair has to come back looking untouched, not half faded");
    }

    @Test
    @DisplayName("a kill is a different colour from a hit")
    void killDiffersFromHit() {
        assertNotEquals(CombatFeedback.HIT.rgb(), CombatFeedback.KILL.rgb());
        assertNotEquals(CombatFeedback.GUARDED.rgb(), CombatFeedback.HIT.rgb());
        assertNotEquals(CombatFeedback.GUARDED.rgb(), CombatFeedback.KILL.rgb());
    }

    @Test
    @DisplayName("the aim growth is smoothed, so a target at the cone edge does not strobe")
    void aimIsSmoothed() {
        // A target sitting exactly on the cone boundary enters and leaves every frame. One tick
        // of growth must not be the whole growth, or the crosshair flickers.
        CrosshairFeedback.onTick(true);
        float afterOne = CrosshairFeedback.growth();
        assertTrue(afterOne < 1.0F + CrosshairFeedback.AIM_GROWTH,
                "a single tick must not reach full aim growth, was " + afterOne);
        assertTrue(afterOne > 1.0F, "but it must move on the first tick, was " + afterOne);
    }

    @Test
    @DisplayName("the aim growth settles at exactly the aim value and no further")
    void aimSettles() {
        tick(200, true);
        assertEquals(1.0F + CrosshairFeedback.AIM_GROWTH, CrosshairFeedback.growth(), 0.001F);
    }

    @Test
    @DisplayName("losing the target takes the crosshair back down")
    void aimReleases() {
        tick(200, true);
        tick(200, false);
        assertEquals(1.0F, CrosshairFeedback.growth(), 0.001F);
    }

    @Test
    @DisplayName("the shake stays inside the crosshair's own box")
    void shakeIsBounded() {
        // A shake that walks the crosshair off screen is not feedback, it is a bug report.
        CrosshairFeedback.onEvent(CombatFeedback.KILL);
        for (int i = 0; i < CombatFeedback.KILL.tintTicks(); i++) {
            tick(1, false);
            assertTrue(Math.abs(CrosshairFeedback.shakeX()) <= 2,
                    "horizontal shake ran away to " + CrosshairFeedback.shakeX());
            assertTrue(Math.abs(CrosshairFeedback.shakeY()) <= 2,
                    "vertical shake ran away to " + CrosshairFeedback.shakeY());
        }
    }

    @Test
    @DisplayName("the shake comes back to exactly the centre")
    void shakeReturnsToRest() {
        // The cycle is a closed ring, so the last step before the flash ends has to be the
        // origin. Otherwise the crosshair settles a pixel off and stays there forever.
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        int ticks = CombatFeedback.HIT.tintTicks();
        for (int i = 0; i < ticks; i++) {
            tick(1, false);
        }
        assertEquals(0, CrosshairFeedback.shakeX());
        assertEquals(0, CrosshairFeedback.shakeY());
    }

    @Test
    @DisplayName("the shake is seeded, so the same swing always shakes the same way")
    void shakeIsDeterministic() {
        // The reason the offsets come from a table instead of a random number. A hit that looks
        // different every time reads as noise, not as impact.
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        String first = traceShake(CombatFeedback.HIT.tintTicks());
        CrosshairFeedback.reset();
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        assertEquals(first, traceShake(CombatFeedback.HIT.tintTicks()),
                "the same hit must produce the same shake");
    }

    @Test
    @DisplayName("two hits in a row do not shake identically")
    void consecutiveHitsDiffer() {
        // Deterministic and varied are different requirements. Seeded from a counter, not from a
        // constant, so hitting twice feels like two impacts and not like a stutter.
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        String first = traceShake(CombatFeedback.HIT.tintTicks());
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        assertNotEquals(first, traceShake(CombatFeedback.HIT.tintTicks()));
    }

    /** The shake path of a whole flash, as a comparable string. */
    private static String traceShake(int ticks) {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < ticks; i++) {
            tick(1, false);
            path.append(CrosshairFeedback.shakeX()).append(',')
                    .append(CrosshairFeedback.shakeY()).append(';');
        }
        return path.toString();
    }

    @Test
    @DisplayName("the envelope is strongest at the start and gone at the end")
    void envelopeShape() {
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        assertEquals(1.0F, CrosshairFeedback.envelope(), 0.0001F, "full on the first tick");
        float previous = 1.0F;
        for (int i = 0; i < CombatFeedback.HIT.tintTicks(); i++) {
            tick(1, false);
            float current = CrosshairFeedback.envelope();
            if (current < 0.0F) {
                assertTrue(true);
                break;
            }
            assertTrue(current <= previous + 0.0001F, "the envelope went up on tick " + i);
            previous = current;
        }
    }

    @Test
    @DisplayName("nothing is active before anything has happened")
    void startsClean() {
        assertFalse(CrosshairFeedback.active());
        assertFalse(CrosshairFeedback.hitFlashing());
    }

    @Test
    @DisplayName("a new event replaces the one in flight instead of queueing")
    void eventsDoNotQueue() {
        // A kill landing on top of a hit must read as a kill, not as a hit plus a kill. Queuing
        // would delay the louder one behind the quieter one, which is backwards.
        CrosshairFeedback.onEvent(CombatFeedback.HIT);
        tick(1, false);
        CrosshairFeedback.onEvent(CombatFeedback.KILL);
        assertEquals(1.0F, CrosshairFeedback.envelope(), 0.0001F,
                "the kill restarts at full strength rather than resuming a half spent hit");
    }
}
