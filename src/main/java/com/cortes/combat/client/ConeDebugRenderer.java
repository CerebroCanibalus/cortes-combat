package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.reach.ConeMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Debug visualisation of the attack cone. Development only, off by default.
 *
 * <p>The point of drawing it is that the cone is invisible, and "it does not feel like a cone"
 * is not debuggable from inside a fight. With this on, you can stand still, look at the shape,
 * and see exactly what is and is not inside it.</p>
 *
 * <p>It computes the hit test through the same {@link ConeMath} the real attack uses, so what
 * you see lit up green is genuinely what the game would let you hit. A debug overlay that
 * draws its own approximation would be worse than none.</p>
 *
 * <p>Colour code: cyan is the cone itself, green marks a target that is actually hittable,
 * red one that is just outside, and the vertical limit is drawn as the flat blue cross so the
 * vertical angle is visible rather than something you have to infer.</p>
 */
public final class ConeDebugRenderer {

    private ConeDebugRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (!ClientDebugFlags.coneVisible()
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || event.getPoseStack() == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        if (camera == null) {
            return;
        }

        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        ReachProfile reach = stats.reach();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        pose.pushPose();
        pose.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
        renderCone(pose, eye, look, reach, player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach.distance() + 2.0)));
        pose.popPose();
    }

    private static void renderCone(PoseStack pose, Vec3 eye, Vec3 look, ReachProfile reach,
                                    List<LivingEntity> nearby) {

        VertexConsumer lines = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(RenderType.lines());

        Vec3 forward = horizontalForward(look);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 up = look.subtract(forward.scale(look.dot(forward))).normalize();

        double hRad = Math.toRadians(reach.horizontalAngle());
        double vRad = Math.toRadians(reach.verticalAngle());
        double distance = reach.distance();

        // Cone outline: the axis, plus the circular rim at max distance.
        line(lines, pose, eye, eye.add(look.scale(distance)), 0.0F, 0.9F, 0.9F, 1.0F);

        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double a = (Math.PI * 2.0 * i) / segments;
            // Tilt the rim by the vertical half-angle so the drawn shape is a real cone.
            Vec3 rim = forward.scale(Math.cos(hRad) * Math.cos(vRad))
                    .add(right.scale(Math.sin(hRad) * Math.cos(vRad)))
                    .add(up.scale(Math.sin(vRad)))
                    .normalize()
                    .scale(distance);
            double b = (Math.PI * 2.0 * (i + 1)) / segments;
            Vec3 rimNext = forward.scale(Math.cos(hRad) * Math.cos(vRad))
                    .add(right.scale(Math.sin(hRad) * Math.cos(vRad) * Math.cos(b - a)))
                    .add(up.scale(Math.sin(vRad)))
                    .normalize()
                    .scale(distance);
            line(lines, pose, eye.add(rim), eye.add(rimNext), 0.0F, 0.9F, 0.9F, 1.0F);
        }

        // Radial spokes every quarter turn, which makes the horizontal spread readable.
        for (int i = 0; i < 4; i++) {
            double a = (Math.PI * 2.0 * i) / 4.0;
            Vec3 edge = forward.scale(Math.cos(hRad))
                    .add(right.scale(Math.sin(hRad) * Math.cos(a)))
                    .add(up.scale(Math.sin(hRad) * Math.sin(a)))
                    .normalize()
                    .scale(distance);
            line(lines, pose, eye, eye.add(edge), 0.0F, 0.7F, 0.7F, 0.5F);
        }

        // The vertical limit, drawn flat at the max distance so it is a plane you can see.
        Vec3 centre = eye.add(look.scale(distance));
        double halfWidth = distance * Math.tan(hRad);
        double halfHeight = distance * Math.tan(vRad);
        line(lines, pose, centre.add(right.scale(-halfWidth)).add(up.scale(-halfHeight)),
                centre.add(right.scale(halfWidth)).add(up.scale(-halfHeight)), 0.2F, 0.4F, 1.0F, 0.8F);
        line(lines, pose, centre.add(right.scale(-halfWidth)).add(up.scale(halfHeight)),
                centre.add(right.scale(halfWidth)).add(up.scale(halfHeight)), 0.2F, 0.4F, 1.0F, 0.8F);
        line(lines, pose, centre.add(right.scale(-halfWidth)).add(up.scale(-halfHeight)),
                centre.add(right.scale(-halfWidth)).add(up.scale(halfHeight)), 0.2F, 0.4F, 1.0F, 0.8F);
        line(lines, pose, centre.add(right.scale(halfWidth)).add(up.scale(-halfHeight)),
                centre.add(right.scale(halfWidth)).add(up.scale(halfHeight)), 0.2F, 0.4F, 1.0F, 0.8F);

        // Every nearby entity, lit by the real hit test. This is the part that answers
        // "why did that not hit".
        for (LivingEntity target : nearby) {
            if (target == null || target.isDeadOrDying() || !target.isAlive()) {
                continue;
            }
            boolean hittable = ConeMath.inCone(
                    eye.x, eye.y, eye.z,
                    look.x, look.y, look.z,
                    nearestX(eye, target), nearestY(eye, target), nearestZ(eye, target),
                    reach.distance(), reach.horizontalAngle(), reach.verticalAngle());
            AABB box = target.getBoundingBox();
            if (hittable) {
                boxOutline(lines, pose, box, 0.2F, 1.0F, 0.3F, 1.0F);
            } else {
                boxOutline(lines, pose, box, 1.0F, 0.2F, 0.2F, 0.6F);
            }
        }

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static double nearestX(Vec3 eye, Entity target) {
        AABB b = target.getBoundingBox();
        return ConeMath.closestOnAxis(eye.x, b.minX, b.maxX);
    }

    private static double nearestY(Vec3 eye, Entity target) {
        AABB b = target.getBoundingBox();
        return ConeMath.closestOnAxis(eye.y, b.minY, b.maxY);
    }

    private static double nearestZ(Vec3 eye, Entity target) {
        AABB b = target.getBoundingBox();
        return ConeMath.closestOnAxis(eye.z, b.minZ, b.maxZ);
    }

    private static void boxOutline(VertexConsumer lines, PoseStack pose, AABB box,
                                   float r, float g, float b, float a) {
        Vec3 c = box.getCenter();
        double hx = box.getXsize() / 2.0;
        double hy = box.getYsize() / 2.0;
        double hz = box.getZsize() / 2.0;
        Vec3[] corners = {
                new Vec3(c.x - hx, c.y - hy, c.z - hz), new Vec3(c.x + hx, c.y - hy, c.z - hz),
                new Vec3(c.x + hx, c.y - hy, c.z + hz), new Vec3(c.x - hx, c.y - hy, c.z + hz),
                new Vec3(c.x - hx, c.y + hy, c.z - hz), new Vec3(c.x + hx, c.y + hy, c.z - hz),
                new Vec3(c.x + hx, c.y + hy, c.z + hz), new Vec3(c.x - hx, c.y + hy, c.z + hz)
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] e : edges) {
            line(lines, pose, corners[e[0]], corners[e[1]], r, g, b, a);
        }
    }

    private static void line(VertexConsumer lines, PoseStack pose, Vec3 from, Vec3 to,
                             float r, float g, float b, float a) {
        lines.addVertex(pose.last().pose(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, a);
        lines.addVertex(pose.last().pose(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, a);
    }

    private static Vec3 horizontalForward(Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        return flat.lengthSqr() < 1.0E-7 ? new Vec3(0, 0, 1) : flat.normalize();
    }
}
