package com.cortes.combat.client;

import com.cortes.combat.config.ClientConfig;

/**
 * Los cinco colores del HUD, parseados una vez y re-parseados solo cuando el texto cambia.
 *
 * <h2>Por que existe, con el numero en la mano</h2>
 *
 * <p>{@code ConfigValue.get()} ya es barato -- verificado en las fuentes de las dos patas:
 * devuelve un campo cacheado, sin parseo, sin I/O y sin locks ({@code USE_CACHES = true} en
 * Forge 1.20.1; {@code if (cachedValue == null) cachedValue = getRaw();} en NeoForge 21.1.250).
 * <b>Lo caro era el parseo del color que escribi yo:</b> medido a 14,6 ns por llamada
 * (20.000.000 de iteraciones, JIT caliente), y cinco por frame son ~73 ns por frame y ~15
 * objetos chicos que no hacen falta.</p>
 *
 * <p><b>73 ns no es lag, y decirlo importa.</b> Un frame a 240 fps dura 4.166.666 ns; cinco
 * parseos son el <b>0,0018 %</b> de eso. Lo que este cache compra no es rendimiento percibible,
 * es que la pregunta "esto corre por frame" no tenga una respuesta con asterisco: con esto el
 * costo por frame es una comparacion de strings y cinco lecturas de campo, y la asignacion de
 * basura desaparece.</p>
 *
 * <p><b>La invalidacion es la comparacion del texto crudo</b>, y no un listener de config: un
 * {@code /reload} de config reemplaza el valor, el texto cambia, el color se re-parsea. Un
 * listener seria otra pieza que puede no llamarse -- la forma exacta de bug que este proyecto
 * pagó cuatro veces.</p>
 */
public final class HudPalette {

    /** Un color parseado y el texto del que salio. */
    public record Slot(String raw, int rgb) {
    }

    private static Slot idle;
    private static Slot aim;
    private static Slot bracket;
    private static Slot charge;
    private static Slot arcane;

    private HudPalette() {
    }

    /** El tinte de reposo de la crosshair. */
    public static int idle() {
        idle = refresh(idle, ClientConfig.HUD_CROSSHAIR_COLOR.get(), HudColors.WHITE);
        return idle.rgb();
    }

    /** El tinte de la crosshair con un objetivo en el cono. */
    public static int aim() {
        aim = refresh(aim, ClientConfig.HUD_CROSSHAIR_AIM_COLOR.get(), HudColors.WHITE);
        return aim.rgb();
    }

    /** El tinte de reposo de los brackets. */
    public static int bracket() {
        bracket = refresh(bracket, ClientConfig.HUD_BRACKET_COLOR.get(), HudColors.WHITE);
        return bracket.rgb();
    }

    /** El tinte de la barra del golpe cargado. */
    public static int charge() {
        charge = refresh(charge, ClientConfig.HUD_CHARGE_BAR_COLOR.get(), 0xFFFFFF);
        return charge.rgb();
    }

    /** El tinte de la barra arcana. */
    public static int arcane() {
        arcane = refresh(arcane, ClientConfig.HUD_ARCANE_BAR_COLOR.get(), 0xC06BFF);
        return arcane.rgb();
    }

    /**
     * La regla del cache, pura y testeable: mismo texto -> mismo slot, sin volver a parsear;
     * texto distinto o ausente -> uno nuevo.
     */
    public static Slot refresh(Slot slot, String raw, int fallback) {
        if (slot != null && slot.raw() != null && slot.raw().equals(raw)) {
            return slot;
        }
        return new Slot(raw, HudColors.parse(raw, fallback));
    }

    /** Test hook: descarta los cinco, como al arrancar. */
    public static void reset() {
        idle = null;
        aim = null;
        bracket = null;
        charge = null;
        arcane = null;
    }
}
