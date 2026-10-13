package com.cortes.combat.client;

/**
 * El ángulo de un temblor, <b>envuelto</b> para que la tabla de senos de {@code Mth} no sature.
 *
 * <h2>El bug que esto arregla, y por qué el shake no hacía nada</h2>
 *
 * <p>{@code Mth.sin} no calcula: <b>consulta una tabla</b>. Leído en las fuentes de 21.1.250:</p>
 *
 * <pre>private static final float SIN_SCALE = 10430.378F;   // = 65536 / 2π
 * public static float sin(float value) { return SIN[(int)(value * 10430.378F) &amp; 65535]; }
 * public static float cos(float value) { return SIN[(int)(value * 10430.378F + 16384.0F) &amp; 65535]; }</pre>
 *
 * <p>Y en Java, <b>convertir un float demasiado grande a {@code int} satura en
 * {@code Integer.MAX_VALUE}</b>. Con {@code value > 205.887} el índice se queda clavado en
 * {@code MAX_VALUE &amp; 65535 = 65535}, y {@code SIN[65535]} es
 * {@code sin(2π − 2π/65536)} = <b>−0,0001</b>. O sea: <b>cero</b>. Lo mismo el coseno, porque el
 * {@code +16384} se suma <em>antes</em> de la conversión.</p>
 *
 * <p>Como el ángulo es <b>el reloj por la frecuencia</b>, el límite es un reloj máximo. Medido en el
 * mundo de prueba, {@code Time = 285340}, y las cuentas dan:</p>
 *
 * <table border="1">
 *   <caption>Cuándo muere cada término</caption>
 *   <tr><th>término</th><th>frecuencia</th><th>muere con reloj &gt;</th><th>con 285340</th></tr>
 *   <tr><td>temblor de matar, pitch</td><td>3,1</td><td>66.415</td><td><b>muerto</b></td></tr>
 *   <tr><td>temblor de matar, roll</td><td>4,7</td><td>43.806</td><td><b>muerto</b></td></tr>
 *   <tr><td>reactor, pitch</td><td>0,7</td><td>294.125</td><td>vivo (por poco)</td></tr>
 *   <tr><td>reactor, roll</td><td>1,9</td><td>108.362</td><td>muerto</td></tr>
 * </table>
 *
 * <p><b>Eso explica el reporte exacto:</b> el shake de matar no hacía <em>nada</em> y el del reactor
 * sí, con la misma mecánica y el mismo evento. Y el del reactor es suerte: su roll ya está muerto
 * también, sólo sobrevive el pitch.</p>
 *
 * <h2>El período del ÁNGULO es 2π, y no 65536</h2>
 *
 * <p>Es la trampa que casi se comete al arreglar esto. {@code SIN_SCALE} vale {@code 65536 / 2π}, así
 * que la tabla da una vuelta entera en <b>{@code 2π} de ángulo</b> -- envolver con {@code 65536}
 * <b>no</b> devuelve el mismo índice (sobra {@code 65536 × SIN_SCALE mod 65536 ≈ 24.786} entradas, o
 * sea 2,376 radianes de escalón). Con {@code 2π} el escalón es exactamente una vuelta, y por eso el
 * temblor <b>no salta</b> cuando envuelve.</p>
 *
 * <h2>Por qué el ángulo sale de la EDAD del impulso y no del reloj del mundo</h2>
 *
 * <p>Dos razones, y la segunda es la que importa:</p>
 *
 * <ul>
 *   <li><b>La fase arranca en cero</b> en el tick en que nace el temblor, así que dos impulsos
 *       iguales se mueven igual. Con el reloj del mundo, dos temblores idénticos empezaban en fases
 *       distintas y no había forma de saber cuál iba a salir.</li>
 *   <li><b>El número es chico, así que la precisión es exacta.</b> {@code tiempo × frecuencia} con
 *       el reloj del mundo vale millones, y un {@code float} de millones tiene una resolución de
 *       décimas: el módulo arrastra ese error. La edad vale 0..6 ticks, y ahí no se pierde nada.</li>
 * </ul>
 *
 * <p>Y no rompe la regla del proyecto: la edad se deriva del reloj <b>local</b>, así que el temblor
 * sigue sin viajar por la red -- dos clientes ven el mismo movimiento y distinto temblor, a
 * propósito.</p>
 *
 * <h2>Por qué es una clase aparte</h2>
 *
 * <p>Porque {@code Mth} es de Minecraft y <b>no se puede cargar en un test</b>. Acá queda la
 * aritmética -- que es la que estaba mal -- y el test la afirma: que el ángulo envuelto está por
 * debajo del umbral de saturación, que su seno coincide con el de la fase real calculada en
 * {@code double}, y que sin envolver el ángulo <b>sí</b> saturaba.</p>
 */
public final class ShakePhase {

    /**
     * Una vuelta completa, y el período real del ángulo en la tabla.
     *
     * <p>Ver el javadoc de la clase: <b>no</b> es {@code 65536}. Ese número es el tamaño de la tabla,
     * no su período en radianes.</p>
     */
    public static final float TURN = (float) (Math.PI * 2.0);

    /**
     * El ángulo más grande que {@code Mth.sin} acepta sin saturar, {@code 2^31 / (65536 / 2π)}.
     *
     * <p>Está acá para que un test pueda afirmar que el resultado queda por debajo, y para que el
     * número que causó el bug tenga nombre.</p>
     */
    public static final float MAX_SAFE_ANGLE = 205887.0F;

    /**
     * El techo de la <b>suma</b> de todos los impulsos vivos, en grados.
     *
     * <h2>Por qué hay un techo, y por qué no es el de un impulso</h2>
     *
     * <p>Los impulsos se suman: {@code CameraShake} recorre su lista y acumula el pitch y el roll.
     * Eso es lo correcto -- un reventón al lado de un reactor sacude más que cualquiera de los dos --
     * pero significa que <b>el tamaño de un temblor no acota el de la cámara</b>. Cinco mobs pegando
     * en el mismo tick son cinco impulsos, y con el {@code HURT} más chico de la tabla eso ya son
     * cuatro grados que nadie eligió.</p>
     *
     * <p>∴ el techo es de la suma y no de las partes, y <b>está por encima del impulso más grande</b>
     * ({@code ShakeKind.largestDegrees()}, que es el reventón nuclear): si estuviera por debajo, un
     * reactor solo se recortaría a sí mismo y el número de la tabla sería mentira. Un test lo afirma
     * cruzando las dos constantes.</p>
     *
     * <p>Subió de 6,0 a 10,0 cuando entró la nuke, y la protección no se perdió: lo que el techo
     * defiende es la <b>suma de muchos chicos</b> -- cinco mobs pegando en el mismo tick --, y eso
     * sigue muy por debajo. Un solo impulso grande es un hecho, no una acumulación.</p>
     */
    public static final float MAX_TOTAL_DEGREES = 10.0F;

    private ShakePhase() {
    }

    /**
     * La fase de un temblor, envuelta a una vuelta.
     *
     * <p>El llamador hace {@code Mth.sin(ShakePhase.angle(t, frequency))} y ya no puede saturar: el
     * resultado está en {@code [0, 2π)}, que multiplicado por {@code SIN_SCALE} da {@code 65.536} --
     * tres mil veces por debajo de {@code 2^31}.</p>
     *
     * @param time <b>de dónde sale el tiempo lo decide el llamador</b>: un impulso puntual pasa su
     *     <b>edad</b> (0..6 ticks, exacta y con la fase empezando en cero), y un temblor continuo
     *     como el del reactor pasa el <b>reloj del mundo</b>. Los dos son válidos porque lo único
     *     que la tabla necesita es un ángulo envuelto; la edad además gana precisión, que es lo que
     *     importa cuando el reloj ya lleva millones de ticks
     * @param frequency radianes por tick
     * @return el mismo ángulo que {@code time * frequency}, dentro de una vuelta
     */
    public static float angle(float time, float frequency) {
        return (time * frequency) % TURN;
    }

    /**
     * Recorta la suma de los impulsos al techo, conservando el signo.
     *
     * <p>El signo importa: el roll de un impulso lateral es negativo, y recortarlo contra cero lo
     * apagaría en vez de limitarlo.</p>
     */
    public static float clampTotal(float degrees) {
        if (degrees > MAX_TOTAL_DEGREES) {
            return MAX_TOTAL_DEGREES;
        }
        if (degrees < -MAX_TOTAL_DEGREES) {
            return -MAX_TOTAL_DEGREES;
        }
        return degrees;
    }
}
