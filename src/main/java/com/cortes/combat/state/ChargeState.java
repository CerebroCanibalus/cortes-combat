package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Charge accumulated by holding the attack button.
 *
 * <p>Predicted on the client so the indicator stays smooth under lag, but the server owns the
 * value that actually multiplies damage. The client can report "a click happened", never
 * "I have charged for 40 ticks".</p>
 */
public record ChargeState(int ticks, boolean holding) {

    public static final ChargeState EMPTY = new ChargeState(0, false);
    public static final Codec<ChargeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("ticks").forGetter(ChargeState::ticks),
            Codec.BOOL.fieldOf("holding").forGetter(ChargeState::holding)
    ).apply(instance, ChargeState::new));

    public ChargeState advance() {
        return new ChargeState(ticks + 1, true);
    }

    public ChargeState reset() {
        return EMPTY;
    }

    public float ratio(int fullChargeTicks) {
        if (fullChargeTicks <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) ticks / (float) fullChargeTicks);
    }
}
