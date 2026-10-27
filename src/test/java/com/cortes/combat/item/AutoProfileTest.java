package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
