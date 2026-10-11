package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las dos ventanas de la guardia: la que abre que te rompan la postura y el reloj del bloqueo gratis.
 *
 * <h2>Por qué se prueban acá y no en el handler</h2>
 *
 * <p>Porque un {@code ServerPlayer} no existe en una JVM pelada. Los dos son <b>records puros</b> --
 * un tick adentro y una pregunta afuera -- así que la decisión que importa ("¿este bloqueo es
 * gratis?", "¿este golpe cobra la ventana?") se puede caminar sin abrir el juego. El handler queda
 * como lo que tiene que ser: leer el reloj y llamar.</p>
 */
class GuardWindowsTest {

    @Test
    @DisplayName("Segundo Aliento: la ventana se abre al romperse la guardia y se cierra sola")
    void theSecondWindWindow() {
        long now = 1_000L;
        SecondWindWindow wind = SecondWindWindow.armedAt(now);

        assertTrue(wind.isOpen(now), "**abre en el mismo tick**: el golpe que rompe la guardia y el "
                + "que cobra el premio pueden ser el mismo, y un tick de espera seria un premio que "
                + "el jugador no puede cobrar");
        assertTrue(wind.isOpen(now + SecondWindWindow.WINDOW_TICKS - 1),
                "y sigue abierta hasta el ultimo tick de la ventana");
        assertFalse(wind.isOpen(now + SecondWindWindow.WINDOW_TICKS),
                "**y en el tick del vencimiento ya no**: la condicion es `now < expires`, no `<=`");
        assertFalse(SecondWindWindow.NONE.isOpen(now),
                "sin guardia rota no hay ventana, en ningun tick");
    }

    @Test
    @DisplayName("Segundo Aliento: **el golpe que la usa la gasta**")
    void spendingTheSecondWind() {
        SecondWindWindow wind = SecondWindWindow.armedAt(500L);
        assertFalse(wind.spent().isOpen(500L),
                "**\"tu siguiente golpe\" es uno.** Si la ventana sobreviviera al golpe serian tres "
                        + "segundos de premio en vez de un golpe, que es un estado y no un premio");
    }

    @Test
    @DisplayName("Guardia Firme: **el primer bloqueo de una partida es gratis**")
    void theFirstBlockOfAGameIsFree() {
        // El caso que el default eligio: con `lastFreeTick = 0` en vez de `-INTERVAL_TICKS`, esta
        // asercion fallaria y el encantamiento no haria nada durante el primer minuto del mundo.
        assertTrue(GuardFree.READY.isReady(0L),
                "**en el tick cero del mundo ya esta listo.** Con el default en cero, `0 - 0 >= 60` "
                        + "es falso y el primer bloqueo gratis no llegaria hasta el tick sesenta");
    }

    @Test
    @DisplayName("Guardia Firme: tres segundos entre uno gratis y el siguiente")
    void oneFreeBlockEveryThreeSeconds() {
        long now = 2_000L;
        GuardFree used = GuardFree.READY.usedAt(now);

        assertFalse(used.isReady(now), "el que se acaba de gastar no esta listo");
        assertFalse(used.isReady(now + GuardFree.INTERVAL_TICKS - 1),
                "ni un tick antes del intervalo");
        assertTrue(used.isReady(now + GuardFree.INTERVAL_TICKS),
                "y si en el tick del intervalo: la condicion es `>=`");
    }
}
