package com.cortes.combat.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El sprite plano del arcabuz: <b>una sola puerta por pata, y la llave correcta en cada una.</b>
 *
 * <h2>El reporte que escribe este guard</h2>
 *
 * <p>"El arcabuz sigue sin texturas en la GUI." Eran dos mecanismos escribiendo el mismo
 * pixel: el wrapper del mapa del bakery (que si encontraba el icono) y el mixin, que corre
 * despues y decide los contextos. En 1.20.1 el mixin pedia el icono con un
 * {@code ModelResourceLocation} con variante, y el parche de Forge a {@code ModelBakery}
 * archiva los modelos adicionales bajo el {@code ResourceLocation} <b>pelado</b>: el
 * {@code hashCode} con variante cae en otro balde y {@code ModelManager} devuelve el
 * <b>missing model</b> -- el checkerboard. El log decia "icono plano listo" y la pantalla
 * decia otra cosa, porque el que pintaba era el otro.</p>
 *
 * <p>Lo que este test afirma, por pata: el registro existe (el icono se hornea), la llave con
 * la que se pide es la de esa pata, el mixin pregunta por la puerta y no por la llave, y el
 * wrapper de la 1.20.1 no volvio. (⌒‿⌒)</p>
 */
class ArcabuzIconWiringTest {

    private static final Path MAIN_PROPS = Path.of("src", "main", "java", "com", "cortes",
            "combat", "client", "ArcabuzModelProperties.java");
    private static final Path V120_PROPS = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "client", "ArcabuzModelProperties.java");
    private static final Path V120_ICON = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "client", "FlatInventoryIcon.java");
    private static final Path V120_CLIENT = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "client", "CortesCombatClient.java");
    private static final Path MIXIN = Path.of("src", "main", "java", "com", "cortes", "combat",
            "mixin", "ArcabuzGuiIconMixin.java");

    @Test
    @DisplayName("both legs ask the icon through their own key")
    void eachLegAsksItsOwnKey() {
        // 1.21.1 archiva por ModelResourceLocation con variante standalone...
        String main = code(MAIN_PROPS);
        assertTrue(main.contains("ModelResourceLocation.standalone("),
                MAIN_PROPS + " left the standalone variant: NeoForge's RegisterAdditional "
                        + "rejects any other one");
        assertTrue(main.contains("public static") && main.contains("iconModel()"),
                MAIN_PROPS + " lost the iconModel door the shared mixin uses");

        // ...y 1.20.1 por el ResourceLocation pelado que el parche de ModelBakery usa.
        String v120 = code(V120_PROPS);
        assertTrue(v120.contains("ResourceIds.rl(\"arcabuz_icon\")"),
                V120_PROPS + " lost the plain key: the additional model is filed under it");
        assertFalse(v120.contains("ModelResourceLocation"),
                V120_PROPS + " names a ModelResourceLocation again: its hashCode adds the "
                        + "variant, the lookup misses and the missing model comes back -- the "
                        + "exact 'sigue sin texturas en la GUI' of the playtest");
        assertTrue(v120.contains("iconModel()"),
                V120_PROPS + " lost the iconModel door the shared mixin uses");
    }

    @Test
    @DisplayName("the shared mixin asks the door, never the key")
    void theMixinAsksTheDoor() {
        String mixin = code(MIXIN);
        assertTrue(mixin.contains("ArcabuzModelProperties.iconModel()"),
                MIXIN + " no longer asks the per-leg door: one of the two legs would ask with "
                        + "the other's key and draw the missing model");
        assertFalse(mixin.contains("getModelManager()"),
                MIXIN + " asks the model manager directly again: the key is a per-leg fact and "
                        + "a constant here can only be right on one of them");
    }

    @Test
    @DisplayName("1.20.1 registers the icon and nothing else")
    void v120RegistersAndDoesNotWrap() {
        String icon = code(V120_ICON);
        assertTrue(icon.contains("onRegisterAdditional"),
                V120_ICON + " lost the register: an icon nobody references is a file on disk");
        assertTrue(icon.contains("event.register(ICON)"),
                V120_ICON + " stopped asking the bakery for the icon");
        assertFalse(icon.contains("onBakingResult"),
                V120_ICON + " brought the map wrapper back: the fuse overrides resolve AFTER "
                        + "it, and with no override matching it draws the sprite in the HAND");
        assertFalse(icon.contains("isGui3d"),
                V120_ICON + " is wrapping models again: the GUI is the mixin's call");

        String client = code(V120_CLIENT);
        assertTrue(client.contains("FlatInventoryIcon::onRegisterAdditional"),
                V120_CLIENT + " does not wire the icon registration to the mod bus");
        assertFalse(client.contains("FlatInventoryIcon::onBakingResult"),
                V120_CLIENT + " still wires the removed wrapper");
    }

    /** El archivo sin comentarios: los javadocs de estas clases NOMBRAN lo prohibido. */
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
