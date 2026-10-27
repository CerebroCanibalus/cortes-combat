package com.cortes.combat.enchant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La tabla de descripciones cubre a los 48 encantamientos, en las dos lenguas, y no tiene
 * entradas huerfanas.
 *
 * <h2>Por que existe, ademas de "cobertura"</h2>
 *
 * <p>La version anterior escribia las descripciones a mano en Java, una por encantamiento con un
 * ternario {@code spanish ? ... : ...} en la misma linea. Agregar una entrada y olvidar una de las
 * dos lenguas no rompia nada: se veia un encantamiento con descripcion solo en ingles y nadie se
 * enteraba hasta que un hispanohablante preguntaba. Con la tabla, este test es lo que convierte
 * "me olvide" en un build rojo.</p>
 *
 * <p><b>Camina {@code CombatEnchantRules.IDS + GOLD_IDS}</b> en vez de una lista escrita aca: un
 * encantamiento nuevo entra solo, y una entrada de la tabla que no corresponde a ningun id se
 * reporta como huerfana (una descripcion que se pudre porque nadie la lee).</p>
 *
 * <h2>Los dos bordes que afirma</h2>
 *
 * <ul>
 *   <li><b>Ida:</b> cada id tiene su texto en es y en en, no vacio.</li>
 *   <li><b>Vuelta:</b> cada clave de la tabla es un id conocido.</li>
 * </ul>
 *
 * <p>Lee el archivo del disco (no el classpath) para no depender de que el recurso este en el
 * runtime de test; el formato es YAML simple y se camina con lineas, sin libreria, para que este
 * test no arrastre nada de Minecraft ni de SnakeYAML.</p>
 */
class EnchantmentDescriptionsTest {

    private static final Path TABLE = Path.of("src", "main", "resources", "data",
            "cortescombat", "enchantment_descriptions.yaml");

    /** Todos los ids, como los conoce el codigo puro. */
    private static List<String> allIds() {
        List<String> ids = new ArrayList<>(CombatEnchantRules.IDS);
        ids.addAll(CombatEnchantRules.GOLD_IDS);
        return ids;
    }

    @Test
    @DisplayName("every enchantment has its description in both languages")
    void everyIdIsDescribed() throws IOException {
        String yaml = Files.readString(TABLE, StandardCharsets.UTF_8);
        for (String id : allIds()) {
            assertTrue(yaml.contains("\n" + id + ":"),
                    "falta la entrada '" + id + "' en " + TABLE + ": el encantamiento queda mudo "
                            + "en el tooltip");
        }
        // Y cada entrada con sus dos lineas: `es:` y `en:` no vacias.
        String[] blocks = yaml.split("\n(?=[a-z_0-9]+:)");
        int counted = 0;
        for (String block : blocks) {
            if (!block.contains("  es:") && !block.contains("  en:")) {
                continue;
            }
            counted++;
            assertTrue(block.contains("  es: \""),
                    "una entrada no tiene `es`: " + block.substring(0, Math.min(60, block.length())));
            assertTrue(block.contains("  en: \""),
                    "una entrada no tiene `en`: " + block.substring(0, Math.min(60, block.length())));
        }
        assertEquals(allIds().size(), counted,
                "la tabla tiene " + counted + " entradas y hay " + allIds().size() + " ids");
    }

    @Test
    @DisplayName("no description is left behind for an id that does not exist")
    void noOrphanDescriptions() throws IOException {
        String yaml = Files.readString(TABLE, StandardCharsets.UTF_8);
        for (String line : yaml.split("\n")) {
            if (!line.endsWith(":") || line.startsWith(" ") || line.startsWith("#")
                    || line.isEmpty()) {
                continue;
            }
            String id = line.substring(0, line.length() - 1);
            assertTrue(allIds().contains(id),
                    "la tabla describe '" + id + "', que no es un encantamiento del mod: una "
                            + "descripcion que nadie puede leer es una que se pudre");
        }
    }

    @Test
    @DisplayName("the text says what happens: no empty lines, and a length a tooltip can hold")
    void theTextIsReadable() throws IOException {
        for (String line : Files.readAllLines(TABLE, StandardCharsets.UTF_8)) {
            String s = line.strip();
            if (!s.startsWith("es:") && !s.startsWith("en:")) {
                continue;
            }
            String text = s.substring(3).strip();
            assertFalse(text.isEmpty() || text.equals("\"\""), "descripcion vacia: " + line);
            assertTrue(text.length() >= 20,
                    "descripcion demasiado corta para decir algo (" + text.length() + "): " + text);
            assertTrue(text.length() <= 130,
                    "descripcion de " + text.length() + " caracteres: no entra en el tooltip sin "
                            + "cortarse mal -> " + text);
        }
    }

    /**
     * Las construcciones que el dueno veto por nombre, comprobadas sobre el texto real.
     *
     * <p><b>Una regla de estilo sin guard es una regla que se pierde en la tercera reescritura.</b>
     * Las tres familias vetadas: <b>epanortosis</b> (corregirse a mitad de frase), <b>coordinadas
     * adversativas</b> ({@code pero/sino/aunque}) y las <b>rhetoricas de contraste</b>
     * ({@code No es X, sino Y} / {@code No solo X, sino tambien Y} / {@code Mas que X, es Y}).
     * El registro buscado es el de un objeto del juego que se recuerda, no el de un manual; una
     * descripcion afirmativa dice lo mismo sin la muleta del contraste.</p>
     *
     * <p>Se busca por palabra completa y en minusculas: {@code sino} como subcadena vive dentro de
     * {@code destino}, y un guard que se dispara con una palabra legitima se termina apagando.</p>
     */
    @Test
    @DisplayName("no adversatives, no contrast rhetoric, no self-correction")
    void noVetoedConstructions() throws IOException {
        String[] bannedEs = {"pero", "sino", "aunque", "sin embargo", "no obstante", "mas bien",
                "más bien", "no solo", "digamos"};
        String[] bannedEn = {"but", "however", "although", "rather", "not only", "i mean"};
        for (String line : Files.readAllLines(TABLE, StandardCharsets.UTF_8)) {
            String s = line.strip();
            if (!s.startsWith("es:") && !s.startsWith("en:")) {
                continue;
            }
            boolean spanish = s.startsWith("es:");
            String text = s.substring(3).strip().toLowerCase();
            String[] banned = spanish ? bannedEs : bannedEn;
            for (String word : banned) {
                boolean hit = spanish
                        ? (" " + text + " ").contains(" " + word + " ")
                        : java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(word)
                                + "\\b").matcher(text).find();
                assertFalse(hit, "la descripcion usa '" + word + "', vetado por el dueno: " + s);
            }
        }
    }
}
