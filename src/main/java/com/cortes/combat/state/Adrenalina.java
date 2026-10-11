package com.cortes.combat.state;

/**
 * Adrenalina: el reloj de la daga, y la cuenta de lo que ahorro.
 *
 * <h2>Por que son dos numeros en un record y no dos attachments</h2>
 *
 * <p>Porque son <b>el mismo hecho contado dos veces</b>. El daño que se ahorro solo tiene sentido
 * dentro de la ventana que el reloj abre, y un segundo attachment habria que limpiarlo junto con el
 * primero -- dos campos que se vacian en el mismo lugar son un campo con dos nombres, y el dia que
 * uno se olvide el encantamiento cobra un daño ahorrado de una pelea que ya termino.</p>
 *
 * <h2>Por que {@code READY} es cero y no un negativo, al reves que GuardFree</h2>
 *
 * <p>Porque la pregunta es distinta. {@code GuardFree} pregunta {@code now - last >= 60} y por eso su
 * valor inicial tiene que ser {@code -60} para que la primera vez llegue en el tick cero. Aca la
 * pregunta es {@code now >= readyAt}, asi que <b>cero ya es "listo"</b> -- y un {@code -600} seria un
 * numero que funciona por una razon que nadie lee.</p>
 *
 * <p>Sin codec: un reloj de medio minuto. Guardarlo en el NBT seria escribir un estado que ninguna
 * sesion sobrevive, y una Adrenalina que reaparece lista tras reconectar es un golpe gratis que el
 * jugador no se gano.</p>
 *
 * @param readyAt el tick del mundo en el que la Adrenalina vuelve a estar disponible
 * @param banked  el daño que el ultimo uso ahorro, que el proximo golpe cobra (solo en L2)
 */
public record Adrenalina(long readyAt, float banked) {

    /** Listo desde el primer tick del mundo. Ver la nota de la clase. */
    public static final Adrenalina READY = new Adrenalina(0L, 0.0F);

    /** Si la Adrenalina se puede encender en {@code now}. */
    public boolean isReady(long now) {
        return now >= readyAt;
    }

    /**
     * La enciende, con su cooldown.
     *
     * <p><b>Y vacia la cuenta al mismo tiempo.</b> El daño ahorrado se cobra en el proximo golpe o no
     * se cobra: dejarlo guardado durante el cooldown seria un daño que crece entre peleas y que el
     * jugador no puede leer en ninguna parte.</p>
     */
    public Adrenalina spent(long now, int cooldownTicks) {
        return new Adrenalina(now + cooldownTicks, 0.0F);
    }

    /** Suma lo que este golpe ahorro. Solo el L2 llama a esto. */
    public Adrenalina banking(float saved) {
        return new Adrenalina(readyAt, banked + Math.max(0.0F, saved));
    }

    /** El golpe lo cobro. */
    public Adrenalina cashed() {
        return new Adrenalina(readyAt, 0.0F);
    }
}
