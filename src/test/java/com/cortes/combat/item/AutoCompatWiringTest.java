package com.cortes.combat.item;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La ronda de compatibilidad automatica, afirmada donde vive: el hook del tier en
 * {@code WeaponStats}, el generador colgado de {@code ServerStartedEvent} en las dos patas, y
 * el nerfeo de la daga como un solo numero.
 *
 * <p><b>Las tres afirmaciones son de fuente y por una razon</b>: los tres call sites tocan
 * clases de Minecraft que no estan en el classpath de test (el atributo del item, el
 * {@code ServerStartedEvent}, el registro de items), asi que el valor puro se testea en
 * {@code AutoProfileTest} y la puerta se afirma leyendo el codigo sin comentarios -- el
 * patron ya pagado tres veces en este proyecto. (⌒‿⌒)</p>
 */
class AutoCompatWiringTest {

    private static final Path WEAPON_STATS = Path.of("src", "main", "java", "com", "cortes",
            "combat", "attack", "WeaponStats.java");
    private static final Path MAIN_PLATFORM = Path.of("src", "main", "java", "com", "cortes",
            "combat", "platform", "ModPlatform.java");
    private static final Path V120_PLATFORM = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "platform", "ModPlatform.java");
    private static final Path FAMILY = Path.of("src", "main", "java", "com", "cortes", "combat",
            "item", "WeaponFamily.java");

    @Test
    @DisplayName("the tier scales the charge of a foreign weapon, and only what the molde said")
    void theTierHookScalesOnlyTheMolde() {
        String body = methodBody(WEAPON_STATS, "public static WeaponStats of(");
        assertTrue(body.contains("AutoProfile.tieredChargeTicks("),
                WEAPON_STATS + " lost the tier ticks: a diamond spear and a wooden one would "
                        + "charge in the same 25 ticks again");
        assertTrue(body.contains("AutoProfile.tieredChargeBonus("),
                WEAPON_STATS + " lost the tier bonus: the whole 'mejores o peores stats' is "
                        + "the two charge numbers");
        assertTrue(body.contains("AutoProfile.isForeign("),
                WEAPON_STATS + " stopped asking whose weapon it is: our own catalogue would "
                        + "get its authored charge scaled a second time");
        // El pack manda: el tier solo escala el campo que sigue viniendo del molde.
        assertTrue(body.contains("pack.chargeBonus() == null"),
                WEAPON_STATS + " no longer respects an explicit charge bonus from the YAML: the "
                        + "tier would double-scale a number the pack already decided");
        assertTrue(body.contains("pack.fullChargeTicks() == null"),
                WEAPON_STATS + " no longer respects explicit ticks from the YAML: same double "
                        + "scale, on the window");
    }

    @Test
    @DisplayName("the auto-generator runs on ServerStartedEvent, in both legs")
    void theGeneratorRunsWhereTheTagsAreBound() {
        for (Path platform : new Path[]{MAIN_PLATFORM, V120_PLATFORM}) {
            String body = methodBody(platform, "private static void autoPackGenerate(");
            assertTrue(body.contains("AutoPackGenerator.generate()"),
                    platform + " registers a handler that does not call the generator");
            assertTrue(body.contains("ServerStartedEvent"),
                    platform + " lost the ServerStartedEvent parameter: at any earlier event the "
                            + "sword tag answers false for every item");
            String whole = code(platform);
            assertTrue(whole.contains("ModPlatform::autoPackGenerate"),
                    platform + " never subscribes the generator to the game bus");
        }
    }

    @Test
    @DisplayName("the trident fights in both legs, by class and not by a 1.21.1 tag")
    void theTridentFightsInBothLegs() {
        // El reporte de arranque del server 1.20.1 lo canto: `Trident=NONE(NO PELEA)`.
        // `enchantable/trident` es un tag de 1.21.1 y en 1.20.1 no existe; `TridentItem` si,
        // en las dos patas. Y ahora importa el doble: las lanzas ajenas cabalgan este molde.
        String body = methodBody(WEAPON_STATS, "private static WeaponProfile deriveProfile(");
        assertTrue(body.contains("instanceof net.minecraft.world.item.TridentItem"),
                WEAPON_STATS + " lost the trident door: in 1.20.1 the tag does not exist and "
                        + "the weapon resolves NONE, which the startup report proved");
        assertTrue(body.contains("WeaponProfile.TRIDENT"),
                WEAPON_STATS + " asks the class and forgets the answer");
    }

    @Test
    @DisplayName("the dagger nerf is one number, -1.2 on every tier at once")
    void theDaggerNerfIsOneNumber() {
        // El dano por familia es el unico numero que TODOS los tiers comparten (el bono del
        // material, en CortesTier, se suma encima), asi que el -1.2 pedido por el dueno es
        // exactamente este. CortesTier NO se nombra aca: implementa Tier, que es de Minecraft,
        // y un test de JVM pelada no puede cargarlo -- la familia sola alcanza para afirmar
        // el nerfeo.
        assertEquals(0.4F, WeaponFamily.DAGA.attackDamage(), 1.0E-4F,
                "la daga volvio a 1.6: el pedido del playtest era -1.2 en todos los tiers, y "
                        + "el numero que lo aplica de una vez es el de la familia");
    }

    @Test
    @DisplayName("no comment was taken for code: the guards read the files stripped")
    void theGuardsReadCodeAndNotProse() throws IOException {
        // Guard de la herramienta, no del juego: si el strip se rompe, los asserts de arriba
        // empiezan a leer javadoc y vuelven a morder su propia explicacion.
        String stripped = code(WEAPON_STATS);
        assertFalse(stripped.contains("/**"), "el strip dejo javadoc adentro");
        assertFalse(stripped.contains("// "), "el strip dejo comentarios de linea adentro");
        assertTrue(stripped.length() > 1000, "el archivo descascarado conserva el cuerpo");
    }

    private static String methodBody(Path file, String method) {
        String source = code(file);
        int start = source.indexOf(method);
        assertFalse(start < 0, file + " lost " + method);
        return source.substring(start);
    }

    /** El archivo sin comentarios de linea ni de bloque: lo que aserciona es codigo. */
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
