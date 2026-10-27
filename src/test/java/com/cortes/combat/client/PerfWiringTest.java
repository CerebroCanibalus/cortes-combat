package com.cortes.combat.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las guardas de la auditoria de rendimiento del 2026-10-27: cada hallazgo que se arreglo queda
 * clavado, porque cada uno era invisible desde el juego.
 *
 * <p>La forma del bug en los cuatro casos es la misma: <b>algo que existe y nadie usa</b> -- tres
 * {@code clear()} sin llamador, una {@code WeaponStats.of} de mas por frame, una id de item que se
 * alocaba en cada consulta, y trazas de playtest que logueaban en el camino del combate para
 * siempre. Ninguno tira un error; todos se leen como "anda bien". (⌒‿⌒)</p>
 */
class PerfWiringTest {

    private static final Path V120_ROOT = Path.of("src", "v120", "java", "com", "cortes",
            "combat");
    private static final Path MAIN_ROOT = Path.of("src", "main", "java", "com", "cortes",
            "combat");

    @Test
    @DisplayName("the orphan clears have a caller now, in both legs")
    void theOrphanClearsHaveACaller() {
        String cleanup = code(MAIN_ROOT.resolve("client/ClientWorldCleanup.java"));
        for (String clear : new String[]{"CameraShake.clear()", "ReactorShake.clear()",
                "RemotePoseStates.clear()", "HudIdle.reset()", "CrosshairFeedback.reset()",
                "CombatAim.reset()"}) {
            assertTrue(cleanup.contains(clear),
                    "ClientWorldCleanup no llama " + clear + ": el estado del mundo viejo "
                            + "sobrevive al cambio de mundo");
        }
        for (Path client : new Path[]{MAIN_ROOT.resolve("client/CortesCombatClient.java"),
                V120_ROOT.resolve("client/CortesCombatClient.java")}) {
            assertTrue(code(client).contains("ClientWorldCleanup::onLevelUnload"),
                    client + " no registra el cleanup: las clear() vuelven a quedar huerfanas");
        }
    }

    @Test
    @DisplayName("the item id is not minted per resolve")
    void theItemIdIsCached() {
        String store = code(MAIN_ROOT.resolve("weaponpack/WeaponPackStore.java"));
        assertTrue(store.contains("ITEM_IDS.computeIfAbsent("),
                "WeaponPackStore volvio a hacer getKey(item).toString() por consulta: una "
                        + "asignacion por cada WeaponStats.of, y of() corre por frame en el HUD");
        assertFalse(store.contains("getKey(stack.getItem()).toString()"),
                "la consulta directa volvio: el cache esta de adorno");
    }

    @Test
    @DisplayName("the bracket layer resolves the weapon once per frame")
    void theBracketLayerResolvesOnce() {
        for (Path root : new Path[]{MAIN_ROOT, V120_ROOT}) {
            Path layer = root.resolve("client/CooldownBracketLayer.java");
            String body = code(layer);
            int calls = body.split("WeaponStats\\.of\\(", -1).length - 1;
            assertEquals(1, calls,
                    layer + " resuelve el arma " + calls + " veces por frame: se pide una vez y "
                            + "se reusa");
        }
    }

    @Test
    @DisplayName("every leftover playtest trace is behind the debug switch")
    void theLeftoverTracesAreGated() {
        record Site(Path file, String needle, String guard) {
        }
        Site[] sites = {
                new Site(V120_ROOT.resolve("guard/GuardHandler.java"), "[guardia] golpe",
                        "ServerConfig.debug()"),
                new Site(V120_ROOT.resolve("guard/GuardHandler.java"), "[guardia] mis-swing",
                        "ServerConfig.debug()"),
                new Site(V120_ROOT.resolve("guard/GuardHandler.java"), "[guardia] cobro",
                        "ServerConfig.debug()"),
                new Site(MAIN_ROOT.resolve("particle/CurarOnHeal.java"), "[curar]",
                        "ServerConfig.debug()"),
                new Site(MAIN_ROOT.resolve("special/SpecialHandler.java"), "[pala]",
                        "ServerConfig.debug()"),
                new Site(MAIN_ROOT.resolve("special/SpecialCharges.java"), "[carga]",
                        "ServerConfig.debug()"),
                new Site(MAIN_ROOT.resolve("enchant/GoldEnchantmentHandler.java"), "[frenzy]",
                        "ServerConfig.debug()"),
                new Site(MAIN_ROOT.resolve("mixin/LivingEntityOverlayMixin.java"), "[flash]",
                        "ClientConfig.DEBUG.get()"),
                new Site(MAIN_ROOT.resolve("mixin/LivingEntityRenderMixin.java"), "[flash/bar]",
                        "ClientConfig.DEBUG.get()"),
        };
        for (Site site : sites) {
            String body = code(site.file());
            int at = body.indexOf(site.needle());
            assertTrue(at >= 0, site.file() + " no tiene la traza " + site.needle());
            String before = body.substring(Math.max(0, at - 260), at);
            assertTrue(before.contains(site.guard()),
                    site.file() + " loguea '" + site.needle() + "' sin preguntar " + site.guard()
                            + ": una linea de log en el camino del combate, en cada golpe");
        }
    }

    /** El archivo sin comentarios: estas lineas de traza se nombran en sus propios comentarios. */
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
