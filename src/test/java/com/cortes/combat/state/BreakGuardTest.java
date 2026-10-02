package com.cortes.combat.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The warhammer's charged hit reaches past a guard, and it empties the whole bar.
 *
 * <p>Two separate claims, tested separately, because they were once one confused one. "The axe
 * breaks a shield" is about whether the hit gets through. "The warhammer empties the guard" is
 * about what the defender is left with. Only the second one is a decision, and the first is
 * vanilla's.</p>
 */
class BreakGuardTest {

    private static final int REGEN = 40;

    @Test
    @DisplayName("it takes every point, whatever was left")
    void itTakesEveryPoint() {
        GuardState three = new GuardState(true, 3, 3, REGEN);
        GuardState one = new GuardState(true, 3, 1, REGEN);

        assertEquals(0, three.breakGuard(REGEN).stamina(),
                "a full shield is worth three blocked hits. That number belongs to the shield, and "
                        + "a warhammer is not three hits, it is one");
        assertEquals(0, one.breakGuard(REGEN).stamina(),
                "and a shield already down to one is emptied by exactly the same swing, which is "
                        + "the whole point: the cost does not depend on how much is left to take");
    }

    /**
     * It costs the same for a sword as for a shield.
     *
     * <p>The bar is three points or one depending on the item, and a method that spent "one point,
     * repeatedly" would read as the same rule while being a different mechanic: the sword would
     * look identical to a shield taken down one notch at a time, and nothing in the game would
     * ever break a sword's guard from full.</p>
     */
    @Test
    @DisplayName("a one-point sword loses its only point too")
    void itTakesTheSwordToo() {
        GuardState sword = new GuardState(true, 1, 1, REGEN);
        GuardState broken = sword.breakGuard(REGEN);

        assertEquals(0, broken.stamina());
        assertFalse(broken.isActive(),
                "a sword's guard is one hit by design. If the warhammer only emptied shields this "
                        + "is where it would go unnoticed, because one point is also what "
                        + "spending it would leave");
    }

    @Test
    @DisplayName("it lowers the guard, the same way spending the last point does")
    void itLowersTheGuard() {
        GuardState broken = new GuardState(true, 3, 3, REGEN).breakGuard(REGEN);

        assertFalse(broken.held(),
                "the guard comes down. Raising it again has to cost the normal regen wait, or a "
                        + "broken guard is a guard the player got back for free");
        assertTrue(broken.isExhausted());
        assertFalse(broken.isActive());
    }

    /**
     * It does not shrink the item.
     *
     * <p>The maximum belongs to the item and the bar belongs to the player. Zeroing the stamina is
     * a cost; zeroing the maximum would be upgrading the shield to a sword for free, and it would
     * still be at zero afterwards so the bug would hide until the regen ran.</p>
     */
    @Test
    @DisplayName("the item is still worth what it was worth")
    void theMaximumIsUntouched() {
        assertEquals(3, new GuardState(true, 3, 3, REGEN).breakGuard(REGEN).max());
        assertEquals(1, new GuardState(true, 1, 1, REGEN).breakGuard(REGEN).max());
    }

    /** The regen countdown is armed, so a broken guard costs the player real time to come back. */
    @Test
    @DisplayName("the regen countdown starts, it does not skip")
    void theRegenIsArmed() {
        int regen = new GuardState(true, 3, 3, REGEN).breakGuard(REGEN).regenTicks();
        assertEquals(REGEN, regen,
                "the same full interval spending the last point uses. A broken guard that "
                        + "regenerates early is a guard that was never broken");
        assertNotEquals(0, regen, "starting it at zero hands a point back on the next tick, which "
                + "is the bug that made every later point arrive a tick early");
    }

    /** An item that cannot guard has no bar, and this must not invent one. */
    @Test
    @DisplayName("a guard with no points left alone stays alone")
    void anEmptyGuardIsAlreadyBroken() {
        GuardState already = new GuardState(false, 3, 0, REGEN);
        GuardState after = already.breakGuard(REGEN);

        assertFalse(after.isActive(), "there was nothing to take");
        assertEquals(0, after.stamina());
    }
}