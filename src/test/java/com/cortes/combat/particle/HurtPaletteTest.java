package com.cortes.combat.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.CombatFeedback;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * La paleta de la lectura de daño. Pura, así que corre en una JVM pelada.
 *
 * <p>Estos guards existen porque una tabla de colores es <b>justo</b> el tipo de dato donde un error
 * no se ve: dos colores iguales, un canal fuera de rango, o dos códigos para el mismo hecho. Ninguno
 * de los tres rompe el build ni crashea -- se ven mal en pantalla y en silencio.</p>
 */
class HurtPaletteTest {

    @Test
    void everyChannelIsInRange() {
        for (Map.Entry<String, Integer> e : all().entrySet()) {
            int rgb = e.getValue();
            assertTrue(rgb >= 0 && rgb <= 0xFFFFFF,
                    e.getKey() + " no es un color de 24 bits: " + Integer.toHexString(rgb));
        }
    }

    @Test
    void theNormalisedChannelsMatchTheHex() {
        for (Map.Entry<String, Integer> e : all().entrySet()) {
            int rgb = e.getValue();
            assertEquals(((rgb >> 16) & 0xFF) / 255.0F, HurtPalette.red(rgb), 1.0e-6F, e.getKey());
            assertEquals(((rgb >> 8) & 0xFF) / 255.0F, HurtPalette.green(rgb), 1.0e-6F, e.getKey());
            assertEquals((rgb & 0xFF) / 255.0F, HurtPalette.blue(rgb), 1.0e-6F, e.getKey());
        }
    }

    /**
     * Los cinco colores del split tienen que ser distintos entre sí.
     *
     * <p>Es el eje que el jugador va a leer <b>de un vistazo y sin contar</b>: si "lo paró la
     * armadura" y "lo frenó una poción" fueran el mismo azul, la mitad de la información del diseño
     * desaparecería sin que nada fallara.</p>
     */
    @Test
    void theFiveSplitColoursAreAllDifferent() {
        Map<String, Integer> split = new LinkedHashMap<>();
        split.put("dealt", HurtPalette.DEALT);
        split.put("pure", HurtPalette.PURE);
        split.put("armour", HurtPalette.ARMOUR);
        split.put("effect", HurtPalette.EFFECT);
        split.put("guard", HurtPalette.GUARD);
        assertDistinct(split);
    }

    /**
     * <b>El escudo no puede tener dos colores.</b>
     *
     * <p>{@code CombatFeedback.GUARDED} ya tiñe el crosshair cuando un golpe se bloquea. Si los
     * puntos usaran otro azul gris, serían dos códigos para el mismo hecho -- y el jugador tendría
     * que aprender los dos. Este guard ata las dos tablas, que viven en paquetes distintos.</p>
     */
    @Test
    void theGuardColourIsTheOneTheCrosshairAlreadyUses() {
        assertEquals(CombatFeedback.GUARDED.rgb(), HurtPalette.GUARD,
                "los puntos y el crosshair dicen cosas distintas sobre el mismo bloqueo");
    }

    /**
     * El martillo comparte la familia del "puro", a propósito.
     *
     * <p>{@code warhammer_ignored_armour} <b>es</b> un daño que atraviesa, así que darle un color
     * propio diría que es otra cosa. La aserción fija la decisión: si alguien le pone un blanco
     * distinto "para que se note", el test le contesta por qué no.</p>
     */
    @Test
    void theWarhammerSharesThePureFamily() {
        assertEquals(HurtPalette.PURE, HurtPalette.WARHAMMER);
    }

    /** Y el hacha cargada NO lo comparte: conserva la armadura, así que no es pura. */
    @Test
    void theChargedAxeIsNotPure() {
        assertTrue(HurtPalette.AXE_CHARGED != HurtPalette.PURE,
                "el hacha cargada no atraviesa la armadura: no puede leerse como daño puro");
    }

    private static void assertDistinct(Map<String, Integer> colours) {
        for (Map.Entry<String, Integer> a : colours.entrySet()) {
            for (Map.Entry<String, Integer> b : colours.entrySet()) {
                if (a.getKey().equals(b.getKey())) {
                    continue;
                }
                assertTrue(!a.getValue().equals(b.getValue()),
                        "'" + a.getKey() + "' y '" + b.getKey() + "' comparten el color "
                                + Integer.toHexString(a.getValue()) + ": son dos hechos distintos");
            }
        }
    }

    private static Map<String, Integer> all() {
        Map<String, Integer> all = new LinkedHashMap<>();
        all.put("DEALT", HurtPalette.DEALT);
        all.put("PURE", HurtPalette.PURE);
        all.put("ARMOUR", HurtPalette.ARMOUR);
        all.put("EFFECT", HurtPalette.EFFECT);
        all.put("GUARD", HurtPalette.GUARD);
        all.put("MELEE", HurtPalette.MELEE);
        all.put("PROJECTILE", HurtPalette.PROJECTILE);
        all.put("FIRE", HurtPalette.FIRE);
        all.put("EXPLOSION", HurtPalette.EXPLOSION);
        all.put("MAGIC", HurtPalette.MAGIC);
        all.put("WITHER", HurtPalette.WITHER);
        all.put("STARVE", HurtPalette.STARVE);
        all.put("FREEZE", HurtPalette.FREEZE);
        all.put("IMPACT", HurtPalette.IMPACT);
        all.put("SUFFOCATE", HurtPalette.SUFFOCATE);
        all.put("THORNS", HurtPalette.THORNS);
        all.put("LIGHTNING", HurtPalette.LIGHTNING);
        all.put("VOID", HurtPalette.VOID);
        all.put("POISON", HurtPalette.POISON);
        all.put("AXE_CHARGED", HurtPalette.AXE_CHARGED);
        all.put("WARHAMMER", HurtPalette.WARHAMMER);
        return all;
    }
}
