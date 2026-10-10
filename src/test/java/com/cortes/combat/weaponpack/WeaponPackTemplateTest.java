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
 * <p>Dos promesas y sólo dos: <b>escribe la estructura completa</b> (pack.mcmeta, LEEME, y las
 * carpetas donde va cada tipo de asset) y <b>no pisa un byte de lo suyo</b> en la segunda
 * pasada -- la carpeta es del usuario desde el momento en que existe.
 */
class WeaponPackTemplateTest {

    @TempDir
    Path tempDir;

    @Test
    void templateWritesEverythingOnceAndKeepsUserFiles() throws Exception {
        Path root = tempDir.resolve(WeaponPackTemplate.ROOT);
        WeaponPackTemplate.writeIfMissing(root);

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
        WeaponPackTemplate.writeIfMissing(root);
        assertEquals("del usuario", Files.readString(mine, StandardCharsets.UTF_8),
                "**no se pisa**: la carpeta dejó de ser nuestra en el primer byte");
    }
}
