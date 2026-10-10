package com.cortes.combat.weaponpack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El contrato de la fase 5: el tag de categoría existe, el built-in lo traduce, y las dos
 * listas son la misma.
 *
 * <p><b>Por qué un test de archivos y no sólo de código:</b> los nueve tags nacen vacíos del
 * datagen, y un tag que nadie escribe en disco es un tag que no existe para un datapack de
 * tercero -- la falla sería silenciosa para él y verde para nosotros. El invariante es de
 * sistema completo: {@code ArchetypeSpec} (los nueve arquetipos) = {@code builtin.yaml} (los
 * nueve traductores) = los nueve archivos en {@code generated/}.
 */
class WeaponPackBuiltInTest {

    private static final Path GENERATED = Path.of("src", "generated", "resources", "data",
            "cortescombat", "tags", "item", "category");
    private static final Path BUILTIN = Path.of("src", "main", "resources", "data",
            "cortescombat", "weapon_packs", "builtin.yaml");

    /** Cada arquetipo tiene SU traductor en el built-in, con el mismo nombre. */
    @Test
    void builtInTranslatesEveryCategory() {
        WeaponPackLoader.Result builtIn = WeaponPackLoader.loadBuiltIn();
        assertTrue(builtIn.warnings().isEmpty(), "el builtin del classpath no avisa: "
                + builtIn.warnings());
        List<WeaponPackSpec.Entry> entries = builtIn.packs().get(0).weapons();

        Set<String> types = new HashSet<>(ArchetypeSpec.builtins().keySet());
        assertEquals(types.size(), entries.size(),
                "nueve arquetipos y nueve entradas: una lista sin su otra mitad es un "
                        + "traductor que nadie invoca");

        for (String type : types) {
            WeaponPackSpec.Entry found = entries.stream()
                    .filter(e -> ("#cortescombat:category/" + type).equals(e.tag()))
                    .findFirst().orElse(null);
            assertNotNull(found, "falta el traductor de category/" + type);
            assertEquals(type, found.archetype(),
                    "category/" + type + " traduce a archetype " + type + " -- el mismo string "
                            + "en los dos lados, para que no haya traducción que recordar");
        }
    }

    /** Los nueve archivos vacíos en disco: el tag que un datapack de tercero va a poblar. */
    @Test
    void everyCategoryHasItsEmptyFileOnDisk() throws IOException {
        for (String type : ArchetypeSpec.builtins().keySet()) {
            Path file = GENERATED.resolve(type + ".json");
            assertTrue(Files.exists(file),
                    "falta " + file + " -- sin archivo el datapack de otro no tiene puerta: "
                            + "un tag vacío es la especificación, uno ausente es el bug");
            String json = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(json.contains("\"values\": []") || json.contains("\"values\":[]"),
                    type + ".json debería estar vacío: los miembros los pone un datapack, no "
                            + "nosotros (decisión #9)");
        }
    }

    /** El archivo fuente del built-in existe y es el del classpath, no otro. */
    @Test
    void theBuiltInFileIsWhereItIsSupposedToBe() throws IOException {
        assertTrue(Files.exists(BUILTIN),
                "builtin.yaml no está en data/cortescombat/weapon_packs/: loadBuiltIn lo lee "
                        + "del classpath y sin él el traductor desaparece en silencio");
        String yaml = Files.readString(BUILTIN, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("version: 1"), "y trae su versión");
        assertTrue(yaml.contains("category/hammer"), "y traduce al menos la categoría del nombre");
    }

    /** Un item en la categoría recibe el arquetipo entero de punta a punta. */
    @Test
    void anItemAtagIsAWeaponEndToEnd() {
        WeaponPackLoader.Result builtIn = WeaponPackLoader.loadBuiltIn();
        ResolvedWeapon resolved = PackResolver.resolve("thirdmod:bone_knife",
                Set.of("cortescombat:category/dagger")::contains, builtIn.packs());
        assertNotNull(resolved, "una membership basta: cero YAML del tercero");
        assertEquals(com.cortes.combat.core.weapon.WeaponProfile.DAGGER, resolved.profile());
        assertEquals("tag=#cortescombat:category/dagger", resolved.source());
    }
}
