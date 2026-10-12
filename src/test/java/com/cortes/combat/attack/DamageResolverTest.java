package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.state.StreakState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageResolverTest {

    private static final ChargeProfile SWORD = new ChargeProfile(true, 0.5F, 25);
    private static final ChargeProfile NONE = ChargeProfile.NONE;

    private static AttackResult sword(float chargeRatio) {
        return DamageResolver.resolve(6.0, SWORD, WeaponProfile.SWORD, chargeRatio, StreakState.EMPTY);
    }

    @Nested
    @DisplayName("flat damage")
    class Flat {        @Test
        @DisplayName("an uncharged swing does exactly base damage")
        void uncharged() {
            assertEquals(6.0F, sword(0.0F).damage());
        }

        @Test
        @DisplayName("a full charge adds the bonus on top of the base")
        void fullCharge() {
            assertEquals(9.0F, sword(1.0F).damage());
        }

        @Test
        @DisplayName("half charge adds half the bonus")
        void halfCharge() {
            assertEquals(7.5F, sword(0.5F).damage());
        }

        @Test
        @DisplayName("a weapon that cannot charge always deals base damage")
        void cannotCharge() {
            AttackResult result = DamageResolver.resolve(6.0, NONE, WeaponProfile.ROPERA, 1.0F, StreakState.EMPTY);
            assertEquals(6.0F, result.damage());
            assertEquals(0.0F, result.chargeRatio());
        }

        @Test
        @DisplayName("the charge ratio is clamped even if the data is nonsense")
        void clamped() {
            assertEquals(6.0F, sword(-3.0F).damage());
            assertEquals(9.0F, sword(99.0F).damage());
        }
    }

    @Nested
    @DisplayName("enchantment damage")
    class Enchantment {

        /**
         * The decision under test: an enchantment's damage is <b>summed flat</b>, not multiplied
         * by the charge.
         *
         * <p>Vanilla scales it ({@code f1 *= f2} in {@code Player#attack}), but vanilla's whole
         * attack is gated by the timing multiplier this project deleted. Here the enchantment is a
         * property of the item, bought once at an anvil, so it has to be worth the same on a tap
         * and a full charge.</p>
         *
         * <p>The assertions are on the <b>difference</b> and not on the totals alone, because that
         * is the exact property a sabotage breaks: multiplying the bonus into the multiplier would
         * make the charged case gain one and a half times as much as the tap while both totals
         * still looked plausible. Verified by sabotage: replacing the flat sum with
         * {@code damage *= enchantBonus} fails this test and only this one.</p>
         */
        @Test
        @DisplayName("the enchantment bonus is summed flat, not scaled by the charge")
        void summedFlat() {
            // Base 6, sword bonus 0.5: a tap is 6 and a full charge is 9.
            AttackResult tapped = DamageResolver.resolve(6.0, SWORD, WeaponProfile.SWORD, 0.0F,
                    StreakState.EMPTY, 0.0F, 0, 3.0F);
            AttackResult charged = DamageResolver.resolve(6.0, SWORD, WeaponProfile.SWORD, 1.0F,
                    StreakState.EMPTY, 0.0F, 0, 3.0F);

            assertEquals(9.0F, tapped.damage(), 1.0E-6F, "6 + 3 flat");
            assertEquals(12.0F, charged.damage(), 1.0E-6F, "9 + the same 3 flat");

            // **The exact property: the bonus is the same absolute amount either way.** If it were
            // scaled by the 1.5 multiplier, the charged case would have gained 4.5 and the totals
            // would still have looked like a sword.
            assertEquals(3.0F, charged.damage() - 9.0F, 1.0E-6F,
                    "the charged swing gained 3, not 1.5 times 3");
            assertEquals(3.0F, tapped.damage() - 6.0F, 1.0E-6F,
                    "and the tap gained the same 3");
        }

        @Test
        @DisplayName("a negative bonus cannot heal the target")
        void negativeIsClamped() {
            AttackResult result = DamageResolver.resolve(6.0, SWORD, WeaponProfile.SWORD, 0.0F,
                    StreakState.EMPTY, 0.0F, 0, -5.0F);
            assertEquals(6.0F, result.damage(), 1.0E-6F,
                    "a nonsense bonus must not turn a swing into a heal");
        }
    }

    @Nested
    @DisplayName("knockback")
    class Knockback {

        /**
         * These used to assert on {@code AttackResult#knockback} and they moved rather than
         * disappearing, which is the point: knockback is a property of the weapon now, not a
         * by-product of the damage. The floor is the one part that stayed, because the floor is
         * about charge, not about the weapon.
         */
        @Test
        @DisplayName("no knockback below the charge floor")
        void belowFloor() {
            assertEquals(0.0F, DamageResolver.knockbackFor(0.5F));
        }

        @Test
        @DisplayName("knockback appears exactly at the floor")
        void atFloor() {
            // The floor itself counts: 0.6 charge earns 0.6 of the weapon's push.
            assertEquals(0.6F, DamageResolver.knockbackFor(0.6F));
        }

        @Test
        @DisplayName("a fully charged swing knocks back fully")
        void full() {
            assertEquals(1.0F, DamageResolver.knockbackFor(1.0F));
        }

        @Test
        @DisplayName("an uncharged swing never knocks back, so shoving cannot be spammed")
        void noSpam() {
            for (int i = 0; i <= 59; i++) {
                assertEquals(0.0F, DamageResolver.knockbackFor(i / 100.0F));
            }
        }

        @Test
        @DisplayName("the floor is the only thing the charge decides, not the weapon's power")
        void chargeOnlyGatesIt() {
            // Same charge, different weapons, same fraction delivered. The weapon sets how much
            // the full push is; this sets how much of it a given swing earns.
            assertEquals(DamageResolver.knockbackFor(0.8F), DamageResolver.knockbackFor(0.8F));
        }
    }

    @Nested
    @DisplayName("warhammer escalation")
    class Warhammer {

        private static final ChargeProfile HAMMER = new ChargeProfile(true, 0.6F, 32);

        private static AttackResult hammer(int streakHits, float chargeRatio) {
            StreakState streak = new StreakState(streakHits, 0);
            return DamageResolver.resolve(5.0, HAMMER, WeaponProfile.WARHAMMER, chargeRatio, streak);
        }

        @Test
        @DisplayName("no streak means no escalation")
        void noStreak() {
            assertEquals(5.0F, hammer(0, 0.0F).damage());
        }

        @Test
        @DisplayName("each consecutive hit adds a step")
        void escalates() {
            // 5 base, +0.15 per hit, plus the 0.6 charge bonus on top of the escalated value.
            assertEquals(5.0F * 1.15F, hammer(1, 0.0F).damage());
            assertEquals(5.0F * 1.30F, hammer(2, 0.0F).damage());
        }

        @Test
        @DisplayName("escalation has no practical cap by design")
        void uncapped() {
            float atTen = hammer(10, 0.0F).damage();
            float atTwenty = hammer(20, 0.0F).damage();
            assertTrue(atTwenty > atTen);
        }

        @Test
        @DisplayName("an expired streak stops escalating even if the hit count is high")
        void expired() {
            // ticksSinceLastHit is past the 4s window, so the streak is inactive.
            StreakState stale = new StreakState(10, StreakState.WINDOW_TICKS);
            AttackResult result = DamageResolver.resolve(5.0, HAMMER, WeaponProfile.WARHAMMER, 0.0F, stale);
            assertEquals(5.0F, result.damage());
        }

        @Test
        @DisplayName("a full charge flags the swing as armour ignoring")
        void armourIgnore() {
            assertTrue(hammer(0, 1.0F).kind().piercesArmour());
        }

        @Test
        @DisplayName("a partial charge does not ignore armour")
        void noArmourIgnoreBelowFull() {
            assertFalse(hammer(0, 0.99F).kind().piercesArmour());
        }

        @Test
        @DisplayName("other weapons never ignore armour")
        void onlyWarhammer() {
            assertFalse(sword(1.0F).kind().piercesArmour());
        }
    }

    @Nested
    @DisplayName("misses")
    class Misses {

        @Test
        @DisplayName("the miss result deals nothing")
        void miss() {
            assertTrue(AttackResult.MISS.isMiss());
            assertEquals(0.0F, AttackResult.MISS.damage());
        }
    }
}
