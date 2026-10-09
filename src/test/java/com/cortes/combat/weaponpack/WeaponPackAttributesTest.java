package com.cortes.combat.weaponpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La aritmética del puente de atributos, que es lo único de él testeable sin un juego.
 *
 * <p>La regla que afirma: <b>el pack escribe corazones y toques por segundo -- lo que el jugador
 * lee -- y la conversión al atributo vive en un solo lugar.</b> Si esta función se equivoca, el
 * tooltip y el golpe se equivocan juntos, que es al menos un error coherente; si viviera
 * inline en el evento, nadie podría verla.
 */
class WeaponPackAttributesTest {

    @Test
    void heartsBecomeAttackPoints() {
        // 4.5 corazones = 9 puntos de atributo; el atributo arranca en 1, modifier = 8.
        assertEquals(8.0F, WeaponPackAttributes.damageAmount(4.5F), 1e-6F);
        // 2 corazones = 4 puntos: exactamente lo que una espada de hierro declara.
        assertEquals(3.0F, WeaponPackAttributes.damageAmount(2.0F), 1e-6F);
        // 1 corazón = 2 puntos → modifier 1.
        assertEquals(1.0F, WeaponPackAttributes.damageAmount(1.0F), 1e-6F);
    }

    @Test
    void speedIsAnOffsetFromVanilla() {
        // 1.6 toques/s es la espada de vanilla: el offset -2.4 es el que el juego ya usa.
        assertEquals(-2.4F, WeaponPackAttributes.speedAmount(1.6F), 1e-6F);
        assertEquals(-1.0F, WeaponPackAttributes.speedAmount(3.0F), 1e-6F);
        // Más rápido que el default: un offset positivo, que el evento admite sin truco.
        assertEquals(1.0F, WeaponPackAttributes.speedAmount(5.0F), 1e-6F);
    }
}
