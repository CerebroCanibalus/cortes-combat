package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Warhammer escalation: how many consecutive hits landed, and when the last one was.
 *
 * <p>By design there is no reset on taking damage. The only thing that breaks the streak is
 * the 4s window running out, which makes the warhammer a weapon of commitment rather than a
 * weapon that works against whoever stands still.</p>
 */
public record StreakState(int hits, int ticksSinceLastHit) {

    /** 4 seconds, per DESIGN.md. */
    public static final int WINDOW_TICKS = 80;

    public static final StreakState EMPTY = new StreakState(0, WINDOW_TICKS);
    public static final Codec<StreakState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("hits").forGetter(StreakState::hits),
            Codec.INT.fieldOf("ticks_since_last_hit").forGetter(StreakState::ticksSinceLastHit)
    ).apply(instance, StreakState::new));

    public boolean isActive() {
        return hits > 0 && ticksSinceLastHit < WINDOW_TICKS;
    }

    public StreakState onHit() {
        return new StreakState(hits + 1, 0);
    }

    public StreakState tick() {
        if (hits == 0) {
            return this;
        }
        int since = ticksSinceLastHit + 1;
        return since >= WINDOW_TICKS ? EMPTY : new StreakState(hits, since);
    }
}
