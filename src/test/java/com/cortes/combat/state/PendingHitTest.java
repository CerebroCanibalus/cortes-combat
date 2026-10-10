package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a pending hit is an intention with an expiry, and where the window's edge falls.
 *
 * <p>The boundary test is the load-bearing one. The window's whole claim is "five ticks of
 * trying", and the code that decides it is one comparison: {@link PendingHit#expiredAt} runs
 * <b>after</b> the cone re-check on the same tick, so {@code >=} versus {@code >} is the
 * difference between five attempts and six -- and a window that silently grows is a hit landing
 * later than any feedback the player ever saw for it. The other half, {@link #none}, is what
 * makes {@code targetId == 0} mean "nothing waiting" everywhere the state is cleared, and a
 * record that answered otherwise would leave every swing in the world permanently pending.</p>
 */
class PendingHitTest {

    @Test
    @DisplayName("none is the resting value, and anything with a target is not none")
    void noneIsTheRestingValue() {
        assertTrue(PendingHit.NONE.none(), "the resting value holds nothing");

        assertFalse(new PendingHit(7, 0.5F, "minecraft:iron_sword", 100L).none(),
                "a real intention is not none -- clearing on a false none would cancel every "
                        + "window the moment it opened, silently returning the instant-miss "
                        + "behaviour this whole class exists to replace");

        assertTrue(new PendingHit(0, 1.0F, "minecraft:iron_sword", 100L).none(),
                "target id zero is NO_TARGET regardless of the other fields, because that is "
                        + "the same sentinel the swing payload sends for a swing at nothing");
    }

    @Test
    @DisplayName("the window closes ON the due tick: last attempt first, then give up")
    void expiryBoundary() {
        PendingHit waiting = new PendingHit(7, 0.5F, "minecraft:iron_sword", 100L);

        assertFalse(waiting.expiredAt(99L), "one tick before the due tick: still trying");
        assertTrue(waiting.expiredAt(100L), "on the due tick: this tick's attempt is the last, "
                + "and after it the window is closed (expiredAt runs after the cone check)");
        assertTrue(waiting.expiredAt(101L), "past the due tick: closed");
    }
}
