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
    class Flat {

        @Test
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
            AttackResult result = DamageResolver.resolve(6.0, NONE, WeaponProfile.RAPIER, 1.0F, StreakState.EMPTY);
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
            assertTrue(hammer(0, 1.0F).ignoredArmour());
        }

        @Test
        @DisplayName("a partial charge does not ignore armour")
        void noArmourIgnoreBelowFull() {
            assertFalse(hammer(0, 0.99F).ignoredArmour());
        }

        @Test
        @DisplayName("other weapons never ignore armour")
        void onlyWarhammer() {
            assertFalse(sword(1.0F).ignoredArmour());
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
