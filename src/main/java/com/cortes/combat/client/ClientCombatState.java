package com.cortes.combat.client;

/**
 * The client's mirror of the two server-owned HUD values.
 *
 * <p>Written by {@code CombatStatePayload} and read by the GUI layers. It is display state
 * only: nothing here ever feeds back into damage or cooldown, because the server owns
 * those. Keeping it in one small holder is what stops the layers from each keeping their own
 * copy and disagreeing.</p>
 */
public final class ClientCombatState {

    private static float chargeRatio;
    private static float cooldownRatio;
    private static int guardStamina;
    private static int guardMax = 3;
    private static boolean guardHeld;

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

    public static void set(float charge, float cooldown) {
        chargeRatio = charge;
        cooldownRatio = cooldown;
    }

    public static void setGuard(int stamina, int max, boolean held) {
        guardStamina = stamina;
        guardMax = Math.max(1, max);
        guardHeld = held;
    }
}
