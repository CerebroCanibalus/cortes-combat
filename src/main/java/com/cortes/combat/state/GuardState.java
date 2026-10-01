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

    /**
     * A valid state with nothing raised and <em>nothing charged</em>, so the attachment's first
     * read is never null.
     *
     * <p>Starting at zero is a rule, not a default nobody thought about: picking a shield up
     * must not be worth three free blocks. The guard then refills on the normal regen, so the
     * cost of a fresh item is a few seconds of not being ready, which is a cost the player can
     * see counting down in the orbs.</p>
     */
    public static final GuardState EMPTY = new GuardState(false, 0, 0, 40);

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
     * Regen, one point per interval, and never while the guard is raised or broken.
     *
     * <p>Holding the guard up costs the regen. Being staggered costs it too, and that is a
     * change of mind rather than a first draft: the refill used to be <em>spread across</em> the
     * stagger, so the guard came back full exactly as the item cooldown ran out. The playtest
     * overruled that -- "the points keep regenerating while the guard is broken" -- and it is
     * right about what it feels like. Points arriving during the stun is the one window where a
     * player who is helpless is also recovering, and the stagger is supposed to be the cost.</p>
     *
     * <p>So a broken guard is a plain pause in the same clock that holding it up is, and it
     * resumes on the tick the guard is usable again.</p>
     *
     * <p>What this removed is worth noting: three parameters, a special interval, and a counter
     * that snapped between two rates. All of it existed to describe a stagger in words other than
     * "do not regenerate", which is a sign the rule was written wrong the first time and then
     * made more precise instead of being corrected.</p>
     *
     * @param regenInterval ticks per point in normal play
     * @param regenPaused   whether the guard is raised <em>or</em> the item is staggered. Both
     *                      pause the refill, and they arrive as one flag because from here they
     *                      are one question: can this guard be used right now?
     *
     * <p>The parameter is named after what it does rather than after one of the two reasons,
     * because it was written once named after the wrong one. Passing "can I use this" where the
     * method means "stop regenerating" inverted both branches at once: the guard regenerated while
     * it was raised and refused to while it was broken, and the second of those is what the
     * playtest saw as three full orbs on a guard that was down.</p>
     */
    public GuardState tick(int regenInterval, boolean regenPaused) {
        if (max <= 0 || stamina >= max) {
            return new GuardState(held, max, stamina, regenTicks);
        }
        if (regenPaused) {
            return new GuardState(held, max, stamina, regenTicks);
        }
        int counter = regenTicks - 1;
        if (counter > 0) {
            return new GuardState(held, max, stamina, counter);
        }
        return new GuardState(held, max, stamina + 1, Math.max(1, regenInterval));
    }
}
