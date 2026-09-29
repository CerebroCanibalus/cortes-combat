package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Remaining guard hits, shared by the shield and the sword.
 *
 * <p>Only ever spent when a hit is actually blocked, never for holding the guard up. That one
 * rule is what lets the sword cover indefinitely for free, without a special case: there is
 * no special case.</p>
 */
public record GuardState(int stamina, int stunTicks) {

    public static final int MAX_STAMINA = 3;
    public static final GuardState FULL = new GuardState(MAX_STAMINA, 0);
    public static final Codec<GuardState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("stamina").forGetter(GuardState::stamina),
            Codec.INT.fieldOf("stun_ticks").forGetter(GuardState::stunTicks)
    ).apply(instance, GuardState::new));

    public boolean isStunned() {
        return stunTicks > 0;
    }

    public boolean isExhausted() {
        return stamina <= 0;
    }

    /** Spend one hit of guard. Returns the new state, stunned if this was the last one. */
    public GuardState block() {
        int left = stamina - 1;
        return new GuardState(Math.max(0, left), left <= 0 ? 40 : stunTicks);
    }

    /** Tick the stun down. Guard recovers on its own, without touching food. */
    public GuardState tick() {
        if (stunTicks > 0) {
            return new GuardState(stamina, stunTicks - 1);
        }
        if (stamina < MAX_STAMINA) {
            return new GuardState(stamina + 1, 0);
        }
        return this;
    }
}
