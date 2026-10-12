package com.cortes.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * La aritmética del flash. Pura, así que corre en una JVM pelada.
 *
 * <p>Los dos mixins que la usan <b>no arrancan sin el juego</b>, así que si los números vivieran
 * adentro de ellos no habría una sola aserción mirando lo único que esta ronda cambia.</p>
 */
class DamageFlashTest {

    private static final int WHITE = 0xFFFFFF;

    private static int alpha(int abgr) {
        return (abgr >>> 24) & 0xFF;
    }

    private static int channel(int abgr, int shift) {
        return (abgr >>> shift) & 0xFF;
    }

    @Test
    void theVanillaRedBecomesTheStrongerOne() {
        assertEquals(DamageFlash.STRONGER_RED, DamageFlash.strengthen(DamageFlash.VANILLA_RED));
    }

    /**
     * <b>El invariante de la ronda.</b>
     *
     * <p>El alpha es cuánto <em>sobrevive</em> el color de la entidad, así que un alpha más chico es
     * más rojo. Se escribe como la propiedad -- "es más rojo" -- y no como el número, porque el
     * número es lo que se va a retunear después de un playtest.</p>
     */
    @Test
    void theBaseFlashIsStrongerThanVanilla() {
        assertTrue(alpha(DamageFlash.BASE_ALPHA) < alpha(DamageFlash.VANILLA_RED),
                "el flash de un mob entero quedaría igual o más débil que el de vanilla");
    }

    /** Y no tan fuerte que el mob deje de leerse: el piso que la ronda anterior ya defendía. */
    @Test
    void butTheBaseIsStillReadable() {
        assertTrue(DamageFlash.BASE_ALPHA >= 0x40,
                "con alpha " + DamageFlash.BASE_ALPHA + " el mob queda tapado por el flash");
    }

    /**
     * <b>El gradiente, que es lo nuevo.</b>
     *
     * <p>De la columna 0 a la 15 el rojo tiene que subir <em>siempre</em> -- nunca bajar ni
     * quedarse. Un escalón sería un mob que al perder vida de pronto flashea <b>menos</b>, que es el
     * jugador aprendiendo el código al revés.</p>
     */
    @Test
    void theRedOnlyGrowsAcrossTheColumns() {
        int previous = DamageFlash.alphaForColumn(0);
        for (int column = 1; column <= DamageFlash.LAST_COLUMN; column++) {
            int alpha = DamageFlash.alphaForColumn(column);
            assertTrue(alpha <= previous,
                    "la columna " + column + " da alpha " + alpha + ", menos rojo que los "
                            + previous + " de la anterior");
            previous = alpha;
        }
        assertTrue(DamageFlash.alphaForColumn(DamageFlash.LAST_COLUMN)
                        < DamageFlash.alphaForColumn(0),
                "el gradiente no cambia nada: las dos puntas del rojo son iguales");
    }

    /** Las columnas fuera de rango no explotan ni se salen: la textura tiene 16. */
    @Test
    void columnsOutsideTheTextureAreClamped() {
        assertEquals(DamageFlash.alphaForColumn(0), DamageFlash.alphaForColumn(-5));
        assertEquals(DamageFlash.alphaForColumn(DamageFlash.LAST_COLUMN),
                DamageFlash.alphaForColumn(99));
    }

    /**
     * Las filas blancas pasan intactas, y es la mitad que no se está arreglando.
     *
     * <p>Su alpha varía por columna y es el gradiente entero lo que hace funcionar el flash blanco
     * -- el del creeper y el de la muerte que esta ronda agrega. Tocarlo rompería algo que nadie
     * pidió.</p>
     */
    @Test
    void theWhiteRowsPassThroughUntouched() {
        for (int column = 0; column <= DamageFlash.LAST_COLUMN; column++) {
            for (int alpha = 0; alpha <= 255; alpha += 51) {
                int white = (alpha << 24) | WHITE;
                assertEquals(white, DamageFlash.texturePixel(column, white),
                        "una fila blanca fue modificada en la columna " + column);
            }
        }
    }

    /** Y el rojo no es el blanco: si fueran iguales, el test de arriba no probaría nada. */
    @Test
    void theTwoHalvesAreActuallyDifferent() {
        assertNotEquals(DamageFlash.VANILLA_RED, (0xFF << 24) | WHITE);
    }

    /** Sólo cambia el alpha: el rojo sigue siendo rojo. */
    @Test
    void theColourItselfIsUntouched() {
        for (int column = 0; column <= DamageFlash.LAST_COLUMN; column++) {
            int red = DamageFlash.texturePixel(column, DamageFlash.VANILLA_RED);
            for (int shift : new int[]{16, 8, 0}) {
                assertEquals(channel(DamageFlash.VANILLA_RED, shift), channel(red, shift),
                        "el canal " + shift + " cambió en la columna " + column);
            }
        }
    }

    /**
     * La vida elige la columna, y va <b>al revés</b>.
     *
     * <p>Vida llena da 0 -- el rojo suave -- y vida por agotarse da 1. La columna es "cuánto se está
     * muriendo", no "cuánta vida le queda", y una inversión acá haría que un mob sano flasheara como
     * uno agonizante y al revés.</p>
     */
    @Test
    void healthPicksTheColumnInverted() {
        assertEquals(0.0F, DamageFlash.uForHealth(20.0F, 20.0F), 1.0e-6F);
        assertEquals(1.0F, DamageFlash.uForHealth(0.0F, 20.0F), 1.0e-6F);
        assertEquals(0.5F, DamageFlash.uForHealth(10.0F, 20.0F), 1.0e-6F);
        // Y con vida negativa o un máximo roto no se sale de rango.
        assertEquals(1.0F, DamageFlash.uForHealth(-5.0F, 20.0F), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.uForHealth(5.0F, 0.0F), 1.0e-6F);
    }

    /** El blanco de la muerte entra de a poco a lo largo de la animación, que dura veinte ticks. */
    @Test
    void theDeathWhiteRampsUp() {
        assertEquals(0.0F, DamageFlash.whiteForDeath(0, 20), 1.0e-6F);
        assertEquals(0.5F, DamageFlash.whiteForDeath(10, 20), 1.0e-6F);
        assertEquals(1.0F, DamageFlash.whiteForDeath(20, 20), 1.0e-6F);
        // Y si el tick se pasa -- que pasa, porque el cuerpo sigue un tick más -- no se sale.
        assertEquals(1.0F, DamageFlash.whiteForDeath(25, 20), 1.0e-6F);
    }
}
