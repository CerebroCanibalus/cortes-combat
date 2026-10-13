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
 *   <li><b>El impacto</b> -- un tick de negro. Es el golpe <em>llegando</em>.</li>
 *   <li><b>El rojo</b> -- los nueve ticks que quedan, el rojo al 50 % que el playtest aprobó. Es el
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

    /**
     * El alpha del rojo: <b>0x20 = 87,5 % de rojo</b>, el mismo que el del impacto.
     *
     * <p><b>Y el número subió dos veces por la misma razón: no se veía.</b> El original era 0x80 --
     * 50 % de rojo, ya por encima del 30 % de vanilla -- y el playtest lo llamó <em>"muy poco
     * intenso"</em>. Ahora usa el mismo alpha que {@link #IMPACT_BLACK}, así que los dos frames del
     * flash tienen la <b>misma fuerza</b> y lo único que los distingue es el color: el impacto apaga
     * y el rojo inunda. Un solo número para la intensidad y dos colores es más fácil de razonar que
     * dos intensidades distintas sin motivo.</p>
     *
     * <p>⚠ Es el <b>techo práctico</b>: el shader hace {@code mix(overlayColor.rgb, color.rgb, a)},
     * así que el rojo es puro a alpha cero, pero con cero el mob desaparece y deja de leerse como un
     * cuerpo golpeado. 0x20 deja pasar un octavo de la textura, que es lo que mantiene la silueta.</p>
     */
    public static final int RED_ALPHA = 0x20;

    /**
     * El alpha del frame negro.
     *
     * <p><b>0x20, y el número no es una elección: es el dato.</b> Es el alpha con el que el playtest
     * vio un mob negro, y es lo que se reproduce acá. Un alpha distinto sería volver a adivinar
     * sobre el mismo mecanismo que ya nos corrigió una vez.</p>
     */
    public static final int IMPACT_ALPHA = 0x20;

    /**
     * Cuántos ticks dura el frame negro. <b>Tres.</b>
     *
     * <p><b>La historia de este número son tres playtests, y cada uno dijo algo distinto.</b> Arrancó
     * en dos; el playtest pidió un negro <em>"muy rápido"</em> y bajó a uno; y el siguiente lo
     * mandó a subir otra vez, con la razón más útil de las tres: <b>un tick no se lee como una
     * decisión, se lee como un bug</b>. Un parpadeo de 50 ms no parece el golpe llegando -- parece
     * que el mob se saltó un frame.</p>
     *
     * <p>Tres ticks son 150 ms: suficiente para que el ojo lo registre como <em>algo que pasó</em> y
     * no como un defecto, y todavía lo bastante corto para que el rojo se quede con la parte larga
     * del presupuesto de {@code hurtTime} -- siete ticks.</p>
     *
     * <p>Y el negro va primero por una razón de física y no de gusto: es el golpe <b>llegando</b>. El
     * rojo, que dura más, es el golpe <b>habiendo llegado</b> y lo que le queda al cuerpo.</p>
     */
    public static final int IMPACT_TICKS = 3;

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

    /**
     * La columna del blanco mientras el cuerpo se está muriendo.
     *
     * <p>Quince es el blanco más fuerte que la fila blanca de vanilla sabe dar: su alpha es
     * {@code (1 − j/15 × 0,75) × 255}, así que la columna 15 da 63 de alpha y el shader mezcla un
     * <b>75 %</b> de blanco. Es el techo de esa fila -- para pasar de ahí habría que escribir una
     * fila blanca propia, como se hizo con el negro del impacto.</p>
     */
    public static final int DEATH_WHITE_COLUMN = 15;

    /** El rojo del golpe, con el alpha de {@link #RED_ALPHA}. */
    public static final int STRONGER_RED = (RED_ALPHA << 24) | 0x0000FF;

    /**
     * El frame negro: <b>canales en cero</b>, con el alpha que el playtest produjo.
     *
     * <p><b>Estuvo en rojo, y por eso "el flash negro" no existía.</b> El valor era
     * {@code (0x20 << 24) | 0x0000FF} -- el mismo rojo de {@link #STRONGER_RED} con menos alpha --,
     * así que el shader hacía {@code mix(rojo, textura, 0.125)} = <b>87,5 % de rojo</b>: un rojo
     * saturado, lo contrario de un frame negro. Y el nombre ya decía cuál era la intención.</p>
     *
     * <p>Con los canales en cero el mismo alpha hace {@code mix(negro, textura, 0.125)} =
     * <b>12,5 % de brillo</b>: el mob se apaga. Eso es el impacto.</p>
     */
    public static final int IMPACT_BLACK = (IMPACT_ALPHA << 24) | 0x000000;

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
     * Qué parte de la vida le queda al cuerpo, de 0 a 1.
     *
     * <p>Cero si el máximo no es un número positivo -- un cuerpo sin vida máxima no tiene barra que
     * dibujar, y devolver 1 (vida llena) sería dibujarle un flash que nadie pidió.</p>
     */
    public static float healthFraction(float health, float maxHealth) {
        if (!(maxHealth > 0.0F)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(health / maxHealth, 1.0F));
    }

    /**
     * Dónde está la línea del flash, <b>en la Y del MUNDO</b>.
     *
     * <h2>Es una barra de vida: empieza llena y se vacía</h2>
     *
     * <p>El rojo cubre <b>la parte del cuerpo que todavía tiene vida</b>: a vida llena flashea todo,
     * a media vida la mitad de abajo, y a cero no flashea nada. Es la lectura de una barra de vida,
     * que es la que el dueño pidió explícitamente -- <em>"comienza completa y se va bajando hasta
     * vaciarse"</em>.</p>
     *
     * <p><b>Antes era al revés, y la vuelta tiene historia.</b> La versión anterior hacía cubrir
     * <code>1 - fracción</code> -- "la herida trepa": a vida llena no flasheaba nada y el rojo
     * crecía desde los pies. Venía de <code>docs/fx.md</code> §2.3, y el doc se escribió desde una
     * premisa que después resultó falsa. La barra que se vacía es la que el playtest reconoció como
     * la correcta, así que el doc se corrige, no el código.</p>
     *
     * <h2>Por qué toma el rango medido y no la altura de la caja de colisión</h2>
     *
     * <p>Las dos versiones anteriores calcularon la línea a partir de datos que <b>no son la
     * geometría que se dibuja</b>, y las dos fallaron por lo mismo:</p>
     *
     * <ol>
     *   <li><b>Unidades mezcladas.</b> La Y de un vértice llega en <b>bloques</b>:
     *       {@code ModelPart.Cube.compile} hace {@code pos.y() / 16} <em>antes</em> de transformar
     *       (leído en las fuentes de 21.1.250). El umbral se armaba con {@code getBbHeight() * 16}
     *       -- <b>unidades de modelo</b> --, así que comparaba bloques contra dieciseisavos y el
     *       corte caía 16 veces más arriba de lo que debía: <b>nunca cortaba nada</b>.</li>
     *   <li><b>Un offset que nadie declaró.</b> Entre la posición de la entidad y el modelo hay un
     *       {@code translate(0, -1.501, 0)} y una convención de ejes (el modelo cuenta su Y hacia
     *       abajo), así que ni siquiera con las unidades bien la Y del vértice es
     *       {@code pies + altura local}. El desplazamiento depende del modelo y de su escala.</li>
     * </ol>
     *
     * <p>∴ la única fuente honesta del rango vertical <b>es el modelo</b>, medido en el mismo
     * espacio en que se dibuja. El llamador lo mide y pasa sus dos extremos; acá sólo queda la
     * fracción, que es pura y tiene test.</p>
     *
     * @param minY la Y más baja que el modelo escribe, en el mundo (los pies)
     * @param maxY la Y más alta que el modelo escribe, en el mundo (la cabeza)
     * @return la Y del mundo por <b>encima</b> de la cual el cuerpo no flashea
     */
    public static float barCut(float minY, float maxY, float health, float maxHealth) {
        if (!(maxY > minY)) {
            return minY;
        }
        return minY + (maxY - minY) * healthFraction(health, maxHealth);
    }

    /**
     * La columna del blanco de la muerte, dentro de la textura del overlay.
     *
     * <h2>Es 0..15, y la versión anterior llegaba a 225</h2>
     *
     * <p><b>El bug estaba en el empaquetado, no en la curva.</b> El mixin llamaba
     * {@code OverlayTexture.pack(OverlayTexture.u(white), false)}, y {@code pack} tiene dos
     * sobrecargas: {@code pack(int, int)} y {@code pack(float, boolean)}. Con un {@code int} y un
     * {@code boolean} la que resuelve es <b>la de float</b>, que <b>vuelve a aplicar {@code u()}</b>
     * -- así que la columna salía {@code u(u(white))}, hasta 225 en una textura de 16. Un
     * {@code texelFetch} fuera de rango es <b>indefinido</b>, y el playtest lo vio: el mob se ponía
     * <b>negro</b> al morir en vez de blanco, que es exactamente lo que devuelve muestrear un texel
     * transparente.</p>
     *
     * <p>∴ la columna se calcula acá, pura y en 0..15, y el mixin la empaqueta con la sobrecarga
     * de enteros y la fila explícita. Un test afirma el rango, que es lo que faltaba.</p>
     *
     * @param hurtTime los ticks que le quedan al cuerpo
     * @return la columna, de 0 (sin blanco) a 15 (blanco máximo)
     */
    public static int whiteColumn(int hurtTime) {
        return (int) (whiteForDeath(hurtTime) * 15.0F);
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
