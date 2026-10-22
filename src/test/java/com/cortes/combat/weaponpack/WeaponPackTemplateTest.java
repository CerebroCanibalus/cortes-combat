package com.cortes.combat.weaponpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El template, que es la primera cosa que un packmate ve del sistema.
 *
 * <p>Dos promesas y sólo dos: <b>escribe la estructura completa</b> (pack.mcmeta, los dos LEEME,
 * el ejemplo de config, y las carpetas donde va cada tipo de asset) y <b>no pisa un byte de lo
 * suyo</b> en la segunda pasada -- la carpeta es del usuario desde el momento en que existe.</p>
 */
class WeaponPackTemplateTest {

    @TempDir
    Path tempDir;

    @Test
    void templateWritesEverythingOnceAndKeepsUserFiles() throws Exception {
        Path root = tempDir.resolve(WeaponPackTemplate.ROOT);
        WeaponPackTemplate.writeResourcePackFiles(root);

        assertTrue(Files.exists(root.resolve("pack.mcmeta")), "sin pack.mcmeta no es un pack");
        assertTrue(Files.exists(root.resolve("LEEME.txt")), "y sin LEEME es adivinar");
        assertTrue(Files.isDirectory(root.resolve("assets").resolve("cortescombat")
                .resolve("models").resolve("item")), "iconos del ítem creado");
        assertTrue(Files.isDirectory(root.resolve("assets").resolve("cortescombat")
                .resolve("textures").resolve("particle").resolve("slash")), "sprites del golpe");
        assertTrue(Files.isDirectory(root.resolve("assets").resolve("cortescombat").resolve("poses")),
                "poses");

        // El LEEME dice dónde van las dos mitades -- la que ya sabemos que preguntan.
        String readme = Files.readString(root.resolve("LEEME.txt"), StandardCharsets.UTF_8);
        assertTrue(readme.contains("config/cortescombat/weapons"),
                "el LEEME separa stats (config) de assets (acá): esa es la confusión previsible");
        assertTrue(readme.contains("pack_format"),
                "y dice qué hacer si el formato cambia, en vez de dejarlo atascado");

        // Segunda pasada: la carpeta existe, y lo que el usuario puso dentro se queda.
        Path mine = root.resolve("assets").resolve("cortescombat").resolve("mio.txt");
        Files.writeString(mine, "del usuario", StandardCharsets.UTF_8);
        WeaponPackTemplate.writeResourcePackFiles(root);
        assertEquals("del usuario", Files.readString(mine, StandardCharsets.UTF_8),
                "**no se pisa**: la carpeta dejó de ser nuestra en el primer byte");
    }

    /**
     * El LEEME en español y el README en inglés, los dos al lado.
     *
     * <p>El motivo de dos archivos y no uno mezclado: un archivo con los dos idiomas se lee mal
     * de arriba abajo, y quien sólo lee uno se queda con la mitad de las anotaciones.</p>
     */
    @Test
    void theResourcePackShipsBothReadmes() throws Exception {
        Path root = tempDir.resolve(WeaponPackTemplate.ROOT);
        WeaponPackTemplate.writeResourcePackFiles(root);

        assertTrue(Files.exists(root.resolve("README.txt")), "el README en inglés");
        assertTrue(Files.exists(root.resolve("LEEME.txt")), "y el LEEME en español");

        for (String name : new String[]{"README.txt", "LEEME.txt"}) {
            String readme = Files.readString(root.resolve(name), StandardCharsets.UTF_8);
            assertTrue(readme.contains("config/cortescombat/weapons"),
                    name + " separa stats (config) de assets (acá)");
            assertTrue(readme.contains("pack_format"),
                    name + " dice qué hacer si el formato cambia");
            assertTrue(readme.contains(WeaponPackTemplate.CONFIG_EXAMPLE),
                    name + " señala el ejemplo de config");
            assertTrue(readme.contains("custom-weapons"),
                    name + " dice dónde está la guía");
        }
    }

    /**
     * El ejemplo de config: anotado en los dos idiomas, y fuera de la vista del loader.
     *
     * <p>La extensión {@code .example} es lo que lo saca de la vista del loader, y eso no es un
     * detalle: si el loader lo leyera, cada arranque dejaría un warning por un archivo que el
     * usuario todavía no tocó.</p>
     */
    @Test
    void theConfigExampleIsBilingualAndTheLoaderNeverSeesIt() throws Exception {
        Path weapons = tempDir.resolve("cortescombat").resolve("weapons");
        Path example = weapons.resolve(WeaponPackTemplate.CONFIG_EXAMPLE);
        assertTrue(WeaponPackTemplate.writeConfigExample(example), "el ejemplo tiene que escribirse");

        String yaml = Files.readString(example, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("version: 1"), "el ejemplo tiene que ser un pack válido");
        assertTrue(yaml.contains("match: { id:"), "y mostrar cómo se matchea un ítem");
        assertTrue(yaml.contains("archetype:"), "y el molde");
        assertTrue(yaml.contains("Obligatoria."), "anotaciones en español");
        assertTrue(yaml.contains("Required."), "anotaciones en inglés");

        // Lo que el loader ve: nada. Ni un pack, ni un warning.
        WeaponPackLoader.Result result = WeaponPackLoader.loadDirectory(weapons);
        assertTrue(result.warnings().isEmpty(),
                "el ejemplo no puede dejar warnings: " + result.warnings());
        assertTrue(result.packs().isEmpty(), "y el loader no lo puede leer como un pack");

        // Segunda pasada: el ejemplo del usuario se queda.
        Files.writeString(example, "del usuario", StandardCharsets.UTF_8);
        assertTrue(!WeaponPackTemplate.writeConfigExample(example), "no se escribe dos veces");
        assertEquals("del usuario", Files.readString(example, StandardCharsets.UTF_8),
                "no se pisa");
    }
}
