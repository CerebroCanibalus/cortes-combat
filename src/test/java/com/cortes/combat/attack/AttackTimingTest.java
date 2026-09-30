package com.cortes.combat.attack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackTimingTest {

    @Nested
    @DisplayName("the minimum hold that counts as a charge")
    class MinimumHold {

        @Test
        @DisplayName("a tap gets nothing at all, not a fraction")
        void tapIsFlat() {
            // The whole point of the hard cut, and the reason the charge bar does not fill on a
            // normal click. Below the threshold the bar, the wind-up and the FOV are all exactly
            // zero: nothing happens and nothing is shown. A bar that shows a sliver for a swing
            // that lands flat is the game telling the player it charged them when it did not.
            assertEquals(0.0F, AttackTiming.chargeRatioFor(0, 25), 0.0F);
            assertEquals(0.0F, AttackTiming.chargeRatioFor(1, 25), 0.0F);
            assertEquals(0.0F, AttackTiming.chargeRatioFor(3, 25), 0.0F);
        }

        @Test
        @DisplayName("the threshold is the bottom of the scale, not the top of a sliver")
        void thresholdIsZero() {
            // Remapped rather than clamped. Taking the raw ratio gives 0.16 here, and a bar that
            // visibly moves at the instant the swing becomes a charge reads as the player having
            // charged further than they had.
            assertEquals(0.0F,
                    AttackTiming.chargeRatioFor(AttackTiming.MIN_CHARGE_HOLD_TICKS, 25), 0.0F);
        }

        @Test
        @DisplayName("the charge then crosses the whole range, so the bar fills to the end")
        void crossesTheWholeRange() {
            // A window of twenty-four leaves twenty ticks of ramp after the threshold, so the
            // quarters land on whole ticks. A first attempt used twenty-five and asserted 0.25 at
            // nine ticks, which is 5/21 and not a quarter: the test was wrong, not the code.
            assertEquals(0.25F, AttackTiming.chargeRatioFor(9, 24), 0.0F);
            assertEquals(0.5F, AttackTiming.chargeRatioFor(14, 24), 0.0F);
            assertEquals(0.75F, AttackTiming.chargeRatioFor(19, 24), 0.0F);
            assertEquals(1.0F, AttackTiming.chargeRatioFor(24, 24), 0.0F);
            assertEquals(1.0F, AttackTiming.chargeRatioFor(500, 24), 0.0F);
        }

        @Test
        @DisplayName("there is no in-between: one tick either side of the threshold")
        void noGradientEitherSide() {
            int justUnder = AttackTiming.MIN_CHARGE_HOLD_TICKS - 1;
            assertEquals(0.0F, AttackTiming.chargeRatioFor(justUnder, 25), 0.0F,
                    "a hold of " + justUnder + " ticks is still nothing");
            assertEquals(0.0F, AttackTiming.chargeRatioFor(AttackTiming.MIN_CHARGE_HOLD_TICKS, 25), 0.0F,
                    "and the threshold itself starts at the bottom of the scale");
        }

        @Test
        @DisplayName("a charge window no longer than the threshold is all or nothing")
        void shortChargeWindow() {
            // A weapon that charges in exactly the threshold has no room to ramp. Dividing by
            // the remainder would be a division by zero.
            assertEquals(1.0F, AttackTiming.chargeRatioFor(4, 4), 0.0F);
            assertEquals(0.0F, AttackTiming.chargeRatioFor(3, 4), 0.0F);
            assertEquals(1.0F, AttackTiming.chargeRatioFor(9, 2), 0.0F);
        }

        @Test
        @DisplayName("the threshold is a couple of hundred milliseconds, not half a second")
        void thresholdIsAFastDecision() {
            // Four ticks. A longer one would make the fast click feel like the game ignored it,
            // because the swing resolves on the release and a longer hold is a longer wait.
            assertEquals(4, AttackTiming.MIN_CHARGE_HOLD_TICKS);
        }
    }

    @Nested
    @DisplayName("attack interval")
    class Interval {

        @Test
        @DisplayName("a sword's 1.6 attacks per second is 12 or 13 ticks")
        void sword() {
            // 20 / 1.6 = 12.5, which rounds to 13. Vanilla lands on 12.5 too.
            assertEquals(13, AttackTiming.ticksFor(1.6));
        }

        @Test
        @DisplayName("an axe or mace's 1.0 is 20 ticks")
        void axe() {
            assertEquals(20, AttackTiming.ticksFor(1.0));
        }

        @Test
        @DisplayName("a pickaxe or shovel's 1.2 is 17 ticks")
        void pickaxe() {
            assertEquals(17, AttackTiming.ticksFor(1.2));
        }

        @Test
        @DisplayName("the dagger's 2.5 is 8 ticks, the fastest in the set")
        void dagger() {
            assertEquals(8, AttackTiming.ticksFor(2.5));
        }

        @Test
        @DisplayName("a zero or negative attack speed falls back to bare hands")
        void degenerate() {
            assertEquals(AttackTiming.UNARMED_INTERVAL, AttackTiming.ticksFor(0.0));
            assertEquals(AttackTiming.UNARMED_INTERVAL, AttackTiming.ticksFor(-3.0));
            assertEquals(AttackTiming.UNARMED_INTERVAL, AttackTiming.ticksFor(Double.NaN));
        }

        @Test
        @DisplayName("absurd speeds are clamped so the interval never reaches zero")
        void clamped() {
            // 10000 attacks per second would otherwise divide down to 0 and allow attacking
            // every tick, which is the exact bug the hard cooldown exists to prevent.
            assertEquals(1, AttackTiming.ticksFor(10000.0));
        }

        @Test
        @DisplayName("absurdly slow weapons are capped")
        void capped() {
            assertEquals(AttackTiming.MAX_INTERVAL, AttackTiming.ticksFor(0.01));
        }
    }

    @Nested
    @DisplayName("charge multiplier")
    class Charge {

        @Test
        @DisplayName("no charge leaves damage untouched")
        void noCharge() {
            assertEquals(1.0F, AttackTiming.chargeMultiplier(0.5F, 0.0F));
        }

        @Test
        @DisplayName("full charge applies the whole bonus")
        void fullCharge() {
            assertEquals(1.5F, AttackTiming.chargeMultiplier(0.5F, 1.0F));
        }

        @Test
        @DisplayName("a bonus of 1.0 doubles damage, which is the sabre's full charge")
        void sabre() {
            assertEquals(2.0F, AttackTiming.chargeMultiplier(1.0F, 1.0F));
        }

        @Test
        @DisplayName("the multiplier is never below 1.0")
        void neverBelowBase() {
            // This is the rule that makes holding the button a safe default rather than a
            // gamble. A negative bonus must still not reduce damage.
            assertTrue(AttackTiming.chargeMultiplier(-0.5F, 1.0F) >= 1.0F);
        }

        @Test
        @DisplayName("an out of range ratio is clamped")
        void clampedRatio() {
            assertEquals(1.0F, AttackTiming.chargeMultiplier(0.5F, -1.0F));
            assertEquals(1.5F, AttackTiming.chargeMultiplier(0.5F, 5.0F));
        }
    }

    @Nested
    @DisplayName("spread")
    class Spread {

        @Test
        @DisplayName("zero spread leaves damage exactly as it was")
        void noSpread() {
            assertEquals(7.0F, AttackTiming.applySpread(7.0F, 0.0F));
        }

        @Test
        @DisplayName("damage never goes negative")
        void neverNegative() {
            for (int i = 0; i < 200; i++) {
                assertTrue(AttackTiming.applySpread(1.0F, 0.5F) >= 0.0F);
            }
        }
    }
}
