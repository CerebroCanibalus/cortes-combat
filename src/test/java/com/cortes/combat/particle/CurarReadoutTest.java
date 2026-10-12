package com.cortes.combat.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Cuántos corazones vale una curación.
 *
 * <p>Es la lectura del efecto -- el jugador ve de un vistazo si se curó un punto de la regeneración
 * de comida o si se comió una manzana dorada -- así que el número tiene que decir la diferencia sin
 * llenar la pantalla.</p>
 *
 * <p><b>Y vive en {@link CurarReadout} y no en {@link CurarParticles} por una razón que costó un
 * build:</b> {@code CurarParticles} tiene un método que toma un {@code ServerLevel}, y eso alcanza
 * para que la clase entera no cargue en una JVM pelada. Los cinco tests murieron con
 * {@code NoClassDefFoundError} antes de la primera aserción, que es exactamente el modo de falla que
 * este proyecto ya documenta: <em>la clase que un test carga no puede nombrar un tipo de
 * Minecraft, ni siquiera en un método que el test no llame</em>.</p>
 */
class CurarReadoutTest {

    /** Una curación chica vale un corazón: la regeneración de comida cura un punto por segundo. */
    @Test
    void aSmallHealIsOneHeart() {
        assertEquals(1, CurarReadout.heartsFor(1.0F));
        assertEquals(1, CurarReadout.heartsFor(0.5F));
        assertEquals(1, CurarReadout.heartsFor(2.0F));
    }

    /** Y una grande se ve grande. */
    @Test
    void aBigHealIsMoreHearts() {
        assertEquals(2, CurarReadout.heartsFor(3.0F));
        assertEquals(3, CurarReadout.heartsFor(5.0F));
    }

    /**
     * <b>El techo, y es la aserción que importa.</b>
     *
     * <p>La regeneración de comida cura <em>todo el tiempo</em>, así que el efecto aparece solo si
     * significa algo. Un techo alto no sería un adorno: sería la diferencia entre "se curó" y un
     * acuario permanente en la pantalla.</p>
     */
    @Test
    void theCeilingHolds() {
        assertEquals(CurarReadout.MAX_HEARTS, CurarReadout.heartsFor(8.0F));
        assertEquals(CurarReadout.MAX_HEARTS, CurarReadout.heartsFor(100.0F));
    }

    /** Y una curación de cero o negativa no pinta nada -- no existe el corazón de nada. */
    @Test
    void nothingHealedIsNoHearts() {
        assertEquals(0, CurarReadout.heartsFor(0.0F));
        assertEquals(0, CurarReadout.heartsFor(-3.0F));
    }

    /**
     * El conteo nunca decrece con la curación.
     *
     * <p>Es la propiedad que hace que el número sea una <em>lectura</em>: si una cura más grande
     * pudiera dar menos corazones, el jugador aprendería el código al revés. Se camina el rango
     * entero en vez de mirar dos valores, porque el bug que se busca es un escalón.</p>
     */
    @Test
    void moreHealingNeverMeansFewerHearts() {
        int previous = 0;
        for (float amount = 0.0F; amount <= 12.0F; amount += 0.25F) {
            int hearts = CurarReadout.heartsFor(amount);
            assertTrue(hearts >= previous,
                    "curar " + amount + " dio " + hearts + " corazones, menos que los "
                            + previous + " de una curación menor");
            previous = hearts;
        }
    }
}
