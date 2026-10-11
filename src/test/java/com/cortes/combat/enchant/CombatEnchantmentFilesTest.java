package com.cortes.combat.enchant;

import com.cortes.combat.data.ModLocales;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los archivos de encantamiento: que existan, y que su forma sea la que el juego puede leer.
 *
 * <h2>El agujero que este test tapa, y costó un mundo</h2>
 *
 * <p>La primera versión de esta clase comprobaba tres cosas y las tres pasaban: el archivo existe,
 * el tag que nombra existe, y el idioma está. <b>Y el juego crasheaba al crear un mundo.</b></p>
 *
 * <p>El motivo: {@code supported_items} acepta <b>un tag</b> ({@code "#ns:tag"}) o <b>una lista de
 * ids directos</b>, pero <b>no una lista de tags</b>. Verificado contra las fuentes de 1.21.1:
 * {@code Enchantment.EnchantmentDefinition} declara el campo como
 * {@code RegistryCodecs.homogeneousList(ITEM)}, y el camino de lista usa ids directos. Un {@code #}
 * adentro de una lista se parsea como nombre de ítem y el registro entero falla -- con un error que
 * nombra el <em>string</em> y no el archivo, así que la primera pista es un stack trace de
 * {@code RegistryDataLoader} y ninguna mención a este mod.</p>
 *
 * <p>Lo que faltaba no era más cobertura: era comprobar la <b>forma</b> del campo contra la
 * gramática del codec. Un test que sólo pregunta si el archivo existe no puede ver un archivo que
 * existe y no se puede leer, que es exactamente el estado en el que estaba.</p>
 */
class CombatEnchantmentFilesTest {

    private static final Path ENCHANTMENT_DIR =
            Path.of("src", "main", "resources", "data", "cortescombat", "enchantment");
    private static final Path VANILLA_ENCHANTMENTS =
            Path.of("src", "main", "resources", "data", "minecraft", "enchantment");
    private static final Path OUR_ITEM_TAGS =
            Path.of("src", "generated", "resources", "data", "cortescombat", "tags", "item");
    private static final Path PROFILE_TAGS = OUR_ITEM_TAGS.resolve("profile");

    /** El único tag que los encantamientos de identidad pueden nombrar, y de dónde sale su archivo. */
    private static final Pattern PROFILE_TAG =
            Pattern.compile("\"#cortescombat:profile/([a-z_]+)\"");

    /** Un campo de items, con su valor crudo: la lista entre corchetes o el string entre comillas. */
    private static final Pattern ITEM_FIELD = Pattern.compile(
            "\"(supported_items|primary_items)\":\\s*(\\[[^]]*\\]|\"[^\"]*\")");

    /** Un tag nuestro nombrado en cualquier parte del archivo. */
    private static final Pattern OUR_TAG =
            Pattern.compile("\"#cortescombat:([a-z_/]+)\"");

    private static List<Path> allEnchantmentFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : List.of(ENCHANTMENT_DIR, VANILLA_ENCHANTMENTS)) {
            try (var walk = Files.walk(dir)) {
                walk.filter(path -> path.toString().endsWith(".json")).forEach(files::add);
            }
        }
        return files;
    }

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

    /**
     * **La aserción que faltaba, y la que costó un mundo.**
     *
     * <p>Un {@code #} adentro de la lista es un archivo que existe, pasa todos los otros tests, y
     * hace que <b>ningún mundo cargue</b>. Se verifica sobre los doce archivos -- los cinco nuestros
     * y los siete overrides de vanilla -- porque el que rompió era uno de vanilla.</p>
     */
    @Test
    @DisplayName("**supported_items no puede ser una LISTA con tags**: es un mundo que no carga")
    void itemListsCarryNoTags() throws IOException {
        List<Path> files = allEnchantmentFiles();
        assertTrue(files.size() >= 12,
                "esperaba los 12 archivos de encantamiento, encontre " + files.size()
                        + ": si esta lista encogio, el test dejo de mirar algo");

        for (Path file : files) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Matcher fields = ITEM_FIELD.matcher(json);
            while (fields.find()) {
                String value = fields.group(2);
                if (!value.startsWith("[")) {
                    continue;
                }
                assertFalse(value.contains("#"),
                        file.getFileName() + ": `" + fields.group(1) + "` es una LISTA con un tag ("
                                + value + "). El codec de 1.21.1 lee una lista como ids DIRECTOS: el "
                                + "`#` se parsea como nombre de item y el registro falla al cargar el "
                                + "mundo entero. La union va adentro de un tag nuestro -- ver "
                                + "CortesItemTagProvider.enchantClasses()");
            }
        }
    }

    /**
     * Y todo tag nuestro que un encantamiento nombra tiene que existir de verdad.
     *
     * <p>El otro lado del mismo agujero: un tag mal escrito no rompe el registro -- el encantamiento
     * simplemente no se puede poner en ningún arma, y la mesa no dice por qué. Este proyecto ya pagó
     * esa forma una vez, con un tag que nada llenaba.</p>
     */
    @Test
    @DisplayName("**todo tag nuestro que nombran existe en el datagen**: uno que nada define es el bug")
    void ourTagsExist() throws IOException {
        for (Path file : allEnchantmentFiles()) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Matcher tags = OUR_TAG.matcher(json);
            int checked = 0;
            while (tags.find()) {
                Path tag = OUR_ITEM_TAGS.resolve(tags.group(1) + ".json");
                assertTrue(Files.exists(tag),
                        file.getFileName() + " declara #cortescombat:" + tags.group(1) + " y ese tag "
                                + "no existe (se esperaba " + tag + "). El encantamiento no se podria "
                                + "poner en ninguna arma y la mesa no diria por que");
                checked++;
            }
            assertTrue(checked > 0 || file.startsWith(VANILLA_ENCHANTMENTS),
                    file.getFileName() + " no nombra ningun tag nuestro. Si es un override de "
                            + "vanilla puede ser a proposito (cambia el alcance a un tag de vanilla y "
                            + "nada mas); si es NUESTRO, le falta la clase");
        }
    }

    @Test
    @DisplayName("**el tag de molde de cada encantamiento de identidad existe**: un tag que nada llena es el bug")
    void theSupportedTagExists() throws IOException {
        for (String id : CombatEnchantRules.IDS) {
            if (CombatEnchantRules.GENERIC_IDS.contains(id)) {
                // Un generico se pone por clase y no por molde: ver GENERIC_IDS. Su tag tambien se
                // comprueba, pero en ourTagsExist, que no tiene opinion sobre cual tag es.
                continue;
            }
            String json = Files.readString(ENCHANTMENT_DIR.resolve(id + ".json"),
                    StandardCharsets.UTF_8);
            Matcher matcher = PROFILE_TAG.matcher(json);
            assertTrue(matcher.find(),
                    id + " no nombra un tag de perfil en supported_items: los encantamientos de "
                            + "identidad se ponen por molde, y una lista de items escrita a mano "
                            + "seria una segunda verdad sobre que armas son de la familia");

            // Puede nombrar el mismo tag mas de una vez (supported y primary): hay que revisar
            // todos, no el primero.
            int checked = 0;
            for (Matcher m = PROFILE_TAG.matcher(json); m.find(); checked++) {
                Path tag = PROFILE_TAGS.resolve(m.group(1) + ".json");
                assertTrue(Files.exists(tag),
                        id + " declara #cortescombat:profile/" + m.group(1) + " y ese tag no existe "
                                + "(se esperaba " + tag + ")");
            }
            assertTrue(checked >= 2,
                    id + " deberia nombrar el tag en supported_items Y en primary_items, y solo lo "
                            + "nombra " + checked + " vez");
        }
    }

    @Test
    @DisplayName("**y se lee en TODOS los idiomas que el mod escribe**, no solo en dos")
    void theNameIsInBothLanguages() throws IOException {
        // La lista sale de ModLocales, que es pura: el test camina la MISMA lista que el datagen,
        // asi que agregar un idioma y olvidarse del provider (o al reves) falla aca. Con la lista
        // escrita a mano en este archivo, el idioma nuevo pasaba sin que nadie lo mirara.
        for (String lang : ModLocales.ALL) {
            Path file = Path.of("src", "generated", "resources", "assets", "cortescombat",
                    "lang", lang + ".json");
            assertTrue(Files.exists(file),
                    "falta " + file + ": ModLocales lo pide y el datagen no lo escribio");
            String json = Files.readString(file, StandardCharsets.UTF_8);
            for (String id : CombatEnchantRules.IDS) {
                assertTrue(json.contains("\"enchantment.cortescombat." + id + "\""),
                        lang + " no tiene enchantment.cortescombat." + id + ": el tooltip mostraria "
                                + "la clave cruda. Se arregla en CortesLanguageProvider y despues "
                                + "hay que correr runData");
            }
        }
    }
}
