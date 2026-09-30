package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How much guard an item is worth.
 *
 * <p>Split out of the reach and charge components on purpose, for the same reason those two are
 * separate: a shield is an item with 3 points and a wide arc, a sword is an item with 1 point
 * and a narrower one, and neither of those has anything to do with how far the item reaches or
 * what charging it does. Changing the sword to have 2 points must not restate its reach.</p>
 *
 * <p>Having this as data rather than a constant is what lets the sword be the one-point item the
 * design asks for, and lets the next item with 2 be an item rather than a code change.</p>
 *
 * @param points how many blocked hits the item can absorb before it is exhausted
 * @param arcDegrees total width of the frontal block; 120 means 60 degrees either side
 * @param regenTicks ticks between one point coming back
 * @param stunTicks length of the stagger that follows the last point, and that follows an
 *                  axe hit. It is a vanilla item cooldown, not a counter of ours, so it
 *                  arrives already synced and already drawn by the vanilla HUD.
 */
public record GuardProfile(int points, float arcDegrees, int regenTicks, int stunTicks) {

    /** Cannot guard at all. The value an item without a guard profile falls back to. */
    public static final GuardProfile NONE = new GuardProfile(0, 0.0F, 0, 0);

    /** The vanilla shield: the design's three hits. */
    public static final GuardProfile SHIELD = new GuardProfile(3, 120.0F, 40, 100);

    /**
     * A sword used to cover: one hit.
     *
     * <p>Not because the sword is weak, but because holding a sword up should be a last resort
     * rather than a free alternative to carrying a shield. One point, and it is gone.</p>
     *
     * <p>The 100 ticks of stagger are vanilla's own shield number on purpose. The player
     * already knows what five seconds of holding a useless shield feels like, and a sword that
     * punished them for a different length would be a second thing to learn for no gain.</p>
     */
    public static final GuardProfile SWORD = new GuardProfile(1, 90.0F, 40, 100);

    public static final Codec<GuardProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("points").forGetter(GuardProfile::points),
            Codec.FLOAT.fieldOf("arc_degrees").forGetter(GuardProfile::arcDegrees),
            Codec.INT.fieldOf("regen_ticks").forGetter(GuardProfile::regenTicks),
            Codec.INT.fieldOf("stun_ticks").forGetter(GuardProfile::stunTicks)
    ).apply(instance, GuardProfile::new));

    public boolean canGuard() {
        return points > 0;
    }

    /** Clamped here rather than trusted, the same way the charge floor is forced in code. */
    public int safePoints() {
        return Math.max(0, points);
    }

    public float safeArc() {
        return Math.max(0.0F, Math.min(360.0F, arcDegrees));
    }
}
