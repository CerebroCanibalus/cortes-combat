package com.cortes.combat.client;

/**
 * El rojo del flash de daño de vanilla, <b>más fuerte</b>.
 *
 * <h2>Cómo funciona el flash, leído de las fuentes de 21.1.250</h2>
 *
 * <p>{@code OverlayTexture} es una textura dinámica de 16x16 que el juego llena en su constructor.
 * Las filas 0-7 son <b>rojo</b> y las 8-15 <b>blanco</b>, y el shader de entidad mezcla así:</p>
 *
 * <pre>color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);</pre>
 *
 * <p><b>El alpha es cuánto SOBREVIVE el color de la entidad</b>, no cuánto se pinta. Un alpha de
 * 255 es "sin overlay" y un alpha de 0 es "todo overlay" -- que es exactamente por qué
 * {@code NO_OVERLAY} es la columna 0 de una fila blanca, donde el alpha vale 255.</p>
 *
 * <h2>El número que se cambia</h2>
 *
 * <p>El rojo de vanilla es {@code 0xB20000FF} en empaquetado ABGR: alpha {@code 0xB2} = 178, o sea
 * <b>0,698</b> -- la entidad conserva el 70 % de su color y el rojo es un tinte del <b>30 %</b>. Se
 * nota, pero poco: el dueño lo pidió "un poco más intenso".</p>
 *
 * <p>Con {@code 0x80} = 128 el alpha baja a 0,502 y el rojo sube al <b>50 %</b>. Es un aumento del
 * 65 % en la cantidad de rojo, que se ve sin volverse una silueta roja: por debajo de 0x40 el mob
 * deja de leerse y el flash tapa la animación en vez de acompañarla.</p>
 *
 * <p><b>Y es global, no por mob.</b> La textura la comparte todo el juego, así que el flash de
 * vanilla también cambia -- que es lo que se pidió ("el flash de daño al enemigo"). La alternativa
 * era una textura propia por entidad, y eso no es algo que el overlay sepa hacer: es una textura
 * para todos.</p>
 *
 * <h2>Por qué esta clase es pura</h2>
 *
 * <p>Un {@code int} entra y un {@code int} sale, sin un solo tipo de Minecraft. Eso la hace
 * <b>cargable en una JVM pelada</b>, que es la línea exacta de este proyecto: la aritmética vive
 * donde un test la alcanza, y el mixin -- que no se puede testear -- sólo la llama. La alternativa,
 * calcular el número adentro del mixin, dejaría el único valor de esta ronda sin ninguna aserción
 * que lo mire.</p>
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
     * El alpha nuevo: 0,502. La entidad conserva la mitad de su color y el rojo es del 50 %.
     *
     * <p>Constante y no un literal adentro del método, porque <b>es el número que se va a querer
     * tocar después de un playtest</b> y tiene que tener nombre.</p>
     */
    public static final int FLASH_ALPHA = 0x80;

    /** El rojo con el alpha nuevo, mismo canal B/G/R. */
    public static final int STRONGER_RED = (FLASH_ALPHA << 24) | 0x0000FF;

    private DamageFlash() {
    }

    /**
     * El color que va a la textura, dado el que el juego quería escribir.
     *
     * <p><b>Sólo cambia el rojo.</b> Las filas blancas -- las que el creeper usa para su flash, y
     * las que la muerte va a usar -- pasan intactas, porque su alpha no es constante sino que varía
     * por columna y es el gradiente entero lo que las hace funcionar.</p>
     *
     * @param color el color que el constructor de {@code OverlayTexture} iba a escribir
     * @return el rojo reforzado, o el mismo color si no era el rojo
     */
    public static int strengthen(int color) {
        return color == VANILLA_RED ? STRONGER_RED : color;
    }
}
