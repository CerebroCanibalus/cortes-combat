package com.cortes.combat.client;

/**
 * El reloj de inactividad del HUD, como dos funciones puras sobre un contador de ticks.
 *
 * <p><b>La regla, y de donde sale:</b> el dueno pidio que las barras se oculten tras ocho
 * segundos sin haber golpeado ni detectado un objetivo, con el tiempo configurable y la opcion
 * de no ocultarlas nunca. Ocho segundos es {@code 160} ticks; el porque del numero es que
 * dieciseis cooldowns de espada es "deje de pelear" y no "estoy mirando" -- y el numero sigue
 * siendo editable, que es la mitad del pedido.</p>
 *
 * <p>Puro y con el contador afuera: el llamador guarda el numero, esta clase decide. Asi el
 * borde (justo en el tick del limite) se testea sin Minecraft, que es donde esta clase de
 * off-by-one vive y donde nadie lo mira.</p>
 */
public final class HudVisibility {

    private HudVisibility() {
    }

    /**
     * El contador del proximo tick: cualquier actividad lo vuelve a cero, la inactividad suma.
     *
     * <p>Suma de a uno y sin tope a proposito: un tope seria un segundo numero que mantener, y
     * el unico consumidor es una comparacion.</p>
     */
    public static int next(int idleTicks, boolean active) {
        return active ? 0 : idleTicks + 1;
    }

    /**
     * Si el HUD esta oculto: el toggle manda, y con el encendido el numero tiene que haber
     * llegado al limite. {@code idleTicks == timeout} YA esta oculto -- el limite es el primer
     * tick oculto, no el ultimo visible, y el test lo afirma en los dos bordes.
     */
    public static boolean hidden(int idleTicks, boolean enabled, int timeoutTicks) {
        return enabled && timeoutTicks > 0 && idleTicks >= timeoutTicks;
    }
}
