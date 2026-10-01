package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a state opened by a button cannot outlive it.
 *
 * <p>This is the bug a friend's playtest found, and it is the fourth bug in this project with the
 * same shape. The guard's {@code held} survived a stagger, a death and a login. A charge survived
 * a disconnect. A double click opened two swings inside one tick. And then: <b>GLFW delivers no
 * release event when the window is deactivated with a button held</b>, so a press opened a swing,
 * the release never came, and the flag stayed true for the rest of the session.</p>
 *
 * <p>From that moment the player could not attack, could not break blocks and could not interact.
 * Fifteen minutes of server trace showed the presses arriving normally and then stopping dead,
 * and the log had not one client line -- because the only line that says <em>which gate
 * refused</em> is behind a trace flag that session did not have.</p>
 *
 * <p>These tests are on {@link InputLiveness} and not on the handlers because that is the only
 * part that can be tested at all. The handlers do two things: ask GLFW, and apply the answer. The
 * first needs a window and the second is the interesting half being pinned here. A test that
 * pretended to cover the GLFW call would be a test of its own mock.</p>
 */
class InputLivenessTest {

    @Test
    @DisplayName("an open swing with the button gone is closed")
    void lostReleaseClosesTheSwing() {
        assertTrue(InputLiveness.shouldCloseSwing(true, false),
                "a swing whose button is no longer down must close: the release never arrived");
    }

    @Test
    @DisplayName("a swing that is legitimately held stays open")
    void heldSwingStaysOpen() {
        // The guard matters as much as the fix. Without it the reconcile would close every swing
        // on every frame it was properly held, and a charged attack would be impossible.
        assertFalse(InputLiveness.shouldCloseSwing(true, true),
                "a held swing must not be closed, or charging would never work");
    }

    @Test
    @DisplayName("a closed swing is left alone either way")
    void noSwingIsNeverTouched() {
        assertFalse(InputLiveness.shouldCloseSwing(false, false));
        assertFalse(InputLiveness.shouldCloseSwing(false, true));
    }

    @Test
    @DisplayName("the guard follows the same rule on the other button")
    void theGuardToo() {
        assertTrue(InputLiveness.shouldLowerGuard(true, false),
                "a guard whose button is gone must come down: a stuck one eats the attack press");
        assertFalse(InputLiveness.shouldLowerGuard(true, true),
                "a held guard must stay up");
        assertFalse(InputLiveness.shouldLowerGuard(false, false));
        assertFalse(InputLiveness.shouldLowerGuard(false, true));
    }

    @Test
    @DisplayName("both questions agree on every combination")
    void theTwoAreTheSameQuestion() {
        // They are literally the same question on two buttons, and they were written twice. Pinning
        // that they agree means a future change to one cannot quietly drift from the other -- which
        // is how the swing got fixed and the guard was left broken, once already.
        for (boolean open : new boolean[]{false, true}) {
            for (boolean down : new boolean[]{false, true}) {
                assertTrue(InputLiveness.shouldCloseSwing(open, down)
                                == InputLiveness.shouldLowerGuard(open, down),
                        "the swing and the guard disagreed at open=" + open + " down=" + down);
            }
        }
    }

    @Test
    @DisplayName("a missing window answers not-down, which is the safe direction")
    void aGoneWindowClosesRatherThanLatches() {
        // The handle is null on the title screen and after the window closes. If that answered
        // "down", a swing opened as the game closed would latch forever, which is the bug again in
        // a different costume.
        assertFalse(InputLiveness.buttonIsDown(false, true),
                "no window must read as not-down so nothing stays latched to it");
        assertTrue(InputLiveness.buttonIsDown(true, true));
        assertFalse(InputLiveness.buttonIsDown(true, false));
    }
}
