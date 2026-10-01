package com.cortes.combat.client;

import com.cortes.combat.special.LungeDamage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The lunge's movement, on the client.
 *
 * <h2>Why this class exists at all</h2>
 *
 * <p>The lunge had been moved from the server in discrete teleports, ten to thirty of them, one per
 * tick. The arithmetic was right and the playtest called it "it moves in jerks" -- which is not a
 * tuning problem, because no value of the step size fixes it. Eighteen discrete position corrections
 * are eighteen jerks at any distance.</p>
 *
 * <p>And there is no third option: a server-side <em>velocity</em> does not move a player at all,
 * because the player's position is driven by the movement report the client sends every tick, which
 * overwrites whatever the server set. That was verified in vanilla's sources and it is why the
 * previous version moved the player by teleporting instead.</p>
 *
 * <p>So the movement belongs here, where it runs on the physics the game already has. The client
 * knows where the player is, it integrates their own velocity every frame, and the result is a
 * lunge that accelerates and decelerates the way a body does.</p>
 *
 * <h2>What the server still owns</h2>
 *
 * <p>Authority, the cooldown, the invulnerability and the hit. The server keeps the attachment and
 * still runs its own tick, but no longer teleports: it moves nobody. The hit is resolved from the
 * server's own copy of where the player is, which the client's movement reports have already
 * updated by the time it matters.</p>
 */
public final class LungeMotion {

    private static double dirX;
    private static double dirZ;
    private static int ticksLeft;

    private LungeMotion() {
    }

    /**
     * Starts the client's half of a lunge.
     *
     * <p>Called on the press, at the same moment the server opens its own lunge, so the movement
     * begins on the frame the player committed rather than a round trip later.</p>
     */
    public static void begin(LocalPlayer player, int materialRank) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-6) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        flat = flat.normalize();
        dirX = flat.x;
        dirZ = flat.z;
        ticksLeft = LungeDamage.ticksFor(materialRank);
        apply(player, 0);
    }

    /** Whether the client's half is still running. */
    public static boolean active() {
        return ticksLeft > 0;
    }

    /**
 * One tick of the lunge.
 *
 * <p><b>The velocity is set, not added.</b> Adding would make the lunge accelerate -- each tick on
 * top of the last -- and a lunge that builds to a sprint is a different move. Setting it to the
 * decaying curve means the player moves at exactly the speed the curve says, and the deceleration
 * is the shape rather than a side effect of accumulation.</p>
 *
 * <p><b>The step goes in whole, and the half it used to carry is gone.</b> It was a correction for
 * the teleport version overshooting its own request every tick -- 0.48 moved against 0.40 asked --
 * and that overshoot came from {@code teleportRelative} preserving the player's velocity while
 * adding the step on top of it. The client has no such interaction: setting the velocity to the step
 * means the player moves exactly that far this tick and exactly that far again next, so the sum of
 * the steps is the distance. Halving it covered half the distance the tier table promises, which is
 * what the playtest saw.</p>
     */
    public static void tick(LocalPlayer player) {
        if (ticksLeft <= 0) {
            return;
        }
        int elapsed = LungeDamage.ticksFor(tierOf(player)) - ticksLeft;
        // The direction leans where the player is looking, exactly as the server's does, so the
        // movement on screen and the sweep the server resolves agree.
        double[] steered = LungeDamage.steered(new double[]{dirX, dirZ},
                new double[]{lookX(player), lookZ(player)});
        dirX = steered[0];
        dirZ = steered[1];
        apply(player, elapsed);
        ticksLeft--;
    }

    private static void apply(LocalPlayer player, int elapsed) {
        if (player == null || player.isSpectator() || !player.isAlive()) {
            return;
        }
        double speed = LungeDamage.stepFor(elapsed);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(dirX * speed, motion.y, dirZ * speed);
        player.hasImpulse = true;
    }

    /** Stops the client's half. Called when the server says the lunge is over or gone. */
    public static void clear() {
        ticksLeft = 0;
    }

    private static double lookX(LocalPlayer player) {
        Vec3 look = player.getLookAngle();
        return look.x;
    }

    private static double lookZ(LocalPlayer player) {
        Vec3 look = player.getLookAngle();
        return look.z;
    }

    /** The material rank of the held weapon, so the client's curve matches the server's. */
    private static int tierOf(LocalPlayer player) {
        if (player.getMainHandItem().getItem()
                instanceof com.cortes.combat.item.CortesWeaponItem weapon) {
            return weapon.material().rank();
        }
        return 0;
    }
}