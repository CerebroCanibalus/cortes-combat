package com.cortes.combat.particle;

import com.cortes.combat.registry.ModParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Where the purification smoke comes from, in one place.
 *
 * <p><b>Server side, like the slashes and for the same reason:</b> every player has to see it,
 * and a client-predicted puff would show only to the person who caused it. The effect that
 * spawns this runs on the server, so there is nothing to predict -- the plume and the damage are
 * the same event.</p>
 *
 * <p><b>Spawned over the body of the thing, not at its feet.</b> {@code Entity#getY()} is the
 * feet, and a puff on the ground reads as dust at the boots -- the same mistake the charged-hit
 * burst already documented. The centre of the box is where the eye looks.</p>
 */
public final class PurifiedParticles {

    /**
     * Four per puff. The effect ticks every half second, so one enormous cloud every ten ticks
     * would pulse; four small ones read as a burn that never quite goes out.
     */
    public static final int PUFF_COUNT = 4;

    private PurifiedParticles() {
    }

    /** One puff of smoke, spread over the target's body. */
    public static void puff(ServerLevel level, Entity target) {
        AABB box = target.getBoundingBox();
        double centreX = (box.minX + box.maxX) * 0.5;
        double centreZ = (box.minZ + box.maxZ) * 0.5;
        double height = Math.max(0.2D, box.maxY - box.minY);
        double spreadX = Math.max(0.15D, (box.maxX - box.minX) * 0.5);
        double spreadZ = Math.max(0.15D, (box.maxZ - box.minZ) * 0.5);

        level.sendParticles(
                ModParticleTypes.PURIFIED.get(),
                centreX, box.minY + height * 0.4D, centreZ,
                PUFF_COUNT, spreadX, height * 0.3D, spreadZ, 0.01D);
    }
}
