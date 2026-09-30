package com.cortes.combat.attack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tap-versus-charge rule, from the raw count the server keeps.
 *
 * <p>The report was "every attack registers as a charge, and I am clicking very small". That is
 * not a number anyone can argue with, so the rule is exercised here at the two boundaries that
 * decide it, and the boundary case is checked from both sides.</p>
 *
 * <p>One thing this suite is deliberately not allowed to do is pick a specific feel. The
 * threshold's value is a playtest decision, so the tests pin the <em>shape</em> of the rule and
 * leave the number where the player put it.</p>
 */
class MinimumHoldTest {

    private static final int FULL = 25;

    @Test
    @DisplayName("one tick under the threshold is a tap, however long the count")
    void justUnderIsATap() {
        int justUnder = AttackTiming.MIN_CHARGE_HOLD_TICKS - 1;
        assertEquals(0.0F, AttackTiming.chargeRatioFor(justUnder, FULL), 0.0F);
    }

    @Test
    @DisplayName("exactly the threshold is the bottom of the scale")
    void exactlyTheThresholdIsCharged() {
        assertEquals(0.0F, AttackTiming.chargeRatioFor(
                AttackTiming.MIN_CHARGE_HOLD_TICKS, FULL), 0.0F);
    }

    @Test
    @DisplayName("there is one rule, and the server and the viewmodel both call it")
    void oneRuleForBothSides() {
        // This used to be two entry points that took different descriptions of the same
        // gesture, one a ratio and one a tick count, and they were asserted to agree. There is
        // now one method that takes the count, so there is nothing left to disagree about: a
        // client whose count is a tick behind the server's still classifies the swing the same
        // way, because the threshold is the only thing deciding it.
        for (int ticks = 0; ticks <= 60; ticks++) {
            float value = AttackTiming.chargeRatioFor(ticks, FULL);
            // Zero up to and including the threshold, because the threshold is where the ramp
            // starts rather than where it is already part way up. An earlier version of this
            // asserted the boundary was exclusive and failed on exactly that tick.
            assertEquals(ticks <= AttackTiming.MIN_CHARGE_HOLD_TICKS, value == 0.0F,
                    "ticks=" + ticks + " gave " + value + " against a threshold of "
                            + AttackTiming.MIN_CHARGE_HOLD_TICKS);
        }
    }

    @Test
    @DisplayName("a count past the full charge still caps at one")
    void capsAtFull() {
        assertEquals(1.0F, AttackTiming.chargeRatioFor(500, FULL), 0.0F);
    }

    @Test
    @DisplayName("a weapon that does not charge is never charged, whatever the count")
    void nonChargingIsNeverCharged() {
        // The rule is asked about the count, and the weapon is asked separately, and the two meet
        // in CombatEngine. A dagger held for a minute is still a dagger.
        assertEquals(1.0F,
                com.cortes.combat.core.weapon.ChargeProfile.NONE.multiplierFor(1.0F), 0.0F);
    }

    @Test
    @DisplayName("a weapon with no charge window is never charged, and never divides by zero")
    void zeroScaleIsSafe() {
        // The remap divides by the remainder of the window. A weapon whose window is zero, which
        // is what a non-charging profile carries, reaches the guard before the division.
        assertEquals(1.0F, AttackTiming.chargeRatioFor(100, 0), 0.0F);
        assertEquals(0.0F, AttackTiming.chargeRatioFor(3, 0), 0.0F);
    }

    @Test
    @DisplayName("the threshold is short enough that a deliberate click stays under it")
    void thresholdIsAFastDecision() {
        // A click a person means to be a click is about a hundred and fifty milliseconds, which
        // is three ticks. Four leaves one tick of room and no more, which is the whole reason it
        // is not higher.
        assertTrue(AttackTiming.MIN_CHARGE_HOLD_TICKS <= 4,
                "the threshold drifted to " + AttackTiming.MIN_CHARGE_HOLD_TICKS
                        + " ticks, which would make a deliberate click read as a charge");
    }
}
