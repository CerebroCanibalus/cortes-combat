package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;

/**
 * The easing curves a viewmodel track can be shaped with.
 *
 * <p>{@code Mth} only offers linear, quadratic and cubic, and a swing with weight needs more than
 * that: an ease that overshoots and settles is what separates "the arm reached out" from "the
 * arm hit something". These are the curves the design asks for, written out rather than
 * approximated by chaining stock ones.</p>
 *
 * <p>Every curve is normalised: {@code f(0) == 0} and {@code f(1) == 1}, except {@link #BACK},
 * {@link #ELASTIC} and {@link #BOUNCE}, which overshoot past 1 on the way and come back to
 * exactly 1. That invariant is what lets a profile be a plain sum of tracks: a track contributes
 * nothing at rest and everything it promises at the end of its motion, so tracks compose without
 * cancelling each other by accident.</p>
 *
 * <p>Pure Java, on purpose, for the same reason {@code ConeMath} and {@code AttackTiming} are:
 * an enum that reaches for a Minecraft class cannot be unit tested in a bare JVM, and these are
 * numbers that are miserable to tune with the game running and trivial to tune in a test.</p>
 */
public enum Curve {

    /** No shaping. The raw channel value. */
    LINEAR {
        @Override
        public float apply(float t) {
            return t;
        }
    },

    /**
     * Slow start, quick middle, slow end.
     *
     * <p>The wind-up curve. "Raises slowly and settles at the top" is exactly this shape: it
     * makes the last few percent of the charge feel like arriving somewhere rather than like
     * running out of travel.</p>
     */
    SMOOTH {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            return x * x * (3.0F - 2.0F * x);
        }
    },

    /**
     * Explodes out of zero and decelerates hard.
     *
     * <p>The strike curve. A swing that eases in reads as a slow camera move instead of an
     * impact, which is the same reason the crosshair's hit growth jumps instead of easing.</p>
     */
    EXPO {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            if (x >= 1.0F) {
                return 1.0F;
            }
            float inverse = 1.0F - x;
            return 1.0F - inverse * inverse * inverse * inverse;
        }
    },

    /**
     * Overshoots past 1 and comes back to 1.
     *
     * <p>The follow-through curve. The overshoot is how far past the target it goes, and the pull
     * back is what makes the return read as the arm settling rather than as a second motion.</p>
     *
     * <p>The overshoot amount is a constant and not a per-constant field: a constant specific
     * enum body is not an instance context and cannot read one. Only this curve wants it, so a
     * named constant is the whole requirement.</p>
     */
    BACK {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            float c3 = BACK_OVERSHOOT + 1.0F;
            float shifted = x - 1.0F;
            return 1.0F + c3 * shifted * shifted * shifted + BACK_OVERSHOOT * shifted * shifted;
        }
    },

    /**
     * A decaying oscillation settling on 1.
     *
     * <p>For a release that should feel like it snapped and rang. Reads as noise if used for
     * anything slow, so it belongs on short motions only.</p>
     */
    ELASTIC {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            if (x <= 0.0F) {
                return 0.0F;
            }
            if (x >= 1.0F) {
                return 1.0F;
            }
            double period = (2.0 * Math.PI) / 3.0;
            return (float) (Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * period) + 1.0);
        }
    },

    /**
     * Bounces on the way down and comes to rest on 1.
     *
     * <p>For a weapon with real mass in it. The three bounces are the whole point; a
     * single damped oscillator approximation looks like a wobble.</p>
     *
     * <p>Written out rather than called from somewhere else because {@code Mth} has no
     * equivalent and the alternative, nesting three smoothsteps, gets the bounce heights subtly
     * wrong in a way that is invisible frame by frame and obvious in motion.</p>
     */
    BOUNCE {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            if (x >= 1.0F) {
                return 1.0F;
            }
            double n1 = 7.5625;
            double d1 = 2.75;
            if (x < 1.0 / d1) {
                return (float) (n1 * x * x);
            }
            if (x < 2.0 / d1) {
                double u = x - 1.5 / d1;
                return (float) (n1 * u * u + 0.75);
            }
            if (x < 2.5 / d1) {
                double u = x - 2.25 / d1;
                return (float) (n1 * u * u + 0.9375);
            }
            double u = x - 2.625 / d1;
            return (float) (n1 * u * u + 0.984375);
        }
    };

    private static final float BACK_OVERSHOOT = 1.70158F;

    private Curve() {
    }

    /**
     * Evaluate.
     *
     * @param t the channel value, normally 0..1
     * @return the shaped value, {@code f(0) == 0} and {@code f(1) == 1} for every curve here
     */
    public abstract float apply(float t);

    static float clamp01(float t) {
        if (t < 0.0F) {
            return 0.0F;
        }
        return t > 1.0F ? 1.0F : t;
    }

    /**
     * Serialised by name.
     *
     * <p>Hand written, and not by implementing {@code StringRepresentable}: that is a Minecraft
     * class, and an enum holding one cannot be class-loaded in a bare JVM, which takes the unit
     * tests of everything that touches a profile down with it. This has bitten the project
     * twice already with {@code WeaponProfile}.</p>
     *
     * <p>{@code Codec.STRING} rather than a string codec constant: the constants live in
     * different packages across DataFixerUpper versions and this one is in all of them.</p>
     */
    public static final Codec<Curve> CODEC = Codec.STRING.xmap(Curve::valueOf, Enum::name);
}
