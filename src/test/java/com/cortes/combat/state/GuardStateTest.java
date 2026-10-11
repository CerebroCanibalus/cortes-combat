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
            ticked = ticked.tick(REGEN, false);
        }
        assertEquals(3, ticked.stamina());
    }

    @Test
    @DisplayName("a fresh item refills at the normal rate, not the stagger's")
    void freshItemUsesNormalRate() {
        GuardState ticked = GuardState.EMPTY.withMax(3);
        for (int i = 0; i < REGEN - 1; i++) {
            ticked = ticked.tick(REGEN, false);
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
            ticked = ticked.tick(REGEN, false);
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

    /**
     * **Astilladora: el mismo golpe, dos puntos.**
     *
     * <p>Y el borde que hace que el coste sea un parametro y no dos llamadas a {@code block}: con una
     * barra de tres, gastar dos deja uno y <b>la guardia sigue arriba</b>. Gastarlos de a uno bajaria
     * la guardia en el primer punto y el segundo caeria sobre una guardia que ya esta abajo -- que no
     * es lo que "este golpe cuesta dos" significa.</p>
     */
    @Test
    @DisplayName("a two-point block drops the bar by two and keeps the guard up while anything is left")
    void blockSpendsTwo() {
        GuardState after = raised(3).block(REGEN, 2);
        assertEquals(1, after.stamina(), "la barra baja por dos");
        assertTrue(after.held(),
                "**y la guardia sigue arriba**: gastar dos de a uno la habria bajado en el primero");
        assertTrue(after.isActive());

        GuardState emptied = raised(2).block(REGEN, 2);
        assertEquals(0, emptied.stamina());
        assertFalse(emptied.held(), "sin nada que quede, la guardia baja");
    }

    @Test
    @DisplayName("a cost below one is treated as one, so nothing blocks for free")
    void aCostBelowOneIsStillOne() {
        assertEquals(2, raised(3).block(REGEN, 0).stamina(),
                "un coste de cero no puede regalar un bloqueo gratis");
        assertEquals(2, raised(3).block(REGEN, -5).stamina());
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
        // The second argument is the guard's usability, so a raised guard passes true here. It
        // used to be the stagger flag and this passed false, which was correct under the old
        // three-argument signature and quietly stopped meaning anything under the new one.
        GuardState partiallySpent = new GuardState(true, 3, 1, REGEN);
        GuardState ticked = partiallySpent;
        for (int i = 0; i < REGEN * 3; i++) {
            ticked = ticked.tick(REGEN, true);
        }
        assertEquals(1, ticked.stamina(), "a raised guard is the cost of not being able to block twice");
    }

    @Test
    @DisplayName("one point comes back every interval, not every tick")
    void regenRate() {
        GuardState spent = new GuardState(false, 3, 0, REGEN);
        GuardState ticked = spent;
        for (int i = 0; i < REGEN - 1; i++) {
            ticked = ticked.tick(REGEN, false);
        }
        assertEquals(0, ticked.stamina(), "must still be empty one tick before the interval is up");
        ticked = ticked.tick(REGEN, false);
        assertEquals(1, ticked.stamina());
    }

    @Test
    @DisplayName("a broken guard regenerates nothing at all")
    void aStaggeredGuardDoesNotRegenerate() {
        // This test used to assert the exact opposite, and the playtest is what changed it.
        //
        // The old rule spread the refill across the stagger so the guard came back full exactly
        // as the item cooldown ran out. It sounded careful and it was wrong about the feeling:
        // points arriving during the stun is the one window where a player who is helpless is
        // also recovering. A stagger that refills itself is a stagger that costs nothing.
        //
        // Three points over a hundred ticks used to be the expectation. Zero is the rule now,
        // and "used to be three" is the number that makes the change legible.
        GuardState empty = new GuardState(false, 3, 0, REGEN);
        GuardState ticked = empty;
        for (int i = 0; i < STAGGER * 3; i++) {
            ticked = ticked.tick(REGEN, true);
        }
        assertEquals(0, ticked.stamina(),
                "a broken guard is not a shorter refill, it is no refill");
    }

    @Test
    @DisplayName("the refill resumes the moment the guard is usable again")
    void regenResumesAfterTheStagger() {
        // "Do not regenerate while broken" has to mean "and then do", or the guard is a
        // one-way door and a player who loses a fight can end a session at zero permanently.
        GuardState empty = new GuardState(false, 3, 0, REGEN);
        GuardState staggered = empty;
        for (int i = 0; i < STAGGER; i++) {
            staggered = staggered.tick(REGEN, true);
        }
        assertEquals(0, staggered.stamina(), "nothing during the stagger");

        GuardState recovered = staggered;
        for (int i = 0; i < REGEN * 3; i++) {
            recovered = recovered.tick(REGEN, false);
        }
        assertEquals(3, recovered.stamina(), "everything after it");
    }

    @Test
    @DisplayName("a stagger and a raised guard are one rule, not two")
    void staggeredAndHeldCostTheSameThing() {
        // The method takes one flag for both, and this is why: from here they are the same
        // question, which is "can this guard be used right now".
        //
        // It used to be three parameters and two rates, which existed only to describe a
        // stagger in words other than "do not regenerate". That is what a rule written wrong
        // looks like when it is then made more precise instead of corrected.
        GuardState empty = new GuardState(true, 3, 0, REGEN);
        GuardState ticked = empty;
        for (int i = 0; i < REGEN * 4; i++) {
            ticked = ticked.tick(REGEN, true);
        }
        assertEquals(0, ticked.stamina(),
                "held and staggered are the same cost, which is why they are the same flag");
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
            ticked = ticked.tick(REGEN, false);
        }
        assertEquals(2, ticked.stamina(), "still nothing back one tick before the interval");
        ticked = ticked.tick(REGEN, false);
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
        assertEquals(0, nothing.tick(REGEN, false).stamina());
    }

    @Test
    @DisplayName("a guard with points left recovers even if it was never raised this fight")
    void regenWorksAfterLowering() {
        GuardState lowered = new GuardState(false, 3, 1, 0);
        GuardState ticked = lowered;
        for (int i = 0; i < REGEN; i++) {
            ticked = ticked.tick(REGEN, false);
        }
        assertEquals(2, ticked.stamina());
    }
}
