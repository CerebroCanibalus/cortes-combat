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

    /** La fila del impacto escribe el negro, con el alpha que el playtest produjo. */
    @Test
    void theImpactRowIsTheBlackFrame() {
        assertEquals(DamageFlash.IMPACT_BLACK, DamageFlash.texturePixel(DamageFlash.IMPACT_ROW,
                DamageFlash.VANILLA_RED));
        assertEquals(DamageFlash.IMPACT_ALPHA, alpha(DamageFlash.IMPACT_BLACK));
        // **Y los canales en cero, que es lo que lo hace negro.** Estaban en rojo: el valor era
        // `(0x20 << 24) | 0x0000FF`, o sea el mismo rojo del otro renglón con menos alpha, así que el
        // shader hacía `mix(rojo, textura, 0.125)` = 87,5 % de rojo. **"El flash negro" no existía**,
        // y el nombre de la constante ya decía cuál era la intención.
        for (int shift : new int[]{16, 8, 0}) {
            assertEquals(0, channel(DamageFlash.IMPACT_BLACK, shift),
                    "el canal " + shift + " del frame de impacto no es negro");
        }
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

    /**
     * El renglón rojo sólo cambia el alpha: sigue siendo rojo, en los tres canales.
     *
     * <p>El del impacto <b>no</b>: ése pasa a negro. Ver {@link #theImpactRowIsTheBlackFrame}.</p>
     */
    @Test
    void theRedStaysRed() {
        int written = DamageFlash.texturePixel(DamageFlash.RED_ROW, DamageFlash.VANILLA_RED);
        for (int shift : new int[]{16, 8, 0}) {
            assertEquals(channel(DamageFlash.VANILLA_RED, shift), channel(written, shift),
                    "el canal " + shift + " del rojo cambió");
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
    /**
     * <b>Es una barra de vida: empieza llena y se vacía.</b>
     *
     * <p>A vida llena flashea todo, a media vida la mitad de abajo, y a cero no flashea nada. Es la
     * lectura que el dueño pidió explícitamente -- <em>"comienza completa y se va bajando hasta
     * vaciarse"</em> -- después de ver la contraria en el playtest.</p>
     *
     * <p><b>La versión anterior hacía lo opuesto</b> ("la herida trepa": a vida llena nada, y el rojo
     * creciendo desde los pies), y venía de <code>docs/fx.md</code> §2.3, escrito sobre una premisa
     * que después resultó falsa. La fórmula no cambió de forma: cambió <b>de qué lado está la
     * fracción</b>.</p>
     */
    @Test
    void theBarDrains() {
        // Vida llena: el corte en la cabeza -- nada escondido, el cuerpo entero flashea.
        assertEquals(32.0F, DamageFlash.barCut(0.0F, 32.0F, 20.0F, 20.0F), 1.0e-6F);
        // Media vida: la mitad de abajo.
        assertEquals(16.0F, DamageFlash.barCut(0.0F, 32.0F, 10.0F, 20.0F), 1.0e-6F);
        // Muerto: nada -- el corte en los pies.
        assertEquals(0.0F, DamageFlash.barCut(0.0F, 32.0F, 0.0F, 20.0F), 1.0e-6F);
    }

    /**
     * <b>Y el corte respeta dónde está el cuerpo en el mundo.</b>
     *
     * <p>Es la mitad que se rompió dos veces. El rango entra <b>medido</b>, así que subir el cuerpo
     * sube el corte exactamente igual: un mob a Y=64 no puede cortarse contra un número que empieza
     * en cero, que es lo que pasaba cuando el umbral salía de la caja de colisión y el vértice
     * llegaba en coordenadas del mundo.</p>
     */
    @Test
    void theCutFollowsTheBodyUp() {
        assertEquals(80.0F, DamageFlash.barCut(64.0F, 96.0F, 10.0F, 20.0F), 1.0e-6F);
        assertEquals(96.0F, DamageFlash.barCut(64.0F, 96.0F, 20.0F, 20.0F), 1.0e-6F);
        assertEquals(64.0F, DamageFlash.barCut(64.0F, 96.0F, 0.0F, 20.0F), 1.0e-6F);
    }

    /** Y la línea sólo baja al perder vida: la cobertura se retira, nunca crece. */
    @Test
    void theLineOnlyEverFalls() {
        float previous = Float.MAX_VALUE;
        for (float health = 20.0F; health >= 0.0F; health -= 0.5F) {
            float cut = DamageFlash.barCut(0.0F, 32.0F, health, 20.0F);
            assertTrue(cut <= previous,
                    "con " + health + " de vida el corte subió a " + cut
                            + " desde " + previous + ": la barra se llenaría en vez de vaciarse");
            previous = cut;
        }
    }

    /** Un cuerpo raro no rompe nada: el corte queda en los pies, que el llamador lee como "no envolver". */
    @Test
    void aBrokenBodyDoesNotCarveAnything() {
        // Sin vida maxima no hay barra que dibujar: la fraccion es cero y el corte cae en los pies.
        assertEquals(0.0F, DamageFlash.barCut(0.0F, 32.0F, 5.0F, 0.0F), 1.0e-6F);
        // Y un rango vacio o invertido tampoco.
        assertEquals(0.0F, DamageFlash.barCut(0.0F, 0.0F, 5.0F, 20.0F), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.barCut(0.0F, -3.0F, 5.0F, 20.0F), 1.0e-6F);
    }

    /** Y la vida fuera de rango no se sale del cuerpo: ni por arriba ni por abajo. */
    @Test
    void healthOutsideTheBarIsClamped() {
        assertEquals(32.0F, DamageFlash.barCut(0.0F, 32.0F, 999.0F, 20.0F), 1.0e-6F);
        assertEquals(0.0F, DamageFlash.barCut(0.0F, 32.0F, -999.0F, 20.0F), 1.0e-6F);
    }

    /**
     * <b>La columna del blanco está DENTRO de la textura, y esa es la mitad que faltaba.</b>
     *
     * <p>El mixin empaquetaba con {@code OverlayTexture.pack(OverlayTexture.u(white), false)}. Con un
     * {@code int} y un {@code boolean} la sobrecarga que resuelve es {@code pack(float, boolean)}, que
     * <b>vuelve a aplicar {@code u()}</b> -- así que la columna salía {@code u(u(white))}, hasta
     * <b>225</b> en una textura de 16. Un {@code texelFetch} fuera de rango es indefinido, y el
     * playtest lo vio: el mob se ponía <b>negro</b> al morir en vez de blanco, que es exactamente lo
     * que devuelve muestrear un texel transparente.</p>
     */
    @Test
    void theWhiteColumnStaysInsideTheTexture() {
        for (int hurtTime = 0; hurtTime <= DamageFlash.FLASH_TICKS; hurtTime++) {
            int column = DamageFlash.whiteColumn(hurtTime);
            assertTrue(column >= 0 && column <= 15,
                    "con hurtTime " + hurtTime + " la columna del blanco salio " + column
                            + ", fuera de la textura de 16");
        }
        // Y crece con la muerte: el ultimo tick es el blanco mas fuerte, y mientras el rojo sigue no hay blanco.
        assertEquals(0, DamageFlash.whiteColumn(DamageFlash.FLASH_TICKS));
        assertTrue(DamageFlash.whiteColumn(1) > DamageFlash.whiteColumn(DamageFlash.DEATH_WHITE_TICKS),
                "el blanco no crece hacia el final de la ventana");
        assertEquals(15, DamageFlash.whiteColumn(1));
    }
}
