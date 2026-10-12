package com.cortes.combat.client;

/**
 * El rojo del flash de daño de vanilla: <b>más fuerte, y proporcional a la vida que le queda</b>.
 *
 * <h2>Cómo funciona el flash, leído de las fuentes de 21.1.250</h2>
 *
 * <p>{@code OverlayTexture} es una textura dinámica de 16x16 que el juego llena en su constructor.
 * Las filas 0-7 son <b>rojo</b> y las 8-15 <b>blanco</b>, y el shader de entidad mezcla así:</p>
 *
 * <pre>color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);</pre>
 *
 * <p><b>El alpha es cuánto SOBREVIVE el color de la entidad</b>, no cuánto se pinta. Alpha 255 es
 * "sin overlay" y alpha 0 es "todo overlay" -- que es por qué {@code NO_OVERLAY} es la columna 0 de
 * una fila blanca, donde el alpha vale 255.</p>
 *
 * <h2>El truco: el rojo pasa de plano a gradiente</h2>
 *
 * <p>Vanilla deja las <b>ocho filas rojas idénticas</b>, así que el rojo es un booleano: está o no
 * está. Y el entero que devuelve {@code getOverlayCoords} elige la <b>fila</b> y la <b>columna</b> --
 * la fila dice rojo o blanco, y la columna, en las filas rojas, <b>no hacía nada</b>.</p>
 *
 * <p>Acá la columna pasa a elegir la <b>intensidad</b>: la 0 es el rojo de siempre (reforzado) y la
 * 15 el rojo de un cuerpo que está por morir. Y como la columna es lo que el juego ya elige por
 * entidad, <b>un mob con poca vida flashea mucho más fuerte que uno entero</b> sin una línea de
 * render propio.</p>
 *
 * <h2>Por qué la intensidad y no "media silueta"</h2>
 *
 * <p>La idea original era que la mitad del modelo se pintara a media vida. <b>El overlay no puede
 * hacer eso</b>: {@code getOverlayCoords} devuelve <em>un</em> entero para el modelo entero, así que
 * el flash es uniforme por construcción. La cobertura parcial exigiría decidir por <b>vértice</b>, y
 * eso es un {@code VertexConsumer} que envuelva al del modelo -- factible, y pendiente.</p>
 *
 * <p><b>Lo que sí se puede y es lo que importa</b>: que la intensidad <em>diga</em> la vida. El
 * jugador lee "está por caer" en la fuerza del rojo, que es la mitad de la información y la única
 * que el overlay sabe dar.</p>
 *
 * <h2>Por qué esta clase es pura</h2>
 *
 * <p>Enteros que entran, enteros que salen. Los mixins que la usan <b>no arrancan en una JVM
 * pelada</b>, así que si la aritmética viviera adentro de ellos no habría una sola aserción mirando
 * los únicos números de esta ronda.</p>
 */
public final class DamageFlash {

    /**
     * El rojo que el juego escribe hoy, en empaquetado ABGR.
     *
     * <p>Leído de {@code OverlayTexture.<init>}: {@code setPixelRGBA(j, i, -1308622593)} para las
     * filas 0-7, y {@code -1308622593} es este número en complemento a dos.</p>
     */
    public static final int VANILLA_RED = 0xB20000FF;

    /**
     * El alpha de la columna 0: el flash de un cuerpo <b>entero</b>.
     *
     * <p>0x80 = 0,502, o sea <b>50 % de rojo</b> contra el 30 % de vanilla. Es el "más intenso" que
     * se pidió, y es lo que ve un mob sano.</p>
     */
    public static final int BASE_ALPHA = 0x80;

    /**
     * El alpha de la columna 15: el flash de un cuerpo <b>a punto de caer</b>.
     *
     * <p>0x20 = 0,125, o sea <b>87 % de rojo</b>. Casi una silueta -- y está bien que lo sea: a esa
     * altura de vida el mob se está muriendo, y el aviso tiene que ganarle a la animación.</p>
     *
     * <p><b>Este es el número que el diseño pone por debajo del piso de 0x40</b> que el test de la
     * ronda anterior defendía, y no es una contradicción: aquel piso protegía el flash
     * <em>normal</em>, que es {@link #BASE_ALPHA}. Acá el extremo es a propósito.</p>
     */
    public static final int MAX_ALPHA = 0x20;

    /** El rojo de la columna 0: el mismo número que la ronda anterior, ahora como el extremo suave. */
    public static final int STRONGER_RED = (BASE_ALPHA << 24) | 0x0000FF;

    /** Cuántas columnas tiene la textura. La última es la 15. */
    public static final int LAST_COLUMN = 15;

    private DamageFlash() {
    }

    /**
     * El alpha del rojo en una columna: {@link #BASE_ALPHA} en la 0, {@link #MAX_ALPHA} en la 15.
     *
     * <p>Lineal entre las dos. Una curva daría más resolución cerca de la muerte, donde el aviso
     * importa más, y es el retune obvio si el salto se siente tarde.</p>
     */
    public static int alphaForColumn(int column) {
        float t = clamp01(column / (float) LAST_COLUMN);
        return Math.round(BASE_ALPHA + (MAX_ALPHA - BASE_ALPHA) * t);
    }

    /**
     * El píxel que va a la textura, dado el que el juego iba a escribir.
     *
     * <p><b>Sólo toca el rojo.</b> Las filas blancas pasan intactas: su alpha no es constante sino
     * que varía por columna, y es el gradiente entero lo que hace funcionar el flash blanco -- el
     * del creeper y el de la muerte. Reconocer el rojo por su <b>color</b> y no por su fila es lo
     * que permite que el mixin no tenga que saber en qué fila está.</p>
     */
    public static int texturePixel(int column, int colour) {
        if (colour != VANILLA_RED) {
            return colour;
        }
        return (alphaForColumn(column) << 24) | 0x0000FF;
    }

    /** El atajo de la columna 0, que es el caso por defecto de vanilla. */
    public static int strengthen(int colour) {
        return texturePixel(0, colour);
    }

    /**
     * Qué columna le toca a un cuerpo con esta vida, como fracción de 0 a 1.
     *
     * <p><b>Invertido a propósito:</b> vida llena da 0 (el rojo suave) y vida por agotarse da 1 (el
     * rojo fuerte). La columna es "cuánto se está muriendo", no "cuánta vida le queda".</p>
     */
    public static float uForHealth(float health, float maxHealth) {
        if (!(maxHealth > 0.0F)) {
            return 0.0F;
        }
        return 1.0F - clamp01(health / maxHealth);
    }

    /**
     * Cuánto blanco tiene el flash de la muerte, de 0 a 1.
     *
     * <p>La animación de muerte dura veinte ticks ({@code deathTime} de 0 a 20), y el blanco entra
     * a lo largo de ella: arranca en nada y termina en el blanco más fuerte que la textura tiene --
     * el 75 %, que es la columna 15 de las filas blancas.</p>
     *
     * <p><b>Y el rojo se apaga solo</b>, sin una regla que lo diga: cuando esto pasa de cero, el
     * llamador deja de usar la fila roja. El cuerpo deja de estar herido y pasa a estar muriéndose,
     * que son dos cosas distintas.</p>
     */
    public static float whiteForDeath(int deathTime, int deathTicks) {
        if (deathTime <= 0 || deathTicks <= 0) {
            return 0.0F;
        }
        return clamp01(deathTime / (float) deathTicks);
    }

    /** Un clamp sin {@code Mth}, porque esta clase tiene que poder cargar sin Minecraft. */
    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}
