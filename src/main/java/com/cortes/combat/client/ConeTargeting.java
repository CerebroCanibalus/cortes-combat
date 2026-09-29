package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.reach.ConeMath;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Picks what the player is aiming at, by cone instead of by crosshair.
 *
 * <p>This exists because vanilla chooses the target on the <b>client</b> with a raycast at
 * the crosshair, and the server only ever receives that choice. Replacing the damage
 * calculation alone is therefore not enough: the cone gets evaluated against whatever
 * happened to be under the crosshair, so aiming a few degrees off silently does nothing. The
 * client has to pick the target with the same cone the server validates with.</p>
 *
 * <p>Target choice is by smallest angle from the look direction rather than nearest, so the
 * player hits what they are pointing at and not whatever happens to be physically closest.</p>
 */
public final class ConeTargeting {

    /** Inflate the search box by the reach so an entity just past the limit is still tested. */
    private static final double SEARCH_MARGIN = 2.0;

    private ConeTargeting() {
    }

    /** Resolve using whatever is in the player's main hand. */
    public static Optional<LivingEntity> findTargetForHeldItem(LocalPlayer player) {
        return findTarget(player, WeaponStats.of(player.getMainHandItem()).reach());
    }

    public static Optional<LivingEntity> findTarget(LocalPlayer player, ReachProfile reach) {
        if (player == null || player.level() == null || reach == null) {
            return Optional.empty();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(reach.distance() + SEARCH_MARGIN));

        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (candidate == player || !candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            AABB box = candidate.getBoundingBox();
            if (!inCone(eye, look, box, reach)) {
                continue;
            }
            double angle = angleFrom(eye, box.getCenter(), look);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Sends the attack packet for a cone-selected target. */
    public static boolean sendAttack(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null || player.connection == null) {
            return false;
        }
        player.connection.send(ServerboundInteractPacket.createAttackPacket(target, player.isShiftKeyDown()));
        return true;
    }

    /**
     * The same test the server runs: the nearest point of the box, measured in the same two
     * frames. Mirroring {@link ConeMath} is the point; a separate approximation here would
     * make the debug overlay and the hit test disagree.
     */
    private static boolean inCone(Vec3 eye, Vec3 look, AABB box, ReachProfile reach) {
        return ConeMath.inCone(
                eye.x, eye.y, eye.z,
                look.x, look.y, look.z,
                ConeMath.closestOnAxis(eye.x, box.minX, box.maxX),
                ConeMath.closestOnAxis(eye.y, box.minY, box.maxY),
                ConeMath.closestOnAxis(eye.z, box.minZ, box.maxZ),
                reach.distance(), reach.horizontalAngle(), reach.verticalAngle());
    }

    /**
     * Angle between the look direction and the direction to a target.
     * {@code Vec3.angleTo} does not exist in 1.21.1, so this is the acos-of-dot form, clamped
     * because a dot product drifting past 1 would make acos return NaN.
     */
    private static double angleFrom(Vec3 eye, Vec3 target, Vec3 look) {
        Vec3 delta = target.subtract(eye);
        if (delta.lengthSqr() < 1.0E-9) {
            return 0.0;
        }
        double dot = delta.normalize().dot(look);
        return Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
    }
}
