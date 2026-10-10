package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La ventana de la cadena del martillo, y por qué es un campo del estado.
 *
 * <h2>La invariante que este archivo existe para fijar</h2>
 *
 * <p><b>La ventana viaja con la cadena, no con el arma.</b> Ritmo de Hierro la alarga, y el número
 * se decide cuando aterriza el primer golpe: a partir de ahí el estado es el que dice cuándo se
 * rompe la cadena. Si se leyera del ítem en cada tick, cambiar de martillo a mitad de cadena
 * colapsaría una cadena que ya venía legal -- la misma forma que el proyecto ya corrigió una vez,
 * con el material del lunge.</p>
 *
 * <p>Y la segunda mitad, que es el efecto que el jugador compra: <b>un golpe en el tick 90 sostiene
 * la cadena con la ventana de 120 y no con la de 80.</b> Eso es el encantamiento, y sin esto sería
 * un número en una tabla sin nada que lo sintiera.</p>
 */
class StreakWindowTest {

    @Test
    @DisplayName("sin encantamiento la cadena se rompe a los ochenta ticks")
    void theBaseWindow() {
        StreakState streak = StreakState.EMPTY.onHit(StreakState.WINDOW_TICKS);
        assertTrue(streak.isActive(), "recien golpeado, la cadena esta viva");

        for (int i = 0; i < StreakState.WINDOW_TICKS - 1; i++) {
            streak = streak.tick();
        }
        assertTrue(streak.isActive(), "en el tick 79 todavia encadena");

        streak = streak.tick();
        assertFalse(streak.isActive(), "y en el 80 se rompio: la cadena volvio a cero");
    }

    @Test
    @DisplayName("**Ritmo de Hierro II sostiene la cadena donde la de diseno ya se rompio**")
    void theLongerWindowKeepsTheChainAlive() {
        int longer = 120;
        StreakState enchanted = StreakState.EMPTY.onHit(longer);
        for (int i = 0; i < StreakState.WINDOW_TICKS; i++) {
            enchanted = enchanted.tick();
        }
        assertTrue(enchanted.isActive(),
                "en el tick 80 la cadena CON el encantamiento sigue viva: es exactamente lo que el "
                        + "jugador compro, y el numero que hay que medir en playtest");
        assertEquals(1, enchanted.hits(), "y no perdio golpes por el camino");

        StreakState plain = StreakState.EMPTY.onHit(StreakState.WINDOW_TICKS);
        for (int i = 0; i < StreakState.WINDOW_TICKS; i++) {
            plain = plain.tick();
        }
        assertFalse(plain.isActive(), "y la misma cuenta sin el encantamiento la rompe");
    }

    @Test
    @DisplayName("**la ventana viaja con la cadena**: el estado que la armo es el que la juzga")
    void theWindowTravelsWithTheStreak() {
        // Una cadena nacida con la ventana larga no se acorta porque el estado de al lado diga otra
        // cosa: el tick pregunta por SU campo, no por la constante.
        StreakState born = StreakState.EMPTY.onHit(120);
        assertEquals(120, born.windowTicks(), "la cadena guarda la ventana con la que nacio");

        StreakState late = born;
        for (int i = 0; i < 100; i++) {
            late = late.tick();
        }
        assertTrue(late.isActive(), "a los cien ticks sigue viva");
        assertEquals(120, late.windowTicks(), "y sigue midiendo con la suya, no con la de 80");
    }

    @Test
    @DisplayName("y el codec la lleva: una cadena guardada no pierde su ventana")
    void theWindowSurvivesTheCodec() {
        // El codec es lo que hace que la cadena sobreviva a un guardado de partida. Si la ventana no
        // viajara por ahi, un mundo recargado en medio de una cadena larga la volveria a medir con
        // cuatro segundos -- un bug que solo aparece al guardar, que es la peor clase.
        StreakState born = StreakState.EMPTY.onHit(120);
        com.mojang.serialization.JsonOps ops = com.mojang.serialization.JsonOps.INSTANCE;
        var json = StreakState.CODEC.encodeStart(ops, born).getOrThrow();
        StreakState read = StreakState.CODEC.parse(ops, json).getOrThrow();
        assertEquals(born, read, "ida y vuelta sin perder la ventana");
        assertEquals(120, read.windowTicks());
    }
}
