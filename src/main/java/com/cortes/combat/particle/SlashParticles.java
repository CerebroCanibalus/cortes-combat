package com.cortes.combat.particle;

import com.cortes.combat.core.weapon.WeaponProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Where the slashes come out of, in one place.
 *
 * <p><b>Server side, on purpose.</b> Every player has to see it, and that means it cannot be
 * predicted on the client: a predicted slash shows up for the person swinging whether or not the
 * swing connects, and the people watching them see nothing until a packet lands. Spawning on the
 * server when the swing resolves costs one round trip and buys a slash that is true.</p>
 *
 * <p>Vanilla's own swing arc is client side and local, which is why it looks instant. That is the
 * right trade for a flourish attached to your own arm and the wrong one for an event other people
 * are watching.</p>
 *
 * <p><b>Charged hits use vanilla's crit.</b> It is already a big animated slash, it needs no
 * second set of art, and reusing it means a charged hit is unmistakable: different shape, not
 * just the same one brighter. Our own slashes are for the ordinary hit and the miss.</p>
 */
public final class SlashParticles {

    private SlashParticles() {
    }

    /**
     * The slash for a hit that landed.
     *
     * @param charged whether the swing was at full charge, which swaps in vanilla's crit
     */
    /**
     * The gap between the two cuts of a doubled strike, in ticks.
     *
 * <p>Two, and that number is a decision rather than a default. One tick is under a fifth of a
 * second and the two cuts overlap into one bright shape; three is far enough that the eye reads
 * them as separate events but the pair no longer looks like a single committed strike. Two is the
 * only value that says "twice" while still saying "now", which is the whole claim.</p>
 *
 * <p><b>A doubled strike is drawn as two slashes and not as one slash plus the crit burst.</b> The
 * burst was the original answer for a charged hit and it is right for a single one -- a different
 * shape, unmistakable. But it has no way to say "twice": there is no second crit. So a sabre that
 * hits for double used to throw one burst and a doubled number, and the player reported being able
 * to tell the damage was doubled and unable to see why. A slash already comes in a strip of five
 * frames, and one of those sprites drawn a beat later is exactly a second cut -- no new art, and
 * the pair reads as the weapon being fast rather than as an effect being loud.</p>
 */
public static final int DOUBLE_GAP_TICKS = SlashTiming.DOUBLE_GAP_TICKS;

public static void hit(ServerLevel level, WeaponProfile profile, Entity target, boolean charged) {
        if (charged) {
            burst(level, target);
            return;
        }
        slash(level, profile, contactPoint(level, target));
    }

    /**
     * The sabre's full charge: the same slash, twice, a beat apart.
     *
     * <p>Used instead of {@link #hit} for a weapon whose full charge is a <b>double</b> rather
     * than merely a bigger one. The sabre is the only weapon in the set whose charge multiplies
     * outright, and it is the only one where one visual event cannot say what happened.</p>
     *
     * <p>Both cuts are the weapon's own sprite rather than the crit. Two identical slashes with a
     * gap is the shape of "twice"; a slash and a burst is the shape of "twice, differently", and
     * the player has to already know the rule to read it.</p>
     */
    public static void doubledHit(ServerLevel level, WeaponProfile profile, Entity target) {
        double[] point = contactPoint(level, target);
        slash(level, profile, point);

        // **The second cut goes to the side, not on top of the first.** Two of the same sprite at
        // the same place are one shape drawn twice however far apart in time -- the eye reads the
        // silhouette and the silhouette is identical. Half a block across puts the pair over the
        // body of the thing that was cut, which is a difference the player sees even if they were
        // looking somewhere else when the second one fired.
        var eye = target.getEyePosition();
        var look = new double[]{eye.x - point[0], eye.z - point[2]};
        double[] offset = SlashTiming.secondCutOffset(look);

        level.sendParticles(
                new SlashParticleOptions(SlashSprite.of(profile)).delayed(DOUBLE_GAP_TICKS),
                point[0] + offset[0], point[1], point[2] + offset[1],
                1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Whether this weapon's full charge is a second hit rather than a heavier one.
     *
     * <p>Ask the charge profile instead of the weapon's name: the number that doubles is a
     * property of the charge, so the two things that have to agree -- the damage multiplier and
     * the way the hit is drawn -- are read from the same place. A weapon whose bonus was retuned
     * past 1.0 by a data pack would start drawing two cuts without anybody editing a line here.</p>
     */
    public static boolean chargesToDouble(WeaponProfile profile,
                                        com.cortes.combat.core.weapon.ChargeProfile charge) {
        return SlashTiming.chargesToDouble(profile, charge);
    }

    /**
     * The arcabuz's muzzle flash, at the barrel.
     *
     * <p><b>Spawned at the muzzle and not at the player's eye</b>, because a flash drawn where the
     * camera is sits behind the camera and reads as the screen flickering. The caller passes a
     * point ahead of the gun.</p>
     *
     * <p>One particle and no spread: it is a flash and not an explosion, and the shot's damage and
     * the shot's spread are two separate things that each happen once.</p>
     */
    public static void muzzle(ServerLevel level, double x, double y, double z) {
        level.sendParticles(
                new SlashParticleOptions(SlashSprite.MUZZLE),
                x, y, z,
                1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * A slash with no enemy attached, for a swing that swept.
     *
     * <p>The halberd's wide swing is an event in its own right: it happens, it hits three things
     * or it hits nothing, and the player needs to see that they did the thing even when the thing
     * found nothing.</p>
     */
    public static void sweep(ServerLevel level, WeaponProfile profile, double x, double y, double z) {
        // The sweep's OWN sprite, not the weapon's hit sprite.
        //
        // This asked SlashSprite.of(profile) and got alabarda_hit, which is a slash that connects.
        // The sweep is a different event and it has different art: it says the weapon went wide,
        // not that something was cut, and it throws whether or not anything was there. Passing the
        // profile made it say the wrong thing on every swing and the playtest read it as "no
        // particle at all" because the two look alike at a glance.
        level.sendParticles(
                new SlashParticleOptions(SlashSprite.SWEEP),
                x, y, z,
                1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * The charged hit: vanilla's crit, thirty of it, around the target rather than at its feet.
     *
     * <p><b>The centre of the bounding box, not {@code getY()}.</b> {@code Entity#getY} is the
     * feet, and spawning there with a 0.45 spread put every crit on the ground the enemy was
     * standing on, so a full charge against anything tall read as a puff at its boots -- the
     * opposite of what a charged hit is supposed to say.</p>
     *
     * <p>The spread scales with the target, so the burst engulfs whatever was hit. A fixed spread
     * either misses the chest of a large mob or swallows a rabbit whole, and "a charged hit covers
     * the thing" has to hold for every size in the game.</p>
     *
     * <p>Thirty rather than vanilla's eight. This is the read for a swing that cost the player a
     * full hold, and it is the only feedback that says so: the damage number, the crosshair and the
     * field of view either do not show it or show it too quietly to notice.</p>
     */
    private static void burst(ServerLevel level, Entity target) {
        var box = target.getBoundingBox();
        double centreX = (box.minX + box.maxX) * 0.5;
        double centreY = (box.minY + box.maxY) * 0.5;
        double centreZ = (box.minZ + box.maxZ) * 0.5;
        double spreadX = Math.max(0.2, (box.maxX - box.minX) * 0.5);
        double spreadY = Math.max(0.3, (box.maxY - box.minY) * 0.35);
        double spreadZ = Math.max(0.2, (box.maxZ - box.minZ) * 0.5);

        level.sendParticles(ParticleTypes.CRIT,
                centreX, centreY, centreZ,
                30, spreadX, spreadY, spreadZ, 0.05);
    }

    private static void slash(ServerLevel level, WeaponProfile profile, double[] point) {
        level.sendParticles(
                new SlashParticleOptions(SlashSprite.of(profile)),
                point[0], point[1], point[2],
                1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Where the slash appears: the point of the target's box the swing actually reached.
     *
     * <p>The nearest point of the bounding box to the attacker, which is the same point the cone
     * test measured, rather than the box's centre or the entity's feet. It is the one place on the
     * model the player was actually aiming at, so the slash lands where they expect instead of
     * somewhere near it.</p>
     */
    private static double[] contactPoint(ServerLevel level, Entity target) {
        var box = target.getBoundingBox();
        var eye = target.getEyePosition();
        return new double[]{
                com.cortes.combat.reach.ConeMath.closestOnAxis(eye.x, box.minX, box.maxX),
                com.cortes.combat.reach.ConeMath.closestOnAxis(eye.y, box.minY, box.maxY),
                com.cortes.combat.reach.ConeMath.closestOnAxis(eye.z, box.minZ, box.maxZ)};
    }
}