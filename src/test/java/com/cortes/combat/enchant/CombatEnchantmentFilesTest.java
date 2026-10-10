package com.cortes.combat.enchant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los archivos de los encantamientos de combate propios: que existan, y que lo que nombran exista.
 *
 * <h2>El agujero que este test tapa</h2>
 *
 * <p>Estos encantamientos son <b>datos</b> y su comportamiento es <b>código</b>, y las dos mitades no
 * se conocen. Un archivo que declare {@code supported_items: "#cortescombat:profile/sable"} sobre un
 * tag que no existe -- o que existe vacío -- es un encantamiento que <b>no se puede poner en ninguna
 * arma y no dice por qué</b>: la mesa simplemente no lo ofrece. Este proyecto ya pagó esa forma una
 * vez, con un tag que nada llenaba.</p>
 *
 * <p>Y un encantamiento sin su línea de idioma no falla: imprime su clave en la pantalla. Por eso el
 * test camina {@link CombatEnchantRules#IDS} -- la lista real, no una copia -- y comprueba las tres
 * cosas que un archivo puede romper en silencio: que esté, que el tag que nombra esté, y que se lea
 * en los dos idiomas.</p>
 */
class CombatEnchantmentFilesTest {

    private static final Path ENCHANTMENT_DIR =
            Path.of("src", "main", "resources", "data", "cortescombat", "enchantment");
    private static final Path PROFILE_TAGS =
            Path.of("src", "generated", "resources", "data", "cortescombat", "tags", "item", "profile");

    /** El único tag que estos encantamientos pueden nombrar, y de dónde sale su archivo. */
    private static final Pattern PROFILE_TAG =
            Pattern.compile("\"#cortescombat:profile/([a-z_]+)\"");

    @Test
    @DisplayName("cada encantamiento tiene su archivo de datos")
    void everyEnchantmentHasItsFile() {
        for (String id : CombatEnchantRules.IDS) {
            assertTrue(Files.exists(ENCHANTMENT_DIR.resolve(id + ".json")),
                    "falta " + ENCHANTMENT_DIR.resolve(id + ".json") + ": un encantamiento sin su "
                            + "archivo no existe para el juego, y el codigo que lo lee por clave "
                            + "tiraria al primer golpe");
        }
    }

    @Test
    @DisplayName("**el tag que cada archivo nombra existe en disco**: un tag que nada llena es el bug")
    void theSupportedTagExists() throws IOException {
        for (String id : CombatEnchantRules.IDS) {
            String json = Files.readString(ENCHANTMENT_DIR.resolve(id + ".json"),
                    StandardCharsets.UTF_8);
            Matcher matcher = PROFILE_TAG.matcher(json);
            assertTrue(matcher.find(),
                    id + " no nombra un tag de perfil en supported_items: los encantamientos de "
                            + "identidad se ponen por perfil, y una lista de items escrita a mano "
                            + "seria una segunda verdad sobre que armas son de la familia");

            // Puede nombrar el mismo tag mas de una vez (supported y primary): hay que revisar
            // todos, no el primero.
            int checked = 0;
            for (Matcher m = PROFILE_TAG.matcher(json); m.find(); checked++) {
                Path tag = PROFILE_TAGS.resolve(m.group(1) + ".json");
                assertTrue(Files.exists(tag),
                        id + " declara #cortescombat:profile/" + m.group(1) + " y ese tag no existe "
                                + "(se esperaba " + tag + "). El encantamiento no se podria poner en "
                                + "ninguna arma y la mesa no diria por que");
            }
            assertTrue(checked >= 2,
                    id + " deberia nombrar el tag en supported_items Y en primary_items, y solo lo "
                            + "nombra " + checked + " vez");
        }
    }

    @Test
    @DisplayName("y se lee en los dos idiomas: sin la linea, la pantalla imprime la clave")
    void theNameIsInBothLanguages() throws IOException {
        List<String> langs = List.of("en_us", "es_es");
        for (String id : CombatEnchantRules.IDS) {
            for (String lang : langs) {
                Path file = Path.of("src", "generated", "resources", "assets", "cortescombat",
                        "lang", lang + ".json");
                String json = Files.readString(file, StandardCharsets.UTF_8);
                assertTrue(json.contains("\"enchantment.cortescombat." + id + "\""),
                        lang + " no tiene enchantment.cortescombat." + id + ": el tooltip mostraria "
                                + "la clave cruda. Se arregla en CortesLanguageProvider y despues "
                                + "hay que correr runData");
            }
        }
    }
}
