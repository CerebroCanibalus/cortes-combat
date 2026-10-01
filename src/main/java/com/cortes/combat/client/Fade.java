package com.cortes.combat.client;

/**
 * Frame-rate independent fading, as arithmetic.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The damage overlay faded with {@code alpha + (target - alpha) * dt * SPEED}. At sixty frames a
 * second that is a tenth of the way per frame and nobody would ever see it. Then the game hitches --
 * a chunk loads, the window is resized, the player alt-tabs back -- and {@code dt} arrives as a
 * third of a second or a whole one, the factor goes past one, and <b>the alpha overshoots its
 * target</b>. The next frame corrects it, the one after overshoots again, and the vignette visibly
 * strobes.</p>
 *
 * <p>The playtest reported the two symptoms as separate -- "it flickers" and "it feels laggy" -- and
 * they are one: the oscillation only happens <em>during</em> a hitch, so a stutter causes the
 * flicker and the flicker reads as more stutter. No amount of tuning a speed constant fixes it,
 * because the bug is the shape of the interpolation rather than its rate.</p>
 *
 * <h2>What replaces it</h2>
 *
 * <p>Exponential decay, {@code 1 - e^(-rate * dt)}. It is bounded above by one for every input, so it
 * approaches the target and never passes it, and it is the correct function of real elapsed time --
 * a fade that takes the same wall-clock duration at thirty frames as at two hundred.</p>
 *
 * <p>Pure, so the property that matters is a test rather than a claim: <b>the result is never
 * outside the interval between the two inputs, for any delta.</b> That single property is what the
 * broken version violated and what a speed constant can never restore.</p>
 */
public final class Fade {

    private Fade() {
    }

    /**
     * Moves {@code from} toward {@code to}, without ever passing it.
     *
     * @param rate how much of the remaining distance a whole second closes, larger is faster
     * @param dt   real seconds since the last frame. Zero leaves the value untouched.
     */
    public static float toward(float from, float to, float rate, double dt) {
        if (dt <= 0.0 || !Double.isFinite(dt)) {
            return from;
        }
        float closed = (float) (1.0 - Math.exp(-rate * dt));
        return from + (to - from) * closed;
    }

    /**
     * Decays a value toward zero.
     *
     * <p>Separate from {@link #toward} rather than a convenience for it with a zero target,
     * because this one can also be written as a multiplication and the multiplication is both
     * cheaper and the form that cannot produce a negative from a positive.</p>
     */
    public static float decay(float value, float rate, double dt) {
        if (value <= 0.0F) {
            return 0.0F;
        }
        if (dt <= 0.0 || !Double.isFinite(dt)) {
            return value;
        }
        return value * (float) Math.exp(-rate * dt);
    }

    /** Whether a faded value is worth drawing. The threshold lives here so both callers agree. */
    public static boolean visible(float alpha, float threshold) {
        return alpha > threshold;
    }
}