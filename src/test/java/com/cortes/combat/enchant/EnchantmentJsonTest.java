package com.cortes.combat.enchant;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Que cada archivo de encantamiento <b>se pueda parsear</b>, y no solo que exista.
 *
 * <h2>Lo que le paso a este proyecto, y es exactamente la forma del bug</h2>
 *
 * <p>Un archivo tenia <b>una comilla doble de mas</b> al final de su {@code translate}. El
 * build estaba verde, los tests estaban verdes, el archivo existia, tenia su tag, tenia su
 * nombre en los ocho idiomas, y su forma de campo matcheaba todo lo que los guards miraban. Y
 * <b>no se podia abrir un mundo</b>: {@code RegistryDataLoader} lo parsea con Gson y reventaba
 * con {@code Unterminated object}, y el cliente se caia en el momento de entrar.</p>
 *
 * <p>Es la misma leccion que {@code docs/api-trampas.md} ya tiene escrita con otras palabras:
 * <b>un test que solo pregunta si el archivo existe no ve un archivo que existe y no se puede
 * leer.</b> Los guards de mas abajo leen el texto y buscan substrings -- y un substring no
 * distingue un JSON valido de uno que Gson rechaza.</p>
 *
 * <h2>Por qué Gson y no un parser de PowerShell</h2>
 *
 * <p>Porque <b>Gson es el parser del juego</b>: si Gson lo acepta, Minecraft lo acepta. La
 * primera version de esta revision la hizo PowerShell, y el veredicto fue peor que inutil --
 * marc&oacute; 265 archivos rotos porque el validador que use <b>no existia en esa
 * PowerShell</b> y su {@code catch} convirtio cada "tipo no encontrado" en "JSON invalido".
 * Un validador que no se valido a si mismo primero es ruido con forma de alarma.</p>
 *
 * <p>Y la segunda pasada con {@code ConvertFrom-Json} <em>si</em> encontro el archivo roto --
 * pero marco tambien a un blockstate que tiene una clave vacia ({@code ""}), que es JSON valido y
 * muy comun en vanilla. Gson no tiene ese falso positivo porque es el mismo parser.</p>
 */
class EnchantmentJsonTest {

    @Test
    @DisplayName("todos los archivos de encantamiento son JSON que Gson parsea")
    void everyEnchantmentFileParses() {
        Path dir = Path.of("src", "main", "resources", "data", "cortescombat", "enchantment");
        List<String> rotos = new ArrayList<>();

        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String raw;
                try {
                    raw = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new AssertionError("no se pudo leer " + file + ": " + e.getMessage(), e);
                }
                try {
                    JsonParser.parseString(raw);
                } catch (RuntimeException rechazado) {
                    rotos.add(file.getFileName() + " -> " + rechazado.getMessage());
                }
            }
        } catch (IOException e) {
            throw new AssertionError("no se pudo listar " + dir + ": " + " " + e.getMessage(), e);
        }

        assertTrue(rotos.isEmpty(),
                "**Hay archivos de encantamiento que Gson rechaza, y el build no se entero.** "
                        + "Un archivo asi pasa todos los guards de existencia, tag, idioma y forma "
                        + "de campo -- y despues impide abrir un mundo. Rotos: " + rotos);

        // Y se afirma que la lista no vino vacia: un directorio que no se pudo listar daria cero
        // archivos y el test quedaria verde sin mirar nada.
        assertTrue(dir.toFile().isDirectory(),
                "**Desaparecio la carpeta de encantamientos**, y entonces no se parseo nada");
    }
}
