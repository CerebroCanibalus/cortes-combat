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
    EXPLOSION(5.0F, 14, 40.0F),

    /**
     * El reventón del reactor. <b>Más fuerte y mucho más lejos que una TNT, porque lo es.</b>
     *
     * <p>Su alcance de 160 bloques no es un adorno: la caída es cuadrática, así que a 80 bloques
     * todavía llega a un cuarto (2,0°) y a 120 a un dieciseisavo (0,5°). Una TNT se siente en la
     * cuadra; esto se siente en el mapa.</p>
     *
     * <p><b>Y es el único impulso por encima del techo de 6,0°</b> -- por eso el techo tuvo que
     * subir: si un reventón nuclear se recortara a sí mismo, su número sería mentira. Sigue estando
     * por debajo de {@code MAX_TOTAL_DEGREES} para no mentir, y sigue siendo uno solo: la suma de
     * muchos chicos es lo que el techo protege, no la magnitud de uno.</p>
     */
    NUCLEAR(8.0F, 20, 160.0F),

    /** Matar. La referencia de la escala: 4,0° recortado un 40 % porque el dueño lo pidió. */
    KILL(2.4F, 6, 40.0F),

    /** Te rompieron la guardia. Es un golpe grande: la postura se cae y quedás quieto dos segundos. */
    GUARD_BREAK(2.0F, 8, 40.0F),

    /** El parry del sable devolvió el golpe. Un momento corto y premiado, del orden de la guardia. */
    PARRY(1.8F, 6, 40.0F),

    /** Un golpe a carga completa aterrizando. Cargar cuesta 40 ticks: que se sienta al llegar. */
    CHARGED_HIT(1.6F, 7, 40.0F),

    /** Bloqueaste. El golpe murió en el escudo, y el hombro lo cobra sin que la vida lo diga. */
    GUARDED(1.2F, 5, 40.0F),

    /** Te pegaron. El más chico de todos, y a propósito: es el que más se repite. */
    HURT(0.8F, 5, 40.0F);

    /**
     * El alcance de todo lo que no sea la nuke.
     *
     * <p><b>Está acá abajo y no arriba por una razón del lenguaje:</b> las constantes de un enum se
     * declaran primero y no pueden referenciar un campo estático -- es una referencia hacia adelante
     * ilegal --, así que los cuarenta bloques están escritos dos veces: en las filas de arriba y acá.
     * Un test los ata ({@code theReachIsWhatSeparatesThem}), que es lo único que evita que se separen
     * en silencio.</p>
     *
     * <p>Lo usan los dos que traen su número a mano en vez de una fila: el culatazo del arcabuz y el
     * reventón del orbe. Ver {@code Shake}.</p>
     */
    public static final float COMMON_REACH = 40.0F;

    private final float degrees;
    private final int ticks;
    private final float reach;

    ShakeKind(float degrees, int ticks, float reach) {
        this.degrees = degrees;
        this.ticks = ticks;
        this.reach = reach;
    }

    /** La amplitud en grados <b>a quemarropa</b>; el cliente la baja con el cuadrado de la distancia. */
    public float degrees() {
        return this.degrees;
    }

    /** Cuánto dura el impulso, en ticks. */
    public int ticks() {
        return this.ticks;
    }

    /**
     * Hasta dónde se siente, en bloques, con la caída cuadrática ya aplicada.
     *
     * <p><b>Va por impulso y no fija, y esa es la mitad que faltaba.</b> El cliente tenía el alcance
     * escrito en una constante -- 40 bloques, el de un reventón común --, así que ningún número podía
     * llegar más lejos por grande que fuera: pasando los 40 el temblor era <b>cero</b>, no chico. Un
     * reactor tiene que sentirse a cien bloques y una flecha que pasa al lado a dos, y eso no es una
     * diferencia de amplitud sino de <b>alcance</b>.</p>
     */
    public float reach() {
        return this.reach;
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
