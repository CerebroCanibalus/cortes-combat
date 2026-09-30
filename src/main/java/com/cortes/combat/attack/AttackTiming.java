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
     */
    public static final int MIN_CHARGE_HOLD_TICKS = 4;

    private AttackTiming() {
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
