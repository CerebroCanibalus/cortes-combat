package com.cortes.combat.viewmodel;

/**
 * Deterministic tremble, seeded per event.
 *
 * <p>Two rules, both learned the hard way in this project:</p>
 * <ul>
 *   <li><b>Not random per frame.</b> A tremble drawn from {@code Random} every frame means the
 *       same charge looks different every time you watch it, which reads as graphical noise
 *       rather than as a held weapon shaking under effort. The crosshair's shake already works
 *       this way and the same argument applies here.</li>
 *   <li><b>Seeded per swing, not per session.</b> A single fixed cycle means two charges in a
 *       row shake identically, which reads as a loop. The seed rotates through a fixed set of
 *       integer offsets, so there are finitely many distinct tremors, every one of them
 *       repeatable, and consecutive swings differ.</li>
 * </ul>
 *
 * <p>Cost is one integer hash per axis per frame, and the hash is a fixed sequence of shifts and
 * multiplies with no allocation and no lookup. That matters because this runs inside the item
 * renderer, once per track, on every frame, for the item in the player's hand.</p>
 */
public final class ViewModelNoise {

    private ViewModelNoise() {
    }

    /**
     * One axis of the tremble, in -1..1.
     *
     * <p>Smoothly interpolated between whole buckets rather than stepped. Stepped is cheaper
     * still, and it looks like a glitch: a held weapon that shivers is trembling, and one that
     * jumps between two positions every sixtieth of a second is twitching. The extra hash and
     * the lerp are a rounding error next to the cost of being wrong.</p>
     *
     * @param seed  rotates the pattern; bumped once per swing
     * @param time  seconds, so the frequency does not depend on the frame rate
     * @param hz    how many times a second the tremble crosses
     * @param axis  0, 1 or 2, so the three axes are not the same signal
     */
    public static float axis(int seed, float time, float hz, int axis) {
        if (hz <= 0.0F) {
            return 0.0F;
        }
        float scaled = time * hz;
        int bucket = (int) Math.floor(scaled);
        float blend = scaled - bucket;
        float a = sample(seed, bucket, axis);
        float b = sample(seed, bucket + 1, axis);
        return a + (b - a) * blend;
    }

    /**
     * One hash, one axis, one bucket, in -1..1.
     *
     * <p>Derived from a small integer mix rather than {@code Objects.hash} or a sine of the
     * bucket index. A sine gives an obviously periodic tremble that the eye locks onto, and an
     * object hash allocates or boxes. This is a few integer operations and nothing else.</p>
     */
    private static float sample(int seed, int bucket, int axis) {
        int h = seed * 0x9E3779B1 + bucket * 0x85EBCA6B + axis * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        // The low 16 bits mapped to -1..1. Not the top bits: the mix pushes entropy upwards
        // and the top bits are better, but the low bits here are already well mixed and using
        // the same 16 for all three axes would be a mistake, which the axis term above prevents.
        return ((h & 0xFFFF) * (2.0F / 65535.0F)) - 1.0F;
    }
}
