package com.cortes.combat.reach;

import com.cortes.combat.core.weapon.ReachProfile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The one and only way to ask "can this attacker hit that target".
 *
 * <p>Thin adapter. All the geometry lives in {@link ConeMath} so it can be tested without a
 * game running. Keeping this layer thin is deliberate: the rules that decide combat live in
 * one place, and this class is only responsible for pulling positions out of entities.</p>
 */
public final class Reach {

    private Reach() {
    }

    /** Origin is the attacker's eye, matching what the player actually aims with. */
    public static boolean inCone(LivingEntity source, Entity target, ReachProfile reach) {
        Vec3 origin = source.getEyePosition();
        Vec3 look = source.getLookAngle();
        AABB box = target.getBoundingBox();
        return inCone(origin, look, box, reach);
    }

    public static boolean inCone(Vec3 origin, Vec3 look, AABB targetBox, ReachProfile reach) {
        double x = ConeMath.closestOnAxis(origin.x, targetBox.minX, targetBox.maxX);
        double y = ConeMath.closestOnAxis(origin.y, targetBox.minY, targetBox.maxY);
        double z = ConeMath.closestOnAxis(origin.z, targetBox.minZ, targetBox.maxZ);
        return inCone(origin, look, x, y, z, reach, targetBox);
    }

    /**
     * The same test, with the leashes the target's size has earned.
     *
     * <p>This is the server's half of the leash, and it has to exist. The client resolves the
     * target with the same numbers and then only sends an id, so if this one left the leash out the
     * client would promise a hit on a zombie at arm's length and the server would refuse it, which
     * reads as lag rather than as a disagreement. Two adapters over one set of numbers is the
     * whole reason {@link ConeMath} exists; a third argument added to one of them and not the other
     * is how they drift.</p>
     */
    public static boolean inCone(Vec3 origin, Vec3 look,
                                  double x, double y, double z,
                                  ReachProfile reach, AABB targetBox) {
        return ConeMath.inCone(
                origin.x, origin.y, origin.z,
                look.x, look.y, look.z,
                x, y, z,
                reach.distance(), reach.horizontalAngle(), reach.verticalAngle(),
                ConeMath.leash(halfWidth(targetBox)),
                ConeMath.leash((targetBox.maxY - targetBox.minY) * 0.5));
    }

    /** The box's half width, taking whichever horizontal axis is the larger one. */
    private static double halfWidth(AABB box) {
        return Math.max(box.maxX - box.minX, box.maxZ - box.minZ) * 0.5;
    }
}
