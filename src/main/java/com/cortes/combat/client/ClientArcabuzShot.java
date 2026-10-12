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
     * The fuse tip, in world space: <b>the fuse of the gun that is actually on screen.</b>
     *
     * <p><b>Read out of the renderer's own matrix, which is the fix for "the sparks come out of
     * the camera".</b> It used to be three hand-written numbers against the camera basis
     * ({@code 0.72} forward, {@code 0.22} right, {@code -0.26} up) and it could never be right,
     * because the gun is drawn through four transforms -- the hand to {@code ITEM_POS}, vanilla's
     * own rotations, the profile's offset, and the model's own display transform -- and none of them
     * is the camera's basis. A fixed offset in the camera's frame is a point in front of the
     * player's face, always, whatever the weapon is doing.</p>
     *
     * <p>So it is a point in the <em>item's</em> frame now: {@link ArcabuzMuzzle#FUSE_RIGHT},
     * {@link ArcabuzMuzzle#FUSE_UP} and {@link ArcabuzMuzzle#FUSE_FORWARD} pushed through the
     * matrix the viewmodel was drawn with, which puts it on the weapon by construction. Those three
     * numbers are still a retune; the frame they are used in is not.</p>
     *
     * <p><b>The fallback is the old guess and it is only ever a fallback.</b> Before the first frame
     * is drawn there is no matrix, and returning the camera-relative point keeps a fuse lit on the
     * very first tick from spawning nothing at all.</p>
     */
    public static Vec3 fuseTip(Minecraft mc) {
        if (!ItemViewModel.drawnFrameIsValid()) {
            return inViewSpace(mc, FALLBACK_FORWARD, FALLBACK_RIGHT, FALLBACK_UP);
        }
        org.joml.Matrix4f frame = ItemViewModel.drawnFrame();
        double x = ArcabuzMuzzle.FUSE_RIGHT;
        double y = ArcabuzMuzzle.FUSE_UP;
        double z = ArcabuzMuzzle.FUSE_FORWARD;
        double viewX = frame.m00() * x + frame.m10() * y + frame.m20() * z + frame.m30();
        double viewY = frame.m01() * x + frame.m11() * y + frame.m21() * z + frame.m31();
        double viewZ = frame.m02() * x + frame.m12() * y + frame.m22() * z + frame.m32();

        // **-Z is away from the camera**, which is the one sign this conversion cannot get wrong by
        // accident: `ItemInHandRendererMixin`'s frame documents `ITEM_POS = (0.56, -0.52, -0.72)`,
        // and an item at a negative Z that is not behind the camera is an item in front of it.
        // `inViewSpace` takes a distance *along the view axis*, so a view-space z of -0.33 is a
        // third of a block forward and arrives here negated.
        return inViewSpace(mc, -viewZ, viewX, viewY);
    }

    /**
     * Where the fuse was drawn, in the camera's own basis, for the frame before any matrix exists.
     *
     * <p>The numbers the playtest rejected, kept for exactly one frame's worth of frames. They are
     * <em>wrong</em> and they are here so that lighting a fuse on the first tick draws something
     * rather than nothing -- a wrong position for one frame beats a hole.</p>
     */
    private static final double FALLBACK_FORWARD = 0.72;
    private static final double FALLBACK_RIGHT = 0.22;
    private static final double FALLBACK_UP = -0.26;

    /**
     * Smoke off the fuse when it is struck, and nothing at all the rest of the time.
     *
     * <p><b>Smoke rather than sparks, which is the playtest's word and it is also the right
     * shape.</b> An electric spark is a blue-white arc that appears for a frame and is gone; a lit
     * match is a grey thread that hangs there for the second the fuse burns. The old version drew
     * the wrong thing about the wrong end of the weapon.</p>
     *
     * <p><b>The transition, on the client, with no packet for it.</b> The fuse going from unlit to
     * lit is already a single packet the server sends on the press, and the client was holding
     * the answer in a field it was not looking at. Detecting the edge here means the smoke costs
     * nothing on the wire and it lands on the same frame the server lit the match.</p>
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
        Vec3 at = fuseTip(mc);
        spawnBurst(mc.level, ParticleTypes.SMOKE, at, ArcabuzMuzzle.fuseSmoke()[0]);
        // **The number, not the picture.** "The smoke is not on the fuse" is one observation and it
        // has three candidate causes -- the frame is not captured, the frame is captured and the
        // offset is wrong, or the frame is wrong -- and they look identical from outside. One line
        // per lighting, at debug, separates them without a screenshot.
        //
        // **`distOjo` es el numero que decide, y por eso se agrego.** La posicion sola no dice nada
        // sin saber donde estaba el jugador. La interfaz de esta posicion es
        // `ojo + adelante + derecha + arriba`, asi que:
        //   distOjo < 0,2  ->  las tres contribuciones son cero y el humo nace sobre la LENTE, o sea
        //                      la matriz de pose esta degenerada. Es el reporte.
        //   distOjo ~ 0,7  ->  el humo sale del arma, y lo que esta mal es el offset dentro del
        //                      modelo, que si es un retune.
        // Y `traslacion` es la matriz cruda: el desplazamiento del item en el marco de la vista
        // deberia rondar (0,56, -0,52, -0,72) segun `ItemInHandRendererMixin`. Ceros ahi es la prueba
        // directa de que la pose capturada no es la del arma dibujada.
        org.joml.Matrix4f matrix = ItemViewModel.drawnFrame();
        double eyeDistance = at.distanceTo(mc.gameRenderer.getMainCamera().getPosition());
        org.slf4j.LoggerFactory.getLogger("cortescombat").debug(
                "[arcabuz/mecha] fumo en {} (matriz={} traslacion=({}, {}, {}) distOjo={})",
                at, ItemViewModel.drawnFrameIsValid(),
                String.format("%.3f", matrix.m30()), String.format("%.3f", matrix.m31()),
                String.format("%.3f", matrix.m32()), String.format("%.3f", eyeDistance));
    }

    /** Whether the fuse was lit on the previous call. See the method above. */
    private static boolean wasLit;

    /** Forget the edge, for a world change or a fresh session. */
    public static void clear() {
        wasLit = false;
        armedForNothing = false;
        pending = List.of();
        ItemViewModel.forgetDrawnFrame();
    }
}
