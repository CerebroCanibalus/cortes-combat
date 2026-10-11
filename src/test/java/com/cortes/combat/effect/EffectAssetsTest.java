package com.cortes.combat.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * Que cada efecto tenga <b>icono</b> y <b>nombre</b>, y que los dos esten donde el juego los mira.
 *
 * <h2>Por qué existe, y es la razon mas cara de las que hay en este archivo</h2>
 *
 * <p>Un efecto sin icono <b>no falla nada</b>. No crashea, no avisa, no escribe en el log: el HUD
 * dibuja el id crudo -- {@code cortescombat.anti_healing} -- y en pantalla es indistinguible de un
 * mod a medias. Y un efecto <b>con</b> icono pero en la carpeta equivocida es todavia peor,
 * porque el archivo existe, pesa bytes y alguien lo va a abrir en un explorador y va a ver que
 * esta ahi.</p>
 *
 * <p>Le paso a un jugador real: dibujo seis iconos, los dejo en {@code textures/effects/} -- que es
 * la carpeta de staging de este proyecto y <b>el juego no la lee nunca</b> -- y con nombres de
 * <em>encantamiento</em> y no de <em>efecto</em>. Seis archivos correctos, en el sitio incorrecto,
 * con el build verde.</p>
 *
 * <h2>Las dos reglas que este test impone</h2>
 *
 * <ul>
 *   <li><b>El icono va en {@code textures/mob_effect/<id>.png}.</b> Es lo que resuelve
 *       {@code MobEffect}, verificado en {@code ModEffects}: cualquier otra carpeta es un archivo
 *       que el HUD nunca mira.</li>
 *   <li><b>El nombre va en {@code effect.cortescombat.<id>}.</b> Sin el, la barrita aparece con
 *       el identificador adentro.</li>
 * </ul>
 *
 * <h2>Y por qué lee el fuente de {@code ModEffects} en vez de cargarlo</h2>
 *
 * <p>Porque <b>no se puede</b>: la clase nombra {@code MobEffect}, que es de Minecraft, y cargarse
 * en un JVM pelado moriria con {@code NoClassDefFoundError} antes de la primera asercion. Los ids
 * se leen del texto, que es fragil a proposito -- y por eso se afirma que la lista
 * <b>no vino vacia</b>: un regex que no matchea devuelve cero ids y el test quedaria verde sin
 * mirar nada, que es exactamente el modo de fallar que existe para combatir.</p>
 */
class EffectAssetsTest {

    @Test
    @DisplayName("cada efecto tiene icono en mob_effect y nombre en los dos idiomas")
    void everyEffectHasAnIconAndAName() {
        String effects = read("src/main/java/com/cortes/combat/registry/ModEffects.java");
        Matcher found = Pattern.compile("EFFECTS\\.register\\(\"([a-z_0-9]+)\"").matcher(effects);
        List<String> ids = new ArrayList<>();
        while (found.find()) {
            ids.add(found.group(1));
        }
        assertFalse(ids.isEmpty(),
                "**No se pudo leer ni un id de efecto.** El regex cambio de forma y el test "
                        + "quedaria verde sin mirar nada -- que es peor que no tenerlo");

        String lang = read("src/main/java/com/cortes/combat/data/CortesLanguageProvider.java");
        for (String id : ids) {
            Path icon = Path.of("src", "main", "resources", "assets", "cortescombat",
                    "textures", "mob_effect", id + ".png");
            assertTrue(Files.exists(icon),
                    "**El efecto `" + id + "` no tiene icono en textures/mob_effect/**. Si la "
                            + "textura existe en textures/effects/ esta en la carpeta de staging, "
                            + "y el juego no la lee: hay que copiarla bajo el id del efecto");

            assertTrue(lang.contains("effect.cortescombat." + id + "\""),
                    "**El efecto `" + id + "` no tiene nombre.** El HUD le muestra al jugador "
                            + "`cortescombat." + id + "` en vez de una palabra");
        }
    }

    @Test
    @DisplayName("y la carpeta de staging no es donde el juego mira")
    void stagingIsNotTheDestination() {
        // La regla dicha al reves, para que un dia alguien decida "simplificar" moviendo todo a
        // textures/effects/ y el build siga verde.
        Path staging = Path.of("src", "main", "resources", "assets", "cortescombat",
                "textures", "effects");
        Path destination = Path.of("src", "main", "resources", "assets", "cortescombat",
                "textures", "mob_effect");
        assertTrue(Files.isDirectory(destination),
                "**Desaparecio textures/mob_effect/**, que es lo unico que el HUD lee. Los "
                        + "iconos pasarian a ser archivos existentes que nadie dibuja");
        assertTrue(Files.isDirectory(staging),
                "**Desaparecio la carpeta de staging**, que es donde el arte del usuario se deja "
                        + "antes de copiarse bajo su id. Es donde se mira si algo no se ve");
    }

    private static String read(String relative) {
        Path file = Path.of(relative.replace('/', java.io.File.separatorChar));
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("no se pudo leer " + file + ": " + e.getMessage(), e);
        }
    }
}
