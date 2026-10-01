package com.cortes.combat.guard;

import com.cortes.combat.guard.GuardHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the guard is worth, as numbers, before the game can be asked.
 *
 * <p>The explosion fraction is a constant the caller writes into the damage container, so the
 * only two things a test can honestly pin here are the number itself and the arithmetic that
 * turns it into "half the damage got through". The direction rule and the projectile rule live in
 * {@code GuardHandler#blockedFraction} and need a live {@code DamageSource} to exercise.</p>
 */
class GuardReductionTest {

    @Test
    @DisplayName("an explosion is half, which is what makes positioning still matter")
    void explosionIsHalf() {
        assertEquals(0.5F, GuardHandler.EXPLOSION_BLOCKED, 1.0E-6,
                "the design's old answer was zero -- a guard that is worthless against the one "
                        + "thing a player cannot choose to be standing next to");
        assertTrue(GuardHandler.EXPLOSION_BLOCKED > 0.0F, "it takes some of it");
        assertTrue(GuardHandler.EXPLOSION_BLOCKED < 1.0F,
                "and not all of it: a creeper has to stay worth walking away from, or the guard "
                        + "is a better answer than positioning");
    }

    @Test
    @DisplayName("half blocked means half the damage is written off the container")
    void theArithmetic() {
        // `setBlockedDamage(original * (1 - blocked))`, against an event whose `dmgBlocked`
        // starts at the full incoming amount. Getting this backwards blocks nothing at all,
        // which is the bug the old comment in GuardHandler was warning about.
        for (float incoming : new float[]{6.0F, 11.5F, 40.0F}) {
            float writtenOff = incoming * (1.0F - GuardHandler.EXPLOSION_BLOCKED);
            assertEquals(incoming * 0.5F, writtenOff, 1.0E-6);
            assertEquals(incoming * 0.5F, incoming - writtenOff, 1.0E-6,
                    "of " + incoming + " the player eats the other half");
        }
    }

    @Test
    @DisplayName("a full block still writes zero, and zero means stop all of it")
    void fullBlockUnchanged() {
        // The event's `dmgBlocked` is initialised to the container's new damage, so NOT calling
        // setBlockedDamage blocks everything. Calling it with 0 blocks nothing. The old code
        // refused to write a 0 on purpose, and that refusal is load bearing.
        float incoming = 20.0F;
        float untouched = incoming;                 // the default: dmgBlocked == newDamage
        float explicitlyZero = incoming * 0.0F;    // the mistake the comment warned about

        assertEquals(0.0F, incoming - untouched, 1.0E-6, "the default removes the whole thing");
        assertEquals(incoming, incoming - explicitlyZero, 1.0E-6,
                "and writing zero removes none of it, which is why a melee block has never "
                        + "called the setter with a literal");
    }
}
