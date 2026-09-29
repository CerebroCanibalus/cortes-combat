package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.state.StreakState;

/**
 * The damage formula, as a pure function of its inputs.
 *
 * <p>Everything that decides "how much does this hit for" lives here with no Minecraft types,
 * so the formula that defines the whole game can be tested on a bare JVM.</p>
 */
public final class DamageResolver {

    /** Knockback only starts being meaningful past this charge, so light taps do not shove. */
    public static final float KNOCKBACK_CHARGE_FLOOR = 0.6F;

    /** Escalation per consecutive warhammer hit. */
    public static final float WARHAMMER_STEP = 0.15F;

    private DamageResolver() {
    }

    public static AttackResult resolve(
            double baseDamage,
            ChargeProfile charge,
            WeaponProfile profile,
            float chargeRatio,
            StreakState streak) {

        if (charge != null && !charge.canCharge()) {
            chargeRatio = 0.0F;
        }
        float ratio = Math.max(0.0F, Math.min(1.0F, chargeRatio));

        float multiplier = AttackTiming.chargeMultiplier(charge == null ? 0.0F : charge.bonus(), ratio);

        // The warhammer escalates on consecutive hits, with no cap by design: the window
        // running out is the only thing that breaks the streak.
        boolean warhammer = profile == WeaponProfile.WARHAMMER;
        if (warhammer && streak != null && streak.isActive()) {
            multiplier += streak.hits() * WARHAMMER_STEP;
        }

        float damage = (float) baseDamage * multiplier;
        boolean ignoreArmour = warhammer && ratio >= 1.0F;

        // Knockback scales with charge, and below the floor there is none at all. This stops
        // a stream of light taps from being a shove spam exploit.
        float knockback = ratio < KNOCKBACK_CHARGE_FLOOR ? 0.0F : ratio;

        return new AttackResult(damage, ratio, knockback, ignoreArmour);
    }
}
