package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.WeaponProfile;

import net.minecraft.world.entity.LivingEntity;

/**
 * The maza's reach while its wielder is in the air, which is a fifth longer and a fifth wider.
 *
 * <h2>Why this is a rule about the weapon and not a second profile</h2>
 *
 * <p>The maza's whole identity is the fall: its damage is the fall, its cap is the fall, and the
 * design's answer to "why would I jump at something" has to be that the weapon is <em>different</em>
 * up there. So it is not a different profile with different numbers -- a second {@code WeaponProfile}
 * would be a second row in every table, six of them, and the two would drift the first time anybody
 * retuned one. It is the same weapon with one number scaled while the player is off the ground.</p>
 *
 * <h2>What is scaled, and what the words mean</h2>
 *
 * <p><b>The range and the radius, and both are the cone.</b> {@code ReachProfile} carries a distance
 * and two angles, and the angles <em>are</em> the radius: a wider cone covers more ground at the same
 * distance, which is the same thing as a bigger area. So scaling all three by the same factor is the
 * one change that means "the maza reaches further and covers more when you are falling", and it is
 * one multiplication rather than a distance here and an angle there.</p>
 *
 * <p>Pure in its arithmetic -- {@link #reachFor(WeaponProfile, ReachProfile, boolean)} takes no
 * Minecraft type -- so a test can walk it. The {@link LivingEntity} overload is the thin half that
 * asks the world.</p>
 */
public final class MaceAir {

    /** Twenty percent more of everything, while airborne. The design's number. */
    public static final float BONUS = 1.2F;

    private MaceAir() {
    }

    /**
     * The cone to use, which is the base one for everything except a maza in the air.
     *
     * @param airborne whether the wielder is off the ground right now
     */
    public static ReachProfile reachFor(WeaponProfile profile, ReachProfile base, boolean airborne) {
        // NONE has no cone at all and must stay none: scaling a none would turn "this weapon has no
        // second cone" into a small real one, which is the arcabuz's bayonet/barrel pair all over
        // again.
        if (!airborne || profile != WeaponProfile.MACE || base.isNone()) {
            return base;
        }
        return new ReachProfile(
                base.distance() * BONUS,
                base.horizontalAngle() * BONUS,
                base.verticalAngle() * BONUS);
    }

    /** The same, off a resolved weapon and the entity holding it. */
    public static ReachProfile reachFor(WeaponStats stats, LivingEntity holder) {
        return reachFor(stats.profile(), stats.reach(), airborne(holder));
    }

    /**
     * Whether a wielder counts as airborne, which is <b>not</b> the same question as "not on the
     * ground".
     *
     * <p><b>The playtest found this the hard way: swimming counted as air.</b> {@code !onGround()}
     * is true in water, so a player paddling about got the fifth-longer reach, the wider cone and
     * the falling meter for free -- "bug total", and it is one: water is the one place in the game
     * where nothing is falling, and the maza was at its strongest there. Lava is the same shape of
     * wrong, and so is an elytra: {@code isFallFlying} is exactly the state vanilla's own
     * {@code canSmashAttack} excludes, read off {@code MaceItem} rather than guessed.</p>
     *
     * <p>Pure, so a test can walk every combination without a level. The {@link LivingEntity}
     * overload is the thin half that asks the world, and it is the only place the three questions
     * are answered -- which is what keeps the reach, the display and the meter from disagreeing
     * about what "in the air" means.</p>
     */
    public static boolean airborne(boolean onGround, boolean inFluid, boolean fallFlying) {
        return !onGround && !inFluid && !fallFlying;
    }

    /** The same, off the entity. */
    public static boolean airborne(LivingEntity holder) {
        return airborne(holder.onGround(), holder.isInWater() || holder.isInLava(),
                holder.isFallFlying());
    }

    /**
     * The cone to <b>show</b>, which is the displayed one with the fall's scaling applied.
     *
     * <p><b>This is the half that was missing, and its absence was invisible in exactly the way
     * that matters.</b> "The reach" is two questions -- what a hit uses and what the brackets
     * measure -- and the air bonus is the first thing that applies to both. Every display site
     * read {@code displayedReach()} off the item with no notion of a holder, which was correct
     * while the cone was a property of the item alone. A maza in the air is the first weapon whose
     * cone is a property of the <em>player</em>, and the crosshair kept measuring the grounded one:
     * the swing reached four point oh eight blocks while the brackets drew three point four, so the
     * player was told they were out of range and the hit connected anyway.</p>
     *
     * <p>Which is the one disagreement this whole design exists to prevent, and the reason it was
     * not caught is worth writing down: the reach <em>did</em> grow, on the server and in the
     * targeting, so nothing was broken in the sense a test can see. It was the marker that lied,
     * and a marker lying is reported as "I do not notice the bonus" rather than as a bug.</p>
     *
     * <p>The four display sites -- the crosshair, the cooldown brackets, {@code /cc cone} and
     * {@code /cc hud} -- all come through here now, and {@code ConeTargeting} comes through
     * {@link #reachFor(WeaponStats, LivingEntity)}, so the marker and the hit read the same cone on
     * the same frame.</p>
     */
    public static ReachProfile displayedFor(WeaponStats stats, LivingEntity holder) {
        return reachFor(stats.profile(), stats.displayedReach(), airborne(holder));
    }
}
