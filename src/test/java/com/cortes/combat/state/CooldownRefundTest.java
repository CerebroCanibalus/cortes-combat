package com.cortes.combat.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Segunda Pasada: la devolucion de cooldown.
 *
 * <p>El encantamiento devuelve una fraccion del cooldown que el barrido ya cobro, y la resta tiene un
 * borde que es lo unico que hay que probar: <b>una devolucion mayor que lo que queda tiene que caer
 * en cero</b> y no en un contador negativo.</p>
 */
@DisplayName("Segunda Pasada: la devolucion de cooldown")
class CooldownRefundTest {

    @Test
    @DisplayName("devuelve ticks, y una devolucion mayor que lo que queda cae en cero")
    void refunds() {
        assertEquals(30, new CooldownState(72).refund(42).ticksRemaining());
        assertEquals(0, new CooldownState(10).refund(50).ticksRemaining(),
                "**una devolucion mayor que lo que queda cae en cero**, no en negativo: un cooldown "
                        + "negativo no es 'muy listo', es un estado donde isReady contesta si por una "
                        + "razon que nadie escribio, y el proximo golpe pasaria por accidente y no "
                        + "por la regla");
        assertTrue(new CooldownState(10).refund(50).isReady());
    }

    @Test
    @DisplayName("y sobre un arma lista no hace nada, que es lo que impide bancar un golpe gratis")
    void aRefundOnAReadyWeaponDoesNothing() {
        CooldownState ready = CooldownState.READY;
        assertEquals(0, ready.refund(40).ticksRemaining(),
                "una devolucion sobre un arma lista no puede guardarse para despues");
        assertEquals(0, ready.refund(0).ticksRemaining());
        assertEquals(0, ready.refund(-5).ticksRemaining(), "y una negativa tampoco es un prestamo");
    }

    @Test
    @DisplayName("nunca sube el contador")
    void neverRaises() {
        assertTrue(new CooldownState(5).refund(1).ticksRemaining() <= 5,
                "una devolucion solo puede bajar el contador");
        assertEquals(5, new CooldownState(5).refund(0).ticksRemaining(),
                "y devolver cero no puede cambiarlo");
    }
}
