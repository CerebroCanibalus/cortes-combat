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
    private static float cooldownRatio;
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
