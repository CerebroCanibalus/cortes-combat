package com.cortes.combat.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las reglas de la onda de filo, en JVM pelada.
 *
 * <p>Lo que se afirma acá no son "los números" sino <b>la forma de la tabla</b>: que subir de nivel
 * siempre pega más lejos, más fuerte, más ancho y a más gente; que el nivel cero no dispara nada; y
 * que la vida de la onda <b>alcanza para recorrer su propio alcance</b>, que es la invariante que
 * ata los dos números que podrían desincronizarse (la distancia y la vida).</p>
 */
class SweepingEdgeTest {

    @Test
    @DisplayName("nivel cero no dispara: una espada sin el encantamiento no tiene onda")
    void zeroDoesNotFire() {
        assertFalse(SweepingEdge.fires(0), "sin encantamiento no hay onda");
        assertEquals(0.0F, SweepingEdge.share(0), 1.0E-6F, "y no lleva daño");
        assertEquals(0, SweepingEdge.pierce(0), "ni atraviesa a nadie");
        assertEquals(0, SweepingEdge.lifeTicks(0), "ni vive");
    }

    @Test
    @DisplayName("los tres niveles, con los números que el dueno aprobo")
    void theThreeLevels() {
        assertEquals(0.40F, SweepingEdge.share(1), 1.0E-6F);
        assertEquals(0.60F, SweepingEdge.share(2), 1.0E-6F);
        assertEquals(0.80F, SweepingEdge.share(3), 1.0E-6F);

        assertEquals(0.7, SweepingEdge.width(1), 1.0E-6);
        assertEquals(1.0, SweepingEdge.width(2), 1.0E-6);
        assertEquals(1.4, SweepingEdge.width(3), 1.0E-6);

        assertEquals(8.0, SweepingEdge.distance(1), 1.0E-6);
        assertEquals(10.0, SweepingEdge.distance(2), 1.0E-6);
        assertEquals(12.0, SweepingEdge.distance(3), 1.0E-6);

        assertEquals(3, SweepingEdge.pierce(1));
        assertEquals(4, SweepingEdge.pierce(2));
        assertEquals(5, SweepingEdge.pierce(3));
    }

    @Test
    @DisplayName("y la tabla solo crece: ningun nivel pega menos que el anterior")
    void theTableOnlyGrows() {
        for (int level = 2; level <= SweepingEdge.MAX_LEVEL; level++) {
            assertTrue(SweepingEdge.share(level) > SweepingEdge.share(level - 1),
                    "nivel " + level + ": la porcion de dano sube");
            assertTrue(SweepingEdge.width(level) > SweepingEdge.width(level - 1),
                    "nivel " + level + ": el tajo se ensancha");
            assertTrue(SweepingEdge.distance(level) > SweepingEdge.distance(level - 1),
                    "nivel " + level + ": llega mas lejos");
            assertTrue(SweepingEdge.pierce(level) > SweepingEdge.pierce(level - 1),
                    "nivel " + level + ": atraviesa a mas");
        }
    }

    @Test
    @DisplayName("**la vida alcanza para recorrer el alcance**: la distancia y la vida no se desincronizan")
    void lifeReachesItsOwnDistance() {
        for (int level = 1; level <= SweepingEdge.MAX_LEVEL; level++) {
            double travelled = SweepingEdge.lifeTicks(level) * SweepingEdge.SPEED;
            assertTrue(travelled >= SweepingEdge.distance(level),
                    "nivel " + level + ": la onda vive " + SweepingEdge.lifeTicks(level)
                            + " ticks y recorre " + travelled + ", y su alcance es "
                            + SweepingEdge.distance(level)
                            + " -- una onda que muere antes de llegar promete un alcance que no da");
        }
    }

    @Test
    @DisplayName("los niveles fuera de rango se clampean y no explotan")
    void levelsAreClamped() {
        assertEquals(SweepingEdge.share(3), SweepingEdge.share(9), 1.0E-6F,
                "un nivel de mas vale lo que el maximo, no un indice fuera de la tabla");
        assertEquals(0.0F, SweepingEdge.share(-4), 1.0E-6F, "y uno negativo no dispara");
        assertFalse(SweepingEdge.fires(-1), "ni enciende");
        assertEquals(0, SweepingEdge.pierce(-1), "ni atraviesa");
    }
}
