package com.cortes.combat.client;

/**
 * <b>El latido del HUD mientras el jugador sostiene el ataque esperando una puerta.</b>
 *
 * <p>El pedido fue doble: que el boton sostenido siga intentando el golpe, y que eso <b>se
 * note</b>. Lo primero es {@code SwingInputHandler}; lo segundo es este multiplicador, que hace
 * respirar los corchetes de cooldown para que la espera se vea como una espera y no como un
 * boton que el juego ignoro.</p>
 *
 * <p><b>Puro y sin reloj propio</b>: recibe los milisegundos y devuelve un factor. Asi la forma
 * del latido se testea en JVM pelada, que es la misma razon por la que {@code ConeGauge} y
 * {@code HudVisibility} viven separados de las capas que dibujan.</p>
 *
 * <p>El piso es 1.0 a proposito: <b>nunca apaga los corchetes</b>. Un efecto que baja de su brillo
 * normal se lee como que el widget se esta yendo, y lo que hay que decir es lo contrario --
 * "sigo aca, esperando". (⌒‿⌒)</p>
 */
public final class HudPulse {

    /** Un ciclo completo del latido: algo menos de un segundo, que se lee sin marcar ritmo. */
    public static final long PERIOD_MILLIS = 900L;

    /** Piso del multiplicador: exactamente el brillo normal, nunca menos. */
    public static final float MIN_BOOST = 1.0F;

    /** Techo: casi el doble del brillo normal, y solo mientras el boton esta apretado. */
    public static final float MAX_BOOST = 1.9F;

    private HudPulse() {
    }

    /**
     * El factor de brillo para un instante dado.
     *
     * <p>Una coseno elevada y no un cuadrado: la subida y la bajada tienen que enlentecerse en
     * los extremos o el latido se lee como un parpadeo. (￣ε￣＠)</p>
     */
    public static float waiting(long millis) {
        double phase = (double) Math.floorMod(millis, PERIOD_MILLIS) / (double) PERIOD_MILLIS;
        double wave = 0.5D - 0.5D * Math.cos(phase * Math.PI * 2.0D);
        return MIN_BOOST + (MAX_BOOST - MIN_BOOST) * (float) wave;
    }
}
