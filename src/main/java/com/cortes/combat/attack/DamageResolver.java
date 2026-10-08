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
        return resolve(baseDamage, charge, profile, chargeRatio, streak, 0.0F);
    }

    /**
     * The same, with the fall in hand.
     *
     * <p><b>The overload exists because the fall is the one input that is not about the weapon.</b>
     * Everything else in this signature is a property of the item or of the swing; a fall belongs
     * to the player, and it is zero for every weapon in the game except one.</p>
     *
     * <p>Keeping it out of the five-argument version is what lets the existing tests keep calling a
     * function with no notion of gravity in it, and it is also the honest shape: a reader of the
     * short signature is not silently promised a fall.</p>
     */
    public static AttackResult resolve(
            double baseDamage,
            ChargeProfile charge,
            WeaponProfile profile,
            float chargeRatio,
            StreakState streak,
            float fallDistance) {
        return resolve(baseDamage, charge, profile, chargeRatio, streak, fallDistance, 0, 0.0F);
    }

    /**
     * The same, with the maza's fall in hand and the enchantment's flat bonus already settled.
     *
     * <h2>Why this is a flat argument and not part of the multiplier</h2>
     *
     * <p><b>The design's decision: the enchantment's damage is summed whole, like the maza's
     * fall.</b> Everything else in this file that is about the swing scales with the charge,
     * because the charge is the player's commitment and a commitment has to buy something
     * proportional. Enchantments are not commitment -- they are a permanent property of the item,
     * paid for once at an anvil -- so making them scale with a tic of timing would mean a sword
     * with Sharpness I hit for different amounts depending on how the player's finger was
     * hovering, which is a number nobody could learn.</p>
     *
     * <p>Vanilla does scale it ({@code f1 *= f2} in {@code Player#attack}), but vanilla's whole
     * attack is gated by the timing multiplier that this project deliberately removed. Keeping the
     * scaling would reintroduce exactly the thing the hard cooldown exists to delete.</p>
     *
     * <p>It is computed by the caller, per target, because two of the enchantments that feed it are
     * not the same for every body the swing touches: Sharpness does not care, but Smite adds five
     * points against the undead, and a sword swung into a zombie next to a cow has to pay the
     * zombie more. That is why the value arrives here already resolved rather than derived from
     * the weapon alone.</p>
     *
     * @param enchantBonus the enchantment's contribution, already reduced to a bonus (the
     *                     {@code modifyDamage} result minus its input). Zero for a swing whose
     *                     weapon carries no combat enchantment
     * @param densityLevel the Density level on the weapon, zero when it has none. It sits beside
     *                     {@code fallDistance} and not in {@code enchantBonus} because the two are
     *                     the same family of number -- "the maza and its fall" -- while
     *                     {@code enchantBonus} is summed for every profile. Only the maza reads it,
     *                     and only when there was a fall: see {@link com.cortes.combat.weapon.MaceFall#densityBonus}
     */
    public static AttackResult resolve(
            double baseDamage,
            ChargeProfile charge,
            WeaponProfile profile,
            float chargeRatio,
            StreakState streak,
            float fallDistance,
            int densityLevel,
            float enchantBonus) {

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

        // **And the maza, which is the fall.**
        //
        // <p>Added after the multiplier and outside it, on purpose. A charge multiplier and a fall
        // are the same sentence about commitment -- "how long did this take" -- and composing them
        // would make the maza's number a product of two things, which no player could be told
        // apart. The maza cannot be charged at all, so the multiplier is one and this is simply
        // added to it.</p>
        //
        // <p><b>It is added, never multiplied</b>, because vanilla's is a bonus in the same place:
        // {@code getAttackDamageBonus} is a flat sum on top of {@code ATTACK_DAMAGE}, capped by
        // MaceFall's ceiling. A weapon whose whole argument is
        // "the higher you were, the more it hurts" cannot have its two halves multiply -- a
        // twelve block fall would be worth twice a six block one instead of once and a third.</p>
        // <p><b>Density goes through the same door, and not through {@code enchantBonus}.</b> It is
        // the enchantment that scales this very fall, so it belongs beside the fall and it is zero
        // for every profile but the maza -- while {@code enchantBonus} is summed for every weapon in
        // the game. It is added flat for the same reason the fall is: a root, and not a product of
        // two things no player could be told apart.</p>
        if (com.cortes.combat.weapon.MaceFall.reads(profile)) {
            damage += com.cortes.combat.weapon.MaceFall.bonusFor(fallDistance);
            damage += com.cortes.combat.weapon.MaceFall.densityBonus(densityLevel, fallDistance);
        }

        // **And the enchantment, summed whole.** Added here rather than multiplied into `damage`
        // above, because a sword with Sharpness I has to pay the same three points whether the
        // player tapped or charged -- see the argument's own note. It sits after the fall for the
        // same reason the fall sits after the multiplier: it is a flat bonus, not a scale.
        damage += Math.max(0.0F, enchantBonus);

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
