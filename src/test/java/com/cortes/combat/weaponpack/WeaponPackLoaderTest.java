package com.cortes.combat.weaponpack;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La frontera entre un archivo escrito por una persona y los DTOs: aquí se afirma qué se acepta,
 * qué avisa y qué se niega.
 *
 * <p>El contrato entero es de tres filas: <b>version obligatoria</b>, <b>tipo equivocado =
 * warning con nombre de archivo y el campo se cae</b>, <b>sintaxis rota = excepción que nombra
 * el archivo</b>. Nada de esto es Minecraft, y por eso corre en JVM pelada.
 */
class WeaponPackLoaderTest {

    @TempDir
    Path tempDir;

    private static final String FULL = """
            # Cabecera de comentario: sobrevive, no es campo.
            version: 1
            archetypes:
              chef:
                profile: dagger
                special: parry
                charge: false
            weapons:
              - match: { tag: "#mymod:knives" }
                archetype: chef
                stats:
                  damage: 4.5
                  attack_speed: 1.6
                  charge: { can_charge: false, bonus: 1.0, full_charge_ticks: 25 }
                  knockback: { horizontal: 1.2, vertical: 0.3 }
                reach: { distance: 2.6 }
                cone: { horizontal: 22, vertical: 18 }
                special: lunge
                texture: { slash: mymod:slash.png }
                sounds: { swing: mymod:sw }
                animation: { pose: mymod:chef }
                display: { name: "Cuchillo de Rubí", tab: cortescombat }
            """;

    @Test
    void fullDocumentParses() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("ejemplo.yaml", FULL);
        assertTrue(result.warnings().isEmpty(), "documento limpio no avisa: " + result.warnings());
        WeaponPackSpec pack = result.packs().get(0);
        assertEquals("ejemplo.yaml", pack.origin());

        WeaponPackSpec.Entry entry = pack.weapons().get(0);
        assertEquals("#mymod:knives", entry.tag(), "el tag se guarda tal cual, con almohadilla");
        assertEquals("chef", entry.archetype());
        assertEquals(4.5F, entry.stats().damage());
        assertEquals(1.0F, entry.stats().chargeBonus());
        assertEquals(25, entry.stats().fullChargeTicks());
        assertEquals(1.2F, entry.stats().knockback().horizontal());
        assertEquals(Boolean.FALSE, entry.stats().canCharge());
        assertEquals(2.6F, entry.reach().distance(), "reach es SÓLO distancia: parámetro separado");
        assertEquals(22.0F, entry.cone().horizontal(),
                "el cono es radio en crudo por eje, sin bandas");
        assertEquals(18.0F, entry.cone().vertical());
        assertEquals("lunge", entry.special());
        assertEquals("mymod:slash.png", entry.slash());
        assertEquals("mymod:chef", entry.pose());
        assertEquals("Cuchillo de Rubí", entry.display().name(), "comillas y acentos enteros");

        ArchetypeSpec chef = pack.archetypes().get("chef");
        assertNotNull(chef);
        assertEquals(WeaponProfile.DAGGER, chef.profile());
        assertEquals(Boolean.FALSE, chef.canCharge());
    }

    /** `version` obligatoria y sólo la 1: parsear a medias un formato que va a crecer es trampa. */
    @Test
    void versionIsMandatoryAndOnlyOneIsKnown() {
        WeaponPackLoader.ParseException missing = assertThrows(
                WeaponPackLoader.ParseException.class,
                () -> WeaponPackLoader.fromYaml("sin-version.yaml", "weapons: []\n"));
        assertEquals("sin-version.yaml", missing.origin(),
                "la excepción nombra el archivo: sin eso el jugador no sabe qué abrir");
        assertTrue(missing.getMessage().contains("version"));

        WeaponPackLoader.ParseException future = assertThrows(
                WeaponPackLoader.ParseException.class,
                () -> WeaponPackLoader.fromYaml("futuro.yaml", "version: 2\n"));
        assertTrue(future.getMessage().contains("futuro.yaml"));
        assertTrue(future.getMessage().contains("2"));
    }

    /** Tipo equivocado: warning con archivo::campo y el campo se cae, nunca un crash. */
    @Test
    void badTypesWarnAndFallThrough() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("malo.yaml", """
                version: 1
                weapons:
                  - match: { id: x }
                    stats: { damage: alto, durability: true }
                """);
        WeaponPackSpec.Entry entry = result.packs().get(0).weapons().get(0);
        assertNull(entry.stats().damage(), "un string donde va un número no vuelve número");
        assertNull(entry.stats().durability(), "ni un booleano donde va un entero");
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("malo.yaml"), "warning con el archivo");
        assertTrue(result.warnings().get(0).contains("damage"), "y con el campo");
    }

    /** Campo desconocido y entrada sin identidad: los dos se nombran, los dos se ignoran. */
    @Test
    void unknownFieldsAndIdentitylessEntriesWarn() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("raro.yaml", """
                version: 1
                potencia: 9000
                weapons:
                  - archetype: dagger
                """);
        assertEquals(2, result.warnings().size(), "uno por campo desconocido, otro por identidad");
        assertTrue(result.warnings().get(0).contains("potencia"));
        assertTrue(result.warnings().get(1).contains("identidad"),
                "una entrada que nunca matchea es un error del autor, no un no-op silencioso");
    }

    /** Sintaxis rota: la excepción de SnakeYAML (con su línea) envuelta en el nombre del archivo. */
    @Test
    void syntaxErrorNamesTheFile() {
        WeaponPackLoader.ParseException broken = assertThrows(
                WeaponPackLoader.ParseException.class,
                () -> WeaponPackLoader.fromYaml("roto.yaml", "version: 1\nweapons: [\n"));
        assertTrue(broken.getMessage().contains("roto.yaml"));
    }

    /** El orden de precedencia sale del loader: built-in, después config alfabético. */
    @Test
    void loadOrderIsBuiltInThenAlphabetical() throws Exception {
        Files.writeString(tempDir.resolve("z-ultimo.yaml"), "version: 1\nweapons: []\n",
                StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("a-primero.yaml"), "version: 1\nweapons: []\n",
                StandardCharsets.UTF_8);

        WeaponPackLoader.Result result = WeaponPackLoader.loadAll(tempDir);
        List<String> origins = result.packs().stream().map(WeaponPackSpec::origin).toList();
        assertEquals(List.of("builtin.yaml", "a-primero.yaml", "z-ultimo.yaml"), origins,
                "built-in menor, y dentro de config lo alfabético posterior gana");
    }

    /** El builtin no encontrado avisa: un recurso que desaparece es un bug, no un estado. */
    @Test
    void missingBuiltInWouldWarnButIsPresent() {
        WeaponPackLoader.Result builtIn = WeaponPackLoader.loadBuiltIn();
        assertEquals(1, builtIn.packs().size(), "el builtin.yaml del mod está en el classpath");
        assertEquals("builtin.yaml", builtIn.packs().get(0).origin());
        assertTrue(builtIn.warnings().isEmpty());
    }

    /** Un archivo roto no aborta el directorio: se nombra y los demás siguen entrando. */
    @Test
    void aBrokenFileDoesNotAbortTheDirectory() throws Exception {
        Files.writeString(tempDir.resolve("a-bueno.yaml"), "version: 1\nweapons: []\n",
                StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("b-roto.yaml"), "version: 99\n",
                StandardCharsets.UTF_8);

        WeaponPackLoader.Result result = WeaponPackLoader.loadDirectory(tempDir);
        assertEquals(1, result.packs().size(), "el bueno entró");
        assertEquals("a-bueno.yaml", result.packs().get(0).origin());
        assertEquals(1, result.warnings().size(), "y el roto quedó como warning, no como crash");
        assertTrue(result.warnings().get(0).contains("b-roto.yaml"),
                "el warning nombra el archivo malo, no el estado del mundo");
    }

    /** La banda vieja no es "campo desconocido": enseña el mapa exacto a escribir. */
    @Test
    void theOldBandTeachesTheNewMap() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("banda.yaml", """
                version: 1
                weapons:
                  - match: { id: x }
                    cone: small
                """);
        assertNull(result.packs().get(0).weapons().get(0).cone(),
                "la banda ya no se convierte en nada: es un apodo, y un apodo no deja elegir");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("cone")
                        && w.contains("cone: {horizontal: 22, vertical: 18}")),
                "y el warning dice el mapa a escribir, no un 'desconocido' que deja adivinando");
    }

    /** `creates` es el Nivel B: el id que el mod va a registrar, con su display. */
    @Test
    void createsParsesItsIdAndDisplay() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("crea.yaml", """
                version: 1
                weapons:
                  - creates: mymod:ruby_knife
                    archetype: dagger
                    display: { name: "Cuchillo de Rubí", tab: cortescombat }
                """);
        WeaponPackSpec.Entry entry = result.packs().get(0).weapons().get(0);
        assertEquals("mymod:ruby_knife", entry.creates());
        assertEquals(2, entry.specificity(), "un creates es identidad de nivel id");
        assertEquals("Cuchillo de Rubí", entry.display().name());
        assertEquals("cortescombat", entry.display().tab());
        assertTrue(result.warnings().isEmpty());
    }

    /** El pipeline completo: texto -> DTO -> resolver. Un archivo de dos líneas es un arma. */
    @Test
    void fromTextToResolvedWeapon() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("dos-lineas.yaml", """
                version: 1
                weapons:
                  - match: { tag: knives }
                    archetype: dagger
                """);
        ResolvedWeapon resolved = PackResolver.resolve("mymod:ruby",
                List.of("knives"), result.packs());
        assertNotNull(resolved);
        assertEquals(WeaponProfile.DAGGER, resolved.profile());
        assertEquals("tag=knives", resolved.source());
        assertNull(resolved.damage(), "nada escrito: los números los hereda el molde");
    }

    /** `strict` sin problemas: carga igual que sin él, cero warnings y cero errores. */
    @Test
    void strictCleanFileLoads() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("limpio.yaml", """
                version: 1
                strict: true
                weapons:
                  - match: { id: mymod:ruby_knife }
                    archetype: dagger
                """);
        assertEquals(1, result.packs().size());
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.errors().isEmpty());
    }

    /** `strict` con campo desconocido: el archivo no carga y el error lo dice. */
    @Test
    void strictUnknownFieldFailsTheFile() {
        WeaponPackLoader.ParseException strict = assertThrows(
                WeaponPackLoader.ParseException.class,
                () -> WeaponPackLoader.fromYaml("estricto.yaml", """
                        version: 1
                        strict: true
                        potencia: 9000
                        weapons:
                          - match: { id: x }
                        """));
        assertTrue(strict.isStrict());
        assertTrue(strict.getMessage().contains("estricto.yaml"));
        assertTrue(strict.getMessage().contains("potencia"));
    }

    /** Sin `strict`, el mismo campo desconocido es warning y el archivo sí carga. */
    @Test
    void nonStrictUnknownFieldWarnsAndLoads() {
        WeaponPackLoader.Result result = WeaponPackLoader.fromYaml("raro.yaml", """
                version: 1
                potencia: 9000
                weapons:
                  - match: { id: x }
                """);
        assertEquals(1, result.packs().size(), "el archivo carga igual");
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("potencia"));
    }

    /** `strict` con tipo que no corresponde: también es error de carga, no warning. */
    @Test
    void strictWrongTypeFailsTheFile() {
        assertThrows(WeaponPackLoader.ParseException.class,
                () -> WeaponPackLoader.fromYaml("tipos.yaml", """
                        version: 1
                        strict: true
                        weapons:
                          - match: { id: x }
                            stats: { damage: alto }
                        """));
    }
}
