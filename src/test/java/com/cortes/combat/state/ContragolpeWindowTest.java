package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La ventana de Contragolpe, en JVM pelada.
 *
 * <h2>Las tres formas que este archivo existe para fijar</h2>
 *
 * <p><b>Sin ventana no se abre nunca</b> -- y el cero es la respuesta, no un caso especial: el reloj
 * del mundo arranca en cero y sólo sube, así que {@code now < 0} no es cierto nunca. Se probó con un
 * campo {@code armed} al lado y <b>este archivo lo destapó redundante</b>: sacarlo no rompía nada.
 * Un campo que no cambia ninguna respuesta no es claridad.</p>
 *
 * <p><b>Vence a los cuarenta ticks</b> y no antes ni después: es el "≤ 40 ticks" del diseño.</p>
 *
 * <p><b>Y un golpe la gasta.</b> "Tu siguiente ataque" es uno; si sobreviviera al golpe, un bloqueo
 * daría dos segundos de penetración de armadura en vez de un golpe.</p>
 */
class ContragolpeWindowTest {

    @Test
    @DisplayName("sin ventana no esta abierta, ni siquiera para un mundo ya avanzado")
    void noneIsNeverOpen() {
        assertFalse(ContragolpeWindow.NONE.isOpen(0L), "en el tick cero");
        assertFalse(ContragolpeWindow.NONE.isOpen(1L), "y en el tick uno");
        assertFalse(ContragolpeWindow.NONE.isOpen(100_000L), "ni un rato despues");
    }

    @Test
    @DisplayName("abierta en el tick del bloqueo, y viva hasta el 39")
    void theWindowIsFortyTicks() {
        long blockedAt = 1_000L;
        ContragolpeWindow window = ContragolpeWindow.armedAt(blockedAt);

        assertTrue(window.isOpen(blockedAt), "el mismo tick del bloqueo, obviamente");
        assertTrue(window.isOpen(blockedAt + ContragolpeWindow.WINDOW_TICKS - 1),
                "en el tick 39 todavia se cobra: el diseno dice hasta cuarenta");
        assertFalse(window.isOpen(blockedAt + ContragolpeWindow.WINDOW_TICKS),
                "y en el 40 se cerro: la ventana son dos segundos, no dos y un poquito");
        assertFalse(window.isOpen(blockedAt + 10_000L), "y no vuelve");
    }

    @Test
    @DisplayName("**un golpe la gasta**: 'tu siguiente ataque' es uno")
    void oneHitSpendsIt() {
        long blockedAt = 500L;
        ContragolpeWindow window = ContragolpeWindow.armedAt(blockedAt);
        ContragolpeWindow afterTheHit = window.spent();

        assertTrue(window.isOpen(blockedAt + 1), "antes del golpe estaba abierta");
        assertFalse(afterTheHit.isOpen(blockedAt + 1),
                "**y despues del golpe no.** Si sobreviviera, un bloqueo daria cuarenta ticks de "
                        + "penetracion de armadura en vez de un golpe -- un premio que se puede "
                        + "sostener es un estado, no un premio");
    }

    @Test
    @DisplayName("y volver a bloquear la rearma, con su propio vencimiento")
    void blockingAgainReArms() {
        ContragolpeWindow first = ContragolpeWindow.armedAt(100L);
        ContragolpeWindow second = ContragolpeWindow.armedAt(130L);

        assertFalse(first.isOpen(100L + ContragolpeWindow.WINDOW_TICKS),
                "la primera vencio en su momento");
        assertTrue(second.isOpen(100L + ContragolpeWindow.WINDOW_TICKS),
                "y la segunda sigue viva: el vencimiento es absoluto y se rearma con el bloqueo "
                        + "nuevo, no se estira el viejo");
    }
}
