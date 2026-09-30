package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Guard state, shared by the shield and the sword.
 *
 * <p>The one rule that shapes this whole class: stamina is only ever spent by a hit that is
 * actually blocked, never for holding the guard up. That is what lets the sword cover
 * indefinitely, and it falls out of the rule instead of being a special case.</p>
 *
 * <p>The regen counter is here rather than in a tick handler because "how long since the last
 * block" is part of the state. A handler with its own timer is a second place where the same
 * fact lives, and the two drift apart the moment someone changes the rules.</p>
 */
public record GuardState(boolean held, int stamina, int stunTicks, int regenTicks) {

    public static final int MAX_STAMINA = 3;

    /** Ticks between one stamina coming back. Two seconds, which is slow enough to matter. */
    public static final int REGEN_INTERVAL = 40;

    /** Ticks of stun when the last point of stamina is spent. */
    public static final int STUN_TICKS = 40;

    public static final GuardState FULL = new GuardState(false, MAX_STAMINA, 0, REGEN_INTERVAL);

    public static final Codec<GuardState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("held", false).forGetter(GuardState::held),
            Codec.INT.fieldOf("stamina").forGetter(GuardState::stamina),
            Codec.INT.fieldOf("stun_ticks").forGetter(GuardState::stunTicks),
            Codec.INT.optionalFieldOf("regen_ticks", REGEN_INTERVAL).forGetter(GuardState::regenTicks)
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
        return new GuardState(true, stamina, stunTicks, regenTicks);
    }

    public GuardState lower() {
        return new GuardState(false, stamina, stunTicks, regenTicks);
    }

    /**
     * Spend one point of stamina for a blocked hit.
     *
     * <p>Spending the last one drops the guard: the design says the axe stuns in one hit, and
     * running on empty is what makes the shield a decision rather than a habit.</p>
     */
    public GuardState block() {
        int left = stamina - 1;
        return new GuardState(
                held && left > 0,
                Math.max(0, left),
                left <= 0 ? STUN_TICKS : stunTicks,
                0);
    }

    /**
     * Stun down, then regen.
     *
     * <p>Regen only runs while the guard is <em>not</em> raised. A player holding guard up
     * recovers nothing, which is the cost of holding it: not stamina, but the ability to block
     * a second thing in a row.</p>
     */
    public GuardState tick() {
        if (stunTicks > 0) {
            return new GuardState(held, stamina, stunTicks - 1, REGEN_INTERVAL);
        }
        if (held || stamina >= MAX_STAMINA) {
            return new GuardState(held, stamina, 0, regenTicks);
        }
        int counter = regenTicks - 1;
        if (counter > 0) {
            return new GuardState(held, stamina, 0, counter);
        }
        return new GuardState(held, stamina + 1, 0, REGEN_INTERVAL);
    }
}
