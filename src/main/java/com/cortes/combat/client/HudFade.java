package com.cortes.combat.client;

/**
 * La cuenta del fade del HUD, como una funcion pura.
 *
 * <h2>Por que una clase entera para una interpolacion</h2>
 *
 * <p>Porque el fade es <b>la parte que se puede equivocar</b>, y la parte que se equivoca en
 * silencio: un alpha que se pasa del blanco o que salta de golpe no rompe nada, se ve "raro".
 * Con la cuenta aparte, el borde es un test en una JVM pelada y no una opinion de playtest.</p>
 *
 * <p><b>Y por que no es el {@link Fade} de la pantalla de dano.</b> Ese es una caza continua
 * ({@code 1 - e^(-rate * dt)}): persigue un objetivo y nunca lo pasa. Este es otra cosa: un
 * <em>tramo</em> con principio y fin -- el HUD estaba en 1, ahora va a 0, y tarda lo que
 * tarda. Un tramo se describe con su duracion, y una duracion es un numero que el jugador
 * puede entender; una "velocidad de caza" es un numero que hay que traducir a segundos cada
 * vez que se toca.</p>
 *
 * <h2>La duracion va en el estado, no aca</h2>
 *
 * <p>Ir y volver duran distinto: irse es un descanso, volver es una urgencia. Por eso los dos
 * numeros viven en la clase que decide el sentido del tramo y esta solo hace la cuenta.</p>
 */
public final class HudFade {

    /**
     * Cuanto tarda el HUD en irse, en milisegundos.
     *
     * <p><b>Tres decimas de segundo.</b> Menos que eso y el apagado se lee como un parpadeo;
     * mucho mas y el HUD sigue ahi flotando cuando el jugador ya no lo mira, que es
     * exactamente lo que el pedido queria evitar.</p>
     */
    public static final int OUT_MILLIS = 320;

    /**
     * Cuanto tarda en volver, en milisegundos.
     *
     * <p><b>La mitad que irse, y a proposito.</b> Volver es el momento en que el jugador vuelve
     * a pelear: la informacion tiene que estar ahi cuando la mira, no un rato despues. Irse
     * puede ser lento; volver no.</p>
     */
    public static final int IN_MILLIS = 150;

    private HudFade() {
    }

    /**
     * El alpha del HUD a cierta distancia del comienzo del tramo.
     *
     * <p><b>Lineal y acotado.</b> Lineal porque un alpha que sale disparado al principio y
     * vuelve a la cola se ve como dos cambios de opinion; y acotado porque el unico valor
     * prohibido es el que no esta entre los dos extremos: un alpha mayor que uno o menor que
     * cero es un widget dibujando al reves.</p>
     *
     * @param from          el alpha al comienzo del tramo
     * @param to            el alpha al final del tramo
     * @param elapsedMillis quantos milisegundos pasaron desde el comienzo del tramo
     * @param durationMillis cuanto dura el tramo
     * @return el alpha, siempre entre {@code from} y {@code to}
     */
    public static float alpha(float from, float to, long elapsedMillis, int durationMillis) {
        if (durationMillis <= 0) {
            // Un tramo de duracion cero es un salto, y el salto es al final.
            return to;
        }
        if (elapsedMillis <= 0) {
            return from;
        }
        if (elapsedMillis >= durationMillis) {
            return to;
        }
        float t = (float) elapsedMillis / (float) durationMillis;
        return from + (to - from) * t;
    }
}
