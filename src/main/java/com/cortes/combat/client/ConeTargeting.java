package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.reach.ConeMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Picks what the player is aiming at, by cone instead of by crosshair, and decides whether a
 * block is genuinely in the way of the swing.
 *
 * <p>Targeting exists because vanilla chooses the target on the <b>client</b> with a raycast at
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
        return findTarget(player, com.cortes.combat.attack.MaceAir.reachFor(
                WeaponStats.of(player.getMainHandItem()), player));
    }

    public static Optional<LivingEntity> findTarget(LocalPlayer player, ReachProfile reach) {
        if (player == null || player.level() == null || reach == null) {
            return Optional.empty();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // The one place in the project that scans for a target on a schedule, and the reason it
        // runs on a slower one now. Level#getEntities also has an overload that takes a
        // Consumer and fills a caller-supplied list, which would save the ArrayList and the
        // Optional per call; that is left for later, on evidence. The list is one small object
        // every few ticks, and the cost that actually showed up in a profile was walking the
        // entities, which both overloads do identically.
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(reach.distance() + SEARCH_MARGIN));

        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (!isCandidate(player, candidate)) {
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

    /**
     * Whether a target found on an earlier tick is still one, without scanning for anything.
     *
     * <p>This is what lets {@link CombatAim} run its scan on a slower cadence. The scan finds a
     * target; this is what stops the found one from being believed once it stops being true. A
     * mob that died, got unloaded, changed dimension or stepped out of the cone stops counting
     * on the same tick instead of up to three ticks later, and a crosshair promising a hit on a
     * corpse is the one thing this whole class must never do.</p>
     *
     * <p>It shares {@link #isCandidate} with the scan rather than repeating the conditions. Two
     * versions of "is this something we can hit" is precisely how the crosshair and the swing
     * end up disagreeing, and that disagreement is invisible until a player reports that the
     * game refuses a hit the crosshair promised.</p>
     */
    public static boolean stillHittable(LocalPlayer player, LivingEntity target, ReachProfile reach) {
        if (player == null || player.level() == null || target == null || reach == null) {
            return false;
        }
        if (!isCandidate(player, target) || target.level() != player.level()) {
            return false;
        }
        return inCone(player.getEyePosition(), player.getLookAngle(), target.getBoundingBox(), reach);
    }

    /**
     * What an entity has to be before a swing is allowed to pick it, in one place.
     *
     * <p>{@code isRemoved} is in the list on top of what vanilla's own check covers because
     * {@code isAlive} is defined in terms of it on some versions and not on others, and a target
     * that has already been unloaded is the case the aiming cache gets wrong if it is not
     * spelled out.</p>
     */
    private static boolean isCandidate(LocalPlayer player, LivingEntity candidate) {
        return candidate != player
                && candidate.isAlive()
                && !candidate.isSpectator()
                && !candidate.isRemoved();
    }

    /**
     * True when the swing should be left to vanilla because a block is really in the way.
     *
     * <p>The test is a line of sight from the eye to the nearest point of the target, not
     * "is the crosshair resting on a block". Those are different questions, and answering the
     * second one is what made a wall <em>behind</em> the enemy eat the swing: the crosshair sat
     * on the wall, the player started mining, and the hit never happened.</p>
     *
     * <p>It also fixes grass, flowers, torches and water for free. They have an empty
     * collider, so a ray against {@code ClipContext.Block.COLLIDER} passes straight through
     * them: the swing goes through the lawn instead of eating it. The hoe is untouched, because
     * this only runs when there is a cone target to hit.</p>
     */
    public static boolean swingBlockedByBlock(Minecraft mc, LivingEntity target) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        return !hasLineOfSight(mc.player, target);
    }

    /** A ray from the eye to the nearest point of the target's box, against colliders only. */
    public static boolean hasLineOfSight(LocalPlayer player, LivingEntity target) {
        if (player == null || player.level() == null || target == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 point = nearestPoint(eye, target.getBoundingBox());
        return player.level()
                .clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /** Closest point of a box to a position, per axis. The same point the cone test measures. */
    public static Vec3 nearestPoint(Vec3 from, AABB box) {
        return new Vec3(
                ConeMath.closestOnAxis(from.x, box.minX, box.maxX),
                ConeMath.closestOnAxis(from.y, box.minY, box.maxY),
                ConeMath.closestOnAxis(from.z, box.minZ, box.maxZ));
    }

    /**
     * The same test the server runs: the nearest point of the box, measured in the same two
     * frames. Mirroring {@link ConeMath} is the point; a separate approximation here would
     * make the debug overlay and the hit test disagree.
     *
     * <p>The leashes come from the box's own half extents, per axis. A wide flat thing and a
     * tall thin thing do not earn the same tolerance, and giving them the same one would make
     * the rule a single "target size" that reads as if it existed.</p>
     */
    private static boolean inCone(Vec3 eye, Vec3 look, AABB box, ReachProfile reach) {
        return ConeMath.inCone(
                eye.x, eye.y, eye.z,
                look.x, look.y, look.z,
                ConeMath.closestOnAxis(eye.x, box.minX, box.maxX),
                ConeMath.closestOnAxis(eye.y, box.minY, box.maxY),
                ConeMath.closestOnAxis(eye.z, box.minZ, box.maxZ),
                reach.distance(), reach.horizontalAngle(), reach.verticalAngle(),
                ConeMath.leash(halfWidth(box)),
                ConeMath.leash(halfHeight(box)));
    }

    /** The box's half width, taking whichever horizontal axis is the larger one. */
    private static double halfWidth(AABB box) {
        return Math.max(box.maxX - box.minX, box.maxZ - box.minZ) * 0.5;
    }

    private static double halfHeight(AABB box) {
        return (box.maxY - box.minY) * 0.5;
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
