package com.cortes.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * La fase de los temblores. Pura, así que corre en una JVM pelada -- que es lo que hace falta, porque
 * {@code Mth} es de Minecraft y no se puede cargar acá.
 *
 * <p>El bug que esta clase guarda: {@code Mth.sin} es una tabla y su índice <b>satura</b> con ángulos
 * grandes, así que el temblor de matar no hacía <b>nada</b> en un mundo con el reloj alto. Ver
 * {@link ShakePhase} para las cuentas y el dato medido.</p>
 */
class ShakePhaseTest {

    /** El reloj del mundo de prueba, leído del {@code level.dat}: {@code Time = 285340}. */
    private static final float WORLD_CLOCK = 285340.0F;

    /** Las cuatro frecuencias que el proyecto usa: dos del temblor de matar, dos del reactor. */
    private static final float[] FREQUENCIES = {0.7F, 1.9F, 3.1F, 4.7F};

    /** La edad más larga que un impulso puede tener, en ticks. */
    private static final float LONGEST_AGE = 6.0F;

    /**
     * <b>Con el reloj del mundo real, sin envolver, la tabla saturaba.</b>
     *
     * <p>Es la mitad que hace del resto una prueba y no una tautología, y encima reproduce la
     * asimetría exacta del reporte: el temblor de matar muerto y el del reactor vivo.</p>
     */
    @Test
    void theRawAngleOfTheRealWorldSaturates() {
        // Los dos terminos del temblor de matar, por encima del umbral.
        assertTrue(WORLD_CLOCK * 3.1F > ShakePhase.MAX_SAFE_ANGLE,
                "el pitch del temblor de matar no saturaba, asi que el reporte no se explica por esto");
        assertTrue(WORLD_CLOCK * 4.7F > ShakePhase.MAX_SAFE_ANGLE,
                "el roll del temblor de matar no saturaba");
        // Y el pitch del reactor NO saturaba: por eso el reactor si temblaba. La asimetria ES el bug.
        assertTrue(WORLD_CLOCK * 0.7F < ShakePhase.MAX_SAFE_ANGLE,
                "el pitch del reactor saturaba, asi que el reactor tampoco deberia temblar");
        // Y su roll SI, o sea que el reactor tambien tiene un termino muerto -- y no se notaba.
        assertTrue(WORLD_CLOCK * 1.9F > ShakePhase.MAX_SAFE_ANGLE,
                "el roll del reactor no saturaba");
    }

    /** Y envuelto nunca satura, con ninguna de las cuatro frecuencias ni con el reloj del mundo. */
    @Test
    void theWrappedAngleCannotSaturate() {
        for (float frequency : FREQUENCIES) {
            float wrapped = ShakePhase.angle(WORLD_CLOCK, frequency);
            assertTrue(wrapped >= 0.0F && wrapped < ShakePhase.TURN,
                    "con frecuencia " + frequency + " el angulo envuelto salio " + wrapped);
            assertTrue(wrapped < ShakePhase.MAX_SAFE_ANGLE,
                    "con frecuencia " + frequency + " el angulo " + wrapped + " todavia satura");
        }
    }

    /**
     * Y la fase es <b>la misma</b>: el seno de la envuelta coincide con el de la real.
     *
     * <p>Se prueba sobre la <b>edad</b>, que es lo que el código pasa de verdad -- 0 a 6 ticks --, así
     * que la tolerancia puede ser apretada: con el reloj del mundo el {@code float} de millones tiene
     * una resolución de décimas y el módulo arrastraría ese error.</p>
     */
    @Test
    void theWrapPreservesThePhase() {
        for (float age = 0.0F; age <= LONGEST_AGE; age += 0.25F) {
            for (float frequency : FREQUENCIES) {
                double truth = Math.sin((double) age * frequency);
                double wrapped = Math.sin(ShakePhase.angle(age, frequency));
                assertEquals(truth, wrapped, 1.0e-4,
                        "frecuencia " + frequency + " con edad " + age);
            }
        }
    }

    /**
     * Y envolver <b>no deja escalón</b>, que es la trampa de este arreglo.
     *
     * <p>El período del ángulo es {@code 2π} y <b>no</b> {@code 65536}: ese número es el tamaño de la
     * tabla, no su período. Envolver con {@code 65536} dejaría un salto de {@code 2,376} radianes --
     * un tirón de hasta 2,0 en el seno --, así que este test lo muerde: con un paso de 0,001 ticks el
     * salto legítimo es de 0,0094, y el del error sería de hasta 2,0.</p>
     */
    @Test
    void theWrapLeavesNoStepInTheSine() {
        for (float frequency : FREQUENCIES) {
            float turn = ShakePhase.TURN / frequency;
            double before = Math.sin(ShakePhase.angle(turn - 0.001F, frequency));
            double after = Math.sin(ShakePhase.angle(turn + 0.001F, frequency));
            assertTrue(Math.abs(after - before) < 0.02,
                    "con frecuencia " + frequency + " el seno pega un escalon de "
                            + Math.abs(after - before) + " al envolver");
        }
    }

    /**
     * Y el techo de la suma recorta de verdad, <b>conservando el signo</b>.
     *
     * <p>El signo es la mitad que importa y la que un recorte ingenuo rompe: el roll de un impulso
     * lateral es negativo, así que un {@code Math.min(degrees, techo)} -- que es lo primero que sale
     * escribir -- apagaría todos los impulsos hacia un lado en vez de limitarlos. Y son la mitad de
     * los que hay: el zumbido de una flecha trae su lado justamente para eso.</p>
     */
    @Test
    void theTotalIsClamped() {
        assertEquals(ShakePhase.MAX_TOTAL_DEGREES, ShakePhase.clampTotal(99.0F), 1.0e-6F);
        assertEquals(-ShakePhase.MAX_TOTAL_DEGREES, ShakePhase.clampTotal(-99.0F), 1.0e-6F);
        // Y por debajo del techo no toca nada.
        assertEquals(1.5F, ShakePhase.clampTotal(1.5F), 1.0e-6F);
        assertEquals(-1.5F, ShakePhase.clampTotal(-1.5F), 1.0e-6F);
        assertEquals(0.0F, ShakePhase.clampTotal(0.0F), 1.0e-6F);
    }
}
