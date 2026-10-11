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

    /**
     * Give back some of the cooldown that is still counting.
     *
     * <p><b>Segunda Pasada is why this exists</b>, and it is a method here rather than arithmetic at
     * the call site because the subtraction has one edge and the edge is the whole point: a refund
     * larger than what is left has to land on {@code READY} and not on a negative count. A negative
     * cooldown is not "very ready" -- it is a state where {@code isReady} answers yes for a reason
     * nobody wrote down, and the next swing would be allowed by accident rather than by the rule.</p>
     *
     * <p>And it never <em>raises</em> the count: a refund on a ready weapon does nothing, which is
     * what keeps the enchantment from banking a free swing for later.</p>
     */
    public CooldownState refund(int ticks) {
        if (ticks <= 0) {
            return this;
        }
        return new CooldownState(Math.max(0, ticksRemaining - ticks));
    }

    public CooldownState tick() {
        return ticksRemaining > 0 ? new CooldownState(ticksRemaining - 1) : READY;
    }
}
