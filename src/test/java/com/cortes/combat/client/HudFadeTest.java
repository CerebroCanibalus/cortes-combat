package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El fade del HUD: la cuenta, en una JVM pelada.
 *
 * <p><b>Por que una clase entera para esto.</b> Porque el fade es la parte del HUD que se
 * equivoca en silencio: un alpha que se pasa del extremo, o que salta, no rompe nada -- se ve
 * "raro", y "raro" es la palabra con la que se descarta un bug. Con la cuenta aparte, el
 * borde es una asercion y no una opinion de playtest.</p>
 */
class HudFadeTest {

    @Test
    @DisplayName("el fade arranca exactamente en su comienzo")
    void theFadeStartsAtTheBeginning() {
        assertEquals(1.0F, HudFade.alpha(1.0F, 0.0F, 0L, 320), 1.0E-6);
        assertEquals(0.0F, HudFade.alpha(0.0F, 1.0F, 0L, 320), 1.0E-6);
    }

    @Test
    @DisplayName("el fade termina exactamente en su final, y se queda ahi")
    void theFadeEndsAtTheEnd() {
        assertEquals(0.0F, HudFade.alpha(1.0F, 0.0F, 320L, 320), 1.0E-6);
        assertEquals(0.0F, HudFade.alpha(1.0F, 0.0F, 5_000L, 320), 1.0E-6,
                "y pasado el final se queda en el final, no se pasa");
        assertEquals(1.0F, HudFade.alpha(0.0F, 1.0F, 5_000L, 320), 1.0E-6);
    }

    @Test
    @DisplayName("a la mitad del tramo esta a la mitad del camino")
    void theFadeIsHalfwayAtHalfway() {
        assertEquals(0.5F, HudFade.alpha(1.0F, 0.0F, 160L, 320), 1.0E-6);
        assertEquals(0.5F, HudFade.alpha(0.0F, 1.0F, 75L, 150), 1.0E-6);
    }

    @Test
    @DisplayName("el alpha nunca sale del intervalo entre los dos extremos")
    void theFadeNeverLeavesTheInterval() {
        // **La unica propiedad que importa**: un alpha mayor que uno o menor que cero es un
        // widget que se dibuja al reves, y un alpha que se pasa del extremo es un widget que
        // rebota. Ni una velocidad de caza ni un factor de interpolacion pueden garantizarlo
        // sola: lo garantiza la forma de la cuenta.
        for (long elapsed = -50L; elapsed <= 500L; elapsed += 7L) {
            float down = HudFade.alpha(1.0F, 0.0F, elapsed, 320);
            assertTrue(down >= 0.0F && down <= 1.0F,
                    "el alpha se salio del intervalo en " + elapsed + " ms: " + down);

            float up = HudFade.alpha(0.0F, 1.0F, elapsed, 320);
            assertTrue(up >= 0.0F && up <= 1.0F,
                    "el alpha se salio del intervalo en " + elapsed + " ms: " + up);
        }
    }

    @Test
    @DisplayName("un tramo de duracion cero es el salto, y el salto es al final")
    void aZeroLengthFadeIsTheJump() {
        assertEquals(0.0F, HudFade.alpha(1.0F, 0.0F, 0L, 0), 1.0E-6);
        assertEquals(1.0F, HudFade.alpha(0.0F, 1.0F, 999L, 0), 1.0E-6);
        assertEquals(0.0F, HudFade.alpha(1.0F, 0.0F, 999L, -5), 1.0E-6,
                "y una duracion negativa es lo mismo que cero");
    }

    @Test
    @DisplayName("el tiempo negativo no mueve el alpha")
    void aNegativeElapsedDoesNotMoveIt() {
        assertEquals(1.0F, HudFade.alpha(1.0F, 0.0F, -100L, 320), 1.0E-6);
    }

    @Test
    @DisplayName("ir y volver usan la misma cuenta")
    void theTwoDirectionsAreTheSameArithmetic() {
        // El que vuelve es el mismo tramo al reves: si el del juego fuera otro, el HUD
        // desapareceria mas lento de lo que aparece, y se veria como dos widgets distintos.
        for (long elapsed = 0L; elapsed <= 320L; elapsed += 40L) {
            float down = HudFade.alpha(1.0F, 0.0F, elapsed, 320);
            float up = HudFade.alpha(0.0F, 1.0F, 320L - elapsed, 320);
            assertEquals(down, up, 1.0E-6, "en " + elapsed + " ms");
        }
    }

    @Test
    @DisplayName("los dos trampos tienen duraciones distintas, y volver es el mas corto")
    void theTwoDurationsAreDifferent() {
        // Volver es urgencia (el jugador vuelve a pelear) e irse es un descanso. Con la misma
        // duracion, el HUD tarda lo mismo en irse que en volver, y al volver se llega tarde.
        assertTrue(HudFade.IN_MILLIS < HudFade.OUT_MILLIS,
                "volver (" + HudFade.IN_MILLIS + " ms) tiene que ser mas rapido que irse ("
                        + HudFade.OUT_MILLIS + " ms)");
        assertTrue(HudFade.OUT_MILLIS >= 200,
                "e irse no puede ser un parpadeo: " + HudFade.OUT_MILLIS + " ms");
    }
}
