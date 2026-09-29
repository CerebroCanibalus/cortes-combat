package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Server-authoritative attack cooldown.
 *
 * <p>Ticks remaining before the next attack is allowed. This is the permission to attack and
 * the source of truth: the horizontal HUD bar reads this, and it can never be skipped.</p>
 */
public record CooldownState(int ticksRemaining) {

    public static final CooldownState READY = new CooldownState(0);
    public static final Codec<CooldownState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("ticks_remaining").forGetter(CooldownState::ticksRemaining)
    ).apply(instance, CooldownState::new));

    public boolean isReady() {
        return ticksRemaining <= 0;
    }

    public float ratio(int attackInterval) {
        if (attackInterval <= 0) {
            return 1.0F;
        }
        return 1.0F - Math.min(1.0F, (float) ticksRemaining / (float) attackInterval);
    }

    public CooldownState start(int attackInterval) {
        return new CooldownState(attackInterval);
    }

    public CooldownState tick() {
        return ticksRemaining > 0 ? new CooldownState(ticksRemaining - 1) : READY;
    }
}
