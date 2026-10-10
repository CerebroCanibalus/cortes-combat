package com.cortes.combat.state;

/**
 * Contragolpe: la ventana ofensiva que abre un bloqueo de verdad.
 *
 * <h2>Qué premia, y por qué el premio le faltaba a la postura</h2>
 *
 * <p>Cubrirse ya cuesta: la guardia es una postura y mientras está arriba no se puede golpear. Lo
 * que no existía era la otra mitad -- <b>que cubrirse <em>abra</em> algo</b>. El diseño ya dice
 * "cubrirse no cuesta aguante hasta que te pegan"; esto hace que ese golpe que pagaste te devuelva
 * una ventana: <b>tu siguiente ataque entra por donde la armadura ya no cubre.</b> La postura sigue
 * intacta (con la guardia arriba seguís sin poder pegar) y el premio es por haber cubierto, no por
 * haber pegado.</p>
 *
 * <h2>Por qué es un vencimiento y no un contador</h2>
 *
 * <p><b>Un contador habría que bajarlo cada tick, en el ticker del jugador, para todos los
 * jugadores, todo el tiempo.</b> Un vencimiento absoluto contra {@code getGameTime()} no se toca
 * nunca: se pregunta. Es la misma forma que {@code FallCoyote}, y por la misma razón -- una ventana
 * de dos segundos no justifica una entrada más en el tick por jugador.</p>
 *
 * <p><b>Y "sin ventana" es el tick cero.</b> El reloj del mundo arranca en cero y sólo sube, así que
 * {@code now < 0} no es cierto nunca y un {@code 0} es una respuesta que se lee sola: sin bloqueo no
 * hay ventana. Se probó con un campo {@code armed} al lado y <b>el sabotaje lo destapó
 * redundante</b> -- sacarlo no rompía ninguna aserción, porque el centinela ya era seguro. Un campo
 * que no cambia ninguna respuesta no es claridad, es una segunda cosa que puede estar en
 * desacuerdo.</p>
 *
 * <p>Sin codec, a propósito: la ventana son dos segundos y muere sola. Guardarla en el NBT sería
 * escribir un estado que ninguna sesión sobrevive, y una ventana que reaparece tras reconectar es un
 * premio que el jugador no ganó.</p>
 *
 * @param expiresAtTick el tick a partir del cual la ventana está cerrada; cero significa que nunca
 *                      se abrió
 */
public record ContragolpeWindow(long expiresAtTick) {

    /** Dos segundos, por el diseño: "tu siguiente ataque en ≤ 40 ticks". */
    public static final int WINDOW_TICKS = 40;

    /** Sin ventana. Ver la nota de la clase: el cero se lee solo. */
    public static final ContragolpeWindow NONE = new ContragolpeWindow(0L);

    /** Si un golpe dado en {@code now} cobra la ventana. */
    public boolean isOpen(long now) {
        return now < expiresAtTick;
    }

    /** El bloqueo que la abrió. */
    public static ContragolpeWindow armedAt(long now) {
        return new ContragolpeWindow(now + WINDOW_TICKS);
    }

    /**
     * El golpe que la usó.
     *
     * <p><b>"Tu siguiente ataque" es uno, y gastarla es lo que lo hace uno.</b> Si la ventana
     * sobreviviera al golpe, un bloqueo daría cuarenta ticks de penetración de armadura en vez de un
     * golpe -- y un premio que dura cuarenta ticks deja de ser un premio y pasa a ser un estado, que
     * es la misma frase con la que este proyecto ya rechazó un parry que se pudiera sostener.</p>
     */
    public ContragolpeWindow spent() {
        return NONE;
    }
}
