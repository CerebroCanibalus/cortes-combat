package com.cortes.combat.attack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a refused press is remembered for exactly the window that makes it forgiveness and not
 * a second swing.
 */
class PressBufferTest {

    private static final long PRESS = 1000L;

    /** One tick inside the window is the whole point of the feature. */
    @Test
    @DisplayName("a press from the same tick and the next is still alive")
    void insideTheWindowItLives() {
        assertFalse(PressBuffer.expired(PRESS, PRESS), "same tick");
        assertFalse(PressBuffer.expired(PRESS, PRESS + PressBuffer.WINDOW_TICKS),
                "**the window's own edge is alive, not expired.** An explicit boundary: a press "
                        + "one tick before the cooldown ends must fire, which is the reported bug "
                        + "if the edge is written as >=");
    }

    /** And the very next tick past the window kills it. */
    @Test
    @DisplayName("one tick past the window it is gone")
    void pastTheWindowItDies() {
        assertTrue(PressBuffer.expired(PRESS, PRESS + PressBuffer.WINDOW_TICKS + 1),
                "**a press made three ticks too early never fires later.** This is the "
                        + "'muy cortito' rule: anything wider turns an accidental click on a "
                        + "raised guard into a swing the player never saw coming");
    }

    /** The fire re-asks the gate instead of inheriting it. */
    @Test
    @DisplayName("the fire re-asks the same gates a live press obeys")
    void theFireReAsksTheGate() {
        long inside = PRESS + 1;
        assertTrue(PressBuffer.shouldFire(PRESS, inside, true),
                "gate open and alive: fires, which is the whole bug");
        assertFalse(PressBuffer.shouldFire(PRESS, inside, false),
                "**a parked press never fires against a closed gate.** The window forgives "
                        + "timing, and timing only: the state that refused the press in the "
                        + "first place is still the law even two ticks later");
        assertFalse(PressBuffer.shouldFire(PRESS, PRESS + PressBuffer.WINDOW_TICKS + 1, true),
                "and past the window it does not fire even with everything open");
    }

    /** The sentinel is distinct from any real tick. */
    @Test
    @DisplayName("empty is negative, so tick zero cannot be confused with a parked press")
    void emptyIsNotATick() {
        assertTrue(PressBuffer.EMPTY < 0,
                "**a sentinel of zero would swallow the first tick of the world.** A press at "
                        + "tick zero that met EMPTY == 0 would read as pre-parked, and nothing "
                        + "would ever say so");
        assertFalse(PressBuffer.expired(PressBuffer.EMPTY, 0),
                "and the EMPTY sentinel itself must never be asked as a press");
    }
}
