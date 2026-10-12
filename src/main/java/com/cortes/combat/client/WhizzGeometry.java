package com.cortes.combat.client;

/**
 * Si una flecha va a pasar cerca, y por dónde. <b>Sin nada de Minecraft</b>, para que un test lo
 * alcance.
 *
 * <h2>Por qué la distancia al tirador no sirve</h2>
 *
 * <p>Lo que hace que una flecha zumbE es <b>cuánto se acerca a la cabeza</b>, no de dónde vino. Una
 * flecha disparada desde veinte bloques que pasa a un palmo suena igual que una de tres, y una
 * disparada de cerca que se va por el otro lado no se siente. La única pregunta correcta es la
 * <b>distancia mínima a la trayectoria</b>, que es lo que calcula esto.</p>
 *
 * <h2>Un rayo y no un segmento</h2>
 *
 * <p>Se proyecta hacia adelante sin límite porque la flecha va a seguir estando ahí en el próximo
 * tick, y cortar el rayo en la velocidad de un tick haría que el cálculo sólo viera el tramo que ya
 * pasó. <b>Y el {@code t} se clampea a cero</b>: una flecha que ya pasó y se aleja no tiene punto de
 * máxima aproximación por delante, y sin el clamp el mínimo caería detrás del jugador y daría una
 * distancia que nunca ocurrió.</p>
 */
public final class WhizzGeometry {

    /** A qué distancia cuenta como "pasó al lado". Dos bloques: cerca de la cabeza y no del cuerpo. */
    public static final double WHIZZ_RADIUS = 2.0D;

    /**
     * Bajo esta velocidad no hay zumbido.
     *
     * <p>Una flecha clavada en el piso tiene velocidad cero y un "punto de máxima aproximación" que
     * es su propia posición -- así que sin este piso, pararse al lado de una flecha clavada haría
     * temblar la cámara para siempre.</p>
     */
    public static final double MIN_SPEED = 0.4D;

    private WhizzGeometry() {
    }

    /**
     * El punto de máxima aproximación de un rayo al origen.
     *
     * @param rx,ry,rz la posición <b>relativa al observador</b>
     * @param vx,vy,vz la velocidad
     * @return las tres coordenadas del punto, o la posición misma si el rayo no se acerca
     */
    public static double[] closestPoint(double rx, double ry, double rz,
                                        double vx, double vy, double vz) {
        double speed2 = vx * vx + vy * vy + vz * vz;
        if (speed2 < MIN_SPEED * MIN_SPEED) {
            return new double[]{rx, ry, rz};
        }
        double t = -(rx * vx + ry * vy + rz * vz) / speed2;
        if (t < 0.0D) {
            // Se aleja: lo más cerca que estuvo es donde está.
            return new double[]{rx, ry, rz};
        }
        return new double[]{rx + vx * t, ry + vy * t, rz + vz * t};
    }

    /** La distancia mínima al origen. */
    public static double distance(double rx, double ry, double rz,
                                  double vx, double vy, double vz) {
        double[] p = closestPoint(rx, ry, rz, vx, vy, vz);
        return Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
    }

    /** Si esta flecha zumba: pasa cerca y todavía no pasó. */
    public static boolean whizzes(double rx, double ry, double rz,
                                  double vx, double vy, double vz) {
        double speed2 = vx * vx + vy * vy + vz * vz;
        if (speed2 < MIN_SPEED * MIN_SPEED) {
            return false;
        }
        return distance(rx, ry, rz, vx, vy, vz) <= WHIZZ_RADIUS;
    }

    /**
     * De qué lado pasa: positivo a la derecha del observador, negativo a la izquierda.
     *
     * <p>Se proyecta el punto de máxima aproximación sobre el vector "derecha" del observador, que
     * el llamador arma con su propio yaw. <b>Es lo que convierte el aviso en información</b>: un
     * temblor sin lado dice "algo pasó cerca" y uno con lado dice "de la derecha", que es lo que el
     * jugador necesita para girar.</p>
     */
    public static double side(double px, double pz, double rightX, double rightZ) {
        return px * rightX + pz * rightZ;
    }
}
