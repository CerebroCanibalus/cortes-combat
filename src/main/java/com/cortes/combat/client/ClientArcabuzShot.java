package com.cortes.combat.client;

import java.util.List;

import com.cortes.combat.special.ArcabuzMuzzle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The arcabuz's report, drawn from the client's own camera basis.
 *
 * <h2>Why this class exists instead of a bigger number on the server</h2>
 *
 * <p>Because the server cannot know where the gun is. That is not a shortcut, it is the shape of
 * the problem: {@code GameRenderer#renderItemInHand} gives {@code renderHandsWithItems} a pose
 * stack that contains only the <em>inverse projection</em>, with the camera's rotation sitting in
 * the modelview stack instead. So a viewmodel point cannot be turned into a world point from
 * inside the item renderer, and the only position a server can compute is the player's eye -- which
 * is on the view axis, in the middle of the screen, at the crosshair. That is exactly where the
 * flash was, and exactly why it read as the screen flickering rather than as a gun going off.</p>
 *
 * <p>So the server sends {@link com.cortes.combat.net.ArcabuzShotPayload} and this draws. The
 * flash and the smoke are drawn here, in view space, and the fire at the things that were hit is
 * drawn by the server for everybody -- because a line between two entities is in the world and
 * both ends of it are things the server knows.</p>
 *
 * <h2>Why it is armed rather than drawn on arrival</h2>
 *
 * <p>Because the payload arrives on a network thread's schedule and a particle wants a level. Arming
 * on arrival and drawing on the client's own tick means the effect happens at a frame the client
 * chose, which is the same reason every other client-side prediction in this project is ticked
 * rather than spawned from an event.</p>
 *
 * <p>And it is armed for exactly one tick: a payload that arrived and could not be drawn would
 * otherwise sit here and fire on some later shot.</p>
 */
public final class ClientArcabuzShot {

    /** Set by the payload handler, consumed by the tick. */
    private static List<Integer> pending = List.of();

    private ClientArcabuzShot() {
    }

    /** The server said this player fired and reached these. */
    public static void arm(List<Integer> targets) {
        List<Integer> copy = targets == null ? List.of() : List.copyOf(targets);
        pending = copy;
        // **A whiff arrives as an empty list, so an empty list has to count as armed.** Without
        // this a shot that hit nothing would be undrawable, which is the exact case the whiff
        // scatter exists for.
        armedForNothing = copy.isEmpty();
    }

    /** Whether a shot is waiting to be drawn. Read by /cc, and by nothing that decides anything. */
    public static boolean isArmed() {
        return !pending.isEmpty() || armedForNothing;
    }

    /**
     * A whiff carries an empty list, so "armed" cannot be the list being non-empty.
     *
     * <p>A separate flag rather than a null list, because an empty list and "nothing arrived" are
     * different facts and conflating them would make a shot that hit nothing undrawable.</p>
     */
    private static boolean armedForNothing;

    /** The server said this player fired and reached nothing in particular. */
    public static void armEmpty() {
        pending = List.of();
        armedForNothing = true;
    }

    /**
     * Draws the flash and the smoke, once, on the client's own tick.
     *
     * @return whether something was drawn
     */
    public static boolean drawIfArmed() {
        if (!isArmed()) {
            return false;
        }
        pending = List.of();
        armedForNothing = false;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return false;
        }

        // The muzzle is in world space and the burst counts and speeds come from the pure class,
        // so a retune of "how much smoke" is a number change and not a code change.
        Vec3 muzzle = muzzle(mc);
        ArcabuzMuzzle.Burst[] bursts = ArcabuzMuzzle.bursts(direction(mc));

        // **The flash is the mod's own slash sprite**, so a gun going off looks like one of this
        // mod's effects and not like a vanilla spark, and the smoke is vanilla's large smoke --
        // the only smoke in the game, and the right one, because it is what makes a black powder
        // report a report instead of a flash.
        spawnBurst(level, new com.cortes.combat.particle.SlashParticleOptions(
                        com.cortes.combat.particle.SlashSprite.MUZZLE),
                muzzle, bursts[0]);
        spawnBurst(level, ParticleTypes.LARGE_SMOKE, muzzle, bursts[1]);
        return true;
    }

    /**
     * One burst, as particles with velocity away from the muzzle.
     *
     * <p>The velocities come from the burst's own random unit direction times its speed, so the
     * smoke expands and drifts and the flash -- whose speed is zero -- stays a compact blob at the
     * barrel. That zero is the whole difference between a flash and a spray, and it is why
     * {@link ArcabuzMuzzle#FLASH_COUNT} and {@link ArcabuzMuzzle#SMOKE_COUNT} are two numbers and
     * not one count with two appearances.</p>
     */
    private static void spawnBurst(ClientLevel level,
                                  net.minecraft.core.particles.ParticleOptions type,
                                  Vec3 at, ArcabuzMuzzle.Burst burst) {
        for (int i = 0; i < burst.count(); i++) {
            double[] direction = ArcabuzMuzzle.randomDirection();
            // Half the speed, half the spread, so a burst reads as a puff rather than a spray.
            level.addParticle(type,
                    at.x + direction[0] * burst.spread(),
                    at.y + direction[1] * burst.spread(),
                    at.z + direction[2] * burst.spread(),
                    direction[0] * burst.speed(),
                    direction[1] * burst.speed(),
                    direction[2] * burst.speed());
        }
    }

    /**
     * The barrel tip, in world space, from the camera's own basis.
     *
     * <p><b>Forward, right and up against the camera, not a distance along the look vector.</b>
     * Those three numbers are the fix for the bug this whole class is about: the old one was
     * {@code eye + look * 1.7}, which is a point on the view axis -- dead centre, at the crosshair,
     * which is what "the flash comes out of the camera" actually is.</p>
     */
    public static Vec3 muzzle(Minecraft mc) {
        return inViewSpace(mc, ArcabuzMuzzle.FORWARD, ArcabuzMuzzle.RIGHT, ArcabuzMuzzle.UP);
    }

    /**
     * A point in the camera's own basis, which is the only frame a viewmodel can be drawn in.
     *
     * <p><b>Extracted, not copied.</b> The fuse tip needed the same three numbers at different
     * values, and the alternative was a second copy of the orthonormal-basis comment -- and a
     * second copy of the guard that stops {@code cross} degenerating when the player looks
     * straight up, which is the kind of guard that survives in one copy and gets lost in the
     * other. One method, two callers, one polar fix.</p>
     *
     * @param forward metres along the view axis, so a positive number is further from the camera
     * @param right metres to the player's right
     * @param up metres along the camera's own up, so negative is below the crosshair
     */
    public static Vec3 inViewSpace(Minecraft mc, double forwardAmount, double rightAmount,
            double upAmount) {
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        // **getLookVector returns a JOML Vector3f in 1.21.1, not a Vec3.** Read from the sources
        // rather than assumed, and the three accessor calls are the whole conversion.
        var raw = camera.getLookVector();
        Vec3 forward = new Vec3(raw.x(), raw.y(), raw.z()).normalize();

        // **right = forward x up**, and then up = right x forward, so the basis is orthonormal and
        // does not tilt when the player looks up at the sky. A hand-rolled "right" that is just
        // the cross product of forward with world-up degenerates at the poles, and a player looking
        // straight up is a normal thing to do.
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-8) {
            // Looking straight up or down: any perpendicular will do for one frame.
            right = new Vec3(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();

        return eye.add(forward.scale(forwardAmount))
                .add(right.scale(rightAmount))
                .add(up.scale(upAmount));
    }

    /** The look direction as the pure class wants it: a three element array. */
    private static double[] direction(Minecraft mc) {
        var raw = mc.gameRenderer.getMainCamera().getLookVector();
        Vec3 look = new Vec3(raw.x(), raw.y(), raw.z()).normalize();
        return new double[]{look.x, look.y, look.z};
    }
    /**
     * The ids the blast reached, as entities the client can actually find.
     *
     * <p>Resolved here and not on the server's thread, because an id is a thing only the level
     * can turn back into an entity. An id that no longer resolves is dropped rather than logged:
     * a mob that died in the twenty ticks between the shot and the packet is not an error.</p>
     */
    public static List<Entity> resolve(Minecraft mc, List<Integer> ids) {
        if (mc.level == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(mc.level::getEntity)
                .filter(java.util.Objects::nonNull)
                .map(Entity.class::cast)
                .toList();
    }
    /**
     * The fuse tip, in world space: forward of the camera, down and to the side, which is where a
     * flintlock's pan actually is.
     *
     * <p><b>A second basis point, not a nudge of the muzzle's.</b> Sharing one would put the
     * sparks at the far end of a gun whose business end is the other end, and that is worse than
     * drawing nothing: it teaches the player the wrong thing about their own weapon.</p>
     */
    public static Vec3 fuseTip(Minecraft mc) {
        return inViewSpace(mc, 0.72, 0.22, -0.26);
    }

    /**
     * One spark burst when the fuse is struck, and nothing at all the rest of the time.
     *
     * <p><b>The transition, on the client, with no packet for it.</b> The fuse going from unlit to
     * lit is already a single packet the server sends on the press, and the client was holding
     * the answer in a field it was not looking at. Detecting the edge here means the sparks cost
     * nothing on the wire and they land on the same frame the server lit the match.</p>
     *
     * <p>Guarded by its own flag rather than by the countdown, because the countdown reaching its
     * top value would be a different edge: a client that joined mid-burn would fire the effect
     * for a fuse it never saw struck.</p>
     */
    public static void sparkIfTheFuseWasJustLit(Minecraft mc) {
        boolean lit = ArcabuzFuseState.isBurning();
        if (lit == wasLit) {
            return;
        }
        wasLit = lit;
        if (!lit || mc == null || mc.level == null) {
            return;
        }
        spawnBurst(mc.level, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, fuseTip(mc),
                ArcabuzMuzzle.fuseSparks()[0]);
    }

    /** Whether the fuse was lit on the previous call. See the method above. */
    private static boolean wasLit;

    /** Forget the edge, for a world change or a fresh session. */
    public static void clear() {
        wasLit = false;
        armedForNothing = false;
        pending = List.of();
    }
}
