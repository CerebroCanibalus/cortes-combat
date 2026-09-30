package com.cortes.combat.attack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Attack interval maths, as pure doubles.
 *
 * <p>No Minecraft types on purpose, for the same reason as {@code ConeMath}: this runs on a
 * bare JVM so it can be unit tested without booting the game.</p>
 *
 * <p>Minecraft's attack speed attribute is "attacks per second" (a sword is 1.6, an axe 1.0),
 * so the interval in ticks is simply 20 divided by it.</p>
 */
public final class AttackTiming {

    /**
     * Fallback interval when the holder has no usable attack speed at all.
     *
     * <p>Not the bare-handed case: a player always has {@code ATTACK_SPEED}, and vanilla gives
     * it a base of 4.0, which is five ticks. This only fires for an entity whose attribute is
     * missing, zero or not a number, which is a modded-entity problem rather than a player one.</p>
     */
    public static final int UNARMED_INTERVAL = 12;

    /** Vanilla mace reaches 1.0, so no vanilla weapon is slower than this. */
    public static final int MAX_INTERVAL = 40;

    /**
     * How long the attack button has to be held before the swing counts as charged, in ticks.
     *
     * <p>Four ticks is two hundred milliseconds, and it is the number that lets one button mean
     * two things without the player having to learn a modifier. A deliberate click is about a
     * hundred and fifty, so a click lands under it and anything past it was a decision.</p>
     *
     * <p>Deliberately <b>not</b> per weapon. A threshold that varied with the item would mean
     * the same flick of the wrist charges a sword and not a warhammer, which is precisely the
     * "you did not know, so you got punished" failure the design exists to avoid: the muscle
     * memory has to be global or it is not muscle memory at all.</p>
     *
     * <p>And a swing under the threshold is exactly flat rather than proportionally charged. A
     * hidden band where a three tick click does "a bit more" cannot be learned by watching, and
     * a player only discovers it by having hit for less than they expected.</p>
     *
     * <p>What the threshold is also for is what the player <em>sees</em>. The charge bar, the
     * wind-up and the FOV all read the same remapped value this produces, and all three are zero
     * below the threshold. A bar that fills for two hundred milliseconds on every ordinary click
     * is a bar that says "charging" about a swing that lands flat, and the player reads that as
     * the game charging them when it is not. **Below the threshold nothing happens and nothing
     * is shown; at the threshold everything starts.**</p>
     */
    public static final int MIN_CHARGE_HOLD_TICKS = 4;

    /**
     * The charge a swing is allowed to use, given how long the button was actually held.
     *
     * <p>A hard cut, and that is the whole decision. A swing under the threshold gets exactly
     * nothing: there is no band where a three tick click does "a little bit more", because a
     * band nobody can see is a band nobody can learn, and the only way a player would find out
     * about it is by having hit for less than they expected.</p>
     *
     * <p><b>Remapped, not clamped, and that is a change with a reason.</b> Taking the raw ratio
     * means a swing one tick past the threshold reads as sixteen percent charged, and a charge
     * bar that moves at the instant the swing becomes a charge reads as the player having charged
     * further than they had. The playtest reported exactly that: a normal click showing a charge
     * bar. So below the threshold this is exactly zero, and above it the remaining window is the
     * full range.</p>
     *
     * <p>It also means there is <em>one</em> of these rather than two. It used to be a method
     * taking a ratio and another taking a count, described as "the same rule twice, because the
     * client has a ratio and the server has a count", with a test asserting they agreed. They
     * can no longer disagree, because there is one thing to call.</p>
     *
     * <p>Lives here rather than inside {@code CombatEngine} so that the cut is a unit test. The
     * alternative, leaving the comparison in the one class that cannot be class-loaded in a bare
     * JVM, is a rule that can only be checked by playing.</p>
     *
     * @param heldTicks how long the button was down, as the server counted it
     * @param fullChargeTicks the weapon's own charge window
     * @return 0 below the threshold, then 0..1 across the rest of the window
     */
    public static float chargeRatioFor(int heldTicks, int fullChargeTicks) {
        if (heldTicks < MIN_CHARGE_HOLD_TICKS) {
            return 0.0F;
        }
        if (fullChargeTicks <= MIN_CHARGE_HOLD_TICKS) {
            return 1.0F;
        }
        // Remapped rather than clamped. The bar and the item start moving at the threshold and
        // then cross the whole range, instead of showing a sliver that says "charging" for a
        // click the game is about to call a tap. See the class comment on MIN_CHARGE_HOLD_TICKS.
        return Math.min(1.0F,
                (float) (heldTicks - MIN_CHARGE_HOLD_TICKS)
                        / (float) (fullChargeTicks - MIN_CHARGE_HOLD_TICKS));
    }

    /**
     * @param attackSpeed the holder's attack speed attribute, in attacks per second
     * @return ticks between attacks, never below 1
     */
    public static int ticksFor(double attackSpeed) {
        if (Double.isNaN(attackSpeed) || attackSpeed <= 0.0) {
            return UNARMED_INTERVAL;
        }
        long ticks = Math.round(20.0 / attackSpeed);
        return (int) Math.max(1L, Math.min(MAX_INTERVAL, ticks));
    }

    /**
     * Apply a per-weapon tempo to an attack speed, in attacks per second.
     *
     * <p>Expressed as a multiplier on the speed rather than as a subtraction on the ticks,
     * because a multiplier composes with attack speed potions the obvious way: halving the
     * interval and doubling the speed are the same thing, and one of those two is what a
     * player actually holds.</p>
     *
     * @param tempo 1.0 for vanilla's own tempo, below 1.0 for faster
     */
    public static int scaleFor(double attackSpeed, float tempo) {
        if (Double.isNaN(tempo) || tempo <= 0.0F) {
            tempo = 1.0F;
        }
        return ticksFor(attackSpeed * tempo);
    }

    /**
     * Damage multiplier from a charge ratio. Always at least 1.0: charging only ever adds,
     * it never subtracts, so holding the button is always safe and never a gamble.
     *
     * <p>The floor is enforced here rather than trusted from the data, because a config
     * override can supply any bonus it likes and this invariant is too important to leave to
     * whoever writes that file.</p>
     */
    public static float chargeMultiplier(float bonus, float chargeRatio) {
        float ratio = Math.max(0.0F, Math.min(1.0F, chargeRatio));
        float additive = Math.max(0.0F, bonus) * ratio;
        return 1.0F + additive;
    }

    /**
     * Small random spread so repeated hits are not identical. Kept tiny on purpose: the mod
     * removes vanilla's timing-based damage swing, and a replacement that is large enough to
     * feel would put the randomness back.
     */
    public static float applySpread(float damage, float spread) {
        if (spread <= 0.0F) {
            return damage;
        }
        float factor = 1.0F + (ThreadLocalRandom.current().nextFloat() * 2.0F - 1.0F) * spread;
        return Math.max(0.0F, damage * factor);
    }
}
