package com.cortes.combat.client;

/**
 * The client's mirror of the values the HUD needs.
 *
 * <p>Written by the payload handlers and by the per-tick probe, read by the GUI layers. It is
 * display state only: nothing here ever feeds back into damage or cooldown, because the server
 * owns those. Keeping it in one small holder is what stops the layers from each keeping their
 * own copy and disagreeing.</p>
 *
 * <p>{@link #aiming()} is the one value here that is not sent by the server, and that is
 * deliberate. "Is there something in my cone" is a question about the world the client already
 * has, it changes every frame as the player turns, and sending it would be a packet per tick
 * per player to tell them something they can see. It is a hint, never a rule: the server still
 * runs the same cone test before anything is hit.</p>
 */
public final class ClientCombatState {

    private static float chargeRatio;

    /**
     * The server's answer to "may I swing", and {@code 1} until one arrives.
     *
     * <p>It defaulted to {@code 0}, and that is a player who cannot attack. {@code SwingInputHandler}
     * gates the press on this value, a press it refuses is swallowed rather than handed to
     * vanilla, and so a client whose first sync had not landed sat there refusing every click
     * with nothing on screen to explain it. The playtest log caught it exactly: nineteen seconds
     * of {@code press REFUSADO por cooldown: readyAtTick=0 tick=2113 ratio=0.0}, with
     * {@code readyAtTick} long past and the ratio never having been anything but the default.</p>
     *
     * <p>Optimistic is the only safe default here, and being optimistic is not cheating: the
     * server runs {@code AttackCooldown.consume} and the anti-replay gate in
     * {@code CombatEngine}, so a client that guesses wrong loses a packet and nothing else. A
     * pessimistic default is not the mirror of that, it is a player who cannot play until the
     * network happens to help.</p>
     */
    private static float cooldownRatio = 1.0F;

    /** Whether the server has ever told us its cooldown answer. See {@link #cooldownRatio}. */
    private static boolean cooldownKnown;

    private static int guardStamina;
    private static int guardMax = 3;
    private static boolean guardHeld;
    private static boolean aiming;

    private ClientCombatState() {
    }

    public static float chargeRatio() {
        return chargeRatio;
    }

    public static float cooldownRatio() {
        return cooldownRatio;
    }

    /**
     * Whether the server has ever sent its cooldown, and so whether the client has an answer
     * worth trusting.
     *
     * <p>The distinction matters because the value is only sent when it <em>changes</em>, so a
     * client that has not been told anything and a client that has been told "cooldown running"
     * are both holding a small number, and only one of them means the player must wait.</p>
     */
    public static boolean cooldownKnown() {
        return cooldownKnown;
    }

    public static int guardStamina() {
        return guardStamina;
    }

    public static int guardMax() {
        return guardMax;
    }

    public static boolean guardHeld() {
        return guardHeld;
    }

    /** Whether the held weapon currently has a target, or a ranged one is being drawn. */
    public static boolean aiming() {
        return aiming;
    }

    public static void set(float charge, float cooldown) {
        chargeRatio = charge;
        cooldownRatio = cooldown;
        cooldownKnown = true;
    }

    public static void setGuard(int stamina, int max, boolean held) {
        guardStamina = stamina;
        guardMax = Math.max(1, max);
        guardHeld = held;
    }

    public static void setAiming(boolean value) {
        aiming = value;
    }
}
