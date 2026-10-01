package com.cortes.combat.client;

/**
 * Whether a button that opened a state still exists.
 *
 * <p>Two flags, two buttons, one question, and it exists because this project has now lost a
 * playtest to the same shape four times.</p>
 *
 * <p><b>The bug.</b> GLFW does not deliver a release event when the window is deactivated with a
 * button held. A press opened a swing, the release never arrived, and the flag stayed true for
 * the rest of the session: the player could not attack, could not break blocks and could not
 * interact, with nothing in the log. The log said the presses stopped arriving and nothing about
 * why, because the only line that says <em>which gate refused</em> is a client line that session
 * did not have.</p>
 *
 * <p><b>Why it is pure.</b> The hard part is that the input event stream is not trustworthy, and
 * that cannot be tested without a window. So the decision -- open state, button state, close it or
 * not -- is here with no Minecraft and no GLFW in the signature, and the handlers do nothing but
 * read the button and ask. The same reason {@code GuardRules} and {@code ConeMath} are pure, and
 * the same reason the project has twice preferred extracting a decision over testing around it.</p>
 *
 * <p>The four bugs this generalises, all one mistake: a state that outlived the button that
 * created it. The guard's {@code held} survived a stagger, a death and a login. A charge survived
 * a disconnect. A double click opened two swings in one tick. And now a focus loss with the button
 * down left a swing open forever. In every case the state was correct when it was set and nothing
 * ever questioned it afterwards.</p>
 */
public final class InputLiveness {

    private InputLiveness() {
    }

    /**
     * Whether an open swing has to be closed because its button is gone.
     *
     * <p>Closed as an <em>abandon</em> and never as a release: the player let go some time ago and
     * this is the first tick that noticed, so resolving the swing would land a charged hit against
     * whatever happens to be in the crosshair now. Nothing is spent, and nothing is sent.</p>
     *
     * @param swingOpen whether a swing is currently open
     * @param buttonDown what the window says about the button, right now
     */
    public static boolean shouldCloseSwing(boolean swingOpen, boolean buttonDown) {
        return swingOpen && !buttonDown;
    }

    /**
     * Whether a raised guard has to come down because its button is gone.
     *
     * <p>Same question and the same answer as the swing, and it matters more, because a guard
     * flag left raised refuses the attack press as well as the item use. The refusal is silent on
     * purpose, so a stuck flag eats the whole left button with nothing on screen to explain it.</p>
     *
     * @param guardUp whether the guard is currently predicted up
     * @param buttonDown what the window says about the button, right now
     */
    public static boolean shouldLowerGuard(boolean guardUp, boolean buttonDown) {
        return guardUp && !buttonDown;
    }

    /**
     * Whether the window can be asked at all.
     *
     * <p>The handle is null on the title screen and after the window closes. A null handle answers
     * "the button is not down", which is the safe direction for both questions above: it closes a
     * swing and drops a guard rather than leaving either one latched to a window that is gone.</p>
     */
    public static boolean buttonIsDown(boolean windowExists, boolean rawButtonState) {
        return windowExists && rawButtonState;
    }
}
