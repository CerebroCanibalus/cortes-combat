package com.cortes.combat.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crowd charge: more bodies around the target, more damage on the one you hit.
 *
 * <p>Pure, on purpose: the whole formula is two integers in and one float out, so it can be walked
 * in a bare JVM. The half that needs a world -- counting the bodies -- lives in
 * {@code CombatEngine#crowdAround} and only ever produces the second argument.</p>
 */
class SweepingEdgeTest {

    @Test
    @DisplayName("more bodies around the target means more damage")
    void growsWithTheCrowd() {
        float alone = SweepingEdge.bonus(1, 0);
        float one = SweepingEdge.bonus(1, 1);
        float four = SweepingEdge.bonus(1, 4);

        assertEquals(0.0F, alone, 0.0F, "a target standing alone is not a crowd");
        assertTrue(one > alone);
        assertTrue(four > one, "the whole point: the number climbs with the bodies");
        assertEquals(SweepingEdge.DAMAGE_PER_ENEMY_PER_LEVEL * 4, four, 1.0E-6F);
    }

    @Test
    @DisplayName("the level multiplies the crowd, and level zero is no enchantment")
    void scalesWithLevel() {
        assertEquals(0.0F, SweepingEdge.bonus(0, 10), 0.0F,
                "no enchantment, no bonus, no matter how big the crowd");
        assertEquals(SweepingEdge.bonus(1, 3) * 3, SweepingEdge.bonus(3, 3), 1.0E-6F,
                "level III against the same three is three times level I");
    }

    @Test
    @DisplayName("the bonus is never negative, whatever the data says")
    void neverNegative() {
        assertEquals(0.0F, SweepingEdge.bonus(-1, 5), 0.0F);
        assertEquals(0.0F, SweepingEdge.bonus(2, -3), 0.0F);
        assertTrue(SweepingEdge.bonus(5, 20) > 0.0F);
    }

    @Test
    @DisplayName("the crowd radius is tight enough to mean packed, wide enough to catch a group")
    void theRadiusIsReasonable() {
        // Sanity on the one geometric number, so a typo cannot turn the enchantment into a
        // battlefield-wide buff or a strictly-self count.
        assertTrue(SweepingEdge.CROWD_RADIUS > 0.0);
        assertTrue(SweepingEdge.CROWD_RADIUS <= 5.0,
                "the enchantment is about bodies packed around a target, not about the whole room");
    }
}
