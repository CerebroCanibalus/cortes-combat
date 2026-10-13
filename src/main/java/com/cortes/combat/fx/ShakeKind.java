package com.cortes.combat.fx;

import java.util.Locale;

/**
 * Los temblores de cámara que el mod dispara, con <b>su tamaño y su duración</b>.
 *
 * <h2>Por qué una tabla y no un número por llamador</h2>
 *
 * <p>Porque el riesgo de este efecto no es que falte, es que <b>sobre</b>: un temblor de cámara es
 * un disparador de mareo, y con seis fuentes distintas cada una eligiendo su número a ojo, la única
 * forma de contestar <em>"¿cuánto sacude este juego en total?"</em> es leer seis archivos. Acá la
 * respuesta es una lista, se compara de un vistazo, y un test la puede afirmar.</p>
 *
 * <h2>La escala, y por qué los chicos son tan chicos</h2>
 *
 * <p>La referencia es {@link #KILL}: el dueño lo vio funcionar a 4,0° y pidió bajarlo un 40 %, así
 * que <b>2,4° es "se lee"</b> -- y todo lo demás se mide contra eso.</p>
 *
 * <p>Y lo que decide cada número no es cuánto importa el momento sino <b>cada cuánto pasa</b>:</p>
 *
 * <ul>
 *   <li>{@link #HURT} es el más chico <b>porque es el más frecuente</b>. Cinco mobs pegando a la vez
 *       son cinco impulsos en un tick, y un número "razonable" para uno solo es un mareo para
 *       cinco.</li>
 *   <li>{@link #EXPLOSION} es el más grande y el único que se permite pasar de 3°, porque una
 *       explosión <b>es</b> el evento más violento del juego y pasa cada tanto. Igual llega
 *       recortado por la distancia, así que su 5,0 sólo se siente a quemarropa.</li>
 *   <li>{@link #GUARDED} y {@link #PARRY} son del mismo orden que {@link #HURT} y no más: son
 *       momentos que se repiten mucho en una pelea larga.</li>
 * </ul>
 *
 * <h2>El techo de la suma</h2>
 *
 * <p>Estos números son de <b>un</b> impulso. Los impulsos vivos se suman en {@code CameraShake}, así
 * que la única defensa contra un pico es el techo de ahí -- ver {@code ShakePhase.clampTotal}, y el
 * test que afirma que <b>ningún impulso solo llega al techo</b> (si llegara, se recortaría a sí
 * mismo y el número de la tabla sería mentira).</p>
 */
public enum ShakeKind {

    /** Un reventón. El más grande de todos, y el único que se permite pasar de 3°. */
    EXPLOSION(5.0F, 14),

    /** Matar. La referencia de la escala: 4,0° recortado un 40 % porque el dueño lo pidió. */
    KILL(2.4F, 6),

    /** Te rompieron la guardia. Es un golpe grande: la postura se cae y quedás quieto dos segundos. */
    GUARD_BREAK(2.0F, 8),

    /** El parry del sable devolvió el golpe. Un momento corto y premiado, del orden de la guardia. */
    PARRY(1.8F, 6),

    /** Un golpe a carga completa aterrizando. Cargar cuesta 40 ticks: que se sienta al llegar. */
    CHARGED_HIT(1.6F, 7),

    /** Bloqueaste. El golpe murió en el escudo, y el hombro lo cobra sin que la vida lo diga. */
    GUARDED(1.2F, 5),

    /** Te pegaron. El más chico de todos, y a propósito: es el que más se repite. */
    HURT(0.8F, 5);

    private final float degrees;
    private final int ticks;

    ShakeKind(float degrees, int ticks) {
        this.degrees = degrees;
        this.ticks = ticks;
    }

    /** La amplitud en grados <b>a quemarropa</b>; el cliente la baja con el cuadrado de la distancia. */
    public float degrees() {
        return this.degrees;
    }

    /** Cuánto dura el impulso, en ticks. */
    public int ticks() {
        return this.ticks;
    }

    /** El impulso más grande de la tabla. Ver el techo de la suma en el javadoc de la clase. */
    public static float largestDegrees() {
        float largest = 0.0F;
        for (ShakeKind kind : values()) {
            largest = Math.max(largest, kind.degrees);
        }
        return largest;
    }

    @Override
    public String toString() {
        return this.name().toLowerCase(Locale.ROOT) + "(" + this.degrees + " grados, "
                + this.ticks + " ticks)";
    }
}
