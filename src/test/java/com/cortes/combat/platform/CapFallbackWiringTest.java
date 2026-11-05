package com.cortes.combat.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La guarda del crash de caps (2026-11-01): dos servidores de terceros cayeron por
 * {@code IllegalStateException: cortescombat:state missing on ServerPlayer} con un jugador VIVO.
 *
 * <p>La causa no fue un edge nuestro: en Forge 1.20.1 {@code Entity#setRemoved} invalida las
 * capabilities y solo {@code Entity#revive()} las restaura, y los teletransportes de dimension
 * custom que mueven al jugador a mano no lo llaman. Un modulo nuestro que leyera la capability
 * sin proteccion convierte ese estado en una excepcion por tick, y el tick del jugador es el
 * primer lector: la crash la converts el primer lector, no el que la provoco.
 *
 * <p>Por eso la guarda mira <b>la forma de la fuente</b> y no una excepcion en un test: la
 * capacidad no se puede cargar en la JVM de tests (esta prohibido nombrar tipos de Minecraft
 * desde {@code src/test}), y el metodo que decide es una rama. Lo que se afirma es lo que
 * importaba: <b>ningun lector de la capability puede lanzar</b>, todo pasa por {@code bag()}, y
 * la pata 1.21.1 no tiene el problema porque ahi los attachments viven en el objeto.
 * (⌒‿⌒)</p>
 */
class CapFallbackWiringTest {

    private static final Path V120_ROOT = Path.of("src", "v120", "java", "com", "cortes", "combat");
    private static final Path MAIN_ROOT = Path.of("src", "main", "java", "com", "cortes", "combat");
    private static final Path V120_STATE = V120_ROOT.resolve("platform/CombatState.java");
    private static final Path MAIN_STATE = MAIN_ROOT.resolve("platform/CombatState.java");

    @Test
    @DisplayName("the 1.20.1 state reader never throws when the capability is gone")
    void theStateReaderNeverThrows() {
        String state = code(V120_STATE);
        assertFalse(state.contains("orElseThrow"),
                V120_STATE + " vuelve a lanzar cuando la capability no esta: un modulo de "
                        + "dimensiones custom puede invalidar las caps de un jugador VIVO "
                        + "(Forge 1.20.1 solo las revive en Entity#revive) y ese throw por tick "
                        + "mata el server de cualquiera");
        assertTrue(state.contains("orElse(null)"),
                V120_STATE + " no lee la capability de forma tolerante: falta el orElse(null) "
                        + "que decide entre el bag del jugador y el bag muerto");
    }

    @Test
    @DisplayName("both dead states land on the same default bag")
    void bothDeadStatesLandOnTheDefaultBag() {
        String state = code(V120_STATE);
        assertTrue(state.contains("entity.isRemoved()"),
                V120_STATE + " no pregunta por el entidad muerta: el crash de muerte del 2026-10-26 "
                        + "vuelve");
        int deadReturns = state.split("return DEAD_BAG;", -1).length - 1;
        assertEquals(2, deadReturns,
                V120_STATE + " tiene " + deadReturns + " salidas a DEAD_BAG: se esperan dos -- la "
                        + "entidad muerta (isRemoved) y la entidad viva sin capability. Una sola "
                        + "deja a la otra rama lanzando");
    }

    @Test
    @DisplayName("the missing-capability warning fires once, not once per tick")
    void theMissingWarningFiresOnce() {
        String state = code(V120_STATE);
        assertTrue(state.contains("WARNED_MISSING.compareAndSet(false, true)"),
                V120_STATE + " avisa sin latch: un jugador sin capability llenaria el log de una "
                        + "linea por tick, que es el otro modo de Romper el server");
        assertTrue(state.contains("LOGGER.warn("),
                V120_STATE + " avisa pero no lo escribe en el log: el aviso de una capability "
                        + "ausente es la unica pista de que un mod de terceros rompio el estado");
    }

    @Test
    @DisplayName("every capability read goes through bag()")
    void everyCapabilityReadGoesThroughBag() {
        long reads = 0;
        for (Path file : javaFiles(V120_ROOT)) {
            String body = code(file);
            reads += count(body, "getCapability(STATE)");
        }
        assertEquals(1, reads,
                "la capability de estado se lee " + reads + " veces en el arbol v120: cada "
                        + "lectura directa es una via que puede lanzar cuando las caps mueren. "
                        + "Todas deben pasar por CombatState.bag()");
    }

    @Test
    @DisplayName("the 1.21.1 leg has no dead bag: attachments cannot go missing")
    void theNeoForgeLegHasNoDeadBag() {
        String state = code(MAIN_STATE);
        assertFalse(state.contains("DEAD_BAG"),
                MAIN_STATE + " tiene un bag muerto: en NeoForge los attachments viven en el "
                        + "objeto y getData nunca vuelve vacio. Un DEAD_BAG aca seria estado "
                        + "compartido entre jugadores, escrito por error");
        assertFalse(state.contains("getCapability"),
                MAIN_STATE + " lee capabilities: la pata 1.21.1 usa attachments, no capabilities. "
                        + "Si aparece, alguien esta copiando codigo de la 1.20.1");
    }

    private static int count(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new AssertionError("source tree unreadable: " + root, e);
        }
    }

    /** El archivo sin comentarios: el codigo que la guarda busca esta nombrado en su propia prosa. */
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
