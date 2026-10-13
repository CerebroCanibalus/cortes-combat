package com.cortes.combat.client;

/**
 * El rojo del flash de daño de vanilla, <b>más fuerte</b>. Y una idea que el playtest mató.
 *
 * <h2>Cómo funciona el flash, leído de las fuentes de 21.1.250</h2>
 *
 * <p>{@code OverlayTexture} es una textura dinámica de 16x16 que el juego llena en su constructor.
 * Las filas 0-7 son <b>rojo</b> y las 8-15 <b>blanco</b>, y el entero que devuelve
 * {@code getOverlayCoords} lleva una <b>columna</b> y una <b>fila</b>: la fila dice rojo o blanco, y
 * la columna elige la intensidad del blanco (el gradiente del creeper).</p>
 *
 * <p>Y el alpha de la textura es <b>cuánto sobrevive el color de la entidad</b>: 255 es "sin
 * overlay" y 0 es "todo overlay". Por eso {@code NO_OVERLAY} es la columna 0 de una fila blanca,
 * donde el alpha vale 255.</p>
 *
 * <h2>El rojo reforzado</h2>
 *
 * <p>Vanilla usa {@code 0xB20000FF}, alpha {@code 0xB2} = 178, o sea <b>30 % de rojo</b>. Acá baja a
 * {@code 0x80} = 128 y el rojo sube al <b>50 %</b>: se nota sin volverse una silueta. Por debajo de
 * {@code 0x40} el mob deja de leerse y el flash tapa la animación en vez de acompañarla.</p>
 *
 * <h2>Y la idea que el playtest mató: el gradiente por columna</h2>
 *
 * <p>El diseño pedía que la fuerza del rojo dijera <b>cuánta vida le queda</b> al cuerpo, y el
 * camino parecía obvio: las ocho filas rojas de vanilla son <b>idénticas</b>, así que la columna no
 * hacía nada en ellas -- y si se les escribía un gradiente, la columna pasaba a elegir la
 * intensidad y {@code getOverlayCoords} ya elige la columna por entidad.</p>
 *
 * <p><b>Se implementó, y el playtest lo desmintió en un minuto:</b> <em>"los enemigos se ven
 * totalmente negros, y sólo si el golpe es tan bajo que muestra la vida completa se ve
 * correctamente"</em>. O sea: columna 0 (alpha {@code 0x80}) = el rojo correcto, y las columnas
 * altas (alpha {@code 0x20}) = <b>negro</b>.</p>
 *
 * <p><b>O sea que bajar el alpha oscurece, que es lo contrario de lo que dice el shader.</b> La
 * explicación no está en el código que se leyó -- {@code setPixelRGBA} escribe el ABGR crudo y el
 * rojo es el mismo en todas las columnas -- así que hay algo del camino columna→texel que no se
 * puede deducir leyendo, y el playtest tiene razón sobre la teoría. <b>El gradiente se sacó, y la
 * vida en el flash queda pendiente del camino por vértice</b>, que es el único que no depende de un
 * mecanismo que no se pudo verificar.</p>
 *
 * <p>Lo que queda es lo que el playtest <em>sí</em> aprobó: un rojo uniforme y más fuerte.</p>
 *
 * <h2>Por qué esta clase es pura</h2>
 *
 * <p>Enteros que entran, enteros que salen. El mixin que la usa <b>no arranca en una JVM pelada</b>,
 * así que si los números vivieran adentro no habría una sola aserción mirándolos.</p>
 */
public final class DamageFlash {

    /**
     * El rojo que el juego escribe hoy, en empaquetado ABGR.
     *
     * <p>Leído de {@code OverlayTexture.<init>}: {@code setPixelRGBA(j, i, -1308622593)} para las
     * filas 0-7, y {@code -1308622593} es este número en complemento a dos. El parámetro de
     * {@code setPixelRGBA} se llama {@code abgrColor} y el cuerpo lo escribe crudo con
     * {@code memPutInt}, así que el valor <b>es</b> el ABGR: bits 31-24 alpha, 23-16 azul, 15-8
     * verde, 7-0 rojo.</p>
     */
    public static final int VANILLA_RED = 0xB20000FF;

    /**
     * El alpha del rojo: 0x80 = 0,502, o sea <b>50 % de rojo</b> contra el 30 % de vanilla.
     *
     * <p>Es el número que el playtest aprobó y el único que el flash usa. <b>Y no se toca hacia
     * abajo</b>: ver el javadoc de la clase sobre lo que pasó con el gradiente.</p>
     */
    public static final int FLASH_ALPHA = 0x80;

    /** El rojo con el alpha de arriba, mismo canal B/G/R. */
    public static final int STRONGER_RED = (FLASH_ALPHA << 24) | 0x0000FF;

    /** La animación de muerte de vanilla: {@code deathTime} va de 0 a 20. */
    public static final int DEATH_TICKS = 20;

    private DamageFlash() {
    }

    /**
     * El color que va a la textura, dado el que el juego quería escribir.
     *
     * <p><b>Sólo cambia el rojo.</b> Las filas blancas pasan intactas: su alpha varía por columna y
     * es ese gradiente entero lo que hace funcionar el flash blanco -- el del creeper y el de la
     * muerte. Tocarlo rompería algo que nadie pidió.</p>
     */
    public static int strengthen(int colour) {
        return colour == VANILLA_RED ? STRONGER_RED : colour;
    }

    /**
     * Cuánto blanco tiene el flash de la muerte, de 0 a 1.
     *
     * <p>La animación dura veinte ticks, y el blanco entra a lo largo de ella: arranca en nada y
     * termina en el blanco más fuerte que la textura tiene -- el 75 %, que es la columna 15 de las
     * filas blancas.</p>
     *
     * <p><b>Y el rojo se apaga solo</b>, sin una regla que lo diga: cuando esto pasa de cero, el
     * llamador deja de usar la fila roja. El cuerpo deja de estar herido y pasa a estar muriéndose,
     * que son dos cosas distintas.</p>
     */
    public static float whiteForDeath(int deathTime) {
        if (deathTime <= 0) {
            return 0.0F;
        }
        return Math.min(deathTime / (float) DEATH_TICKS, 1.0F);
    }
}
