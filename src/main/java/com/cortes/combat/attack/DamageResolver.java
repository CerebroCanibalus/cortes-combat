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

    /**
     * Below this charge a swing pushes nobody at all.
     *
     * <p>Without the floor, a stream of uncharged taps still pushed people around and the
     * control came from mashing rather than from timing. It sits high enough that a light tap
     * does not shove, because a shove the player did not ask for reads as the game being
     * unpredictable.</p>
     */
    public static final float KNOCKBACK_CHARGE_FLOOR = 0.6F;

    /**
     * How much of a weapon's knockback a given charge actually delivers.
     *
     * <p>Below this there is no push at all, which is the fix for shove-spam: without it, a
     * stream of uncharged taps still pushed people around and the control came from mashing
     * rather than from timing.</p>
     *
     * <p>This is the ramp, not the floor. It sits where a light tap still moves someone a little
     * and a full charge moves them the weapon's full amount, and it is shared by the server and
     * the client so that a shove and the crosshair agree about what just happened.</p>
     */
    public static float knockbackFor(float ratio) {
        return ratio < KNOCKBACK_CHARGE_FLOOR ? 0.0F : ratio;
    }

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

        return new AttackResult(damage, ratio, ignoreArmour);
    }
}
