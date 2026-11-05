package com.cortes.combat.client;

import com.cortes.combat.config.ClientConfig;

/**
 * El estado de la HUD: el reloj de inactividad y el fade que lo acompaña.
 *
 * <p><b>Display state de una sesion, como {@link CrosshairFeedback} y {@link ClientCombatState}.</b>
 * La regla de "nada de {@code static} mutable" es de gameplay: esto no decide un golpe, decide
 * si se dibuja un bracket, y nunca viaja al servidor.</p>
 *
 * <h2>Que cuenta como actividad</h2>
 *
 * <p><b>Solo la intencion de pelear: un objetivo en el cono, o una carga en curso.</b> Lo que
 * <em>pasa</em> -- un golpe que aterriza, el boton sostenido -- <b>no</b> cuenta, y es un
 * cambio pedido por el dueno: el HUD no debe reaparecer por golpear ni por picar un bloque.
 * Un HUD que vuelve con cada golpe es un HUD que esta siempre en pantalla, que es justo lo
 * que el ocultamiento por inactividad vino a resolver.</p>
 *
 * <p>La carga entra porque es el jugador apretando con la mira puesta: ocultarle la barra que
 * esta llenando seria esconderle lo que esta haciendo. Lo que NO cuenta: girar la vista,
 * caminar o tener un arma en la mano.</p>
 *
 * <h2>Y el fade, que es la mitad del pedido</h2>
 *
 * <p>El HUD <b>se desvanece</b> en vez de desaparecer de golpe: un elemento que se apaga de
 * un frame al otro se lee como un fallo de dibujo, y el jugador pierde la referencia de donde
 * estaba. El estado del fade son cuatro numeros (de donde, a donde, cuando empezo, cuanto
 * dura) y la cuenta vive en {@link HudFade}, que es pura y por lo tanto testeable.</p>
 *
 * <p><b>El fade se calcula por reloj, no por frame.</b> Si dos capas lo avanzaran cada una por
 * su cuenta, la velocidad seria el doble; y si lo avanzara el tick del cliente (veinte por
 * segundo) el fade tendria veinte escalones y se veria a saltos. Por eso el estado guarda el
 * instante del comienzo y cada capa pregunta "que alpha hay ahora", que es la misma respuesta
 * para todas.</p>
 */
public final class HudIdle {

    private static int idleTicks;

    /** Si el HUD esta oculto de verdad, segun el reloj y la config. */
    private static boolean hidden;

    /** El alpha al comienzo del tramo de fade. */
    private static float fadeFrom = 1.0F;

    /** El alpha al final del tramo de fade. */
    private static float fadeTo = 1.0F;

    /** Cuando empezo el tramo, en milisegundos de reloj. */
    private static long fadeStartMillis;

    /** Cuanto dura el tramo actual. */
    private static int fadeMillis = HudFade.OUT_MILLIS;

    private HudIdle() {
    }

    /**
     * Un tick de cliente con su veredicto de actividad.
     *
     * <p><b>El cambio de estado del fade pasa por aca</b>, y no en la lectura: la lectura se
     * hace por frame y desde varias capas, y un cambio de estado que dependa de cuantas veces
     * se pregunto es un estado que se rompe.</p>
     *
     * @param fighting there is a target in reach, or a charge is being held
     */
    public static void onTick(boolean fighting) {
        idleTicks = HudVisibility.next(idleTicks, fighting);
        boolean now = HudVisibility.hidden(idleTicks, ClientConfig.HUD_HIDE_WHEN_IDLE.get(),
                ClientConfig.hideIdleTicks());
        if (now == hidden) {
            return;
        }
        hidden = now;
        // **El fade arranca desde donde esta el HUD ahora**, no desde 1: si el jugador estaba
        // a mitad de un fade y el estado cambia otra vez, el nuevo tramo continua desde ahi en
        // vez de dar un salto.
        fadeFrom = visibility();
        fadeTo = now ? 0.0F : 1.0F;
        fadeMillis = now ? HudFade.OUT_MILLIS : HudFade.IN_MILLIS;
        fadeStartMillis = net.minecraft.Util.getMillis();
    }

    /**
     * Si las barras (brackets, barra de carga y orbes de aguante) estan ocultas ahora.
     *
     * <p>El estado logico, sin el fade: "el HUD esta oculto" es una pregunta de si/no y esta
     * es la respuesta. Lo que se dibuja es {@link #visibility()}.</p>
     */
    public static boolean barsHidden() {
        return hidden;
    }

    /**
     * La visibilidad de las barras, de 0 a 1, con el fade adentro.
     *
     * <p><b>Es lo que dibujan las capas</b>, y no un booleano: con un booleano el HUD
     * desaparece de golpe, y el fade que se pidio es justamente lo que evita eso.</p>
     */
    public static float visibility() {
        return HudFade.alpha(fadeFrom, fadeTo,
                net.minecraft.Util.getMillis() - fadeStartMillis, fadeMillis);
    }

    /**
     * La visibilidad de la crosshair, que depende de si el ocultamiento tambien se la lleva.
     *
     * <p><b>El toggle manda.</b> Con {@code hideCrosshairWhenIdle} apagado, la crosshair no se
     * va nunca -- es la mira, y una mira que se apaga sola es una mira que falta -- y por eso
     * ignora el fade de las barras.</p>
     */
    public static float crosshairVisibility() {
        return ClientConfig.HUD_HIDE_CROSSHAIR_WHEN_IDLE.get() ? visibility() : 1.0F;
    }

    /** Ticks de inactividad, para el diagnostico y los tests. */
    static int idleTicks() {
        return idleTicks;
    }

    /** Test hook: HUD limpio, barras como recien abiertas. */
    public static void reset() {
        idleTicks = 0;
        hidden = false;
        fadeFrom = 1.0F;
        fadeTo = 1.0F;
        fadeStartMillis = 0L;
        fadeMillis = HudFade.OUT_MILLIS;
    }
}
