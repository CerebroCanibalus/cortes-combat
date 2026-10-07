package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.KnockbackProfile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Knockback, in two directions, scaled by charge.
 *
 * <p>Per weapon rather than one number for the whole game. It used to be two constants here,
 * which meant a sword, a warhammer and a block in the hand all pushed identically, and a player
 * holding a block could launch a zombie as far as one holding a mace.</p>
 *
 * <h2>Why the direction is horizontal</h2>
 *
 * <p>The push is scaled by the horizontal component of the target's current motion, and the
 * target's own vertical velocity is <b>untouched</b>. Two reasons, and the first is the design:</p>
 * <ul>
 *   <li>The complaint was that the knockback was "too vertical". Vanilla's knockback adds an
 *       upward term that scales with the push, so a strong shove is also a pop off the ground,
 *       and a mob hit with a sword ends up in the air. Nothing about hitting someone with a sword
 *       should put them in the air.</li>
 *   <li>Preserving the target's own vertical motion is also what makes knockback combine sanely.
 *       If this added vertical velocity on top, a mob already falling would have its fall
 *       cancelled by a hit and then be re-launched, and a mob knocked into a wall would climb
 *       it.</li>
 * </ul>
 *
 * <p>What is left is a pure horizontal shove, and the weapon decides how hard. A weapon whose
 * profile has a vertical component of zero never lifts anything, which is reachable and
 * meaningful rather than being a very small number that still lifts a little.</p>
 */
public final class Knockback {

    private Knockback() {
    }

    /**
     * How much of a weapon's push a plain tap already delivers.
     *
     * <p>This scaled from zero, so an ordinary click shoved nothing at all and only a held swing
     * moved anything. That is a much bigger difference than "charge adds damage": it made the
     * most common attack in the game leave the target standing perfectly still, which reads as
     * the hit not having landed even when the numbers say it did. And the playtest, separately,
     * said the sword's knockback was "too small for a charge", which is the same complaint seen
     * from the other end: the whole range was too small, and it started at nothing.</p>
     *
     * <p>So a tap delivers a real shove and a charge delivers more of it. The charge is a bonus on
     * top of a hit, never the thing that makes a hit happen.</p>
     */
    public static final float TAP_PUSH = 0.55F;

    /**
     * The push multiplier a charge buys, 0..1.
     *
     * <p><b>Public because the tooltip reads it and must not be a second copy of it.</b> The
     * knockback line shows a base and a charged number exactly the way the damage line does, and
     * the only way those two can be guaranteed to be the numbers the swing uses is for both to come
     * out of this one function -- {@link #apply} for the swing, the tooltip for the readout. A
     * tooltip that wrote {@code TAP_PUSH} itself would be right today and wrong the first time the
     * ramp is retuned, which is the failure this project has paid for three times.</p>
     *
     * @param charge 0 for a tap, 1 for a full charge, clamped
     */
    public static float pushFor(float charge) {
        float scaled = Math.max(0.0F, Math.min(1.0F, charge));
        return TAP_PUSH + (1.0F - TAP_PUSH) * scaled;
    }

    /**
     * @param source  the attacker
     * @param target  who was hit
     * @param profile the attacker's per-weapon push, already resolved
     * @param charge  0..1, how charged the swing was
     */
    public static void apply(LivingEntity source, Entity target, KnockbackProfile profile,
                             float charge) {
        if (profile == null || !(target instanceof LivingEntity living)) {
            return;
        }
        float push = pushFor(charge);
        float horizontal = profile.clampedHorizontal() * push;
        float vertical = profile.clampedVertical() * push;
        if (horizontal <= 0.0F && vertical <= 0.0F) {
            return;
        }

        Vec3 motion = living.getDeltaMovement();
        Vec3 direction = motion.normalize();
        if (direction.lengthSqr() < 1.0E-7) {
            // Standing still: push straight back from the attacker.
            direction = target.position().subtract(source.position()).normalize();
        }
        // Flattened, so a target that is already falling or jumping gets pushed along the ground
        // rather than having its fall aimed somewhere.
        double pushX = direction.x * horizontal;
        double pushZ = direction.z * horizontal;

        living.hurtMarked = true;
        living.setDeltaMovement(
                motion.x + pushX,
                motion.y + vertical,
                motion.z + pushZ);
        living.hasImpulse = true;
    }

    /**
     * The other direction: a strike that brings the target in.
     *
     * <h2>Why this is not a negative number in the same field</h2>
     *
     * <p>Because {@link KnockbackProfile#clampedHorizontal()} clamps to {@code [0, MAX]}, and a
     * negative lands on zero. That clamp is not incidental &mdash; it is what stops a config
     * override from pulling anybody through the floor &mdash; so loosening it to allow a negative
     * would trade one bound for another on every weapon in the game to serve one.</p>
     *
     * <h2>And why it does not go through the tap ramp</h2>
     *
     * <p>A pull is <b>the charged half of the trident</b>, so the charge has already gated it by the
     * time it is called. Running it through {@code TAP_PUSH} as well would mean a tap pulls at
     * three fifths of the strength, which turns the weapon's whole argument into a rounding
     * difference nobody has to aim for.</p>
     *
     * <p>The direction is from the target toward the attacker, unlike {@link #apply}'s push, which
     * follows whatever motion the target already had &mdash; that is what makes a knockback feel
     * like being hit rather than like being aimed at, and a pull has to feel like being caught.</p>
     */
    public static void pull(LivingEntity source, Entity target, float distance) {
        if (!(target instanceof LivingEntity living) || !(distance > 0.0F)) {
            return;
        }
        Vec3 toward = source.position().subtract(living.position());
        Vec3 direction = toward.normalize();
        // Flattened, for the same reason apply() flattens its push: a pull that lifts the target
        // off the ground stops being a pull and starts being a jump the player did not ask for.
        Vec3 motion = living.getDeltaMovement();
        living.hurtMarked = true;
        living.setDeltaMovement(
                motion.x + direction.x * distance,
                motion.y,
                motion.z + direction.z * distance);
        living.hasImpulse = true;
    }
}
