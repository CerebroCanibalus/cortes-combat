package com.cortes.combat.client;

import com.cortes.combat.block.ReactorOverload;
import com.cortes.combat.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The ground shake while a nether star counts down, and it is an event rather than a mixin.
 *
 * <h2>Why {@code ComputeCameraAngles} and not a camera mixin</h2>
 *
 * <p>NeoForge fires {@code ViewportEvent.ComputeCameraAngles} from {@code Camera.setup} on the game
 * bus, with settable yaw, pitch and roll, and the camera reads the values straight back. That is a
 * supported hook for exactly this, so there is no bytecode injection, no access transformer and no
 * second thing to keep in step with a Minecraft version. A mixin would do the same work with more
 * surface, and the project's rule is to reach for one only when no event exists.</p>
 *
 * <h2>Which reactors are shaking, and how the client learns it</h2>
 *
 * <p>The overload is block-entity data, and the block entity's update tag already carries it -- the
 * server sends one when the star goes in and when the fuse ends, and the client's
 * {@code loadAdditional} calls into here. So the client keeps a set of positions, keyed by
 * dimension, and the server never has to know a camera exists. A forgotten reactor is removed when
 * the block is, so the tricky part is the entry that outlives its block, and
 * {@code NetherReactorBlockEntity.setRemoved} is what closes that.</p>
 *
 * <p><b>The shake is deliberately small and distance-dependent.</b> See
 * {@link ReactorOverload#shakeDegrees}: only reactors within {@link ReactorOverload#QUAKE_RADIUS}
 * are felt, and the falloff is squared so the edge is a whisper rather than a wobble across the
 * map. It can be turned off entirely, because a camera that moves on its own is a motion-sickness
 * trigger and this project already treats every screen effect as opt-out.</p>
 */
public final class ReactorShake {

    /** Active star overloads, by position, with the dimension they are in. */
    private static final Map<BlockPos, ResourceKey<Level>> SHAKING = new HashMap<>();

    private ReactorShake() {
    }

    /** Called from the block entity whenever its synced overload changes. Server side is ignored. */
    public static void observe(Level level, BlockPos pos, boolean star) {
        if (level == null || !level.isClientSide()) {
            return;
        }
        BlockPos key = pos.immutable();
        if (star) {
            SHAKING.put(key, level.dimension());
        } else {
            SHAKING.remove(key);
        }
    }

    /** A block is gone: stop shaking for it. */
    public static void forget(Level level, BlockPos pos) {
        SHAKING.remove(pos.immutable());
    }

    /** A world change: nothing from the old one should carry over. */
    public static void clear() {
        SHAKING.clear();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKING.isEmpty() || !ClientConfig.REACTOR_SHAKE.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float time = minecraft.level.getGameTime() + (float) event.getPartialTick();

        float pitch = 0.0F;
        float roll = 0.0F;
        for (Map.Entry<BlockPos, ResourceKey<Level>> entry : SHAKING.entrySet()) {
            if (!entry.getValue().equals(minecraft.level.dimension())) {
                continue;
            }
            BlockPos pos = entry.getKey();
            double distance = Math.sqrt(camera.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            float amplitude = ReactorOverload.shakeDegrees(distance, ReactorOverload.QUAKE_MAX_DEGREES);
            if (amplitude <= 0.0F) {
                continue;
            }
            // Two frequencies so it does not read as one clean sine: a slow heave and a faster
            // rattle, both seeded by the world clock so every frame of one tick agrees.
            pitch += amplitude * Mth.sin(time * 0.7F);
            roll += amplitude * Mth.cos(time * 1.9F);
        }
        if (pitch != 0.0F || roll != 0.0F) {
            event.setPitch(event.getPitch() + pitch);
            event.setRoll(event.getRoll() + roll);
        }
    }
}
