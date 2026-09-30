package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard's own arithmetic, in a bare JVM.
 *
 * <p>Two of these exist because the playtest caught the bug they describe. A fixed maximum made
 * every weapon worth three points, which turned a sword into a shield. And the regen counter was
 * missing entirely, so a point came back every single tick.</p>
 */
class GuardStateTest {

    private static final int REGEN = 40;
    private static final int STUN = 40;

    private static GuardState state(int max) {
        return new GuardState(false, max, max, 0, REGEN);
    }

    private static GuardState raised(int max) {
        return new GuardState(true, max, max, 0, REGEN);
    }

    @Test
    @DisplayName("a shield is worth three and a sword is worth one")
    void perItemMaximum() {
        // The whole point of the maximum living in the item rather than a constant.
        assertEquals(3, state(3).max());
        assertEquals(1, state(1).max());
    }

    @Test
    @DisplayName("blocking spends exactly one point")
    void blockSpendsOne() {
        GuardState after = raised(3).block(STUN);
        assertEquals(2, after.stamina());
        assertTrue(after.held(), "two points left means the guard is still up");
        assertFalse(after.isStunned());
    }

    @Test
    @DisplayName("the sword's single point breaks the guard outright")
    void swordExhaustsImmediately() {
        GuardState after = raised(1).block(STUN);
        assertEquals(0, after.stamina());
        assertTrue(after.isExhausted());
        assertTrue(after.isStunned());
        assertFalse(after.held(), "spending the last point has to drop the guard, or exhaustion is invisible");
        assertFalse(after.isActive());
    }

    @Test
    @DisplayName("an exhausted guard does not block, even if the input still says it is raised")
    void exhaustedCannotBlock() {
        GuardState empty = new GuardState(true, 3, 0, 0, REGEN);
        assertFalse(empty.isActive());
    }

    @Test
    @DisplayName("the stun runs down before anything comes back")
    void stunBlocksRegen() {
        GuardState stunned = new GuardState(false, 3, 0, STUN, REGEN);
        GuardState ticked = stunned.tick(REGEN);
        assertEquals(STUN - 1, ticked.stunTicks());
        assertEquals(0, ticked.stamina(), "no point may come back while stunned");
    }

    @Test
    @DisplayName("holding the guard up recovers nothing")
    void noRegenWhileHeld() {
        GuardState partiallySpent = new GuardState(true, 3, 1, 0, REGEN);
        GuardState ticked = partiallySpent;
        for (int i = 0; i < REGEN * 3; i++) {
            ticked = ticked.tick(REGEN);
        }
        assertEquals(1, ticked.stamina(), "a raised guard is the cost of not being able to block twice");
    }

    @Test
    @DisplayName("one point comes back every interval, not every tick")
    void regenRate() {
        GuardState spent = new GuardState(false, 3, 0, 0, REGEN);
        GuardState ticked = spent;
        for (int i = 0; i < REGEN - 1; i++) {
            ticked = ticked.tick(REGEN);
        }
        assertEquals(0, ticked.stamina(), "must still be empty one tick before the interval is up");
        ticked = ticked.tick(REGEN);
        assertEquals(1, ticked.stamina());
    }

    @Test
    @DisplayName("swapping to a bigger item does not bank the difference")
    void raisingTheMaximumDoesNotGiftPoints() {
        // Sword with 1 point of 1, then pick up a shield.
        GuardState fromSword = state(1).block(STUN);
        assertEquals(0, fromSword.stamina());
        GuardState asShield = fromSword.withMax(3);
        assertEquals(3, asShield.max());
        assertEquals(0, asShield.stamina(), "a shield does not come pre-charged");
    }

    @Test
    @DisplayName("swapping to a smaller item cannot overflow the bar")
    void loweringTheMaximumClamps() {
        // Shield full at 3, then drop to a sword.
        GuardState asSword = state(3).withMax(1);
        assertEquals(1, asSword.max());
        assertEquals(1, asSword.stamina(), "one point, not three squeezed into a one-point item");
    }

    @Test
    @DisplayName("a zero-point item cannot guard at all")
    void zeroPoints() {
        GuardState nothing = new GuardState(true, 0, 0, 0, REGEN);
        assertFalse(nothing.isActive());
        // And it must not tick itself into existence either.
        assertEquals(0, nothing.tick(REGEN).stamina());
    }
}
