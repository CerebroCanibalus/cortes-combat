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
 * <p>It also scans for entities itself, every frame, instead of reading what
 * {@link CombatAim} cached. That is the one place in the project where doing the expensive
 * thing is the correct choice: the cache throttles its scan to keep a hint current, and an
 * overlay whose whole job is answering "why did that not hit" must not be three ticks behind
 * the world it is drawing. It is off unless asked for, so the cost is only ever paid
 * deliberately.</p>
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
        // The shot's cone when there is one: the debug draw has to describe what the player is being shown, or /cc cone reports a different weapon from the HUD.
        // And the air bonus on top, for the same reason: the HUD draws the maza wider while it
        // falls, so a cone that did not would report a weapon the player is not holding.
        ReachProfile reach = com.cortes.combat.attack.MaceAir.displayedFor(stats, player);
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

        // The frame the hit test uses, asked for from the same method it uses. This renderer
        // used to rebuild the axes by hand and built a ground-anchored fan while the game used
        // a gaze-anchored cone, so looking up drew one shape and hit another. Asking ConeMath for
        // its frame is the fix, and the reason it stays fixed is that there is now exactly one
        // implementation of the maths instead of two that can drift.
        ConeMath.Frame frame = ConeMath.frame(look.x, look.y, look.z);
        Vec3 forward = new Vec3(frame.fx(), frame.fy(), frame.fz());
        Vec3 right = new Vec3(frame.rx(), frame.ry(), frame.rz());
        Vec3 up = new Vec3(frame.ux(), frame.uy(), frame.uz());

        double hRad = Math.toRadians(reach.horizontalAngle());
        double vRad = Math.toRadians(reach.verticalAngle());
        double distance = reach.distance();

        // The gaze axis itself, so it is obvious which way the cone points.
        line(lines, pose, eye, eye.add(forward.scale(distance)), 0.0F, 0.9F, 0.9F, 1.0F);

        // The two limit curves, each in its own colour.
        //
        // The hit region is not an ellipse. In this frame a point is hittable when
        // |lateral| <= along * tan(horizontal) AND |vertical| <= along * tan(vertical), which is
        // the intersection of two half-spaces: a rectangular pyramid, not a cone of revolution.
        // An earlier version of this renderer walked the rim as an ellipse, which put most of
        // its points ninety degrees off the gaze line, outside the region it was claiming to
        // show. So the boundary is drawn as the two curves it actually is.
        limitCurve(lines, pose, eye, forward, right, up, hRad, vRad, distance, true);
        limitCurve(lines, pose, eye, forward, right, up, hRad, vRad, distance, false);

        // The distance limit: a flat cap at max reach, with an edge to each of its corners.
        // The cap is a rectangle, not a disc, because the hit region is a rectangular pyramid
        // in this frame rather than a cone of revolution.
        double halfWidth = distance * Math.tan(hRad);
        double halfHeight = distance * Math.tan(vRad);
        double[][] corners = {
                {-halfWidth, -halfHeight}, {halfWidth, -halfHeight},
                {halfWidth, halfHeight}, {-halfWidth, halfHeight}
        };
        Vec3 capCentre = eye.add(forward.scale(distance));
        for (int i = 0; i < corners.length; i++) {
            double[] here = corners[i];
            double[] next = corners[(i + 1) % corners.length];
            Vec3 a = capCentre.add(right.scale(here[0])).add(up.scale(here[1]));
            Vec3 b = capCentre.add(right.scale(next[0])).add(up.scale(next[1]));
            line(lines, pose, a, b, 0.2F, 0.4F, 1.0F, 0.8F);
            line(lines, pose, eye, a, 0.0F, 0.7F, 0.7F, 0.5F);
        }

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

    /**
     * One boundary curve of the hit region, walked across the other axis.
     *
     * <p>A point at horizontal angle {@code a} and vertical angle {@code v} is the direction
     * {@code forward + right * tan(a) + up * tan(v)}, which is what the test measures: its
     * lateral and vertical components come out as tan(a) and tan(v) against a forward component
     * of 1, and the two atan2 calls return exactly the angles asked for. Drawing the boundary
     * this way means the line on screen and the condition in the code are the same statement
     * rather than two descriptions of it that have to be kept in step.</p>
     *
     * @param horizontal true to sweep the horizontal limit across the vertical, false to sweep
     *                   the vertical limit across the horizontal
     */
    private static void limitCurve(VertexConsumer lines, PoseStack pose, Vec3 eye,
                                   Vec3 forward, Vec3 right, Vec3 up,
                                   double hRad, double vRad, double distance, boolean horizontal) {
        int steps = 32;
        double across = horizontal ? vRad : hRad;
        // Drawn a hair inside the limit rather than exactly on it. The line the player sees is
        // the guarantee "everything inside this is hittable", and a line sitting exactly on the
        // boundary cannot make that promise: tan and atan2 do not round-trip, so a pixel drawn
        // at exactly 35 degrees can fail a 35 degree test by a millionth of a degree. A tenth of
        // a degree is a hundredth of a pixel at any sane distance and makes the promise true.
        double limit = Math.tan(horizontal ? hRad : vRad) * LIMIT_INSET;
        double span = Math.tan(across) * LIMIT_INSET;
        Vec3 previous = null;
        for (int i = 0; i <= steps; i++) {
            double a = -span + (2.0 * span * i) / steps;
            Vec3 direction = horizontal
                    ? forward.add(right.scale(limit)).add(up.scale(a))
                    : forward.add(right.scale(a)).add(up.scale(limit));
            Vec3 scaled = direction.normalize().scale(distance);
            if (previous != null) {
                line(lines, pose, eye.add(previous), eye.add(scaled), 0.9F, 0.6F, 0.1F, 0.9F);
            }
            previous = scaled;
        }
    }

    /** How far inside its own limit the drawn boundary sits. See {@link #limitCurve}. */
    private static final double LIMIT_INSET = 0.995;

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

    /**
     * The line vertex format in 1.21.1 is position, colour AND normal. Leaving the normal
     * unset does not fail at compile time, it throws "Missing elements in vertex: Normal"
     * the first time anything is drawn, which is exactly when you least want to find out.
     */
    private static void line(VertexConsumer lines, PoseStack pose, Vec3 from, Vec3 to,
                             float r, float g, float b, float a) {
        Vec3 delta = to.subtract(from);
        // Normalising a zero-length vector yields NaN, and a NaN in a vertex buffer is a
        // crash several frames later with no useful stack. Guard it here.
        Vec3 dir = delta.lengthSqr() < 1.0E-9 ? new Vec3(0.0, 1.0, 0.0) : delta.normalize();
        lines.addVertex(pose.last().pose(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, a)
                .setNormal(pose.last(), (float) dir.x, (float) dir.y, (float) dir.z);
        lines.addVertex(pose.last().pose(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, a)
                .setNormal(pose.last(), (float) dir.x, (float) dir.y, (float) dir.z);
    }
}
