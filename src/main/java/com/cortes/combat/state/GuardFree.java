package com.cortes.combat.state;

/**
 * Guardia Firme: el reloj del bloqueo que no cuesta aguante.
 *
 * <h2>Qué premia</h2>
 *
 * <p>La guardia ya es gratis <em>mientras la sostenés</em> -- la aguante sólo se gasta al
 * <b>recibir</b> un golpe. Lo que este encantamiento compra es la primera de esas recepciones: cada
 * tres segundos, el golpe que entra no cuesta nada. Contra un enemigo que pega seguido, eso es una
 * guardia que aguanta un golpe más; contra uno que pega lento, es gratis.</p>
 *
 * <h2>Por qué un campo y no un booleano</h2>
 *
 * <p>Porque la pregunta es <b>"¿pasaron tres segundos desde el último gratis?"</b>, y eso necesita
 * el tick, no un sí/no. Un booleano habría que limpiarlo desde otro lado -- el ticker del jugador,
 * para todos -- y un booleano que se limpia en el tick equivocado es una guardia que cobra o no
 * cobra según el orden de los eventos.</p>
 *
 * <p><b>Y el valor inicial es {@code -INTERVAL_TICKS} y no cero.</b> Con cero, "listo" sería
 * {@code now - 0 >= 60}, así que el primer bloqueo gratis de una partida no llegaría hasta el tick
 * sesenta del mundo -- un encantamiento que no hace nada durante su primer minuto. Con el negativo
 * la resta da exactamente el intervalo en el tick cero y la primera vez es la primera.</p>
 *
 * <p>Sin codec: un reloj de tres segundos. Guardarlo en el NBT sería escribir un estado que ninguna
 * sesión sobrevive.</p>
 *
 * @param lastFreeTick el tick del último bloqueo que no costó aguante
 */
public record GuardFree(long lastFreeTick) {

    /** Tres segundos, por el diseño. */
    public static final int INTERVAL_TICKS = 60;

    /** Listo desde el primer tick del mundo. Ver la nota de la clase. */
    public static final GuardFree READY = new GuardFree(-INTERVAL_TICKS);

    /** Si un bloqueo dado en {@code now} no gasta aguante. */
    public boolean isReady(long now) {
        return now - lastFreeTick >= INTERVAL_TICKS;
    }

    /** El bloqueo que lo gastó. */
    public GuardFree usedAt(long now) {
        return new GuardFree(now);
    }
}
