package com.cortes.combat.weaponpack;

import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.AutoProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El contrato del generador: un YAML que escribe el mod y que <b>el loader de al lado acepta
 * sin una sola advertencia</b>.
 *
 * <p><b>El test es el round-trip y no la forma del texto.</b> Afirmar "el archivo dice
 * version: 1" no prueba que se pueda cargar; afirmar que {@code WeaponPackLoader.fromYaml}
 * sobre la salida del generador vuelve con cero warnings y las entradas correctas prueba lo
 * unico que importa: el archivo que el mod escribe es un archivo que el mod lee. Es la misma
 * pregunta que la ronda del sprite del arcabuz contesto mal una vez -- un archivo que existe y
 * un archivo que el juego usa son dos cosas.</p>
 *
 * <p><b>Y por eso el test habla con {@link AutoPackYaml} y no con {@code AutoPackGenerator}:</b>
 * el generador importa {@code Item}/{@code ItemStack} y esta JVM no tiene Minecraft. El cuerpo
 * puro vive separado justamente para que este archivo exista. (⌒‿⌒)</p>
 */
class AutoPackGeneratorTest {

    private static AutoPackYaml.Detected detected(String id, String archetype, Float longReach,
                                                  AutoProfile.OreTier tier) {
        return new AutoPackYaml.Detected(id, archetype, longReach, tier);
    }

    @Test
    @DisplayName("a spear is the trident's molde")
    void aSpearIsTheTridentMolde() {
        // La decision del dueno, afirmada en el builtin y no solo en el javadoc: "las lanzas
        // usan automaticamente el sistema del tridente".
        ArchetypeSpec spear = ArchetypeSpec.builtins().get("spear");
        assertNotNull(spear, "el arquetipo spear existe");
        assertEquals(WeaponProfile.TRIDENT, spear.profile(),
                "una lanza se mueve, alcanza y carga como el tridente");
        assertEquals(SpecialType.NONE, spear.special(),
                "el especial es NONE, como el tridente: el lanzamiento es de vanilla");
    }

    @Test
    @DisplayName("the generated file is a file the loader accepts, with the entries in it")
    void theLoaderAcceptsWhatTheGeneratorWrites() {
        String yaml = AutoPackYaml.of("testmod", List.of(
                detected("testmod:steel_halberd", "polearm", null, AutoProfile.OreTier.STEEL),
                detected("testmod:iron_dagger", "dagger", null, AutoProfile.OreTier.IRON),
                detected("testmod:wood_longsword", "sword", AutoProfile.LONGSWORD_REACH,
                        AutoProfile.OreTier.WOODEN)));

        WeaponPackLoader.Result parsed = WeaponPackLoader.fromYaml("auto-testmod.yaml", yaml);
        assertTrue(parsed.warnings().isEmpty(),
                "el generador escribe lo que el loader lee: " + parsed.warnings());
        assertTrue(parsed.errors().isEmpty(), "y sin errores: " + parsed.errors());
        assertEquals(1, parsed.packs().size(), "un archivo, un pack");

        WeaponPackSpec pack = parsed.packs().get(0);
        assertEquals(3, pack.weapons().size(), "las tres armas detectadas estan");
        // Secciones ordenadas por arquetipo (dagger, sword, polearm) e ids alfabeticos adentro.
        assertEquals("dagger", pack.weapons().get(0).archetype(), "la daga abre el archivo");
        assertEquals("testmod:iron_dagger", pack.weapons().get(0).id());
        assertEquals("sword", pack.weapons().get(1).archetype(), "despues la larga");
        assertEquals(4.1F, pack.weapons().get(1).reach().distance(), 1.0E-4F,
                "la espada larga lleva su alcance, escrito y editable");
        assertEquals("polearm", pack.weapons().get(2).archetype(), "y la alabarda cierra");
        assertNull(pack.weapons().get(2).reach(),
                "una alabarda no lleva reach escrita: la del molde manda");
    }

    @Test
    @DisplayName("the tier rides along as a comment, and never as a stat")
    void theTierIsACommentAndNotANumber() {
        String yaml = AutoPackYaml.of("testmod", List.of(
                detected("testmod:diamond_spear", "spear", null, AutoProfile.OreTier.DIAMOND)));

        assertTrue(yaml.contains("# tier: DIAMOND"),
                "el material se anota para que el usuario sepa que linea tocar");
        assertTrue(yaml.contains("AUTOGENERADO POR CORTES COMBAT"),
                "y el archivo dice quien lo escribio y como se regenera");
        assertTrue(yaml.contains("version: 1"), "la version es obligatoria para el loader");
        // El dano JAMAS se declara: pisar el atributo del arma ajena es pelear contra el
        // balance del mod que la hizo. La unica escala automatica es la carga, en el codigo.
        assertFalse(yaml.contains("damage:"),
                "el generador no escribe dano: el arma ya trae el suyo por material");
    }

    @Test
    @DisplayName("the sections walk the archetype order, and the ids are alphabetical")
    void theSectionsAreOrdered() {
        String yaml = AutoPackYaml.of("testmod", List.of(
                detected("testmod:z_axe", "axe", null, AutoProfile.OreTier.IRON),
                detected("testmod:b_dagger", "dagger", null, AutoProfile.OreTier.IRON),
                detected("testmod:a_dagger", "dagger", null, AutoProfile.OreTier.IRON)));

        int daggers = yaml.indexOf("# ---- dagger");
        int axes = yaml.indexOf("# ---- axe");
        assertTrue(daggers > 0 && axes > daggers,
                "la seccion de dagas va antes de la de hachas: el orden de la portada");
        assertTrue(yaml.indexOf("testmod:a_dagger") < yaml.indexOf("testmod:b_dagger"),
                "y los ids adentro, alfabeticos: el archivo se lee de arriba abajo");
    }
}
