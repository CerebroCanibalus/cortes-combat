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

    /**
     * Cuántos ticks dura el frame negro. <b>Uno.</b>
     *
     * <p>Eran dos, y el playtest los mandó a bajar: se pidió un flash negro <em>"muy rápido"</em> y un
     * rojo <em>"más lento"</em>, y las dos cosas se consiguen moviendo este número, porque la
     * duración total es la de {@code hurtTime} y ⊘ se puede estirar. Un tick de negro y siete de rojo
     * le dan al rojo la parte larga del mismo presupuesto.</p>
     */
    public static final int IMPACT_TICKS = 1;

    /**
     * Los ticks que dura el BLANCO de la muerte.
     *
     * <p>Dos, y va al final: el golpe mortal es negro, después rojo, y el blanco entra cuando el rojo
     * se está yendo. Es el orden que se pidió -- <em>"negro muy rápido, rojo más lento, blanco rápido
     * pero suave al morir"</em> -- y sale de comparar contra este número, ⊘ de un reloj aparte.</p>
     */
    public static final int DEATH_WHITE_TICKS = 2;

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
     * Cuántos unidades de modelo hay en un bloque.
     *
     * <p>Dieciséis, que es la escala de Blockbench y la que usa todo modelo de vanilla. La altura
     * del modelo sale de la <b>caja de colisión</b> de la entidad multiplicada por esto, y no de una
     * tabla por tipo: un mob nuevo, o el de otro mod, queda con su altura correcta sin que nadie
     * agregue una fila.</p>
     */
    public static final float MODEL_UNITS_PER_BLOCK = 16.0F;

    /**
     * Dónde está la línea del flash, <b>medida desde los pies del modelo</b>.
     *
     * <h2>La herida trepa: a menos vida, más rojo</h2>
     *
     * <p><b>El rojo es lo que le FALTA al mob, y por eso crece.</b> A vida llena no flashea nada; a
     * media vida flashea la mitad de abajo; y a un 10 % de vida flashea el 90 %, que es lo que el
     * diseño pide (<code>docs/fx.md</code> §2.3: <em>"crece hacia arriba mientras baja la vida -- la
     * herida trepa --. A 10 %, el 90 %"</em>).</p>
     *
     * <p><b>Esto estuvo al revés en la primera versión</b>, y se escribió un javadoc entero
     * defendiendo la dirección contraria (el rojo como "la vida que queda", retirándose hacia
     * abajo). El doc decía lo opuesto desde el principio: <b>la herida trepa</b>. Un comentario que
     * explica por qué una rareza es correcta es exactamente donde hay que ir a mirar si lo es.</p>
     *
     * <h2>La Y que devuelve NO es la del vértice, y esa fue la segunda trampa</h2>
     *
     * <p>Devuelve una altura <b>relativa a los pies</b>, y el llamador le suma la Y base de la
     * entidad. Es obligatorio: el {@code PoseStack} que llega al modelo <b>ya está trasladado a la
     * posición del mundo</b> (verificado con {@code javap}: {@code EntityRenderDispatcher} hace
     * {@code translate} en el offset 59 y llama a {@code render} en el 75), así que la Y de un
     * vértice vale la altura del mob en el mundo <b>más</b> su altura local. Comparar contra un
     * umbral local de 0..32 con la entidad a Y=70 no corta nunca: <b>eso era "no baja por vida"</b>.</p>
     *
     * @param modelHeight la altura del modelo en unidades de modelo, no en bloques
     * @return la altura, <b>desde los pies</b>, por <b>encima</b> de la cual el cuerpo no flashea
     */
    public static float barThreshold(float health, float maxHealth, float modelHeight) {
        if (!(maxHealth > 0.0F) || !(modelHeight > 0.0F)) {
            return 0.0F;
        }
        float fraction = Math.max(0.0F, Math.min(health / maxHealth, 1.0F));
        return modelHeight * (1.0F - fraction);
    }

    /**
     * Cuánto blanco tiene el flash de la muerte, de 0 a 1.
     *
     * <h2>Toma {@code hurtTime}, ⊘ {@code deathTime}, y esa fue la tercera trampa</h2>
     *
     * <p><b>{@code deathTime} no se sincroniza.</b> Verificado con {@code javap}: en
     * {@code LivingEntity.defineSynchedData} está {@code DATA_HEALTH_ID} y <b>no</b> está
     * {@code deathTime} -- y el cliente ⊘ tickea el contador de una entidad que no sabe que murió,
     * así que <b>vale cero siempre</b>. El blanco de la muerte estaba escrito contra un reloj que en
     * el cliente nunca arranca: <b>no se dibujó ni una vez</b>.</p>
     *
     * <p>{@code hurtTime} <b>sí</b> llega al cliente, por {@code ClientboundHurtAnimationPacket}
     * ({@code animateHurt}), que es lo que hace que el rojo de vanilla funcione. Así que el reloj de
     * la muerte es el mismo del golpe, y lo único que hay que agregar para saber que murió es
     * {@code getHealth() <= 0}, que <b>sí</b> viaja en {@code DATA_HEALTH_ID}.</p>
     *
     * @param hurtTime los ticks que le quedan al cuerpo; ⊘ {@code deathTime}
     * @return 0 mientras el rojo sigue, y crece hasta 1 en los últimos {@link #DEATH_WHITE_TICKS}
     */
    public static float whiteForDeath(int hurtTime) {
        if (hurtTime <= 0 || hurtTime > DEATH_WHITE_TICKS) {
            return 0.0F;
        }
        return (DEATH_WHITE_TICKS + 1 - hurtTime) / (float) DEATH_WHITE_TICKS;
    }
}
