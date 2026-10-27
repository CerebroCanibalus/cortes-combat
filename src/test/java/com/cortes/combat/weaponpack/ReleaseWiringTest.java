package com.cortes.combat.weaponpack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las dos mitigaciones del bug de release: el refmap de los mixins no llegaba al jar.
 *
 * <h2>Que paso, porque este test no se entiende sin eso</h2>
 *
 * <p>El build de release de la 1.20.1 arranco con
 * <b>{@code Mixin apply failed ... could not find any targets matching
 * 'calculateTargetStrength' ... No refMap loaded}</b>, o sea el mod entero caido, con el build
 * verde. La cadena, toda medida: MixinGradle 0.7.38 no inyecta {@code -AoutRefMapFile}, asi que
 * el AP de Mixin no escribia nada; cuando lo escribe, el plugin lo deja en
 * {@code build/tmp/compileJava/} y no lo empaqueta; y el build incremental arrastraba el refmap
 * de un build viejo en {@code build/classes}, que es lo que lo hacia "funcionar". Un
 * {@code clean} lo borro y salio el jar roto.</p>
 *
 * <p><b>Lo que este test clava es el cableado, no el jar</b> (un test unitario no puede mirar un
 * zip que aun no existe): que {@code compileJava} declare el refmap como output y apague su
 * cache, que los jars lo tomen de los dos lugares posibles, y que {@code build.sh} lo verifique
 * antes de copiar nada. Las tres lineas juntas son lo que hace que el bug no vuelva; ninguna
 * sola alcanza. (μ_μ)</p>
 */
class ReleaseWiringTest {

    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    private static final Path BUILD_SH = Path.of("build.sh");

    @Test
    @DisplayName("the refmap is an output of compileJava, and that task is not cached")
    void theRefmapIsTrackedAndTheTaskIsNotCached() throws IOException {
        String gradle = Files.readString(BUILD_GRADLE, StandardCharsets.UTF_8);
        assertTrue(gradle.contains("outputs.file(refmapFile)"),
                "compileJava no declara el refmap como output: un restaurado desde el cache "
                        + "devuelve las clases sin el, y el jar sale sin refmap");
        assertTrue(gradle.contains("outputs.cacheIf { false }"),
                "compileJava volvio a cachearse: medido, un FROM-CACHE devolvio las clases "
                        + "sin el refmap incluso con el output declarado");
        assertTrue(gradle.contains("-AoutRefMapFile="),
                "no se le pasa -AoutRefMapFile al AP: MixinGradle no lo hace en esta "
                        + "combinacion y el AP no escribe nada");
        assertTrue(gradle.contains("-AdefaultObfuscationEnv=searge"),
                "sin el entorno searge el AP no sabe a que traducir los nombres");
    }

    @Test
    @DisplayName("both jar flavors take the refmap, and neither can be shipped without it")
    void theJarsTakeItAndTheScriptChecksIt() throws IOException {
        String gradle = Files.readString(BUILD_GRADLE, StandardCharsets.UTF_8);
        assertTrue(gradle.contains("tasks.withType(Jar).configureEach"),
                "los jars dejaron de tomar el refmap: ni el `jar` ni el `-all` de JarJar "
                        + "lo incluian antes de esta ronda");
        assertTrue(gradle.contains("duplicatesStrategy = DuplicatesStrategy.EXCLUDE"),
                "sin estrategia de duplicados el jar revienta cuando los dos caminos "
                        + "existen (paso: 'Entry cortescombat.refmap.json is a duplicate')");

        String sh = Files.readString(BUILD_SH, StandardCharsets.UTF_8);
        assertTrue(sh.contains("cortescombat.refmap.json"),
                "build.sh no verifica el refmap: un jar verde y muerto puede llegar a una "
                        + "carpeta de mods");
        assertTrue(sh.contains("exit 1"),
                "build.sh verifica pero no aborta: una comprobacion que no corta es un log");
    }
}
