package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La clasificacion automatica de armas ajenas, y la linea que la calibra: la espada de hierro
 * de vanilla (6/1.6) no se mueve, y alrededor suyo caen las demas.
 *
 * <p>Todos los numeros de estos tests son de la <b>escala del tooltip</b> (dano = 1 + sumas
 * del item, velocidad = 4 + sumas), que es la misma escala que lee el dueno cuando calibra a
 * ojo. Un clasificador en otra escala clasificaria contra un arma que no existe.</p>
 */
class AutoProfileTest {

    private final float SWORD_DAMAGE = 6.0F;
    private final float SWORD_SPEED = 1.6F;

    @Test
    @DisplayName("the vanilla sword stays the vanilla answer")
    void theVanillaSwordStaysASword() {
        // La linea de calibracion ES la espada de vanilla. Si esta asercion se mueve, la
        // clasificacion ya no ordena alrededor de lo que todos conocen.
        assertEquals(WeaponProfile.SWORD,
                AutoProfile.refineForeignSword(SWORD_DAMAGE, SWORD_SPEED),
                "la linea de calibracion es la espada: 6/1.6 no se mueve de SWORD");
    }

    @Test
    @DisplayName("a fast foreign blade is a dagger")
    void fastMeansDagger() {
        // Simply Swords katana: 6 de dano, 2.0 de velocidad. Su forma de pelear ES la daga:
        // golpes cortos seguidos, sin carga -- y el molde de la daga es exactamente eso.
        assertEquals(WeaponProfile.DAGGER, AutoProfile.refineForeignSword(6.0F, 2.0F),
                "katana de Simply Swords: rapida sin importar su dano mediano");
        assertEquals(WeaponProfile.DAGGER, AutoProfile.refineForeignSword(3.0F, 3.0F),
                "daga de Spartan: velocidad de daga, dano de daga");
        // Y una "excavadora" con velocidad de daga pero mucho dano SIGUE siendo daga -- la
        // velocidad es lo que el jugador maneja, y el molde se la entrega entera.
        assertEquals(WeaponProfile.DAGGER, AutoProfile.refineForeignSword(15.0F, 3.0F),
                "la velocidad domina: un fast-grinder es daga aunque duela");
    }

    @Test
    @DisplayName("quick and light is also a dagger, just below the speed ceiling")
    void quickAndLightMeansDagger() {
        // La ventana "rapida y liviana" encaja entre la espada (1.6) y el corte en 2.2:
        // un arma en 2.0 con dano mediano es rapida con poco detras, no una espada algo veloz.
        assertEquals(WeaponProfile.DAGGER, AutoProfile.refineForeignSword(5.0F, 2.0F),
                "liviana y en el ritmo de daga: daga aunque no llegue al corte seco");
    }

    @Test
    @DisplayName("heavy and slow means the warhammer")
    void heavyMeansWarhammer() {
        // Spartan warhammer: 10-12 de dano, ~0.9 de velocidad. El molde martillo es la relacion
        // golpes largos con peso detras, y clasificarlo espada le entrega el golpe a un arma
        // que no lo puede sostener.
        assertEquals(WeaponProfile.WARHAMMER, AutoProfile.refineForeignSword(11.0F, 0.9F),
                "martillo de Spartan: peso es peso");
        assertEquals(WeaponProfile.WARHAMMER, AutoProfile.refineForeignSword(9.0F, 1.3F),
                "el borderline exacto de la tabla tambien es martillo");
    }

    @Test
    @DisplayName("a moderate blade stays the sword the tag said")
    void moderateStaysSword() {
        // Una espada ajena media (6.5/1.7): el tag la clasifico espada y nada en sus atributos
        // contradice. La regla no rebana por gusto.
        assertEquals(WeaponProfile.SWORD, AutoProfile.refineForeignSword(6.5F, 1.7F),
                "espada ajena media queda espada");
        // Sin escapar abajo: 8 de dano en 1.2 es pesada pero no pasa el borderline del martillo.
        assertEquals(WeaponProfile.SWORD, AutoProfile.refineForeignSword(8.0F, 1.2F),
                "8/1.2 queda espada: solo el 9 con el molde de martillo cambia el molde");
    }

    @Test
    @DisplayName("the two owned namespaces are never foreign")
    void theOwnNamespacesAreNotForeign() {
        assertFalse(AutoProfile.isForeign("minecraft"), "vanilla no es ajena");
        assertFalse(AutoProfile.isForeign("cortescombat"), "la propia tampoco");
        assertTrue(AutoProfile.isForeign("magistuarmory"), "epic knights es ajena");
        assertTrue(AutoProfile.isForeign("simplyswords"), "simply swords es ajena");
    }

    // --------------------------------------------------------------------------------
    // El nombre del id: la unica fuente que dice lo que el mod QUISO fabricar.
    // --------------------------------------------------------------------------------

    @Test
    @DisplayName("a spear rides the trident's molde, by the owner's order")
    void spearsRideTheTrident() {
        // "Las lanzas usan automaticamente el sistema del tridente": el alcance largo en cono
        // angosto es la identidad del tipo, y el arquetipo `spear` la entrega entera.
        assertEquals("spear", AutoProfile.archetypeOf("iron_spear"), "spear es lanza");
        assertEquals("spear", AutoProfile.archetypeOf("diamond_lance"), "lance es lanza");
        assertEquals("spear", AutoProfile.archetypeOf("pike_of_doom"), "pike es lanza");
        assertEquals("spear", AutoProfile.archetypeOf("netherite_trident"),
                "un tridente ajeno tambien: el nombre pide el sistema entero");
    }

    @Test
    @DisplayName("a halberd family name is the polearm, not a spear and not a sword")
    void polearmsArePolearms() {
        assertEquals("polearm", AutoProfile.archetypeOf("steel_halberd"), "alabarda es polearm");
        assertEquals("polearm", AutoProfile.archetypeOf("iron_glaive"), "glaive tambien");
        assertEquals("polearm", AutoProfile.archetypeOf("naginata"), "y naginata");
    }

    @Test
    @DisplayName("a longsword is a sword with a reach of its own")
    void longswordsAreSwordsWithReach() {
        // La espada larga NO es un arquetipo nuevo: es la espada con un `reach` escrito en el
        // archivo generado. "longsword" contiene "sword", asi que la familia larga tiene que
        // preguntarse ANTES -- si no, caeria en la rama del tag y perderia el alcance.
        assertEquals("sword", AutoProfile.archetypeOf("steel_longsword"), "la larga es espada");
        assertTrue(AutoProfile.isLongsword("steel_longsword"), "y se marca como larga");
        assertEquals("sword", AutoProfile.archetypeOf("diamond_claymore"), "claymore tambien");
        assertTrue(AutoProfile.isLongsword("diamond_claymore"), "y tambien es larga");
        assertFalse(AutoProfile.isLongsword("steel_sword"), "una espada comun no lleva reach");
        assertEquals(4.1F, AutoProfile.LONGSWORD_REACH, 1.0E-4F,
                "medio bloque mas que la espada, un bloque menos que la alabarda");
    }

    @Test
    @DisplayName("the other family names land where their fight says")
    void theOtherFamiliesLandHome() {
        assertEquals("dagger", AutoProfile.archetypeOf("silver_katana"),
                "katana es daga, decision del playtest de la ronda pasada");
        assertEquals("dagger", AutoProfile.archetypeOf("short_sword"), "la corta tambien");
        assertEquals("rapier", AutoProfile.archetypeOf("estoc"), "estoc es ropera");
        assertEquals("sabre", AutoProfile.archetypeOf("iron_scimitar"), "cimitarra es sable");
        assertEquals("hammer", AutoProfile.archetypeOf("steel_mallet"), "mazo es martillo");
        assertEquals("mace", AutoProfile.archetypeOf("bone_mace"), "maza es maza");
        assertEquals("axe", AutoProfile.archetypeOf("battleaxe"), "hacha de batalla es hacha");
        // Y la espada GENERICA no llega por nombre a proposito: ese volumen lo clasifica el
        // tag `#minecraft:swords` + los atributos (refineForeignSword), que es mas honesto
        // que una lista de nombres para la familia mas comun de todas.
        assertNull(AutoProfile.archetypeOf("diamond_sword"), "una espada comun no dice nada");
        assertNull(AutoProfile.archetypeOf("mystery_thing"), "y lo desconocido tampoco");
    }

    @Test
    @DisplayName("the ore lives in the id, and the ladder is anchored on iron")
    void theOreComesFromTheName() {
        assertEquals(AutoProfile.OreTier.NETHERITE, AutoProfile.OreTier.of("netherite_dagger"));
        assertEquals(2.0F, AutoProfile.OreTier.NETHERITE.delta(), 1.0E-4F);
        assertEquals(AutoProfile.OreTier.DIAMOND, AutoProfile.OreTier.of("diamond_spear"));
        assertEquals(1.0F, AutoProfile.OreTier.DIAMOND.delta(), 1.0E-4F);
        assertEquals(AutoProfile.OreTier.STEEL, AutoProfile.OreTier.of("steel_halberd"));
        assertEquals(0.5F, AutoProfile.OreTier.STEEL.delta(), 1.0E-4F);
        // El hierro es el cero de la serie: la espada de vanilla y su dano son la linea.
        assertEquals(0.0F, AutoProfile.OreTier.of("iron_sabre").delta(), 1.0E-4F);
        assertEquals(0.0F, AutoProfile.OreTier.of("mystery_blade").delta(),
                "sin nombre de material no hay tier inventado");
        assertEquals(-1.5F, AutoProfile.OreTier.of("copper_dagger").delta(), 1.0E-4F);
        assertEquals(-1.0F, AutoProfile.OreTier.of("stone_maul").delta(), 1.0E-4F);
        assertEquals(-2.0F, AutoProfile.OreTier.of("wooden_spear").delta(), 1.0E-4F);
        assertEquals(-1.5F, AutoProfile.OreTier.of("golden_blade").delta(), 1.0E-4F);
    }

    @Test
    @DisplayName("a better ore charges faster and harder, and never below the floors")
    void theTierScalesTheCharge() {
        // El numero del molde del tridente: 25 ticks y +0.5. Netherite: 21 ticks y +0.8;
        // madera: 29 y +0.2. El dano no esta aca (el arma ya trae el suyo por material).
        assertEquals(21, AutoProfile.tieredChargeTicks(25, AutoProfile.OreTier.NETHERITE));
        assertEquals(29, AutoProfile.tieredChargeTicks(25, AutoProfile.OreTier.WOODEN));
        assertEquals(25, AutoProfile.tieredChargeTicks(25, AutoProfile.OreTier.IRON),
                "el cero del tier es identidad, no una ventana distinta");
        assertEquals(0.8F, AutoProfile.tieredChargeBonus(0.5F, AutoProfile.OreTier.NETHERITE),
                1.0E-4F);
        assertEquals(0.2F, AutoProfile.tieredChargeBonus(0.5F, AutoProfile.OreTier.WOODEN),
                1.0E-4F);
        // Los dos pisos, los dos afirmados: la ventana no baja de 5 ticks y el bono no resta.
        assertEquals(AutoProfile.MIN_FULL_CHARGE_TICKS,
                AutoProfile.tieredChargeTicks(4, AutoProfile.OreTier.NETHERITE),
                "una carga instantanea no es carga");
        assertEquals(0.0F, AutoProfile.tieredChargeBonus(0.1F, AutoProfile.OreTier.WOODEN),
                1.0E-4F, "el bono nunca resta -- el piso es el mismo de la columna del YAML");
    }
}
