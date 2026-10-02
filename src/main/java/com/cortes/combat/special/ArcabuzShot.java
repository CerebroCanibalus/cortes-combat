package com.cortes.combat.special;

import java.util.List;

/**
 * The arcabuz's numbers, with no Minecraft in sight.
 *
 * <p>Pure for the reason {@code LungeDamage} and {@code ConeMath} are: <b>a number nobody can
 * load in a test is a number nobody checks.</b> Every claim the design makes about this weapon --
 * it caps at ten targets, it hurts a lot, it burns -- is arithmetic, and arithmetic is the one
 * thing a bare JVM can verify.</p>
 *
 * <h2>Why these are not data components</h2>
 *
 * <p>The project rule is that reach, cone, charge and special live in the four
 * {@code ModComponents}, keyed by {@code WeaponProfile}, so a foreign mod can retune a weapon
 * without restating the rest of it. Those four are <i>the shape of the weapon</i>: how far it
 * reaches and what its button means. Everything here is <i>the one special that means the fuse</i>
 * -- the fuse length lives in {@link com.cortes.combat.state.ArcabuzState#FUSE_TICKS} because it
 * is state, and these three are the consequence of firing.</p>
 *
 * <p>A retune point, not a policy: if a datapack wanted a ten-target arcabuz to hit eleven, the
 * honest change is a constant in a class with a test on it, not a fifth component that a foreign
 * mod could set to something the shot's arithmetic does not expect.</p>
 */
public final class ArcabuzShot {

    /**
     * The most things one blast can hit, and it is a hard cap rather than a cone angle.
     *
     * <p><b>Ten, and the reason is that the damage is high.</b> These two numbers are one
     * decision and reading either of them alone gets it wrong: a wide cone with ordinary damage
     * is a crowd weapon like the halberd's sweep, and a high damage with no cap is a delete
     * button aimed at whatever the player happened to be facing.</p>
     *
     * <p>Ten is where the two meet. A blast that lands on ten things is worth a hundred and
     * twenty damage, which is most of a room; a blast that lands on eleven would be worth a bit
     * more, and the only thing the player learns from the eleventh is that the number in the
     * tooltip was a lie. The cap makes the weapon's ceiling knowable instead of a function of
     * how crowded the room happened to be, which is the property that makes a weapon worth
     * choosing on a bad day.</p>
     *
     * <p>Also the answer to a question the cone alone cannot answer: a player standing in a
     * crowd of thirty husks with an arcabuz gets exactly the same ten the design promised, and
     * not the fifteen that happened to be behind them.</p>
     */
    public static final int MAX_TARGETS = 10;

    /**
     * The shot's damage, as a multiple of the weapon's own attack damage.
     *
     * <p><b>Two, and it is a multiplier rather than an absolute number for one reason: the rest
     * of the project reads {@code Attributes.ATTACK_DAMAGE} and so does this.</b> The swing's
     * damage and the shot's damage then cannot drift apart, because they are the same number and
     * one coefficient. An absolute twelve here would have been a second place where the weapon's
     * damage is written down, and the first one to be retuned would have been this.</p>
     *
     * <p>The arithmetic with the item's own number: the arcabuz is built with a
     * {@code createAttributes} damage of 5.0 and a tier bonus of zero, so a player's
     * {@code ATTACK_DAMAGE} reads <b>6.0</b> (the attribute base is 1.0 and the modifier is an
     * ADD_VALUE on top of it). A blast is therefore <b>12.0 per target</b>, and ten targets is
     * <b>120</b>.</p>
     *
     * <p>That is a husk dead in one shot and about six of ten husks dead in one blast, which is
     * what "escopeta" has to mean to be worth a second of standing still. And it is not a delete
     * button, because it costs a firestone, a full second of not moving and a reload, and because
     * armour still reduces it on the way in.</p>
     */
    public static final float SHOT_MULTIPLIER = 2.0F;

    /**
     * How long a hit target burns, in seconds.
     *
     * <p><b>Six, and it is applied with vanilla's own fire rather than with a damage type.</b>
     * {@code Entity#igniteForSeconds} is the only public way to set fire in 1.21.1 --
     * {@code setSecondsOnFire} does not exist in this version, and {@code Entity} carries
     * {@code remainingFireTicks} plus the {@code igniteForSeconds} / {@code igniteForTicks} /
     * {@code clearFire} trio around it. So the burning is vanilla's {@code onFire} damage source
     * doing vanilla's one damage every twenty ticks, which means it is affected by
     * {@code FIRE_RESISTANCE}, it is extinguished by water and rain exactly like any fire, and
     * it needs no new damage type and no new tag to be correct.</p>
     *
     * <p>Six is short against flint and steel's fifteen and long against the one tick a single
     * fire tick gives a mob. That is the whole reason it is a number and not a boolean: the shot
     * has to leave things alive long enough to be on fire, or "proyectiles explosivos ígneos"
     * would be a hit plus an invisible debuff.</p>
     */
    public static final float FIRE_SECONDS = 6.0F;

    /**
     * How far ahead of the eye the muzzle flash is drawn, in blocks.
     *
     * <p><b>Six tenths put it at the camera and the playtest said so.</b> A flash drawn where the
     * camera is sits behind the camera, so what the player sees is the screen flickering rather
     * than a gun going off -- and "the muzzle flash comes out of the camera" is exactly what that
     * looks like.</p>
     *
     * <p><b>Seven tenths of a metre was a guess that failed.</b> The gun's own model is twenty four
     * units long in a sixty four unit texture, and in first person the viewmodel holds it a little
     * forward of the eye -- so the number that puts a flash at the tip of the barrel is about
     * <em>one and a half blocks</em>, not the two thirds it was.</p>
     *
     * <p>Derived from the drawn weapon rather than guessed again: a gun whose art is a different
     * length wants a different number, and this is the one place that should say so.</p>
     */
    public static final double MUZZLE_OFFSET = 1.7;

    private ArcabuzShot() {
    }

    /**
     * The damage one target takes from one blast.
     *
     * @param attackDamage the attacker's {@code Attributes.ATTACK_DAMAGE} right now, so that
     *                     potions and gear are already in it
     */
    public static float damageFor(double attackDamage) {
        return (float) attackDamage * SHOT_MULTIPLIER;
    }

    /**
     * The biggest number this weapon can print, which is the cap and the coefficient together.
     *
     * <p>Only worth anything against a known attack damage, and only there so a test can pin the
     * ceiling as one multiplication rather than as a number somebody wrote down.</p>
     */
    public static float maximumDamage(double attackDamage) {
        return damageFor(attackDamage) * MAX_TARGETS;
    }

    /**
     * The first {@link #MAX_TARGETS} of a nearest-first list, and nothing after them.
     *
     * <p><b>Nearest first is assumed and is the caller's job to sort.</b> It is the same
     * assumption {@code SpecialHandler#targetsInCone} and {@code Lunge#sweep} already make, and
     * it is what makes the cap legible: when the cap bites, the thing that survives is whatever
     * was behind the blast, and the thing the player was looking at is not among them.</p>
     *
     * <p>Unmodifiable rather than a defensive copy, because a copy would be a second list the
     * caller could hold and then disagree with. {@code List#subList} on an unmodifiable parent is
     * already unmodifiable.</p>
     */
    public static <T> List<T> capped(List<T> nearestFirst) {
        if (nearestFirst.size() <= MAX_TARGETS) {
            return List.copyOf(nearestFirst);
        }
        return List.copyOf(nearestFirst.subList(0, MAX_TARGETS));
    }

    /**
     * How many of the targets found actually get hit.
     *
     * <p>The count rather than the list, for the one place that only wants to say it out loud in a
     * log line and would otherwise have to keep the list alive for the message.</p>
     */
    public static int hitCount(int found) {
        return Math.min(Math.max(0, found), MAX_TARGETS);
    }
}
