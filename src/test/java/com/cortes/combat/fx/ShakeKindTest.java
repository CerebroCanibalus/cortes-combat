package com.cortes.combat.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.client.ShakePhase;

import org.junit.jupiter.api.Test;

/**
 * El balance de los temblores. Puro, así que corre en una JVM pelada.
 *
 * <p>Lo que estos tests defienden no es que un número sea bonito: es que <b>la tabla siga siendo la
 * respuesta a "¿cuánto sacude este juego?"</b>. Un temblor de cámara es un disparador de mareo, y
 * con siete fuentes repartidas por el código la única forma de auditarlo es que haya una lista y
 * que algo la mire.</p>
 */
class ShakeKindTest {

    /**
     * <b>Ningún impulso solo llega al techo de la suma.</b>
     *
     * <p>Es el invariante que cruza los dos archivos, y el que hace que los números de la tabla
     * signifiquen algo: si el más grande pasara el techo, una explosión sola se recortaría a sí misma
     * y su 5,0 sería mentira -- el jugador sentiría 6,0 y la tabla diría otra cosa.</p>
     */
    @Test
    void noSingleShakeIsClipped() {
        for (ShakeKind kind : ShakeKind.values()) {
            assertTrue(kind.degrees() < ShakePhase.MAX_TOTAL_DEGREES,
                    kind + " llega al techo de la suma, asi que se recortaria a si mismo");
        }
    }

    /**
     * Y el techo <b>tiene una razón</b>: hay combinaciones reales que lo pasan.
     *
     * <p>Una nuke con una explosión común encima son dos impulsos en el mismo tick, y sin techo
     * serían trece grados que nadie eligió. Si esta suma no pasara el techo, el techo sería
     * decorativo y valdría borrarlo.</p>
     */
    @Test
    void theCapHasAReason() {
        float pile = ShakeKind.NUCLEAR.degrees() + ShakeKind.EXPLOSION.degrees();
        assertTrue(pile > ShakePhase.MAX_TOTAL_DEGREES,
                "una nuke con una explosion encima da " + pile
                        + " grados, que no pasa el techo: el techo no hace nada");
    }

    /**
     * <b>{@code HURT} es el más chico, y no por ser el menos importante.</b>
     *
     * <p>Es el más <b>frecuente</b>: cinco mobs pegando en el mismo tick son cinco impulsos. Un
     * número "razonable" para uno solo es un mareo para cinco, así que el que más se repite tiene que
     * ser el que menos se siente.</p>
     */
    @Test
    void hurtIsTheSmallest() {
        for (ShakeKind kind : ShakeKind.values()) {
            if (kind == ShakeKind.HURT) {
                continue;
            }
            assertTrue(kind.degrees() > ShakeKind.HURT.degrees(),
                    kind + " es mas chico que HURT, que es el que mas se repite");
        }
    }

    /** Y la nuke es la más grande, con la TNT de explosión común justo detrás. */
    @Test
    void nuclearIsTheLargest() {
        for (ShakeKind kind : ShakeKind.values()) {
            if (kind == ShakeKind.NUCLEAR) {
                continue;
            }
            assertTrue(kind.degrees() < ShakeKind.NUCLEAR.degrees(),
                    kind + " es mas grande que un reventon nuclear");
        }
        assertEquals(ShakeKind.NUCLEAR.degrees(), ShakeKind.largestDegrees(), 1.0e-6F,
                "largestDegrees no devuelve el mas grande");
        // Y una TNT común no le llega: la nuke tiene que ser MÁS que ella, que fue el pedido.
        assertTrue(ShakeKind.EXPLOSION.degrees() < ShakeKind.NUCLEAR.degrees(),
                "una TNT sacude tanto como una nuke");
    }

    /**
     * <b>Y el alcance es lo que separa una nuke de una TNT, no sólo la amplitud.</b>
     *
     * <p>Es la mitad que faltaba del pedido: el cliente tenía el alcance escrito en una constante de
     * cuarenta bloques, así que ningún número llegaba lejos -- pasado el alcance el temblor no era
     * chico, era <b>cero</b>. "Mucho rango a la distancia" no se consigue con grados.</p>
     *
     * <p>Y el test fija las dos mitades: que la nuke llegue <b>mucho</b> más lejos, y que <b>nadie
     * más</b> tenga un alcance distinto sin motivo. Un dial suelto por impulso es exactamente la
     * forma en que "¿cuánto sacude esto?" deja de tener respuesta.</p>
     */
    @Test
    void theReachIsWhatSeparatesThem() {
        for (ShakeKind kind : ShakeKind.values()) {
            assertTrue(kind.reach() > 0.0F, kind + " no tiene alcance, asi que no se siente nunca");
        }
        assertTrue(ShakeKind.NUCLEAR.reach() > ShakeKind.EXPLOSION.reach() * 2.0F,
                "el alcance de la nuke (" + ShakeKind.NUCLEAR.reach()
                        + ") no es mucho mas que el de una TNT (" + ShakeKind.EXPLOSION.reach() + ")");
        for (ShakeKind kind : ShakeKind.values()) {
            if (kind == ShakeKind.NUCLEAR) {
                continue;
            }
            assertEquals(ShakeKind.EXPLOSION.reach(), kind.reach(), 1.0e-6F,
                    kind + " tiene un alcance distinto del comun sin razon");
        }
        // Y el alcance comun que usan los que traen su numero a mano es el mismo de la tabla: los
        // valores del enum no pueden referenciar una constante estatica, asi que el numero esta dos
        // veces y este es el guard que lo ata.
        assertEquals(ShakeKind.COMMON_REACH, ShakeKind.EXPLOSION.reach(), 1.0e-6F,
                "el alcance comun y el de la tabla se separaron");
    }

    /**
     * Y ninguno es largo.
     *
     * <p>{@code CameraShake} decae a cero a lo largo de la vida, así que un impulso largo no es un
     * golpe más fuerte: es un <b>zumbido</b>, que es la forma más rápida de marear a alguien. Un
     * segundo es el techo de lo que un golpe seco puede durar.</p>
     */
    @Test
    void everyShakeIsShort() {
        for (ShakeKind kind : ShakeKind.values()) {
            assertTrue(kind.ticks() >= 1 && kind.ticks() <= 20,
                    kind + " dura " + kind.ticks() + " ticks, que no es un golpe");
        }
    }

    /**
     * Y el remate es <b>el número que se acordó</b>: los 4,0 que el playtest vio funcionar, menos el
     * 40 % que pidió después.
     *
     * <p>Está fijado a propósito: es la referencia contra la que se midió todo lo demás, y un cambio
     * de escala tiene que ser un acto deliberado que rompa este test, no un retune que pase
     * desapercibido.</p>
     */
    @Test
    void theKillIsTheAgreedNumber() {
        assertEquals(2.4F, ShakeKind.KILL.degrees(), 1.0e-6F);
    }
}
