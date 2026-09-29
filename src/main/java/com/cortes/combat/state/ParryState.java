package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Sabre parry: how long the parry window is open, and how long until the next one is allowed.
 *
 * <p>Reflects only the single hit that landed inside the window. There is no accumulated
 * buffer and no stored damage waiting to be released.</p>
 */
public record ParryState(int activeTicks, int cooldownTicks) {

    /** 5 seconds, per DESIGN.md. */
    public static final int COOLDOWN_TICKS = 100;

    public static final ParryState READY = new ParryState(0, 0);
    public static final Codec<ParryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("active_ticks").forGetter(ParryState::activeTicks),
            Codec.INT.fieldOf("cooldown_ticks").forGetter(ParryState::cooldownTicks)
    ).apply(instance, ParryState::new));

    public boolean isReady() {
        return cooldownTicks <= 0;
    }

    public boolean isParrying() {
        return activeTicks > 0;
    }

    public ParryState activate() {
        return new ParryState(10, COOLDOWN_TICKS);
    }

    public ParryState tick() {
        return new ParryState(Math.max(0, activeTicks - 1), Math.max(0, cooldownTicks - 1));
    }
}
