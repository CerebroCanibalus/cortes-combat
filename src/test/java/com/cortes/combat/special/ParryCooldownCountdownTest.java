package com.cortes.combat.special;

import com.cortes.combat.state.ParryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Five seconds, walked through the code the game actually calls.
 *
 * <p>This exists because the first version of it did not, and the bug it would have caught took
 * one minute of playtesting to find: the ticker asked "is the window open?" and returned early
 * when it was not, which is a reasonable question to ask about a <em>packet</em> and silently
 * also answered it for the <em>countdown</em>. The cooldown froze at one hundred and the sabre
 * could be parried once per session.</p>
 *
 * <p>{@code ParryState#tick} was never wrong, and a test on it was green the whole time. The
 * wrong thing was the question being asked of it, which is why the question now lives inside the
 * record and this test drives the same entry point the ticker does.</p>
 */
class ParryCooldownCountdownTest {

    /** A hundred ticks, walked the way the server walks them. */
    private static ParryState walk(ParryState start, int ticks, java.util.List<Integer> tells) {
        ParryState state = start;
        for (int i = 0; i < ticks; i++) {
            ParryState.Step step = state.step();
            state = step.state();
            if (step.tellClient()) {
                tells.add(i);
            }
        }
        return state;
    }

    @Test
    @DisplayName("the lockout actually runs down, and the sabre can parry again")
    void cooldownFinishes() {
        var tells = new java.util.ArrayList<Integer>();
        ParryState state = ParryState.READY.activate();

        assertFalse(state.canActivate(), "straight after the press it is not ready");
        state = walk(state, ParryState.COOLDOWN_TICKS - 1, tells);

        assertFalse(state.canActivate(),
                "one tick short of five seconds the sabre is still locked, which is what the "
                        + "blade off line is telling the player");
        assertEquals(1, state.cooldownTicks(), "and the server's own number says so");

        state = walk(state, 1, tells);
        assertTrue(state.canActivate(),
                "five seconds later it can parry again. This is the assertion that was false in "
                        + "the game and true in every test, because the tests ticked the record "
                        + "and the game ticked the record through a guard that stopped it");
    }

    @Test
    @DisplayName("a parry that connects costs the same five seconds as one that whiffs")
    void bothCostTheSame() {
        var a = new java.util.ArrayList<Integer>();
        var b = new java.util.ArrayList<Integer>();

        ParryState missed = walk(ParryState.READY.activate(), ParryState.COOLDOWN_TICKS, a);
        ParryState hit = walk(ParryState.READY.activate().consume(), ParryState.COOLDOWN_TICKS, b);

        assertEquals(0, missed.cooldownTicks());
        assertEquals(0, hit.cooldownTicks(),
                "consume() must not have charged a second five seconds, or a parry that connects "
                        + "would cost ten and the lockout would be a punishment for succeeding");
    }

    @Test
    @DisplayName("the client is told once, on the tick the window closes")
    void tellsOnce() {
        var tells = new java.util.ArrayList<Integer>();
        walk(ParryState.READY.activate(), 400, tells);

        assertEquals(1, tells.size(),
                "one packet, not one per tick for five seconds. Told at tick "
                        + tells + " of a 100 tick lockout");
        // Index 11 is the twelfth tick. The window opens at 12 and the twelfth tick is the one
        // that takes it to 0, so the packet goes out on that tick and not the eleventh.
        assertEquals(ParryState.WINDOW_TICKS - 1, tells.get(0),
                "and it is the tick the blade comes back, which is the tick the player can act");
    }

    @Test
    @DisplayName("a parry sitting ready and idle does nothing at all")
    void idleIsQuiet() {
        var tells = new java.util.ArrayList<Integer>();
        ParryState state = walk(ParryState.READY, 1000, tells);

        assertEquals(ParryState.READY, state);
        assertTrue(tells.isEmpty(),
                "a player who has never parried gets a hundred ticks of silence, which is the "
                        + "cost of getting this wrong in the other direction");
    }

    @Test
    @DisplayName("and a press inside the window is still refused after a whole lockout's worth of ticks")
    void stillNotHoldable() {
        ParryState state = ParryState.READY.activate();
        var tells = new java.util.ArrayList<Integer>();
        state = walk(state, 5, tells);

        assertTrue(state.isParrying());
        assertFalse(state.canActivate(),
                "five ticks into a twelve tick window, a press must do nothing");
    }
}
