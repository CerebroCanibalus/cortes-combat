package com.cortes.combat.special;

import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.enchant.CombatEnchantRules;

/**
 * The halberd's sweep, as numbers.
 *
 * <p>Pure for the same reason {@link LungeDamage} is: this is the formula that decides what the
 * weapon's special is worth, and a formula that can only be checked by launching a client is a
 * formula that is wrong until a playtest finds it.</p>
 *
 * <p><b>And it is a class rather than four constants beside the handler</b> because the four
 * numbers are one decision, not four. The sweep reached further, shoved harder and hit everything
 * in the arc for <em>exactly what one swing costs</em>, which is the playtest's "it is literally a
 * better version of the normal attack". There was no single number that was too high: the cooldown
 * was the one that was wrong, because a special that costs the same as the attack it replaces is
 * not a special, it is the attack with a wider hitbox.</p>
 */
public final class HalberdSweep {

    /**
     * Swings the sweep is worth.
     *
     * <p>Four, not one. At one the sweep was strictly better than the click it competes with --
     * more targets, more reach, more shove, same price -- and the only honest answer to that is
     * that the button should be removed. At four the choice is real: a sweep is 72 ticks, four
     * halberd swings are 72 ticks, and the sweep only wins from <b>five</b> mobs up. One mob, three
     * and a half times less damage per second.</p>
     *
     * <p>Below the lunge's eight, on purpose. The lunge is the ropera's whole identity and the
     * sweep is one of the halberd's two buttons; making the halberd's alternative as expensive as
     * the ropera's signature move would make the halberd's cheaper button the interesting one.</p>
     */
    public static final float COOLDOWN_FACTOR = 4.0F;

    /**
     * How hard the arc shoves, against the tap's 0.55 and a full charge's 1.0.
     *
     * <p>Four fifths, so it is more than a tap and less than a hit that was charged for. A sweep
     * that shoved as hard as a charged swing would be paying for reach, spread and four swings of
     * cooldown with one extra thing, which is how a special ends up strictly better than an
     * attack again.</p>
     */
    public static final float PUSH = 0.8F;

    /**
     * How much further the arc reaches than the thrust.
     *
     * <p>A sweep is the same weapon used differently: the blade is swung, and a swung blade covers
     * more ground than a stab. Scaling the weapon's own reach instead of writing a second number
     * is what keeps a halberd at a different tier being a halberd at a different length.</p>
     */
    public static final float RANGE_SCALE = 1.35F;

    /**
     * How much wider the arc opens, as a fraction of the weapon's own horizontal angle.
     *
     * <p>Same reasoning as the distance, and the vertical angle is left alone: the halberd's 35
     * degrees is already the tallest thing in the game and widening it as well would make the
     * special a cone and not a sweep.</p>
     */
    public static final float WIDTH_SCALE = 1.35F;

    /**
     * Where the arc particle sits, as a fraction of the reach it belongs to.
     *
     * <p>A share rather than the fixed 1.2 blocks it used, because a longer arc drawn at the old
     * distance reads as a particle floating next to the player rather than at the edge of the
     * thing that just happened.</p>
     */
    public static final float ARC_OFFSET_SHARE = 0.3F;

    private HalberdSweep() {
    }

    /**
     * The cone the sweep actually sweeps.
     *
     * <p>Its own method rather than a call site scaling two of the three numbers, because the
     * vertical angle is deliberately <em>not</em> scaled and a caller doing arithmetic on a
     * {@code ReachProfile} has no way to know that.</p>
     */
    public static ReachProfile reachFor(ReachProfile weapon) {
        return reachFor(weapon, 0);
    }

    /**
     * The same, with Barredor's level folded in.
     *
     * <p><b>The level is an argument rather than something this class looks up</b>, and the reason is
     * the same one that puts every other number here: this file is the formula, the stack is the
     * world, and a formula that reads the world cannot be checked without one. The call site reads the
     * enchantment; this multiplies.</p>
     *
     * <p>Both scales move together, and that is the decision rather than a convenience. A sweep that
     * grew longer without growing wider would be a thrust with a longer reach -- and the halberd
     * already has a thrust. What Barredor sells is <em>more of the arc</em>, so the arc is what
     * grows.</p>
     */
    public static ReachProfile reachFor(ReachProfile weapon, int level) {
        return new ReachProfile(
                weapon.distance() * CombatEnchantRules.sweepScale(level, RANGE_SCALE),
                weapon.horizontalAngle() * CombatEnchantRules.sweepScale(level, WIDTH_SCALE),
                weapon.verticalAngle());
    }

    /** What the sweep costs, in ticks, off the weapon's own swing interval. */
    public static int cooldownFor(int attackInterval) {
        return Math.max(1, Math.round(attackInterval * COOLDOWN_FACTOR));
    }

    /** How far ahead of the player the arc is drawn. */
    public static double arcOffset(ReachProfile sweep) {
        return sweep.distance() * ARC_OFFSET_SHARE;
    }
}