package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

/**
 * Whether an item can charge, and how much a full charge is worth.
 *
 * <p>{@code canCharge = false} is the whole mechanism behind weapons that skip charging
 * (rapier, dagger). It is a data value, not a code branch: the charge system asks this and
 * does nothing. Adding the next non-charging weapon is an item, not a code change.</p>
 *
 * <p>The bonus only ever adds on top of base damage. Charging never reduces damage, which is
 * what makes holding the button a safe default rather than a gamble.</p>
 */
public record ChargeProfile(boolean canCharge, float bonus, int fullChargeTicks) {

    public static final ChargeProfile NONE = new ChargeProfile(false, 0.0F, 0);

    public static final Codec<ChargeProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("can_charge").forGetter(ChargeProfile::canCharge),
            Codec.FLOAT.fieldOf("bonus").forGetter(ChargeProfile::bonus),
            Codec.INT.fieldOf("full_charge_ticks").forGetter(ChargeProfile::fullChargeTicks)
    ).apply(instance, ChargeProfile::new));

    /** Damage multiplier for a given charge ratio. Never below 1.0. */
    public float multiplierFor(float chargeRatio) {
        if (!canCharge || fullChargeTicks <= 0) {
            return 1.0F;
        }
        return 1.0F + (bonus * Mth.clamp(chargeRatio, 0.0F, 1.0F));
    }
}
