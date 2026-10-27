package com.cortes.combat.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que las llaves del HUD existan en las dos patas y que cada capa las pregunte de verdad.
 *
 * <p><b>La paridad de config es el guard de esta ronda.</b> Las dos patas tienen un
 * {@code ClientConfig} escrito a mano (uno {@code ModConfigSpec}, otro {@code ForgeConfigSpec}):
 * agregar una llave en una y olvidarla en la otra no rompe el build -- el cliente de esa pata
 * simplemente no tiene el ajuste, y nadie lo nota hasta que un jugador pregunta por que su
 * opcion no aparece. Es exactamente la forma de "los dos README tienen que decir lo mismo" pero
 * en codigo, y por eso se camina una lista y se pregunta en los dos archivos.</p>
 *
 * <p><b>Y la segunda mitad es la que se pagó una vez:</b> {@code cooldownBrackets} y
 * {@code guardOrbs} existian en la config de la 1.20.1 desde la ronda del HUD y <b>ninguna capa
 * las leia</b> -- dos toggles que no hacian nada. Un test de "la llave esta en el archivo" no
 * lo ve; este pregunta tambien quien la lee. (⌒‿⌒)</p>
 */
class HudConfigWiringTest {

    private static final String[] KEYS = {
            "combatCrosshair", "cooldownBrackets", "guardOrbs", "chargeBar", "crosshairSize",
            "crosshairColor", "crosshairAimColor", "bracketColor", "chargeBarColor",
            "arcaneBarColor", "crosshairShake", "hideWhenIdle", "hideIdleTicks",
            "hideCrosshairWhenIdle",
    };

    private static final Path MAIN_CONFIG = Path.of("src", "main", "java", "com", "cortes",
            "combat", "config", "ClientConfig.java");
    private static final Path V120_CONFIG = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "config", "ClientConfig.java");

    @Test
    @DisplayName("every HUD key exists on both legs")
    void everyKeyExistsOnBothLegs() {
        String main = code(MAIN_CONFIG);
        String v120 = code(V120_CONFIG);
        for (String key : KEYS) {
            assertTrue(main.contains("\"" + key + "\""),
                    MAIN_CONFIG + " no declara la llave '" + key + "'");
            assertTrue(v120.contains("\"" + key + "\""),
                    V120_CONFIG + " no declara la llave '" + key + "' -- la pata 1.20.1 se "
                            + "quedo sin el ajuste y nada avisa");
        }
    }

    @Test
    @DisplayName("the bars ask the idle clock and the crosshair asks its own toggles, in both legs")
    void theLayersAskTheToggles() {
        for (String leg : new String[]{"main", "v120"}) {
            Path brackets = layer(leg, "CooldownBracketLayer.java");
            assertTrue(code(brackets).contains("HudIdle.barsHidden()"),
                    brackets + " no consulta el reloj de inactividad: las barras se quedan para "
                            + "siempre y el pedido del dueno no existe en esa pata");
            assertTrue(code(brackets).contains("HUD_COOLDOWN_BRACKETS"),
                    brackets + " no consulta su toggle (la 1.20.1 lo tenia declarado y sin leer)");

            Path orbs = layer(leg, "GuardOrbsLayer.java");
            assertTrue(code(orbs).contains("HudIdle.barsHidden()"),
                    orbs + " no consulta el reloj: los orbes son una barra mas");
            assertTrue(code(orbs).contains("HUD_GUARD_ORBS"),
                    orbs + " no consulta su toggle");

            Path crosshair = layer(leg, "CombatCrosshairLayer.java");
            String cross = code(crosshair);
            assertTrue(cross.contains("HudIdle.crosshairHidden()"),
                    crosshair + " no consulta el ocultamiento de la crosshair");
            assertTrue(cross.contains("HUD_CHARGE_BAR"),
                    crosshair + " no consulta el toggle de la barra de carga");
            assertTrue(cross.contains("HUD_CROSSHAIR_SHAKE"),
                    crosshair + " no consulta el shake: apagarlo no hace nada");
            assertTrue(cross.contains("HudColors.stateOr("),
                    crosshair + " no usa el estado flash/aim para elegir el color");
            assertTrue(cross.contains("HudPalette.idle()") && cross.contains("HudPalette.aim()"),
                    crosshair + " no lee los dos colores de la crosshair desde el cache: el "
                            + "parseo volvio al frame");

            assertTrue(code(brackets).contains("HudPalette.bracket()"),
                    brackets + " no lee el color del bracket desde el cache");
        }
    }

    @Test
    @DisplayName("both legs feed the idle clock, with the same idea of fighting")
    void bothLegsFeedTheClock() {
        for (String leg : new String[]{"main", "v120"}) {
            Path tasks = layer(leg, "ClientTasks.java");
            String body = code(tasks);
            assertTrue(body.contains("HudIdle.onTick("),
                    tasks + " no tickea el reloj de inactividad");
            assertTrue(body.contains("ClientCombatState.aiming()"),
                    tasks + " no cuenta 'hay un objetivo' como actividad: el reloj correria "
                            + "mientras el jugador esta apuntando a un mob");
            assertTrue(body.contains("CrosshairFeedback.active()"),
                    tasks + " no cuenta el golpe: la barra se iria mientras el flash del golpe "
                            + "todavia esta en pantalla");
        }
    }

    @Test
    @DisplayName("turning our crosshair off gives vanilla back, on both legs")
    void offMeansVanilla() {
        // 1.21.1: la capa de vanilla queda VIVA debajo y el toggle decide quien pinta.
        String main = code(Path.of("src", "main", "java", "com", "cortes", "combat", "client",
                "CortesCombatClient.java"));
        assertTrue(main.contains("wrapLayer(VanillaGuiLayers.CROSSHAIR"),
                "la 1.21.1 volvio a reemplazar la capa de vanilla: con el toggle apagado no "
                        + "queda ninguna mira");
        assertFalse(main.contains("replaceLayer(VanillaGuiLayers.CROSSHAIR"),
                "la 1.21.1 usa replaceLayer: la vanilla se pierde para siempre");

        // 1.20.1: la cancelacion de la vanilla esta condicionada al toggle.
        String v120 = code(Path.of("src", "v120", "java", "com", "cortes", "combat", "client",
                "CortesCombatClient.java"));
        int cancel = v120.indexOf("hideVanillaCrosshair");
        assertTrue(cancel >= 0, "la 1.20.1 perdio el handler de la crosshair de vanilla");
        String tail = v120.substring(cancel);
        assertTrue(tail.contains("HUD_CROSSHAIR.get()"),
                "la 1.20.1 cancela la vanilla sin preguntar el toggle: apagarlo deja la "
                        + "pantalla sin mira");
    }

    private static Path layer(String leg, String file) {
        Path path = Path.of("src", leg, "java", "com", "cortes", "combat", "client", file);
        assertTrue(Files.exists(path), "falta " + path);
        return path;
    }

    /** El archivo sin comentarios: los javadocs de estas clases nombran las llaves. */
    private static String code(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            StringBuilder out = new StringBuilder(source.length());
            boolean block = false;
            for (String line : source.split("\n", -1)) {
                StringBuilder kept = new StringBuilder();
                int i = 0;
                while (i < line.length()) {
                    if (block) {
                        int end = line.indexOf("*/", i);
                        if (end < 0) {
                            i = line.length();
                        } else {
                            block = false;
                            i = end + 2;
                        }
                    } else if (line.startsWith("/*", i)) {
                        block = true;
                        i += 2;
                    } else if (line.startsWith("//", i)) {
                        i = line.length();
                    } else {
                        kept.append(line.charAt(i));
                        i++;
                    }
                }
                out.append(kept).append('\n');
            }
            return out.toString();
        } catch (IOException e) {
            throw new AssertionError("source unreadable: " + file, e);
        }
    }
}
