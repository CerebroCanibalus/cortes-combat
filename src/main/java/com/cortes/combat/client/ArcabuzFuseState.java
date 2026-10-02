package com.cortes.combat.client;

import com.cortes.combat.state.ArcabuzState;

/**
 * What the client knows about the arcabuz, and the only place it is kept.
 *
 * <p><b>A separate file on purpose.</b> The fuse is neither the charge nor the cooldown, so
 * putting it in {@code ClientCombatState} would mean a record that every weapon's draw reads
 * three numbers it does not use, plus a second copy of the fuse's length in the state sync's
 * place. This is three numbers and one rule.</p>
 *
 * <p><b>The client counts down from what the server sent and the server still owns the shot.</b>
 * The server sends two packets per shot -- lit, then gone -- and the countdown in between runs
 * on the client's own clock. That is the same trade {@code ClientCombatState} makes for the
 * parry's five second lockout, and it is what keeps a shot at two packets instead of twenty.</p>
 *
 * <p>The drift is handled the way the parry's is: a correction from the server that arrives
 * higher than the local count wins, because a number that jumped up is a new fuse rather than a
 * late packet. Without that, a one tick jitter at the end looks exactly like a press and the
 * client restarts a fuse that has already gone off -- which is the artefact of
 * {@code observeStagger}, mirrored.</p>
 */
public final class ArcabuzFuseState {

    private static volatile int fuseTicks = 0;
    private static volatile boolean loaded = false;

    private ArcabuzFuseState() {
    }

    /**
     * What the server said.
     *
     * <p>A lit packet always resets the count to the full length, because that is what lighting
     * one is. An unlit packet only clears it and never writes a number: the server has already
     * shot by the time it sends, so the client's own count is the one to believe.</p>
     *
     * <p>{@code hasFirestone} comes straight from the server rather than being derived here,
     * because "is there a firestone in it" is the server's question and it is the only thing
     * standing between the player and a free shot.</p>
     */
    public static void accept(boolean lit, int ticks, boolean hasFirestone) {
        if (lit) {
            fuseTicks = Math.max(ticks, 0);
            loaded = false;
            return;
        }
        fuseTicks = 0;
        loaded = hasFirestone;
    }

    /**
     * One client tick: the countdown runs down here rather than on the server's.
     *
     * <p>The server's own countdown lives in {@code ArcabuzState#step} and is the one that
     * decides whether the shot goes off; this one only decides what the player sees, which is
     * why a client that is behind reads a fuse that has already finished.</p>
     */
    public static void tick() {
        if (fuseTicks > 0) {
            fuseTicks--;
        }
    }

    /** Whether a fuse is burning on the client's clock right now. */
    public static boolean isBurning() {
        return fuseTicks > 0;
    }

    /**
     * Ticks of fuse left, or zero.
     *
     * <p>This is the number the four arcabuz viewmodel states read: idle while zero, the lit
     * animations while it counts down. It is exposed rather than recomputed from a texture's
     * frame index because the server's clock is the one that fires the shot.</p>
     */
    public static int fuseTicks() {
        return fuseTicks;
    }

    /**
     * How far through the fuse the client is, 0 to 1, with 0 meaning "not burning".
     *
     * <p>Elapsed rather than remaining, because every other ramp in this project's viewmodel is
     * written as progress: a curve's {@code f(0)} is the rest pose and {@code f(1)} is the end.
     * Returning the countdown here would make the arcabuz the one channel in the mod that runs
     * backwards.</p>
     */
    public static float progress() {
        if (fuseTicks <= 0 || ArcabuzState.FUSE_TICKS <= 0) {
            return 0.0F;
        }
        float elapsed = 1.0F - ((float) fuseTicks / (float) ArcabuzState.FUSE_TICKS);
        return Math.max(0.0F, Math.min(1.0F, elapsed));
    }

    /** Whether the client believes a firestone is in the chamber. */
    public static boolean isLoaded() {
        return loaded;
    }

    /** Everything back to nothing, for a world change or a fresh session. */
    public static void clear() {
        fuseTicks = 0;
        loaded = false;
    }
}
