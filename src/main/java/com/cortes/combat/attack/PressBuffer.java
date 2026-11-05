package com.cortes.combat.attack;

/**
 * A press the game refused, remembered for a tick or two, just in case.
 *
 * <h2>What this is for</h2>
 *
 * <p>A click that lands one tick before the cooldown ends is dropped, and to the player it is
 * indistinguishable from a click the game never heard. The report was exactly that: pressing
 * a hair early loses the swing, and pressing it again late is the same swing a beat behind.
 * So a refused press is <b>remembered for a very short window</b> and fires the instant the
 * gate clears -- and the window is short on purpose, so a press made long before the swing
 * becomes possible never rides in later like a swing nobody asked for at that moment.</p>
 *
 * <p>The window is two ticks wide: long enough for an early click and for one round trip of
 * prediction skew, and nothing else. Every part of it is pure so the number is tested rather
 * than trusted.</p>
 */
public final class PressBuffer {

    /**
     * How long a refused press stays remembered: two ticks.
     *
     * <p>Not a config, not a constant someone will tune later without playtesting it: this is
     * the width of "por si el jugador se arrepiente". Anything longer turns an accidental click
     * on a raised guard into a swing the player never saw coming.</p>
     */
    public static final int WINDOW_TICKS = 2;

    /**
     * Cada cuántos ticks un botón que <b>sigue apretado</b> vuelve a intentar el press.
     *
     * <p>Es otra pregunta que {@link #WINDOW_TICKS} y por eso es otra constante: aquél es "el
     * click fue un pelín temprano", éste es "el jugador sigue apretando y la puerta todavía no
     * abrió". Dos ticks es imperceptible para el jugador (100 ms) y evita un barrido del cono por
     * tick mientras sostiene el botón contra una pared o esperando aguante.</p>
     */
    public static final int RETRY_TICKS = 2;

    /**
     * Cuántos ticks seguidos sin un bloque bajo la mira antes de que el botón sostenido arranque
     * un golpe sin objetivo.
     *
     * <p><b>Es el colchón de la minería.</b> Quien sostiene el ataque para picar pasa uno o dos
     * ticks con la mira en el aire entre bloque y bloque; sin este margen, el reintento abriría
     * un swing en ese hueco, `continueAttack` se cancelaría mientras el swing está abierto y la
     * minería se cortaría sola. Cuatro ticks (200 ms) es más largo que el hueco típico y más
     * corto que darse la vuelta. (μ_μ)</p>
     */
    public static final int OFF_BLOCK_TICKS = 4;

    private PressBuffer() {
    }

    /** No press is parked: the sentinel the latch starts with. */
    public static final long EMPTY = -1L;

    /**
     * Whether a parked press has outlived its window.
     *
     * <p>One tick inside the window is the whole point of the feature; past it a press saved
     * at {@code 0} firing at {@code tick >= 3} would be a swing the player let go of a second
     * ago. Faithful to "muy cortito".</p>
     */
    public static boolean expired(long pressTick, long now) {
        return now - pressTick > WINDOW_TICKS;
    }

    /**
     * Whether a parked press should fire right now.
     *
     * <p>Every one of the three answers is the same gate a live press would have run, asked
     * again at the moment of the fire. The window may not rewrite a rule a real press obeys:
     * a press parked while the guard was up still respects the guard the tick it fires.</p>
     */
    public static boolean shouldFire(long pressTick, long now, boolean gateOpen) {
        return !expired(pressTick, now) && gateOpen;
    }
}
