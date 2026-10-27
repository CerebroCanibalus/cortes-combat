package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El reloj de inactividad: el contador y el borde del ocultamiento.
 *
 * <p><b>El off-by-one es la razon de existir de este archivo.</b> "Se oculta a los 160 ticks"
 * tiene dos lecturas -- oculto EN el 160 o a partir del 161 -- y sin un test la que salga es la
 * que escribio el que lo escribio. El contrato es: {@code idleTicks == timeout} YA esta oculto.
 * (μ_μ)</p>
 */
class HudVisibilityTest {

    @Test
    @DisplayName("activity resets the counter, idleness adds one")
    void theCounter() {
        assertEquals(0, HudVisibility.next(0, true));
        assertEquals(0, HudVisibility.next(159, true), "un golpe vuelve el reloj a cero");
        assertEquals(6, HudVisibility.next(5, false));
        assertEquals(1, HudVisibility.next(0, false));
    }

    @Test
    @DisplayName("the timeout tick is already hidden, the one before is not")
    void theEdge() {
        assertFalse(HudVisibility.hidden(159, true, 160), "un tick antes sigue visible");
        assertTrue(HudVisibility.hidden(160, true, 160), "el tick del limite ya esta oculto");
        assertTrue(HudVisibility.hidden(10_000, true, 160), "y se queda oculto");
    }

    @Test
    @DisplayName("the toggle wins over the clock, and a zero timeout never hides")
    void theToggleAndTheZero() {
        assertFalse(HudVisibility.hidden(10_000, false, 160),
                "con el toggle apagado no se oculta nunca, por mucho que pase el tiempo");
        assertFalse(HudVisibility.hidden(10_000, true, 0),
                "un timeout de cero es una config rota, no 'ocultar al instante'");
        assertFalse(HudVisibility.hidden(10_000, true, -5), "y un negativo tampoco");
    }
}
