package com.cortes.combat.attack;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Knockback, scaled by charge.
 *
 * <p>Below the charge floor there is no knockback at all, which is the fix for shove-spam:
 * without it, a stream of uncharged taps still pushes people around and the control comes
 * from mashing rather than from timing.</p>
 */
public final class Knockback {

    /** Close to vanilla: a normal hit does not send anyone across the map. */
    private static final double BASE_STRENGTH = 0.4;

    private static final double MAX_STRENGTH = 0.9;

    private Knockback() {
    }

    public static void apply(LivingEntity source, Entity target, double charge) {
        if (charge <= 0.0 || !(target instanceof LivingEntity living)) {
            return;
        }
        double strength = BASE_STRENGTH + (MAX_STRENGTH - BASE_STRENGTH) * Math.min(1.0, charge);

        Vec3 push = living.getDeltaMovement();
        Vec3 direction = push.normalize();
        if (direction.lengthSqr() < 1.0E-7) {
            // Standing still: push straight back from the attacker.
            direction = target.position().subtract(source.position()).normalize();
        }
        double x = direction.x * strength;
        double y = Math.max(0.15, direction.y * strength + 0.1);
        double z = direction.z * strength;

        living.hurtMarked = true;
        living.setDeltaMovement(push.x + x, push.y + y, push.z + z);
        living.hasImpulse = true;
    }
}
