package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.KnockbackProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clamp on a knockback profile.
 *
 * <p>The profile is data, and data can be wrong: a datapack, a config override, a number typed
 * into the wrong field. The clamp is what stops a typo from launching a mob into the void, and
 * the reason it exists here rather than at the call site is that there are three call sites and
 * remembering to clamp at each one is how one of them ends up not clamping.</p>
 */
class KnockbackProfileTest {

    @Test
    @DisplayName("a normal profile passes through untouched")
    void normalValues() {
        KnockbackProfile profile = new KnockbackProfile(0.28F, 0.05F);
        assertEquals(0.28F, profile.clampedHorizontal(), 0.0F);
        assertEquals(0.05F, profile.clampedVertical(), 0.0F);
    }

    @Test
    @DisplayName("negative values clamp to zero, not to the other side of the screen")
    void negativesBecomeZero() {
        KnockbackProfile profile = new KnockbackProfile(-5.0F, -0.5F);
        assertEquals(0.0F, profile.clampedHorizontal(), 0.0F);
        assertEquals(0.0F, profile.clampedVertical(), 0.0F);
    }

    @Test
    @DisplayName("an absurd horizontal value is capped")
    void horizontalIsCapped() {
        KnockbackProfile profile = new KnockbackProfile(99.0F, 0.0F);
        assertEquals(KnockbackProfile.MAX_HORIZONTAL, profile.clampedHorizontal(), 0.0F);
    }

    @Test
    @DisplayName("the vertical cap is far below the horizontal one, on purpose")
    void verticalIsMuchLowerThanHorizontal() {
        // The report that produced this was "the knockback is too vertical". The two numbers are
        // separate so they can be different, and the caps are the enforcement of that: a lift
        // that is a quarter of a block is already a lot for a sword, let alone a block.
        assertTrue(KnockbackProfile.MAX_VERTICAL < KnockbackProfile.MAX_HORIZONTAL / 2.0F,
                "the vertical cap should be well under half the horizontal one, was "
                        + KnockbackProfile.MAX_VERTICAL + " against " + KnockbackProfile.MAX_HORIZONTAL);
    }

    @Test
    @DisplayName("a NaN from bad data becomes zero rather than poisoning the motion")
    void nanBecomesZero() {
        KnockbackProfile profile = new KnockbackProfile(Float.NaN, Float.NaN);
        assertEquals(0.0F, profile.clampedHorizontal(), 0.0F);
        assertEquals(0.0F, profile.clampedVertical(), 0.0F);
    }

    @Test
    @DisplayName("a zero vertical is a reachable, meaningful setting")
    void zeroVerticalIsMeaningful() {
        // A weapon that never lifts anyone. It has to be expressible exactly, because a very
        // small number still lifts a little and "nearly none" is not "none".
        assertEquals(0.0F, new KnockbackProfile(0.3F, 0.0F).clampedVertical(), 0.0F);
    }
}
