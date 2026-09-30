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
 *
 * <p>There is deliberately no stun test here. The stagger is a vanilla item cooldown now, so
 * testing it would be testing Minecraft.</p>
 */
class GuardStateTest {

    private static final int REGEN = 40;

    /** Vanilla's own shield stagger length. */
    private static final int STAGGER = 100;

    private static GuardState state(int max) {
        return new GuardState(false, max, max, REGEN);
    }

    private static GuardState raised(int max) {
        return new GuardState(true, max, max, REGEN);
    }

    @Test
    @DisplayName("a shield is worth three and a sword is worth one")
    void perItemMaximum() {
        // The whole point of the maximum living in the item rather than a constant.
        assertEquals(3, state(3).max());
        assertEquals(1, state(1).max());
    }

    @Test
    @DisplayName("a fresh guard item starts with nothing charged")
    void freshItemIsEmpty() {
        // Picking a shield up must not be worth three free blocks. The cost of a new item is
        // a few seconds of not being ready, and the orbs show that countdown.
        assertEquals(0, GuardState.EMPTY.stamina());
        assertFalse(GuardState.EMPTY.isActive());
        // And it is not stuck there: it refills on the normal interval, not the stagger's.
        GuardState ticked = GuardState.EMPTY.withMax(3);
        for (int i = 0; i < REGEN * 3; i++) {
            ticked = ticked.tick(REGEN, false, STAGGER);
        }
        assertEquals(3, ticked.stamina());
    }

    @Test
    @DisplayName("a fresh item refills at the normal rate, not the stagger's")
    void freshItemUsesNormalRate() {
        GuardState ticked = GuardState.EMPTY.withMax(3);
        for (int i = 0; i < REGEN - 1; i++) {
            ticked = ticked.tick(REGEN, false, STAGGER);
        }
        assertEquals(0, ticked.stamina(), "a fresh shield is not a staggered one");
    }

    @Test
    @DisplayName("a fresh item refills on the normal clock, not the stagger's")
    void freshItemIsNotStaggered() {
        // The distinguishing number. Over a 100 tick window the stagger pace would hand out
        // three points on a 3 point guard; the normal pace hands out two. Getting two here is
        // what proves an item that was never staggered is not being paid as if it had.
        GuardState ticked = GuardState.EMPTY.withMax(3);
        for (int i = 0; i < STAGGER; i++) {
            ticked = ticked.tick(REGEN, false, STAGGER);
        }
        assertEquals(2, ticked.stamina());
    }

    @Test
    @DisplayName("blocking spends exactly one point and keeps the guard up")
    void blockSpendsOne() {
        GuardState after = raised(3).block(REGEN);
        assertEquals(2, after.stamina());
        assertTrue(after.held(), "two points left means the guard is still up");
        assertTrue(after.isActive());
    }

    @Test
    @DisplayName("the sword's single point drops the guard outright")
    void swordExhaustsImmediately() {
        GuardState after = raised(1).block(REGEN);
        assertEquals(0, after.stamina());
        assertTrue(after.isExhausted());
        assertFalse(after.held(), "spending the last point has to drop the guard, or exhaustion is invisible");
        assertFalse(after.isActive());
    }

    @Test
    @DisplayName("blocking restarts the regen countdown")
    void blockResetsTheCounter() {
        // The counter restarts on every block, so a player under pressure cannot be chipped
        // down one point at a time while never quite getting back a full one.
        GuardState partly = new GuardState(true, 3, 2, 7);
        assertEquals(REGEN, partly.block(REGEN).regenTicks());
    }

    @Test
    @DisplayName("an exhausted guard does not block, even if the input still says it is raised")
    void exhaustedCannotBlock() {
        GuardState empty = new GuardState(true, 3, 0, REGEN);
        assertFalse(empty.isActive());
    }

    @Test
    @DisplayName("holding the guard up recovers nothing")
    void noRegenWhileHeld() {
        GuardState partiallySpent = new GuardState(true, 3, 1, REGEN);
        GuardState ticked = partiallySpent;
        for (int i = 0; i < REGEN * 3; i++) {
            ticked = ticked.tick(REGEN, false, STAGGER);
        }
        assertEquals(1, ticked.stamina(), "a raised guard is the cost of not being able to block twice");
    }

    @Test
    @DisplayName("one point comes back every interval, not every tick")
    void regenRate() {
        GuardState spent = new GuardState(false, 3, 0, REGEN);
        GuardState ticked = spent;
        for (int i = 0; i < REGEN - 1; i++) {
            ticked = ticked.tick(REGEN, false, STAGGER);
        }
        assertEquals(0, ticked.stamina(), "must still be empty one tick before the interval is up");
        ticked = ticked.tick(REGEN, false, STAGGER);
        assertEquals(1, ticked.stamina());
    }

    @Test
    @DisplayName("a stagger refills the guard exactly as the cooldown runs out")
    void staggerIsTheRefillClock() {
        // Three points over a 100 tick stagger: one back every 33 ticks, so the guard is full
        // the instant the item becomes usable again and not a moment before.
        GuardState empty = new GuardState(false, 3, 0, REGEN);
        GuardState ticked = empty;
        for (int i = 0; i < STAGGER; i++) {
            ticked = ticked.tick(REGEN, true, STAGGER);
        }
        assertEquals(3, ticked.stamina());
    }

    @Test
    @DisplayName("a stagger does not refill faster than the cooldown lasts")
    void staggerCannotOutrunTheCooldown() {
        // Half the stagger must not already be two thirds of the way back.
        GuardState empty = new GuardState(false, 3, 0, REGEN);
        GuardState ticked = empty;
        for (int i = 0; i < STAGGER / 2; i++) {
            ticked = ticked.tick(REGEN, true, STAGGER);
        }
        assertTrue(ticked.stamina() <= 1,
                "at half the cooldown the guard should still be mostly empty, was " + ticked.stamina());
    }

    @Test
    @DisplayName("being staggered refills even with the button still held")
    void staggerBeatsTheHeldPenalty() {
        // The player never let go of the button; the guard broke on its own. Charging them for
        // holding it on top of the stagger would be two punishments for one mistake.
        GuardState empty = new GuardState(true, 3, 0, REGEN);
        GuardState ticked = empty;
        for (int i = 0; i < STAGGER; i++) {
            ticked = ticked.tick(REGEN, true, STAGGER);
        }
        assertEquals(3, ticked.stamina());
    }

    @Test
    @DisplayName("blocking does not hand back a point on the very next tick")
    void blockStartsTheCountdownFull() {
        // The countdown begins full. Starting it at zero made the first point free.
        // Lowered, because a guard that is still raised never regenerates by design and that
        // would be testing the other rule instead of this one.
        GuardState after = raised(3).block(REGEN).lower();
        assertEquals(2, after.stamina());
        GuardState ticked = after;
        for (int i = 0; i < REGEN - 1; i++) {
            ticked = ticked.tick(REGEN, false, STAGGER);
        }
        assertEquals(2, ticked.stamina(), "still nothing back one tick before the interval");
        ticked = ticked.tick(REGEN, false, STAGGER);
        assertEquals(3, ticked.stamina());
    }

    @Test
    @DisplayName("swapping to a bigger item does not bank the difference")
    void raisingTheMaximumDoesNotGiftPoints() {
        // Sword with 1 point of 1, then pick up a shield.
        GuardState fromSword = state(1).block(REGEN);
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
        GuardState nothing = new GuardState(true, 0, 0, REGEN);
        assertFalse(nothing.isActive());
        // And it must not tick itself into existence either.
        assertEquals(0, nothing.tick(REGEN, false, STAGGER).stamina());
    }

    @Test
    @DisplayName("a guard with points left recovers even if it was never raised this fight")
    void regenWorksAfterLowering() {
        GuardState lowered = new GuardState(false, 3, 1, 0);
        GuardState ticked = lowered;
        for (int i = 0; i < REGEN; i++) {
            ticked = ticked.tick(REGEN, false, STAGGER);
        }
        assertEquals(2, ticked.stamina());
    }
}
