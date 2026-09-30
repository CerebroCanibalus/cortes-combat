package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Guard state, shared by the shield and the sword.
 *
 * <p>The one rule that shapes this whole class: points are only ever spent by a hit that is
 * actually blocked, never for holding the guard up. That is what lets the sword cover
 * indefinitely, and it falls out of the rule instead of being a special case.</p>
 *
 * <p>The maximum lives in the state rather than in a constant, because it comes from the item
 * and items disagree: a shield is worth 3, a sword is worth 1. A fixed {@code MAX_STAMINA} here
 * quietly turns every weapon in the game into a shield, and it did exactly that until the
 * playtest caught it.</p>
 *
 * <p>The timings are deliberately <em>not</em> stored. They come from the item's guard profile
 * and a player can swap items mid-fight, so anything cached here would go stale the moment the
 * hotbar changes. They are passed in at the moment they are needed instead.</p>
 */
public record GuardState(boolean held, int max, int stamina, int stunTicks, int regenTicks) {

    /** A valid state with nothing raised, so the attachment's first read is never null. */
    public static final GuardState EMPTY = new GuardState(false, 3, 3, 0, 40);

    public static final Codec<GuardState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("held", false).forGetter(GuardState::held),
            Codec.INT.optionalFieldOf("max", 3).forGetter(GuardState::max),
            Codec.INT.fieldOf("stamina").forGetter(GuardState::stamina),
            Codec.INT.fieldOf("stun_ticks").forGetter(GuardState::stunTicks),
            Codec.INT.optionalFieldOf("regen_ticks", 40).forGetter(GuardState::regenTicks)
    ).apply(instance, GuardState::new));

    public boolean isStunned() {
        return stunTicks > 0;
    }

    public boolean isExhausted() {
        return stamina <= 0;
    }

    /** Whether the guard is actually up: raised, not stunned, and with something to spend. */
    public boolean isActive() {
        return held && !isStunned() && !isExhausted();
    }

    public GuardState raise() {
        return held ? this : new GuardState(true, max, stamina, stunTicks, regenTicks);
    }

    public GuardState lower() {
        return held ? new GuardState(false, max, stamina, stunTicks, regenTicks) : this;
    }

    /**
     * Adopt a new item's maximum without giving or taking points for free.
     *
     * <p>Clamped, so swapping from a sword to a shield cannot bank the difference, and swapping
     * the other way does not empty the bar. The player changes items mid-fight; the bar they
     * are looking at has to mean the same thing afterwards.</p>
     */
    public GuardState withMax(int newMax) {
        int clamped = Math.max(0, newMax);
        if (clamped == max) {
            return this;
        }
        return new GuardState(held, clamped, Math.min(stamina, clamped), stunTicks, regenTicks);
    }

    /**
     * Spend one point for a blocked hit.
     *
     * <p>Spending the last one drops the guard and stuns. That is the real answer to "why does a
     * sword only have one point": the number is not the cost, the two seconds of helplessness
     * afterwards and having to re-raise the guard by hand are the cost.</p>
     */
    public GuardState block(int stunTicks) {
        int left = stamina - 1;
        boolean spent = left <= 0;
        return new GuardState(
                held && !spent,
                max,
                Math.max(0, left),
                // Only the last point stuns. Writing the parameter in unconditionally made
                // every single block stun the player, which quietly turned the shield into
                // one-and-done regardless of its three points.
                spent ? Math.max(0, stunTicks) : this.stunTicks,
                0);
    }

    /**
     * Stun down, then regen.
     *
     * <p>Regen only runs while the guard is <em>not</em> raised. A player holding guard up
     * recovers nothing, which is the cost of holding it: not points, but the ability to block a
     * second thing in a row.</p>
     */
    public GuardState tick(int regenInterval) {
        if (stunTicks > 0) {
            return new GuardState(held, max, stamina, stunTicks - 1, regenTicks);
        }
        if (held || stamina >= max || max <= 0) {
            return new GuardState(held, max, stamina, 0, regenTicks);
        }
        int counter = regenTicks - 1;
        if (counter > 0) {
            return new GuardState(held, max, stamina, 0, counter);
        }
        return new GuardState(held, max, stamina + 1, 0, Math.max(1, regenInterval));
    }
}
