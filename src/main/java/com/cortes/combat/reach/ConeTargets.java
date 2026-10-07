package com.cortes.combat.reach;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.cortes.combat.core.weapon.ReachProfile;

/**
 * Everything standing in a cone, nearest first.
 *
 * <h2>Why this is its own class and not a method in {@code SpecialHandler}</h2>
 *
 * <p>Because it was one, and the maza needed the same thing for a different reason. The halberd's
 * sweep answers "what did this arc cover"; the maza's strike answers "what did this swing cover",
 * and both questions are <b>the same question</b> &mdash; so one of them has to call the other.</p>
 *
 * <p><b>Copying it would have been the easy wrong answer</b>, and the shape of the copy is the
 * part that costs: a private method that sorts by distance, next to another private method that
 * sorts by distance, is two places where "nearest first" is decided. They agree today and one of
 * them would change the day somebody wanted the sweep to reach further. {@code Reach} already owns
 * the cone test itself and stays pure; this owns the enumeration, which needs a world and so
 * cannot live there.</p>
 *
 * <h2>Why nearest first</h2>
 *
 * <p>So that when the invulnerability window eats the second hit of two overlapping mobs, the one
 * that dies is the one the player was looking at. It is the same reason the sweep sorts: the
 * player's intent decides who pays first, and distance is the only reading of intent the server
 * has that the client cannot forge.
 */
public final class ConeTargets {

    private ConeTargets() {
    }

    /**
     * Every living thing in the cone, nearest first, capped.
     *
     * <p>The cap is applied <em>after</em> the sort and not during the scan, because a cap applied
     * while collecting keeps whichever mobs the world's entity list happened to yield first &mdash;
     * which is not the same set, and is not stable between two players looking at the same crowd.</p>
     *
     * @param level the world to scan
     * @param source the player swinging
     * @param reach the cone, which may be the weapon's or a derived one
     * @param limit how many to return; below one means no cap
     * @return nearest first, never including the source
     */
    public static List<LivingEntity> nearestInCone(Level level, LivingEntity source,
                                                    ReachProfile reach, int limit) {
        List<LivingEntity> found = new ArrayList<>();
        // **Inflated by one on top of the distance**, and that extra block is load-bearing: `inCone`
        // tests the target's bounding box rather than its origin, so an entity standing with its
        // feet just past the limit can still be clipped by the swing.
        AABB box = source.getBoundingBox().inflate(reach.distance() + 1.0);
        for (Entity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == source || !candidate.isAlive()
                    || candidate.isSpectator() || candidate.isRemoved()) {
                continue;
            }
            if (Reach.inCone(source, candidate, reach)) {
                found.add((LivingEntity) candidate);
            }
        }
        found.sort((a, b) -> Double.compare(a.distanceToSqr(source), b.distanceToSqr(source)));
        return limit > 0 && found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /** Every living thing in the cone, nearest first, uncapped. */
    public static List<LivingEntity> nearestInCone(Level level, LivingEntity source,
                                                    ReachProfile reach) {
        return nearestInCone(level, source, reach, 0);
    }

    /** Kept so a caller with a position does not need a {@link BlockPos} import it does not use. */
    static double distanceSquaredTo(LivingEntity from, LivingEntity to) {
        return from.distanceToSqr(to);
    }
}
