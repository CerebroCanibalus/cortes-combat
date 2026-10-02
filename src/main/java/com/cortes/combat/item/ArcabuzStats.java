package com.cortes.combat.item;

import com.cortes.combat.attack.AttackTiming;

/**
 * The arcabuz's numbers, below the Minecraft line so a test can open them.
 *
 * <p><b>This class exists because {@code ArcabuzItem} cannot be loaded in a bare JVM.</b> That is
 * not a hypothetical: {@code SwordItem} is a Minecraft class, so a test that so much as named
 * {@code ArcabuzItem.DAMAGE} would die at class-init with a {@code NoClassDefFoundError} that
 * reads like a broken test rather than a missing dependency. It is the same reason
 * {@code WeaponStats}' tables were extracted into {@code WeaponTables} and
 * {@code SlashParticleOptions}' arithmetic into {@code SlashTiming}, and both of those extractions
 * happened after the numbers they held were found to be wrong.</p>
 *
 * <p>So the item and the tier read this and hold no numbers of their own. One place a number is
 * written, and one place a test can read it.</p>
 *
 * <p>Pure on purpose: only {@link AttackTiming}, which is itself pure.</p>
 */
public final class ArcabuzStats {

    /**
     * A player's base ATTACK_SPEED, and the number {@link #SPEED_OFFSET} is measured from.
     *
     * <p>Four, verified in 1.21.1 and already written down by {@code WeaponFamily}, which is
     * where the thirty catalogue weapons get their offsets from. It is repeated here rather than
     * referenced because {@code WeaponFamily} is a catalogue of five families and this weapon is
     * deliberately not one of them; importing the constant would have said it is.</p>
     */
    public static final float PLAYER_BASE_ATTACK_SPEED = 4.0F;

    /**
     * The weapon's own damage, as {@code createAttributes} takes it.
     *
     * <p><b>Not a total, and the difference is one point.</b> Vanilla's
     * {@code SwordItem#createAttributes} adds {@code damage + tier.getAttackDamageBonus()} to the
     * attribute with {@code ADD_VALUE}, and the attribute's own base is <b>1.0</b>. So this
     * five is a six on the attribute, and {@link #ATTACK_DAMAGE} is that six written once.</p>
     *
     * <p>The same trap {@code CortesWeaponItem#attributesFor} documents from the other side of
     * the same call, for ATTACK_SPEED instead of damage: hand {@code createAttributes} a total and
     * every weapon comes out one point too strong, which is exactly what once made all thirty
     * swing at double tempo with a green build.</p>
     *
     * <p><b>Six is a deliberate answer and not "as high as possible".</b> It is a stone halberd's
     * poke and a copper ropera's is seven, which keeps the fastest thing in the game the best
     * thing in the game at hitting one target. The arcabuz's damage claim is about the blast, and
     * the blast is {@link com.cortes.combat.special.ArcabuzShot#SHOT_MULTIPLIER} times this.</p>
     */
    public static final float DAMAGE = 5.0F;

    /**
     * The ATTACK_SPEED argument: an <b>offset</b> from {@link #PLAYER_BASE_ATTACK_SPEED}, not a
     * total.
     *
     * <p>Minus 2.6 lands on {@link #ATTACK_SPEED} = 1.4, and
     * {@link #ATTACK_INTERVAL} is fourteen ticks. Not a sword's 1.6: this is a two block gun and
     * swinging it is the fallback, not the point.</p>
     */
    public static final float SPEED_OFFSET = -2.6F;

    /** The tempo the gun actually gets, in attacks per second. */
    public static final float ATTACK_SPEED = PLAYER_BASE_ATTACK_SPEED + SPEED_OFFSET;

    /**
     * The damage a player reads on {@code Attributes.ATTACK_DAMAGE} holding an arcabuz.
     *
     * <p>{@code 1.0 + DAMAGE + tier bonus}, and the tier bonus is zero because
     * {@code ArcabuzTier} is a class whose reason for existing is that it contributes nothing to
     * the damage. Written as the arithmetic rather than as a literal so that changing
     * {@link #DAMAGE} cannot leave this behind -- the "a number that outlives what justified it"
     * failure, in a field rather than in a constant.</p>
     */
    public static final float ATTACK_DAMAGE = 1.0F + DAMAGE + ArcabuzTierBonus.ZERO;

    /** How many swings fit between them, read through the same function the game reads. */
    public static final int ATTACK_INTERVAL = AttackTiming.ticksFor(ATTACK_SPEED);

    /** Durability. Two hundred and fifty, which is vanilla's iron number. */
    public static final int USES = 250;

    /**
     * Enchantment value: one.
     *
     * <p>Iron's, so an anvil roll on a gun is the roll you would have got on the barrel, and
     * below every metal tier in the catalogue on purpose. Nothing about the gun is improved by
     * Sharpness, and a weapon whose best enchantment is Sharpness has no reason to be
     * enchanted.</p>
     */
    public static final int ENCHANTMENT_VALUE = 1;

    private ArcabuzStats() {
    }

    /**
     * The damage this weapon's blast does to one target.
     *
     * <p>Delegated rather than written again, and the delegation is the point: the swing's damage
     * and the shot's damage are one number and one coefficient, so they cannot drift apart the
     * way two literals would. {@code ArcabuzBalanceTest} asserts the two against each other.</p>
     */
    public static float shotDamage() {
        return com.cortes.combat.special.ArcabuzShot.damageFor(ATTACK_DAMAGE);
    }

    /** The most this weapon can do in one blast, at its own damage. */
    public static float maximumShotDamage() {
        return com.cortes.combat.special.ArcabuzShot.maximumDamage(ATTACK_DAMAGE);
    }

    /**
     * The tier's damage bonus, as a number, for {@link #ATTACK_DAMAGE}'s arithmetic.
     *
     * <p>A one-member class so the zero has a name and the test can compare against it instead of
     * against a bare literal that could be changed without anybody noticing which one it was.</p>
     */
    public static final class ArcabuzTierBonus {
        public static final float ZERO = 0.0F;

        private ArcabuzTierBonus() {
        }
    }
}
