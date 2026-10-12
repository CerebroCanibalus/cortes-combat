package com.cortes.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * El único número de la ronda del flash, y el motivo de que viva en una clase pura.
 *
 * <p>El mixin que lo usa <b>no se puede testear</b>: no arranca en una JVM pelada. Si el número
 * estuviera calculado adentro, esta ronda entera quedaría sin una sola aserción que la mire --
 * que es exactamente el modo de falla que este proyecto ya pagó varias veces.</p>
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
     * <b>El invariante de la ronda.</b>
     *
     * <p>El alpha es cuánto <em>sobrevive</em> el color de la entidad, así que un alpha más chico es
     * más rojo. La aserción se escribe como la propiedad -- "es más rojo" -- y no como el número,
     * porque el número es lo que se va a querer retunear después de un playtest y una aserción
     * sobre él se rompería en cada retune sin que nada esté mal.</p>
     */
    @Test
    void theFlashIsActuallyStronger() {
        int vanilla = alpha(DamageFlash.VANILLA_RED);
        int stronger = alpha(DamageFlash.STRONGER_RED);
        assertTrue(stronger < vanilla,
                "alpha " + stronger + " no es menor que el de vanilla, " + vanilla
                        + ": el flash quedaría igual o más débil, que es lo contrario del pedido");
    }

    /**
     * Y no tan fuerte que el mob deje de leerse.
     *
     * <p>Por debajo de 0x40 el flash tapa la animación en vez de acompañarla, y el mob se vuelve una
     * silueta. El techo no es un número redondo: es donde el diseño dice que el efecto se come lo
     * que está mostrando.</p>
     */
    @Test
    void butNotSoStrongThatTheMobDisappears() {
        assertTrue(alpha(DamageFlash.STRONGER_RED) >= 0x40,
                "con alpha " + alpha(DamageFlash.STRONGER_RED) + " el mob queda tapado por el flash");
    }

    /**
     * Sólo cambia el alpha: el rojo sigue siendo rojo.
     *
     * <p>Si un retune tocara los canales por accidente, el flash dejaría de ser rojo y la aserción
     * de arriba -- que sólo mira el alpha -- seguiría verde. Es la misma trampa del guard que
     * descarta la parte que pregunta.</p>
     */
    @Test
    void theColourItselfIsUntouched() {
        for (int shift : new int[]{16, 8, 0}) {
            assertEquals(channel(DamageFlash.VANILLA_RED, shift),
                    channel(DamageFlash.STRONGER_RED, shift),
                    "el canal " + shift + " cambió: el flash dejó de ser el mismo color");
        }
    }

    /**
     * Las filas blancas pasan intactas.
     *
     * <p>La textura del overlay tiene <b>dos</b> mitades y la de arriba es blanca, con un alpha que
     * varía por columna. Ese gradiente entero es lo que hace funcionar el flash blanco -- el del
     * creeper, y el de la muerte que viene -- así que tocarlo rompería algo que no se estaba
     * arreglando.</p>
     */
    @Test
    void theWhiteRowsPassThroughUntouched() {
        int[] whites = {
                (0xFF << 24) | 0xFFFFFF, // columna 0: sin overlay
                (0x3F << 24) | 0xFFFFFF, // columna 15: blanco fuerte
                (0x00 << 24) | 0xFFFFFF  // el extremo, por si el gradiente se estira
        };
        for (int white : whites) {
            assertEquals(white, DamageFlash.strengthen(white),
                    "una fila blanca fue modificada: el flash blanco dejaría de funcionar");
        }
    }

    /** Y el rojo no es el blanco: si fueran iguales, el test de arriba no probaría nada. */
    @Test
    void theTwoHalvesAreActuallyDifferent() {
        assertNotEquals(DamageFlash.VANILLA_RED, (0xFF << 24) | 0xFFFFFF);
    }
}
