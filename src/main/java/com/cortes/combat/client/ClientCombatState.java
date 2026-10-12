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
    private static float specialCharge;

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

    /**
     * Whether the server has the parry window open right now, and how long until the next one.
     *
     * <p>Server-owned and never predicted, and the difference from the charge is the point. A
     * parry costs the player a press, a crawl across three quarters of a second, and five seconds
     * of lockout, so drawing a parry that the server then refused would be showing the player a
     * cost they did not pay. The blade coming out one packet late costs nothing visible, because
     * three of the window's twelve ticks are the rise anyway.</p>
     */
    private static boolean parryActive;
    private static int parryCooldown;

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

    /** Whether the server currently has the sabre's parry window open. */
    public static boolean parryActive() {
        return parryActive;
    }

    /** Ticks until the sabre can parry again, 0 when it is ready. */
    public static int parryCooldown() {
        return parryCooldown;
    }

    public static void set(float charge, float cooldown) {
        chargeRatio = charge;
        cooldownRatio = cooldown;
        cooldownKnown = true;
    }

    /**
     * La carga del boton derecho: hoy la pala, y el numero que dibuja la barra morada.
     *
     * <p><b>Se setters separado y no un tercer argumento de {@link #set},</b> porque las dos cargas
     * tienen relojes distintos: el del ataque lo mueve el izquierdo y este el derecho, asi que
     * atarlos a la misma llamada obligaria a mandar los dos juntos siempre. Y la barra necesita
     * distinguirlas: la blanca es el golpe, la morada es el arma magica.</p>
     */
    public static void setSpecialCharge(float charge) {
        specialCharge = charge;
    }

    public static float specialCharge() {
        return specialCharge;
    }

    public static void setGuard(int stamina, int max, boolean held) {
        guardStamina = stamina;
        guardMax = Math.max(1, max);
        guardHeld = held;
    }

    public static void setAiming(boolean value) {
        aiming = value;
    }

    public static void setParry(boolean active, int cooldown) {
        parryActive = active;
        parryCooldown = Math.max(0, cooldown);
    }

    /**
     * Counts the lockout down on the client, because the server only speaks on transitions.
     *
     * <p>The packet arrives twice per parry -- the full length on the press, the remainder when
     * the window closes -- and then not again for five seconds. A field that only ever holds the
     * last value it was given therefore reads a stale non-zero number for the rest of the lockout,
     * and the viewmodel's edge detector sees a "fresh" press on every frame: the collapse would
     * restart the moment it finished and the weapon would sit off line forever. That is the same
     * artifact {@code observeStagger} had, and the fix is the same shape -- the caller watches a
     * counter it advances itself, and a correction that arrives from the server wins.</p>
     */
    public static void tickParry() {
        if (parryCooldown > 0) {
            parryCooldown--;
        }
    }

    private static int lungeTicks;

    /**
     * The ropera's lunge, predicted on the press and counted down here.
     *
     * <p>Client owned, which is the difference from the parry and the reason it can be: the price
     * is the ordinary attack cooldown, and the client already has that number synced. The server
     * still runs {@code AttackCooldown.isReady} and still spends the cooldown, so a client that
     * guesses wrong loses a packet and nothing else -- the same bargain the charge makes.</p>
     */
    public static void startLunge() {
        lungeTicks = 12;
    }

    public static boolean lungeActive() {
        return lungeTicks > 0;
    }

    public static void tickLunge() {
        if (lungeTicks > 0) {
            lungeTicks--;
        }
    }
}
