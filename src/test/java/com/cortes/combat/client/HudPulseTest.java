package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El latido del HUD: la forma se prueba aca porque la capa que lo dibuja no existe sin el juego.
 *
 * <p>Lo que hay que afirmar son tres cosas y ninguna es "se ve lindo": <b>nunca baja del brillo
 * normal</b> (un efecto que apaga se lee como que el widget se va), <b>llega al techo</b> (si no,
 * el pedido de "que se note" queda en un cambio invisible) y <b>es periodico</b> (un reloj que
 * solo sube terminaria en un numero fijo y dejaria de latir). (⌒‿⌒)</p>
 */
class HudPulseTest {

    @Test
    @DisplayName("el piso es el brillo normal, nunca menos")
    void neverDimmesBelowTheNormalBrightness() {
        assertEquals(HudPulse.MIN_BOOST, HudPulse.waiting(0L), 1.0E-4F);
        assertEquals(1.0F, HudPulse.MIN_BOOST, 1.0E-4F,
                "el piso es exactamente uno: latir no puede apagar lo que normalmente se ve");
        for (long millis = 0; millis < 4L * HudPulse.PERIOD_MILLIS; millis += 7L) {
            assertTrue(HudPulse.waiting(millis) >= HudPulse.MIN_BOOST,
                    "el factor bajo el piso en " + millis + " ms");
        }
    }

    @Test
    @DisplayName("llega al techo, o el pedido de que se note no se cumple")
    void reachesItsCeiling() {
        float peak = HudPulse.waiting(HudPulse.PERIOD_MILLIS / 2L);
        assertEquals(HudPulse.MAX_BOOST, peak, 1.0E-4F,
                "medio periodo es el pico de la coseno");
        assertTrue(HudPulse.MAX_BOOST >= 1.5F,
                "un 10% mas no es un aviso; el techo tiene que verse");
    }

    @Test
    @DisplayName("es periodico: el mismo instante del ciclo da el mismo brillo")
    void itRepeatsEveryPeriod() {
        for (long millis = 0; millis < HudPulse.PERIOD_MILLIS; millis += 13L) {
            assertEquals(HudPulse.waiting(millis),
                    HudPulse.waiting(millis + HudPulse.PERIOD_MILLIS), 1.0E-4F,
                    "el ciclo no se repite en " + millis + " ms");
        }
        // Y un reloj grande no satura: la fase sale de un floorMod, no de un cast de float. Es
        // la misma familia de bug que el seno de Mth que dejo el shake muerto. (μ_μ)
        assertEquals(HudPulse.waiting(1_000_000_000L % HudPulse.PERIOD_MILLIS),
                HudPulse.waiting(1_000_000_000L), 1.0E-4F);
    }
}
