package com.cortes.combat.client;

import com.cortes.combat.config.ClientConfig;

/**
 * El contador de inactividad del HUD: quien guarda el numero y le pregunta a la config.
 *
 * <p><b>Display state de una sesion, como {@link CrosshairFeedback} y {@link ClientCombatState}.</b>
 * La regla de "nada de {@code static} mutable" es de gameplay: esto no decide un golpe, decide
 * si se dibuja un bracket, y nunca viaja al servidor.</p>
 *
 * <p><b>Que cuenta como actividad:</b> un objetivo en el cono, un golpe que acaba de aterrizar
 * (el flash de {@link CrosshairFeedback} lo prueba) o una carga en curso. La carga entra porque
 * es el jugador apretando el boton con la mira puesta -- ocultarle la barra que esta llenando
 * seria esconderle lo que esta haciendo. Lo que NO cuenta: girar la vista, caminar o tener un
 * arma en la mano; si contara, las barras nunca se irian y el pedido seria decorativo.</p>
 */
public final class HudIdle {

    private static int idleTicks;

    private HudIdle() {
    }

    /**
     * Un tick de cliente con su veredicto de actividad.
     *
     * @param fighting there is a target in reach, a hit just landed, or a charge is being held
     */
    public static void onTick(boolean fighting) {
        idleTicks = HudVisibility.next(idleTicks, fighting);
    }

    /**
     * Si las barras (brackets, barra de carga y orbes de aguante) estan ocultas ahora.
     *
     * <p>Se pregunta por frame y no se cachea: la config se puede cambiar en vivo y el numero es
     * una comparacion.</p>
     */
    public static boolean barsHidden() {
        return HudVisibility.hidden(idleTicks, ClientConfig.HUD_HIDE_WHEN_IDLE.get(),
                ClientConfig.hideIdleTicks());
    }

    /** Si el ocultamiento por inactividad tambien se lleva la crosshair. */
    public static boolean crosshairHidden() {
        return barsHidden() && ClientConfig.HUD_HIDE_CROSSHAIR_WHEN_IDLE.get();
    }

    /** Ticks de inactividad, para el diagnostico y los tests. */
    static int idleTicks() {
        return idleTicks;
    }

    /** Test hook: HUD limpio, barras como recien abiertas. */
    public static void reset() {
        idleTicks = 0;
    }
}
