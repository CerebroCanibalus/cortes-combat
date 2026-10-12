package com.cortes.combat.weapon;

/**
 * Sweeping Edge, reworkeado de crowd charge a <b>la onda de filo</b>: un golpe a carga completa
 * dispara un espadazo que viaja, atraviesa y hace una porción del daño cargado.
 *
 * <h2>Por qué la versión anterior se fue</h2>
 *
 * <p>El crowd charge (daño plano que crecía con los cuerpos alrededor del objetivo) tenía un
 * problema de raíz: <b>el jugador no controlaba la variable</b>. El bonus dependía de cuántos mobs
 * hubiera apretados, que es una situación y no una decisión, y el nivel del encantamiento quedaba
 * en segundo lugar frente al tamaño de la multitud. Un número que sube por lo que el mundo puso
 * ahí no se aprende.</p>
 *
 * <h2>La versión nueva, y por qué es una decisión</h2>
 *
 * <p>La onda se dispara con <b>un golpe a carga completa</b>, y eso hace dos cosas a la vez: le da
 * a la carga de la espada una razón de existir -- contra un solo enemigo cargar pierde en DPS, que
 * es algo que el diseño ya tenía anotado -- y convierte el encantamiento en una elección de
 * distancia. <b>El costo es la carga misma</b>: veinticinco ticks de compromiso, sin cooldown
 * extra.</p>
 *
 * <p>Y no es "otro AoE": la onda es un <em>proyectil</em>. Viaja, se puede esquivar, la pared la
 * frena, y una guardia la devuelve. Un barrido de cono no se esquiva.</p>
 *
 * <p>Pura a propósito: nivel adentro, números afuera, todo entero o {@code double} plano. El único
 * consumidor que necesita un mundo es {@code CombatEngine}, que es quien dispara.</p>
 */
public final class SweepingEdge {

    /** El nivel máximo, que es el del encantamiento de vanilla. */
    public static final int MAX_LEVEL = 3;

    /**
     * Bloques por tick de la onda.
     *
     * <p>Uno, que a veinte ticks por segundo son veinte bloques por segundo: más rápido que una
     * flecha (unos treinta, pero con gravedad) y lo bastante lento para que a ocho bloques se vea
     * venir. El alcance y la vida salen de acá, así que este número es el que decide si la onda es
     * un latigazo o un objeto que viaja.</p>
     */
    public static final double SPEED = 1.0;

    /** El empuje de la onda, muy leve: la onda corta, no empuja. */
    public static final double PUSH = 0.12;

    private SweepingEdge() {
    }

    /** Un nivel de encantamiento dentro de los que existen. */
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** Si este nivel dispara la onda. Cero es una espada sin el encantamiento. */
    public static boolean fires(int level) {
        return clampLevel(level) >= 1;
    }

    /** Qué porción del daño cargado lleva la onda. */
    public static float share(int level) {
        return switch (clampLevel(level)) {
            case 1 -> 0.40F;
            case 2 -> 0.60F;
            case 3 -> 0.80F;
            default -> 0.0F;
        };
    }

    /** El ancho del tajo, en bloques: la caja que barre y lo que se ve. */
    public static double width(int level) {
        return switch (clampLevel(level)) {
            case 1 -> 0.7;
            case 2 -> 1.0;
            case 3 -> 1.4;
            default -> 0.0;
        };
    }

    /**
     * Hasta donde llega, en bloques.
     *
     * <p><b>El playtest pidio un 30 % mas y esto es el 30 % mas</b>: la escalera era 8/10/12 y ahora
     * es 10,4/13/15,6. La onda de filo es lo unico del encantamiento que el jugador <b>no puede
     * compensar apuntando</b> -- a diferencia de un arma, que se acerca -- asi que quedarse corto se
     * lee como "la onda no salio", y pasarse un poco no cuesta nada porque la vida de la onda sale
     * de este numero y no de un segundo.</p>
     */
    public static double distance(int level) {
        return switch (clampLevel(level)) {
            case 1 -> 10.4;
            case 2 -> 13.0;
            case 3 -> 15.6;
            default -> 0.0;
        };
    }

    /** A cuántos enemigos atraviesa antes de apagarse. */
    public static int pierce(int level) {
        return switch (clampLevel(level)) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 5;
            default -> 0;
        };
    }

    /**
     * Cuántos ticks vive la onda antes de apagarse sola.
     *
     * <p>Derivado del alcance y no un número aparte: dos números para "hasta dónde llega" es la
     * clase de copia que se desincroniza, y acá la distancia <em>es</em> la vida por la velocidad.
     * El techo de uno se suma para que el último tick todavía recorra su bloque.</p>
     */
    public static int lifeTicks(int level) {
        double reach = distance(level);
        if (reach <= 0.0) {
            return 0;
        }
        return (int) Math.ceil(reach / SPEED) + 1;
    }
}
