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
        float scaled = Math.max(0.0F, Math.min(1.0F, charge));
        float push = TAP_PUSH + (1.0F - TAP_PUSH) * scaled;
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
}
