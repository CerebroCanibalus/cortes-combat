package com.cortes.combat.particle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * El reparto de los puntos. Puro, así que corre en una JVM pelada.
 *
 * <p>Es la parte con aritmética de verdad de la lectura de daño, y la única que se puede testear:
 * {@link HitParticles} nombra un {@code ServerLevel} y {@link com.cortes.combat.attack.DamageSplit}
 * nombra {@code CombatRules}, así que ninguno de los dos arranca sin el juego.</p>
 */
class HitReadoutTest {

    /**
     * <b>El invariante.</b>
     *
     * <p>La cantidad de puntos <em>es</em> el daño según la regla de vanilla, así que un reparto que
     * no sume exacto rompe lo único que el jugador puede contar. Es la aserción que hace que
     * redondear cada balde por su lado -- que daría 7, 2 y 1 donde había 5 -- muera.</p>
     */
    @Test
    void theTotalAlwaysClosesExactly() {
        float[][] cases = {
                {7.3F, 2.1F, 0.6F},
                {1.0F, 1.0F, 1.0F},
                {0.1F, 0.1F, 99.8F},
                {5.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 3.0F},
                {2.5F, 2.5F, 0.0F},
                {33.3F, 33.3F, 33.4F}
        };
        for (float[] amounts : cases) {
            for (int total = 1; total <= 40; total++) {
                int[] counts = HitReadout.distribute(amounts, total);
                assertEquals(total, HitReadout.sum(counts),
                        "con " + total + " puntos y " + java.util.Arrays.toString(amounts));
            }
        }
    }

    /** Un solo color con peso se lleva todo, y los otros quedan en cero. */
    @Test
    void oneColourTakesEverything() {
        assertArrayEquals(new int[]{9, 0, 0}, HitReadout.distribute(new float[]{5.0F, 0.0F, 0.0F}, 9));
        assertArrayEquals(new int[]{0, 0, 9}, HitReadout.distribute(new float[]{0.0F, 0.0F, 5.0F}, 9));
    }

    /** Un balde sin peso nunca recibe un punto, ni siquiera uno por redondeo. */
    @Test
    void anEmptyBucketNeverGetsAPoint() {
        int[] counts = HitReadout.distribute(new float[]{1.0F, 0.0F, 1.0F}, 7);
        assertEquals(0, counts[1], "un balde sin daño no puede pintar un punto");
    }

    /**
     * Los puntos que sobran van a la fracción más grande.
     *
     * <p>2/3 y 1/3 de 2 puntos son 1,33 y 0,67: los dos caen a 1 y 0, sobra uno, y va al primero --
     * que tiene la fracción mayor. Con el reparto al revés el color dominante sería el minoritario,
     * y el jugador leería la proporción invertida.</p>
     */
    @Test
    void theLeftoverGoesToTheBiggestFraction() {
        assertArrayEquals(new int[]{1, 1, 0}, HitReadout.distribute(new float[]{2.0F, 1.0F, 0.0F}, 2));
        assertArrayEquals(new int[]{2, 1, 0}, HitReadout.distribute(new float[]{2.0F, 1.0F, 0.0F}, 3));
    }

    /** Sin puntos no hay nada que repartir. */
    @Test
    void nothingToDistributeIsAllZeros() {
        assertArrayEquals(new int[]{0, 0, 0}, HitReadout.distribute(new float[]{5.0F, 3.0F, 1.0F}, 0));
        assertArrayEquals(new int[]{0, 0, 0}, HitReadout.distribute(new float[]{5.0F, 3.0F, 1.0F}, -4));
    }

    /**
     * Sin daño positivo no se inventa un color.
     *
     * <p>El caso existe de verdad: un golpe parado <b>entero</b> por la armadura tiene un
     * {@code dealt} de cero, y repartir el total entre pesos que suman cero pintaría puntos de un
     * color que ninguna cuenta respalda. La respuesta correcta es no pintar nada -- la armadura ya
     * lo comió todo y eso lo dice el balde azul, que sí tiene peso.</p>
     */
    @Test
    void weightsThatSumToZeroPaintNothing() {
        assertArrayEquals(new int[]{0, 0, 0}, HitReadout.distribute(new float[]{0.0F, 0.0F, 0.0F}, 6));
    }

    /** Los pesos negativos -- que no deberían existir -- no roban puntos. */
    @Test
    void negativeWeightsAreIgnored() {
        int[] counts = HitReadout.distribute(new float[]{4.0F, -2.0F, 0.0F}, 5);
        assertEquals(5, counts[0]);
        assertEquals(0, counts[1]);
        assertTrue(HitReadout.sum(counts) == 5);
    }
}
