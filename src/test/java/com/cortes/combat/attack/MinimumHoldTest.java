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
    @DisplayName("one tick under the threshold is a tap, whatever the charge says")
    void justUnderIsATap() {
        int justUnder = AttackTiming.MIN_CHARGE_HOLD_TICKS - 1;
        assertEquals(0.0F, AttackTiming.chargeRatioFor(justUnder, 0.9F), 0.0F);
    }

    @Test
    @DisplayName("exactly the threshold is charged")
    void exactlyTheThresholdIsCharged() {
        assertEquals(0.16F, AttackTiming.chargeRatioFor(
                AttackTiming.MIN_CHARGE_HOLD_TICKS, 0.16F), 0.0F);
    }

    @Test
    @DisplayName("the raw count rounds the same way, so the server and the viewmodel agree")
    void rawCountAgreesWithTheRatio() {
        // The two entry points see the same gesture described two ways: a client that already
        // has a ratio, and a server that counted ticks. If they classified the same swing
        // differently then the item would wind up for a swing that lands flat, which is a
        // promise the game does not keep.
        int threshold = AttackTiming.MIN_CHARGE_HOLD_TICKS;
        for (int ticks = 0; ticks <= 40; ticks++) {
            float ratio = (float) ticks / (float) FULL;
            boolean fromRatio = AttackTiming.chargeRatioFor(ticks, ratio) == 0.0F;
            boolean fromCount = AttackTiming.chargeRatioFrom(ticks, FULL) == 0.0F;
            assertEquals(fromRatio, fromCount,
                    "ticks=" + ticks + ": the two entry points classified the swing differently");
        }
    }

    @Test
    @DisplayName("a count past the full charge still caps at one")
    void capsAtFull() {
        assertEquals(1.0F, AttackTiming.chargeRatioFrom(500, FULL), 0.0F);
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
    @DisplayName("a zero full-charge scale means no charge rather than a divide by zero")
    void zeroScaleIsSafe() {
        assertEquals(0.0F, AttackTiming.chargeRatioFrom(100, 0), 0.0F);
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
