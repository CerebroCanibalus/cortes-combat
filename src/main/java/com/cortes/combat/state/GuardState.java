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
 * <p><b>There is no stun counter here on purpose.</b> Minecraft already has one: a staggered
 * item is a vanilla item cooldown, synced by {@code ServerItemCooldowns} and already drawn by
 * the vanilla HUD. Keeping our own countdown would be a second machine tracking a fact the
 * game is already keeping correctly, and the two would disagree the first time someone tuned
 * the vanilla duration.</p>
 */
public record GuardState(boolean held, int max, int stamina, int regenTicks) {

    /** A valid state with nothing raised, so the attachment's first read is never null. */
    public static final GuardState EMPTY = new GuardState(false, 3, 3, 40);

    public static final Codec<GuardState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("held", false).forGetter(GuardState::held),
            Codec.INT.optionalFieldOf("max", 3).forGetter(GuardState::max),
            Codec.INT.fieldOf("stamina").forGetter(GuardState::stamina),
            Codec.INT.optionalFieldOf("regen_ticks", 40).forGetter(GuardState::regenTicks)
    ).apply(instance, GuardState::new));

    public boolean isExhausted() {
        return stamina <= 0;
    }

    /** Whether the guard is actually up: raised, with points left, and the item usable. */
    public boolean isActive() {
        return held && !isExhausted();
    }

    public GuardState raise() {
        return held ? this : new GuardState(true, max, stamina, regenTicks);
    }

    public GuardState lower() {
        return held ? new GuardState(false, max, stamina, regenTicks) : this;
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
        return new GuardState(held, clamped, Math.min(stamina, clamped), regenTicks);
    }

    /**
     * Spend one point for a blocked hit.
     *
     * <p>Spending the last one drops the guard. What follows is not decided here: the handler
     * puts the item on a vanilla cooldown, so the stagger belongs to the item rather than to a
     * second state machine that would have to be kept in step with this one.</p>
     *
     * <p>The regen countdown starts full rather than at zero. Starting it at zero handed a
     * point back on the very next tick, which made the first point free and quietly made every
     * later one arrive a tick early.</p>
     */
    public GuardState block(int regenInterval) {
        int left = stamina - 1;
        return new GuardState(held && left > 0, max, Math.max(0, left),
                Math.max(1, regenInterval));
    }

    /**
     * Regen, one point per interval, and never while the guard is raised.
     *
     * <p>Holding the guard up recovering nothing is the cost of holding it: not points, but
     * the ability to block a second thing in a row.</p>
     *
     * <p>While staggered, the stagger <em>is</em> the clock. The refill is spread across it so
     * the guard comes back full exactly as the item cooldown runs out, rather than on an
     * unrelated faster timer that had the player back at two points before the stagger was
     * even half over, which made the stagger cost nothing at all.</p>
     *
     * @param regenInterval ticks per point in normal play
     * @param staggered whether the guard item is on cooldown right now
     * @param staggerLength the item's full cooldown, used to spread the refill over it
     */
    public GuardState tick(int regenInterval, boolean staggered, int staggerLength) {
        if (stamina >= max || max <= 0) {
            return new GuardState(held, max, stamina, regenTicks);
        }
        // Holding the guard up costs the regen. Being staggered is not holding anything: the
        // player never let go of the button, the guard broke on its own, and charging them for
        // it on top of the stagger would be a second punishment for one mistake.
        if (held && !staggered) {
            return new GuardState(held, max, stamina, regenTicks);
        }
        int interval = staggered && staggerLength > 0
                ? Math.max(1, staggerLength / max)
                : Math.max(1, regenInterval);
        // When a stagger begins, snap the countdown to the stagger's pace instead of letting the
        // one already running finish first. Without this the first point arrives late and the
        // guard is still short at the moment the item becomes usable again, which is the one
        // moment the player actually cares about.
        int counter = (staggered ? Math.min(regenTicks, interval) : regenTicks) - 1;
        if (counter > 0) {
            return new GuardState(held, max, stamina, counter);
        }
        return new GuardState(held, max, stamina + 1, interval);
    }
}
