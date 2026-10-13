package com.cortes.combat.client;

/**
 * Nuestro flash de daño: <b>un frame negro de impacto y después el rojo</b>.
 *
 * <h2>Por qué dejamos de usar el rojo de vanilla</h2>
 *
 * <p>La versión anterior sólo le subía el alpha al rojo de vanilla. Funcionaba, pero dejaba el
 * efecto entero en manos de un mecanismo cuyo comportamiento no se pudo deducir: el intento de
 * escalarlo por la vida restante salió <b>negro</b> (ver la historia en el git), y la conclusión
 * honesta fue que la columna hace algo que el código no explica.</p>
 *
 * <p>∴ este efecto <b>usa el eje que sí está explicado</b>: las <b>filas</b>. Vanilla usa la fila 3
 * para el rojo y la 10 para el blanco, así que la fila <em>es</em> el selector de color y funciona
 * -- y las filas 0-2 y 4-7 están <b>sin usar</b>, escritas de rojo por vanilla y consultadas por
 * nadie. Son nuestras.</p>
 *
 * <h2>El frame negro, y de dónde salió</h2>
 *
 * <p><b>Del bug.</b> Cuando el gradiente falló, el dueño vio un frame negro al principio del golpe y
 * dijo que <em>"le da impacto"</em> y que lo mantengamos. Y el negro <b>no es una suposición</b>: es
 * el alpha que el playtest produjo y aprobó, en una fila roja. Ese es el dato que se copia acá.</p>
 *
 * <p>El efecto queda entonces con dos tiempos, que es lo que lo hace un golpe y no un tinte:</p>
 *
 * <ol>
 *   <li><b>El impacto</b> -- dos ticks de negro. Es el golpe <em>llegando</em>.</li>
 *   <li><b>El rojo</b> -- los ocho ticks que quedan, el rojo al 50 % que el playtest aprobó. Es el
 *       golpe <em>habiendo llegado</em>.</li>
 * </ol>
 *
 * <h2>La paleta es una tabla, y eso es lo que se gana</h2>
 *
 * <p>Antes el efecto era <b>un</b> número. Ahora es una tabla de filas: agregar un tiempo nuevo
 * (una chispa blanca, un segundo rojo más profundo) es escribir una fila y un caso, y el camino
 * está probado. <b>Eso es lo que se pidió al decir "reconstruyamos con nuestro propio efecto".</b></p>
 */
public final class DamageFlash {

    /**
     * El rojo que el juego escribe en las filas 0-7, en empaquetado ABGR.
     *
     * <p>Es la marca que identifica <b>las filas rojas</b>: reconocerlas por su color y no por su
     * índice significa que si Mojang mueve las filas, esto sigue funcionando.</p>
     */
    public static final int VANILLA_RED = 0xB20000FF;

    /** La fila del rojo. Es la que vanilla usa para herir, y la dejamos donde está. */
    public static final int RED_ROW = 3;

    /** La fila del frame negro. <b>Sin usar por vanilla</b>, escrita de rojo, consultada por nadie. */
    public static final int IMPACT_ROW = 5;

    /** El alpha del rojo: 0x80 = 50 % de rojo contra el 30 % de vanilla. El que el playtest aprobó. */
    public static final int RED_ALPHA = 0x80;

    /**
     * El alpha del frame negro.
     *
     * <p><b>0x20, y el número no es una elección: es el dato.</b> Es el alpha con el que el playtest
     * vio un mob negro, y es lo que se reproduce acá. Un alpha distinto sería volver a adivinar
     * sobre el mismo mecanismo que ya nos corrigió una vez.</p>
     */
    public static final int IMPACT_ALPHA = 0x20;

    /** Cuántos ticks dura el frame negro. Dos: se ve y no se queda. */
    public static final int IMPACT_TICKS = 2;

    /**
     * Cuántos ticks dura el flash entero.
     *
     * <p>Es {@code LivingEntity.hurtTime}, que arranca en 10 y baja. La constante está acá y no
     * leída del cuerpo porque es la <b>duración del efecto</b>, no una propiedad de la entidad.</p>
     */
    public static final int FLASH_TICKS = 10;

    /** El rojo al 50 %, que es lo que se escribe en {@link #RED_ROW}. */
    public static final int STRONGER_RED = (RED_ALPHA << 24) | 0x0000FF;

    /** El frame negro, con el alpha que el playtest produjo. */
    public static final int IMPACT_BLACK = (IMPACT_ALPHA << 24) | 0x0000FF;

    private DamageFlash() {
    }

    /**
     * El color que va a la textura, dado el que el juego iba a escribir y en qué fila.
     *
     * <p><b>Sólo toca las filas rojas</b>, reconocidas por su color. Las filas blancas pasan
     * intactas: su alpha varía por columna y es ese gradiente entero lo que hace funcionar el flash
     * blanco -- el del creeper y el de la muerte. Tocarlo rompería algo que nadie pidió.</p>
     *
     * <p>Y las filas rojas que no son nuestras ni de vanilla quedan como están: <b>no se reescribe
     * lo que no se va a usar</b>, porque cada fila reescrita es una que alguien puede estar
     * consultando sin que lo sepamos.</p>
     */
    public static int texturePixel(int row, int colour) {
        if (colour != VANILLA_RED) {
            return colour;
        }
        if (row == IMPACT_ROW) {
            return IMPACT_BLACK;
        }
        if (row == RED_ROW) {
            return STRONGER_RED;
        }
        return colour;
    }

    /**
     * Qué fila le toca a un cuerpo con este {@code hurtTime}.
     *
     * <p>{@code hurtTime} <b>cuenta para abajo</b> desde {@link #FLASH_TICKS}: los primeros ticks
     * son el número más alto. Así que el impacto es la parte alta del rango, y confundir la
     * dirección pondría el frame negro al final -- cuando el golpe ya se fue.</p>
     *
     * @param hurtTime el contador del cuerpo; cero o menos significa que no está herido
     * @return la fila del impacto mientras queden ticks de él, y la del rojo después
     */
    public static int rowFor(int hurtTime) {
        return hurtTime > FLASH_TICKS - IMPACT_TICKS ? IMPACT_ROW : RED_ROW;
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
        return Math.min(deathTime / 20.0F, 1.0F);
    }
}
