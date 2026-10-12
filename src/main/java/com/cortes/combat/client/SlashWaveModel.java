package com.cortes.combat.client;

import com.cortes.combat.weapon.SweepingEdge;

/**
 * La aritmética del modelo de la onda de filo: dónde está su centro, cuánto mide, cuánto se
 * agranda por nivel y hacia dónde mira.
 *
 * <h2>Por qué los números están acá y no en el renderer</h2>
 *
 * <p><b>Porque el renderer es una clase de Minecraft y esto no.</b> Un {@code EntityRenderer} no se
 * puede cargar en una JVM pelada, así que cualquier número adentro suyo es un número que ningún test
 * mira -- y los números de este archivo son <em>copias</em>: el centro y el tamaño salen del JSON del
 * modelo, que es del dueño. <b>Una copia de un número es una segunda verdad</b>, y lo que la hace
 * segura es el test que la ata: {@code SlashWaveModelTest} lee
 * {@code assets/cortescombat/models/projectile/sweeping_edge.json} y compara. Es la misma jugada que
 * {@code SabrePoseMatchesJavaTest} con la pose del sable.</p>
 *
 * <h2>La orientación, y por qué es una función y no dos multiplicaciones sueltas</h2>
 *
 * <p>El modelo es un <b>plano</b>: un elemento sin grosor en X, con el largo sobre Z y el alto sobre
 * Y. Para que el tajo avance de canto hay que llevar su <b>+Z local</b> a la dirección de vuelo, y
 * eso son dos rotaciones -- yaw alrededor de Y, pitch alrededor de X -- cuyos signos son exactamente
 * la clase de cosa que este proyecto ya pagó tres veces con el arcabuz ("adivinar los cardinales es
 * una mierda"). Por eso están acá: el test no comprueba que {@code yawDegrees((1,0)) == 90}, que es
 * aritmética de una línea, sino que <b>la rotación compuesta lleva (0,0,1) a la dirección</b>. Esa es
 * la invariante que importa, y es la única que se puede verificar sin abrir el juego.</p>
 */
public final class SlashWaveModel {

    /** El centro del elemento del modelo, en bloques, medido del JSON: {@code (from + to) / 2 / 16}. */
    public static final double CENTER_X = 0.5;
    public static final double CENTER_Y = 0.8125;
    public static final double CENTER_Z = 0.5;

    /** El alto del plano en bloques: 26 unidades de Blockbench. */
    public static final double HEIGHT = 1.625;

    /** El largo del plano en bloques: 24 unidades. */
    public static final double LENGTH = 1.5;

    /**
     * El {@code texture_size} del modelo, y no es un detalle de estilo.
     *
     * <p><b>Las UV del archivo llegan hasta 12 y están en escala 16, no 64.</b> Con el 64 que traía
     * el export de Blockbench, el plano dibujaba la región (0,0)-(12,6.5) de una textura de 64x64 --
     * un trocito de la esquina -- mientras el dibujo vive en x 5..42, y 1..25. La cuenta que lo
     * delata: el plano mide 24x26 unidades y sus UV son 6x6.5, o sea el tamaño dividido por cuatro.
     * El test lo afirma contra el archivo, porque un número que se elige una vez se vuelve a elegir
     * mal la próxima.</p>
     */
    public static final int TEXTURE_SIZE = 16;

    private SlashWaveModel() {
    }

    /**
     * Cuánto se agranda el modelo en este nivel.
     *
     * <p>La escala <b>es</b> el ancho del tajo que {@link SweepingEdge#width} ya define -- 0,7 / 1,0
     * / 1,4 -- y no un segundo número: el ancho es "la caja que barre y lo que se ve" desde que la
     * clase existe, así que el modelo a escala 1,0 es el del nivel 2 y los otros dos quedan al 70 % y
     * al 140 %. Un tajo que crece con el nivel es la única señal visual de que el nivel importa.</p>
     */
    public static double scale(int level) {
        return SweepingEdge.width(level);
    }

    /** El largo horizontal de un vector, que es contra lo que se mide el pitch. */
    public static double horizontal(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * El giro alrededor de Y que lleva el <b>-Z</b> del modelo a la dirección dada.
     *
     * <h2>Por que -Z y no +Z</h2>
     *
     * <p>El elemento del modelo tiene <b>cero de espesor en X</b>: es un plano, y su normal está
     * entera en el eje X. Eso significa que "hacia dónde mira" no lo dice su largo, lo dice su
     * <b>normal</b> -- y la normal está en X. Alinear el {@code +Z} con la dirección dejaba la cara
     * visible <b>mirando para atrás</b>, y el playtest lo reportó tal cual: <i>"el sprite está
     * volteado al revés, mira hacia mí en vez de al frente"</i>.</p>
     *
     * <p>Alinear el {@code -Z} gira el plano media vuelta sobre su propio eje vertical, que es
     * exactamente lo que hace falta: <b>la cara visible pasa para adelante y el plano no se mueve de
     * lugar</b>, porque el renderer lo recentra después y girar sobre el origen no cambia su
     * centro.</p>
     *
     * <p>En el sistema de Minecraft, rotar {@code (0,0,1)} por {@code Axis.YP.rotationDegrees(t)}
     * da {@code (sin t, 0, cos t)}, así que llevar {@code -Z} a {@code (dx, dz)} pide
     * {@code t = atan2(dx, dz) + 180}. Sin signo negativo, a diferencia del yaw de una entidad, que
     * crece al revés: por eso esto no es "el yaw", es la rotación que hace falta.</p>
     */
    public static float yawDegrees(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dx, dz)) + 180.0F;
    }

    /**
     * La inclinación alrededor de X que lleva el <b>-Z</b> del modelo al plano de vuelo.
     *
     * <p>Con {@code Axis.XP.rotationDegrees(p)}, {@code (0,0,1)} va a {@code (0, -sin p, cos p)}. Con
     * el frente en {@code -Z} -- ver {@link #yawDegrees} -- la composición lleva {@code -Z} a
     * {@code (cos p · h, sin p, ...)}, así que el signo <b>no</b> se invierte como antes: una onda
     * que sube quiere {@code p} positivo. Se usa {@code atan2} y no {@code asin(dy/len)} porque un
     * vector de largo cero tiene una respuesta y no una división.</p>
     */
    public static float pitchDegrees(double dy, double horizontal) {
        return (float) Math.toDegrees(Math.atan2(dy, horizontal));
    }
}
