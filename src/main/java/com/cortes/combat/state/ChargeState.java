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

    /**
     * There is deliberately no {@code ratio(int)} here.
     *
     * <p>There was one, and it was {@code ticks / fullChargeTicks} raw, and it was the reason the
     * playtest kept reporting that an ordinary click counted as a charge: the damage went through
     * the thresholded {@code AttackTiming.chargeRatioFor}, and the bar and the field of view went
     * through this one, so a two tick click against a twenty-four tick window showed eight percent
     * of a charge bar for two hundred milliseconds and told the camera to move slightly.</p>
     *
     * <p>Two functions of one quantity is the whole failure mode, so the answer is that there is
     * one now. This record holds a <b>tick count</b> and nothing else, because a tick count is the
     * fact; turning it into a ratio is {@code AttackTiming}'s job, and it does it identically for
     * the damage, the bar and the camera because all three call the same function.</p>
     */
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
}
