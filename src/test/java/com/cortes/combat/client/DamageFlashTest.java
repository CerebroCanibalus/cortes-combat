package com.cortes.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * La paleta del flash y su curva. Puras, así que corren en una JVM pelada.
 *
 * <p>Los dos mixins que las usan <b>no arrancan sin el juego</b>, así que si los números vivieran
 * adentro no habría una sola aserción mirándolos -- y este efecto ya se equivocó una vez con el
 * playtest como único test.</p>
 */
class DamageFlashTest {

    private static int alpha(int abgr) {
        return (abgr >>> 24) & 0xFF;
    }

    private static int channel(int abgr, int shift) {
        return (abgr >>> shift) & 0xFF;
    }

    /**
     * <b>La curva, y es lo que hace que esto sea un golpe y no un tinte.</b>
     *
     * <p>{@code hurtTime} cuenta para abajo: el primer tick es el número más alto. El impacto tiene
     * que estar <b>al principio</b>, y una inversión pondría el frame negro al final -- cuando el
     * golpe ya se fue, que es el momento en que menos significa.</p>
     */
    @Test
    void theImpactComesFirst() {
        assertEquals(DamageFlash.IMPACT_ROW, DamageFlash.rowFor(DamageFlash.FLASH_TICKS));
        assertEquals(DamageFlash.IMPACT_ROW, DamageFlash.rowFor(DamageFlash.FLASH_TICKS - 1));
        assertEquals(DamageFlash.RED_ROW, DamageFlash.rowFor(DamageFlash.FLASH_TICKS - 2));
        assertEquals(DamageFlash.RED_ROW, DamageFlash.rowFor(1));
    }

    /** Y la curva nunca vuelve al impacto: un solo frame negro por golpe, no dos. */
    @Test
    void theImpactHappensExactlyOnce() {
        int impacts = 0;
        for (int hurtTime = DamageFlash.FLASH_TICKS; hurtTime >= 1; hurtTime--) {
            if (DamageFlash.rowFor(hurtTime) == DamageFlash.IMPACT_ROW) {
                impacts++;
            }
        }
        assertEquals(DamageFlash.IMPACT_TICKS, impacts,
                "el frame de impacto dura lo que dice durar, ni un tick mas");
    }

    /** Las dos filas de la paleta son distintas: si fueran la misma, la curva no haría nada. */
    @Test
    void theTwoRowsAreActuallyDifferent() {
        assertNotEquals(DamageFlash.IMPACT_ROW, DamageFlash.RED_ROW);
    }

    /** La fila del impacto escribe el negro con el alpha que el playtest produjo. */
    @Test
    void theImpactRowIsTheBlackFrame() {
        assertEquals(DamageFlash.IMPACT_BLACK, DamageFlash.texturePixel(DamageFlash.IMPACT_ROW,
                DamageFlash.VANILLA_RED));
        assertEquals(DamageFlash.IMPACT_ALPHA, alpha(DamageFlash.IMPACT_BLACK));
    }

    /** Y la del rojo, el rojo al 50 % -- más fuerte que el 30 % de vanilla. */
    @Test
    void theRedRowIsStrongerThanVanilla() {
        assertEquals(DamageFlash.STRONGER_RED, DamageFlash.texturePixel(DamageFlash.RED_ROW,
                DamageFlash.VANILLA_RED));
        assertTrue(alpha(DamageFlash.STRONGER_RED) < alpha(DamageFlash.VANILLA_RED),
                "el rojo quedaría igual o más débil que el de vanilla");
        assertTrue(DamageFlash.RED_ALPHA >= 0x40,
                "con alpha " + DamageFlash.RED_ALPHA + " el mob queda tapado por el flash");
    }

    /** Sólo cambia el alpha: el rojo sigue siendo rojo, en los tres canales. */
    @Test
    void theColourItselfIsUntouched() {
        for (int row : new int[]{DamageFlash.RED_ROW, DamageFlash.IMPACT_ROW}) {
            int written = DamageFlash.texturePixel(row, DamageFlash.VANILLA_RED);
            for (int shift : new int[]{16, 8, 0}) {
                assertEquals(channel(DamageFlash.VANILLA_RED, shift), channel(written, shift),
                        "el canal " + shift + " cambió en la fila " + row);
            }
        }
    }

    /**
     * Las filas blancas pasan intactas, y las rojas que no son nuestras también.
     *
     * <p>Las blancas porque su alpha varía por columna y es el gradiente entero lo que hace
     * funcionar el flash de la muerte. Y las rojas ajenas porque <b>no se reescribe lo que no se va
     * a usar</b>: cada fila reescrita es una que alguien puede estar consultando sin que lo
     * sepamos.</p>
     */
    @Test
    void everythingElsePassesThroughUntouched() {
        for (int alpha = 0; alpha <= 255; alpha += 51) {
            int white = (alpha << 24) | 0xFFFFFF;
            assertEquals(white, DamageFlash.texturePixel(DamageFlash.RED_ROW, white),
                    "una fila blanca fue modificada");
        }
        for (int row = 0; row <= 15; row++) {
            if (row == DamageFlash.RED_ROW || row == DamageFlash.IMPACT_ROW) {
                continue;
            }
            assertEquals(DamageFlash.VANILLA_RED,
                    DamageFlash.texturePixel(row, DamageFlash.VANILLA_RED),
                    "la fila " + row + " es de vanilla y fue reescrita");
        }
    }

    /** El blanco de la muerte entra de a poco a lo largo de la animación, que dura veinte ticks. */
    @Test
    void theDeathWhiteRampsUp() {
        assertEquals(0.0F, DamageFlash.whiteForDeath(0), 1.0e-6F);
        assertEquals(0.5F, DamageFlash.whiteForDeath(10), 1.0e-6F);
        assertEquals(1.0F, DamageFlash.whiteForDeath(20), 1.0e-6F);
        assertEquals(1.0F, DamageFlash.whiteForDeath(25), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.whiteForDeath(-3), 1.0e-6F);
    }

    /**
     * <b>La barra de vida, que es el efecto entero.</b>
     *
     * <p>El rojo es la vida que <em>queda</em>: a vida llena la línea está en los pies y el cuerpo
     * entero flashea, y a medida que el mob se vacía la línea <b>desciende</b>. Es lo que se pidió
     * -- <em>"que el flash vaya bajando conforme a la vida del mob como si fuera una barra"</em> --
     * y una inversión acá haría que un mob sano flasheara casi nada y uno agonizante entero, que es
     * la barra al revés.</p>
     */
    @Test
    void theBarDrainsDownwards() {
        float height = 32.0F;
        assertEquals(0.0F, DamageFlash.barThreshold(20.0F, 20.0F, height), 1.0e-6F);
        assertEquals(height / 2.0F, DamageFlash.barThreshold(10.0F, 20.0F, height), 1.0e-6F);
        assertEquals(height, DamageFlash.barThreshold(0.0F, 20.0F, height), 1.0e-6F);
    }

    /** Y la línea sólo baja: nunca sube al perder vida. */
    @Test
    void theLineOnlyEverDescends() {
        float previous = -1.0F;
        for (float health = 20.0F; health >= 0.0F; health -= 0.5F) {
            float threshold = DamageFlash.barThreshold(health, 20.0F, 32.0F);
            assertTrue(threshold >= previous,
                    "con " + health + " de vida la linea subio a " + threshold
                            + " desde " + previous + ": la barra se vaciaria al reves");
            previous = threshold;
        }
    }

    /** Un cuerpo raro no rompe nada: sin vida maxima o sin altura, la linea queda en los pies. */
    @Test
    void aBrokenBodyDoesNotCarveAnything() {
        assertEquals(0.0F, DamageFlash.barThreshold(5.0F, 0.0F, 32.0F), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.barThreshold(5.0F, 20.0F, 0.0F), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.barThreshold(5.0F, 20.0F, -3.0F), 1.0e-6F);
    }

    /** Y la vida fuera de rango no se sale del modelo: ni por arriba ni por abajo. */
    @Test
    void healthOutsideTheBarIsClamped() {
        float height = 32.0F;
        assertEquals(0.0F, DamageFlash.barThreshold(999.0F, 20.0F, height), 1.0e-6F);
        assertEquals(height, DamageFlash.barThreshold(-999.0F, 20.0F, height), 1.0e-6F);
    }
}
