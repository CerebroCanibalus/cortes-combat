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

    /**
     * What counts as a full charge for the two weapons whose full charge does something extra.
     *
     * <p>A number rather than {@code 1.0F}, because {@code 1.0F} appears four times in this file
     * and only one of them means this: the charge's ratio clamped, the multiplier's input, the
     * floor of the mapping and this. The other three are not the same question and a reader
     * cannot tell them apart.</p>
     */
    public static final float FULL_CHARGE = 1.0F;

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

        // Two weapons, two different consequences of a full charge, and they are not the same one.
        //
        // The warhammer reaches past armour: no amount of arithmetic before the hit changes what
        // CombatRules takes, so it needs a source tagged bypasses_armor.
        //
        // The axe keeps its armour and takes it apart twice as fast, which is an event and not a
        // tag -- so its type exists to be recognised rather than to behave differently. It also
        // empties a guard in one hit, same as the warhammer, which is why both flags come off the
        // same threshold: a full charge is a full charge and there is no third state.
        boolean ignoreArmour = warhammer && ratio >= FULL_CHARGE;
        boolean axeCharged = profile == WeaponProfile.AXE && ratio >= FULL_CHARGE;

        return new AttackResult(damage, ratio, ignoreArmour, axeCharged);
    }
}
