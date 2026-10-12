package com.cortes.combat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * La geometría del zumbido. Pura, así que corre en una JVM pelada.
 *
 * <p>Es el único disparador de FX con matemática de verdad, y el modo de falla sin test es
 * silencioso: un signo al revés hace que la flecha de la izquierda se sienta a la derecha, y nadie
 * lo nota hasta que un jugador gira para el lado equivocado.</p>
 */
class WhizzGeometryTest {

    /** Derecho al observador: la distancia mínima es cero. */
    @Test
    void aProjectileComingStraightAtYouHasNoDistance() {
        assertEquals(0.0D, WhizzGeometry.distance(0.0D, 0.0D, 5.0D, 0.0D, 0.0D, -1.0D), 1.0e-9D);
        assertTrue(WhizzGeometry.whizzes(0.0D, 0.0D, 5.0D, 0.0D, 0.0D, -1.0D));
    }

    /** Y uno que pasa al costado tiene la distancia del costado. */
    @Test
    void onePassingToTheSideKeepsItsOffset() {
        assertEquals(1.0D, WhizzGeometry.distance(1.0D, 0.0D, 5.0D, 0.0D, 0.0D, -1.0D), 1.0e-9D);
        assertTrue(WhizzGeometry.whizzes(1.0D, 0.0D, 5.0D, 0.0D, 0.0D, -1.0D));
    }

    /**
     * <b>El radio es lo que decide</b>, y por eso se afirma a los dos lados del borde.
     *
     * <p>Un guard que sólo probara el caso que pasa no diría nada: el borde es la única parte donde
     * un error cambia la respuesta.</p>
     */
    @Test
    void theRadiusIsTheBoundary() {
        double justInside = WhizzGeometry.WHIZZ_RADIUS - 0.01D;
        double justOutside = WhizzGeometry.WHIZZ_RADIUS + 0.01D;
        assertTrue(WhizzGeometry.whizzes(justInside, 0.0D, 5.0D, 0.0D, 0.0D, -1.0D),
                "a " + justInside + " bloques deberia zumbar");
        assertFalse(WhizzGeometry.whizzes(justOutside, 0.0D, 5.0D, 0.0D, 0.0D, -1.0D),
                "a " + justOutside + " bloques no deberia zumbar");
    }

    /**
     * Lo que se aleja se mide donde está.
     *
     * <p>Es el clamp de {@code t} a cero, y sin él el mínimo caería detrás del observador -- una
     * distancia que nunca ocurrió. Una flecha que ya pasó no puede volver a acercarse.</p>
     */
    @Test
    void somethingMovingAwayIsMeasuredWhereItIs() {
        assertEquals(5.0D, WhizzGeometry.distance(0.0D, 0.0D, 5.0D, 0.0D, 0.0D, 1.0D), 1.0e-9D);
        assertFalse(WhizzGeometry.whizzes(0.0D, 0.0D, 5.0D, 0.0D, 0.0D, 1.0D));
    }

    /**
     * <b>Y una flecha clavada no zumba nunca.</b>
     *
     * <p>Su "punto de máxima aproximación" es su propia posición, así que sin el piso de velocidad
     * pararse al lado de una flecha en el piso haría temblar la cámara para siempre -- que es
     * exactamente la clase de bug que un jugador no puede ni describir.</p>
     */
    @Test
    void aStuckArrowNeverWhizzes() {
        assertFalse(WhizzGeometry.whizzes(0.1D, 0.0D, 0.1D, 0.0D, 0.0D, 0.0D));
        assertFalse(WhizzGeometry.whizzes(0.0D, 0.0D, 0.0D, 0.1D, 0.0D, 0.1D));
    }

    /**
     * El lado, y el signo.
     *
     * <p>Se le pasa el vector "derecha" del observador y el punto de máxima aproximación: el signo
     * del producto dice de qué lado pasó. Es la única parte del zumbido que <em>informa</em> en vez
     * de sólo avisar.</p>
     */
    @Test
    void theSideIsTheSignOfTheProjection() {
        // Mirando hacia el -Z, "derecha" es -X.
        double rightX = -1.0D;
        double rightZ = 0.0D;
        // Pasa por x = -1 (a la derecha del observador) -> positivo.
        assertTrue(WhizzGeometry.side(-1.0D, 0.0D, rightX, rightZ) > 0.0D);
        // Y por x = +1 (a la izquierda) -> negativo.
        assertTrue(WhizzGeometry.side(1.0D, 0.0D, rightX, rightZ) < 0.0D);
        // Y por el medio -> cero, que el llamador manda a la derecha por el `>= 0`.
        assertEquals(0.0D, WhizzGeometry.side(0.0D, 0.0D, rightX, rightZ), 1.0e-9D);
    }
}
