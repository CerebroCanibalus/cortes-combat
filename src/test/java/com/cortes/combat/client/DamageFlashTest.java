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
        assertEquals(DamageFlash.RED_ROW, DamageFlash.rowFor(DamageFlash.FLASH_TICKS - 1));
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

    /**
     * El blanco de la muerte entra <b>al final</b>, y su reloj es el del golpe.
     *
     * <p><b>Tomaba {@code deathTime} y ese campo no llega al cliente</b> -- verificado: no está en
     * {@code defineSynchedData}. O sea que el blanco estaba escrito contra un reloj que nunca
     * arranca, y <b>no se dibujó ni una vez</b>. El test viejo no podía verlo porque le pasaba
     * números a mano.</p>
     */
    @Test
    void theDeathWhiteEntersAtTheEnd() {
        // Mientras el rojo sigue, no hay blanco: los dos usan el mismo contador.
        assertEquals(0.0F, DamageFlash.whiteForDeath(DamageFlash.FLASH_TICKS), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.whiteForDeath(DamageFlash.DEATH_WHITE_TICKS + 1), 1.0e-6F);
        // Y en los ultimos ticks crece hasta el blanco mas fuerte.
        assertTrue(DamageFlash.whiteForDeath(DamageFlash.DEATH_WHITE_TICKS) > 0.0F,
                "el blanco no llega a entrar en su propia ventana");
        assertEquals(1.0F, DamageFlash.whiteForDeath(1), 1.0e-6F);
        // Un cuerpo sin herida no tiene blanco, y un contador absurdo tampoco.
        assertEquals(0.0F, DamageFlash.whiteForDeath(0), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.whiteForDeath(-3), 1.0e-6F);
    }

    /**
     * <b>La herida trepa, y eso es el efecto entero.</b>
     *
     * <p>El rojo es lo que le <em>falta</em> al mob: a vida llena no flashea nada, a media vida la
     * mitad de abajo, y a un 10 % el 90 %. Es lo que el diseño pide (<code>docs/fx.md</code> §2.3:
     * <em>"crece hacia arriba mientras baja la vida -- la herida trepa --. A 10 %, el 90 %"</em>).</p>
     *
     * <p><b>Estuvo al revés</b>, con un javadoc entero defendiendo la dirección contraria. La
     * fórmula no cambió: cambió <b>qué lado se borra</b>, en el envoltorio.</p>
     */
    @Test
    void theWoundClimbsUpwards() {
        float height = 32.0F;
        // Vida llena: nada herido, nada flashea.
        assertEquals(0.0F, DamageFlash.barThreshold(20.0F, 20.0F, height), 1.0e-6F);
        // Media vida: la mitad de abajo.
        assertEquals(height / 2.0F, DamageFlash.barThreshold(10.0F, 20.0F, height), 1.0e-6F);
        // Muerto: el cuerpo entero -- que es justo lo que hace que el blanco final cubra todo.
        assertEquals(height, DamageFlash.barThreshold(0.0F, 20.0F, height), 1.0e-6F);
    }

    /** Y la línea sólo sube al perder vida: la cobertura crece, nunca se retira. */
    @Test
    void theLineOnlyEverRises() {
        float previous = -1.0F;
        for (float health = 20.0F; health >= 0.0F; health -= 0.5F) {
            float threshold = DamageFlash.barThreshold(health, 20.0F, 32.0F);
            assertTrue(threshold >= previous,
                    "con " + health + " de vida el umbral bajo a " + threshold
                            + " desde " + previous + ": la herida se retiraria en vez de trepar");
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
