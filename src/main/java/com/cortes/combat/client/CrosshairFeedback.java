package com.cortes.combat.client;

import com.cortes.combat.core.CombatFeedback;

/**
 * The crosshair's reaction to the last thing that happened, and nothing else.
 *
 * <p>Pure arithmetic on purpose. Growth, shake and tint are the kind of numbers that are
 * miserable to tune while the game is running and trivial to tune in a test, so they live here
 * with no Minecraft type anywhere and get unit tested.</p>
 *
 * <p>Static because two GUI layers read it in the same frame and neither should own it. That is
 * the same reasoning as {@link ClientCombatState}, and the same carve-out from the ban on
 * static mutable state: this is display state with a lifetime of one client session, and it
 * never feeds back into damage, cooldown or anything the server resolves. The architecture rule
 * is about gameplay; a HUD that has to be told its own feedback is a worse HUD.</p>
 *
 * <p><b>The shake is seeded, not random.</b> Random per frame means the same hit looks different
 * every time you watch it, which reads as graphical noise rather than as an impact. So the
 * offsets come from a fixed cycle rotated by a per-event counter: the same swing always shakes
 * the same way, and two swings in a row are not identical.</p>
 */
public final class CrosshairFeedback {

    /**
     * Extra scale while a target is reachable. Deliberately small: this is a standing hint the
     * player looks at constantly, and a hint that is loud all the time says nothing on the one
     * frame that matters.
     */
    static final float AIM_GROWTH = 0.14F;

    /** Extra scale on the frame a hit lands, decaying to nothing over the flash. */
    static final float HIT_GROWTH = 0.38F;

    /**
     * How fast the aim growth catches up, per tick. Smoothing rather than snapping because a
     * target sitting on the edge of the cone flickers in and out, and a crosshair that
     * strobes is worse than one that is a little late.
     */
    static final float AIM_APPROACH = 0.35F;

    /**
     * The shake path, in GUI pixels, walked once per tick. An eight step ring rather than noise:
     * it starts and ends at the origin, so the crosshair always comes to rest exactly centred
     * instead of drifting a pixel and staying there.
     */
    private static final int[][] SHAKE_CYCLE = {
            {0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}
    };

    /** Base tint: untinted crosshair, tinted by fading towards this. */
    private static final int UNTINTED = 0xFFFFFF;

    private static CombatFeedback kind;
    /** Ticks since the event landed, or -1 when nothing is happening. */
    private static int age = -1;
    /** Bumped per event. The only randomness in this class, and it is not random. */
    private static int seed;
    /** Eased 0..1 approach to "there is something to hit". */
    private static float aim;

    private CrosshairFeedback() {
    }

    /** Called by the payload handler. {@code GUARDED} tints but never grows or shakes. */
    public static void onEvent(CombatFeedback event) {
        kind = event;
        age = 0;
        seed++;
    }

    /**
     * One client tick.
     *
     * @param aiming whether the held weapon currently has something it can hit
     */
    public static void onTick(boolean aiming) {
        if (age >= 0) {
            age++;
            if (age >= kind.tintTicks()) {
                age = -1;
                kind = null;
            }
        }
        float target = aiming ? 1.0F : 0.0F;
        aim += (target - aim) * AIM_APPROACH;
        if (aim < 0.001F) {
            aim = 0.0F;
        }
    }

    /**
     * Scale factor for the 15x15 crosshair sprite.
     *
     * <p>The hit growth jumps instantly and decays: a hit that eases in reads as a slow camera
     * move rather than as an impact. The aim growth is the opposite, and that asymmetry is the
     * whole difference between the two states reading correctly.</p>
     */
    public static float growth() {
        float scale = 1.0F + AIM_GROWTH * aim;
        if (age >= 0 && kind != CombatFeedback.GUARDED) {
            scale += HIT_GROWTH * envelope();
        }
        return scale;
    }

    /**
     * 0 at the end of the flash, 1 on the frame it starts.
     *
     * <p>Square rooted because a linear fade spends most of its life looking washed out. This
     * holds full strength for the first third and then falls off, which is what makes a short
     * flash read as a flash instead of as a tint that never quite arrived.</p>
     */
    static float envelope() {
        if (age < 0 || kind == null) {
            return 0.0F;
        }
        float remaining = 1.0F - (float) age / kind.tintTicks();
        return (float) Math.sqrt(Math.max(0.0F, remaining));
    }

    /** Horizontal shake offset, in GUI pixels. Always 0 outside a landed hit. */
    public static int shakeX() {
        return shake(0);
    }

    /** Vertical shake offset, in GUI pixels. */
    public static int shakeY() {
        return shake(1);
    }

    private static int shake(int axis) {
        if (age < 0 || kind == null || kind == CombatFeedback.GUARDED) {
            return 0;
        }
        int step = (age + seed) % SHAKE_CYCLE.length;
        // Two pixels while the flash is young, one while it dies away. Anything more and the
        // crosshair stops being readable at exactly the moment the player is trying to aim.
        int amplitude = age < kind.tintTicks() / 2 ? 2 : 1;
        return SHAKE_CYCLE[step][axis] * amplitude;
    }

    /**
     * The colour to draw the crosshair and the cooldown brackets in right now.
     *
     * <p>Interpolated towards white rather than faded out with alpha. These are pixel art
     * sprites with hard edges: lowering alpha makes them look like they are dissolving, while
     * sliding the colour back to white makes them look like the flash is being absorbed. The
     * second one is what a crosshair should do.</p>
     */
    public static int rgb() {
        if (age < 0 || kind == null) {
            return UNTINTED;
        }
        return lerp(UNTINTED, kind.rgb(), envelope());
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Whether a hit flash is running right now, as opposed to a block flash.
     *
     * <p>This is what selects the hit sprite, and it is asked as a question rather than inferred
     * from the size. Inferring it meant comparing the growth against a threshold built out of
     * {@link #AIM_GROWTH}, which is a number that has to be edited in two places and breaks
     * silently if either moves: the crosshair would start showing the hit art on a plain aim,
     * or showing the aim art on a hit, and neither looks like a compile error.</p>
     */
    public static boolean hitFlashing() {
        return age >= 0 && kind != null && kind != CombatFeedback.GUARDED;
    }

    /** Whether a flash is currently running at all, blocks included. */
    public static boolean active() {
        return age >= 0 && kind != null;
    }

    /** Test hook: back to a clean HUD. */
    public static void reset() {
        kind = null;
        age = -1;
        seed = 0;
        aim = 0.0F;
    }

    /** Test hook: the current aim approach, which the tick above smooths. */
    static float aim() {
        return aim;
    }
}
