package com.cortes.combat.special;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A lunge that carries the player across several ticks instead of moving them once.
 *
 * <h2>Why a velocity does not work, verified in the vanilla sources</h2>
 *
 * <p>The lunge was written twice on velocity. Both times the playtest reported that it played its
 * animation and its sound and did not move the player at all. The reason is not in this mod:</p>
 *
 * <ul>
 *   <li>{@code ServerEntity.tick} sends {@code ClientboundSetEntityMotionPacket} when
 *       {@code hasImpulse} is set, and {@code ClientPacketListener#handleSetEntityMotion} applies it
 *       to the entity the packet names.</li>
 *   <li>But a <em>player's</em> position is not driven by that packet. It is driven by the movement
 *       report the client sends every tick, which is authoritative for the player and is computed
 *       from the client's own input.</li>
 * </ul>
 *
 * <p>So the velocity arrives, is used for the frame or two it takes the packet to land, and is then
 * overwritten by the client's next report -- which says the player did not move, because the player
 * was not pressing anything. Re-applying it on six consecutive ticks does not help: the client keeps
 * reporting where it thinks it is, and it never moved. That is why "make it last longer" was the
 * wrong instinct and the number of ticks was never the problem.</p>
 *
 * <p>What provably works is {@code ServerPlayer#teleportRelative}, which sends
 * {@code ClientboundPlayerPositionPacket}: {@code handleMovePlayer} moves the local player with it
 * unconditionally, with no {@code isControlledByLocalInstance} check on that path.</p>
 *
 * <h2>So the lunge is a teleport -- and that is not a downgrade</h2>
 *
 * <p>One teleport in a single tick is what felt wrong before, and the complaint was fair: it is a
 * discontinuity, with no acceleration and nothing to read as momentum. <b>The fix is not to move
 * the player less, it is to move them over time.</b> Ten to thirty small teleports, each shorter
 * than the last, is a lunge: the player's position walks forward and decays, the client receives
 * every step separately, and the screen shows travel rather than a blink.</p>
 *
 * <p>What is still wrong with it is on screen, not in the arithmetic, and the fix is a client-side
 * prediction this class does not do yet: a step every tick is still eighteen discrete corrections
 * and the playtest reads them as a judder. That is the next piece of work, and the honest version of
 * it is for the client to move itself with its own smooth physics and for this to be the
 * correction.</p>
 */
public final class Lunge {

    /** The material rank of the held weapon, zero when it is not one of ours. */
    private Lunge() {
    }

    /**
     * How many position updates this tier's lunge is made of.
     *
     * <p>Every one is a real client-visible move, so the count is what decides whether this reads as
     * travel or as a blink. A better material makes MORE of them rather than longer ones -- see
     * {@link LungeDamage#FIRST_STEP} for the three designs that failed before that one.</p>
     */
    public static int ticksFor(int materialRank) {
        return LungeDamage.ticksFor(materialRank);
    }

    /**
     * How much of the direction the player can still change mid-lunge.
     *
     * <p><b>Zero point three.</b> This number is the whole difference between the two designs, and
     * both of the extremes were wrong:</p>
     *
     * <ul>
     *   <li>Fully locked to the direction of the press, and the playtest reported it as "very
     *       hardcoded forward: if I go backwards it still pulls me forward". A commitment you cannot
     *       influence is not a commitment, it is a rail.</li>
     *   <li>Fully re-read from the camera every tick, and the lunge became a steerable flight --
     *       curveable at any moment, which is a double jump nobody asked for.</li>
     * </ul>
     *
     * <p>So the lunge <em>leans</em> where the player looks: turn a little and it follows a little,
     * look backwards and it swings round, but the press still decides most of where it goes. It is
     * the shape vanilla's riptide has, and the reason that ability does not feel like a rail is
     * that the player gets to argue with it.</p>
     */
    public static final float STEER_SHARE = 0.3F;

    /**
     * The direction of one step: where the lunge was committed, leaned toward where the player looks
     * now.
     *
     * <p>Pure, and it is the testable part of the complaint. Unit in, unit out, because a step
     * length multiplied by a vector longer than one makes a diagonal lunge faster than a straight
     * one -- the bug a blended direction always has.</p>
     */
    public static double[] steered(double[] committed, double[] look) {
        double dot = committed[0] * look[0] + committed[1] * look[1];
        // Looking straight backwards would blend to a fraction of the committed vector, and
        // renormalising would hand back a full one -- which reads as the lunge ignoring the player
        // entirely. Damped instead, so turning around fights the lunge rather than instantly
        // reversing it.
        double share = STEER_SHARE * Math.max(0.0, 1.0 - dot) * 0.5;
        double x = committed[0] * (1.0 - share) + look[0] * share;
        double z = committed[1] * (1.0 - share) + look[1] * share;
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-6) {
            return committed;
        }
        return new double[]{x / length, z / length};
    }

    /** How far the nth step carries the player, in blocks. */
    public static double stepFor(int elapsed) {
        return LungeDamage.stepFor(elapsed);
    }

    /** Whether a lunge is in progress, for the tier that started it. */
    public static boolean active(ServerPlayer player) {
        return player.getData(com.cortes.combat.registry.ModAttachments.LUNGE.get())
                .running(com.cortes.combat.special.SpecialHandler.tierOf(player));
    }

    /** Stops the lunge. Called on login, and available for death and world change. */
    public static void clear(ServerPlayer player) {
        player.setData(com.cortes.combat.registry.ModAttachments.LUNGE.get(),
                com.cortes.combat.state.LungeState.IDLE);
    }

    /**
     * Starts a lunge, replacing any already running.
     *
     * <p>The direction is taken once, here, and stored on the player. An attachment rather than a
     * static, because a static is one lunge for the whole server: two players would overwrite each
     * other and it would survive death.</p>
     *
     * <p>The first step happens on the press rather than a tick later, so the blade is extended
     * while the player is still standing where they were.</p>
     */
    public static void begin(ServerPlayer player, int materialRank) {
        var look = player.getLookAngle();
        var flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-6) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        flat = flat.normalize();
        player.setData(com.cortes.combat.registry.ModAttachments.LUNGE.get(),
                new com.cortes.combat.state.LungeState(flat.x, flat.z, 0));
        // No step. The client moves the player -- see tick(ServerPlayer). All the server does here
        // is open the lunge and make the player untouchable for its first tick.
        player.invulnerableTime = LungeDamage.INVULNERABLE_TICKS;
    }

    /**
     * The server's half of a lunge tick: it moves nobody.
     *
     * <p><b>The client moves the player and this class only keeps count.</b> The server cannot do
     * the movement: a position it sets is overwritten by the movement report the client sends every
     * tick, and a velocity it sets is ignored outright for a player. Teleporting around that worked
     * -- {@code teleportRelative} is the one call that does move a player -- but ten to thirty
     * discrete corrections per lunge are ten to thirty jerks, and the playtest called it exactly
     * that. The client integrates its own velocity smoothly, so it owns the movement now.</p>
     *
     * <p>What is left here is the part the server has to keep: the cooldown of the lunge, the
     * invulnerability, and the counter that ends it. The hit is already resolved, at the press,
     * from the sweep box.</p>
     */
    public static void tick(ServerPlayer player) {
        var state = player.getData(com.cortes.combat.registry.ModAttachments.LUNGE.get());
        int tier = com.cortes.combat.special.SpecialHandler.tierOf(player);
        if (!state.running(tier)) {
            return;
        }
        // The dodge, re-armed every tick. Anything that touches the player's damage state mid-dash
        // resets it, and a lunge that quietly stops being a dodge halfway is worse than one that
        // never was.
        player.invulnerableTime = LungeDamage.INVULNERABLE_TICKS;
        player.setData(com.cortes.combat.registry.ModAttachments.LUNGE.get(), state.advance());
    }

    /**
     * Moves the player by one step, along as far as a wall allows.
     *
    // ------------------------------------------------------------ the swept volume --

    /**
     * The space a lunge covers, as a box rather than as a cone.
     *
     * <p><b>A box, and that is both the request and the right shape.</b> A swing is a cone from the
     * camera because the camera is what the player aims with. A lunge is not aimed -- it is thrown,
     * and what it cuts through is the corridor the body travelled. Testing the camera cone made the
     * hit land where the player was <em>looking</em>, which during a movement they did not choose is
     * the one thing that feels wrong: the weapon goes past a mob that the body clearly went
     * through.</p>
     *
     * <p>Anchored at the torso, not the feet, because the torso is where the weapon is. A box
     * anchored at the ground reaches down to the ankles and up to nothing, so a lunge over a
     * crawling target would pass clean over it.</p>
     */
    public static AABB sweepBox(Vec3 from, Vec3 along, double length, double width) {
        double half = width * 0.5;
        Vec3 centre = from.add(along.x * length * 0.5, 0.9, along.z * length * 0.5);
        return new AABB(
                centre.x - half, centre.y - 0.9, centre.z - half,
                centre.x + half, centre.y + 0.9, centre.z + half);
    }

    /**
     * Whether the sweep reaches a target, by its body rather than by its feet.
     *
     * <p>Three points -- feet, middle and head -- because a box is a box and a mob that is only half
     * inside it is a mob the lunge went through.</p>
     */
    public static boolean sweeps(AABB box, Vec3 feet, double height) {
        return box.contains(feet.add(0.0, height * 0.5, 0.0))
                || box.contains(feet)
                || box.contains(feet.add(0.0, height, 0.0));
    }
}
