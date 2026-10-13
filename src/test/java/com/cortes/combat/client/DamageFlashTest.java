package com.cortes.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * La aritmética del flash. Pura, así que corre en una JVM pelada.
 *
 * <p>El mixin que la usa <b>no arranca sin el juego</b>, así que si los números vivieran adentro no
 * habría una sola aserción mirándolos.</p>
 *
 * <p><b>Y este archivo tiene una versión anterior que ya no existe:</b> probaba un gradiente por
 * columna que hacía que el rojo dijera la vida restante. El playtest lo desmintió -- los mobs salían
 * negros -- y los tests del gradiente se fueron con él. Ver {@link DamageFlash}.</p>
 */
class DamageFlashTest {

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
     * <b>El invariante.</b>
     *
     * <p>El alpha es cuánto <em>sobrevive</em> el color de la entidad, así que un alpha más chico es
     * más rojo. Se escribe como la propiedad -- "es más rojo" -- y no como el número, porque el
     * número es lo que se va a querer retunear después de un playtest.</p>
     */
    @Test
    void theFlashIsStrongerThanVanilla() {
        assertTrue(alpha(DamageFlash.STRONGER_RED) < alpha(DamageFlash.VANILLA_RED),
                "el flash quedaría igual o más débil que el de vanilla");
    }

    /**
     * Y no tan fuerte que el mob deje de leerse.
     *
     * <p>Por debajo de 0x40 el flash tapa la animación en vez de acompañarla, y el mob se vuelve una
     * silueta. <b>Este piso es el que el gradiente violaba en su punta</b> -- el playtest lo
     * encontró antes que el test, y por eso el gradiente se fue.</p>
     */
    @Test
    void butNotSoStrongThatTheMobDisappears() {
        assertTrue(DamageFlash.FLASH_ALPHA >= 0x40,
                "con alpha " + DamageFlash.FLASH_ALPHA + " el mob queda tapado por el flash");
    }

    /**
     * Las filas blancas pasan intactas, y es la mitad que no se está arreglando.
     *
     * <p>Su alpha varía por columna y es el gradiente entero lo que hace funcionar el flash blanco
     * -- el del creeper y el de la muerte. Tocarlo rompería algo que nadie pidió, y además es la
     * fila que el flash de la muerte usa.</p>
     */
    @Test
    void theWhiteRowsPassThroughUntouched() {
        for (int alpha = 0; alpha <= 255; alpha += 51) {
            int white = (alpha << 24) | 0xFFFFFF;
            assertEquals(white, DamageFlash.strengthen(white),
                    "una fila blanca fue modificada con alpha " + alpha);
        }
    }

    /** Y el rojo no es el blanco: si fueran iguales, el test de arriba no probaría nada. */
    @Test
    void theTwoHalvesAreActuallyDifferent() {
        assertNotEquals(DamageFlash.VANILLA_RED, (0xFF << 24) | 0xFFFFFF);
    }

    /** Sólo cambia el alpha: el rojo sigue siendo rojo, en los tres canales. */
    @Test
    void theColourItselfIsUntouched() {
        for (int shift : new int[]{16, 8, 0}) {
            assertEquals(channel(DamageFlash.VANILLA_RED, shift),
                    channel(DamageFlash.STRONGER_RED, shift),
                    "el canal " + shift + " cambió: el flash dejó de ser el mismo color");
        }
    }

    /** El blanco de la muerte entra de a poco a lo largo de la animación, que dura veinte ticks. */
    @Test
    void theDeathWhiteRampsUp() {
        assertEquals(0.0F, DamageFlash.whiteForDeath(0), 1.0e-6F);
        assertEquals(0.5F, DamageFlash.whiteForDeath(10), 1.0e-6F);
        assertEquals(1.0F, DamageFlash.whiteForDeath(20), 1.0e-6F);
        // Y si el tick se pasa -- que pasa, porque el cuerpo sigue un tick más -- no se sale.
        assertEquals(1.0F, DamageFlash.whiteForDeath(25), 1.0e-6F);
        // Y un tick negativo -- que no debería existir -- tampoco.
        assertEquals(0.0F, DamageFlash.whiteForDeath(-3), 1.0e-6F);
    }
}
