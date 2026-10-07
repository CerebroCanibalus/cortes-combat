package com.cortes.combat.weapon;

import com.cortes.combat.core.weapon.WeaponProfile;

/**
 * The maza's damage, which is the fall.
 *
 * <h2>Vanilla's version, read</h2>
 *
 * <p>Verified in 1.21.1, {@code MaceItem}: {@code canSmashAttack} is
 * {@code fallDistance > 1.5F &amp;&amp; !isFallFlying()}, and
 * {@code getAttackDamageBonus} reads {@code livingentity.fallDistance} and returns</p>
 *
 * <ul>
 *   <li>{@code fall <= 3}: {@code 4 * fall}</li>
 *   <li>{@code fall <= 8}: {@code 12 + 2 * (fall - 3)}</li>
 *   <li>above: {@code 22 + fall - 8} &mdash; <b>and that is not capped</b></li>
 * </ul>
 *
 * <p>so the bonus is zero without a fall and 22 at eight blocks, and keeps going after that. Falling
 * forty blocks is roughly fifty-four bonus damage.</p>
 *
 * <h2>And why none of that runs here</h2>
 *
 * <p><b>{@code MaceItem} appears nowhere in this project.</b> The whole vanilla attack flow is
 * replaced &mdash; {@code CombatEngine} resolves the hit and {@code DamageResolver} computes it &mdash;
 * so {@code getAttackDamageBonus}, {@code hurtEnemy} and {@code postHurtEnemy} are never called. The
 * fall was a number on a class nobody runs.</p>
 *
 * <p>Which is also why the cap did not need patching: there was no uncapped curve to cap. There was
 * no curve at all, and this is the first version of it.</p>
 *
 * <h2>Why it is scaled and not written fresh</h2>
 *
 * <p>Because vanilla's three segments are a <em>shape</em> someone tuned &mdash; gentle to three
 * blocks, steeper to eight, then a long tail &mdash; and only the magnitude is wrong for this game.
 * A fresh linear ramp would be easier to write and would throw away the one piece of knowledge in
 * the mechanic. So the shape is kept and the size is changed: {@link #VANILLA_CEILING} is what
 * vanilla reaches, and the bonus here reaches {@link #BONUS_CEILING} instead.</p>
 */
public final class MaceFall {

    /** Below this, the maza is a heavy club and nothing else. Vanilla's own threshold. */
    public static final float THRESHOLD = 1.5F;

    /** Above this, vanilla switches to its long tail. */
    private static final float KNEE = 3.0F;

    /** Where vanilla's tail starts. */
    private static final float TAIL = 8.0F;

    /** What vanilla's curve is worth at its knee: {@code 22} at eight blocks. */
    private static final float VANILLA_CEILING = 22.0F;

    /**
     * What the fall is worth here, and it is also what makes the weapon's top damage.
     *
     * <p><b>Thirty five, which with {@link #BASE_DAMAGE} makes {@link #MAX_DAMAGE} fifty.</b> It was
     * twelve, and the playtest asked for the top of the weapon to move: the maza is the only thing
     * in the game that pays for height, and a bonus that stops at twelve stops feeling like one
     * past the first cliff.</p>
     */
    public static final float BONUS_CEILING = 35.0F;

    /** The maza's damage with no fall at all. It was ten; the playtest moved it up five. */
    public static final float BASE_DAMAGE = 15.0F;

    /** The most a maza hit can ever do. */
    public static final float MAX_DAMAGE = BASE_DAMAGE + BONUS_CEILING;

    /**
     * The maza's one and only weapon, for free of reference.
     *
     * <p><b>It scales the borrowed curve and not the tail, and that split is what lets the ceiling
     * move without the low end moving with it.</b> It used to be {@code BONUS_CEILING /
     * VANILLA_CEILING}, which welds the two together: raising the ceiling then raises every point
     * of the curve by the same factor, the knee stays where vanilla's is, and the cap stays at
     * eight blocks -- <em>the opposite of raising the height</em>. Fixed at what makes vanilla's own
     * curve fit the first eight blocks, so the low end is untouched and the tail takes over above
     * them.</p>
     */
    public static final float SCALE = 12.0F / VANILLA_CEILING;

    /** What the fall is worth at the knee, where the borrowed curve ends: twelve. */
    private static final float KNEE_BONUS = VANILLA_CEILING * SCALE;

    /** How fast the tail climbs past the knee. Vanilla's own slope, one point per block. */
    private static final float TAIL_SLOPE = 1.0F;

    /**
     * The fall that earns the whole bonus: <b>31 blocks</b>, derived and not chosen.
     *
     * <p>{@code 8 + (35 - 12) / 1}. The playtest asked for the top of the weapon to reach fifty
     * damage <em>and</em> for the height to move with it, and this is the height that falls out of
     * the curve rather than a round number somebody liked: the tail starts at the knee with twelve
     * and climbs a point a block, so it needs thirteen more blocks to reach thirty five.</p>
     *
     * <p><b>The knob is {@link #TAIL_SLOPE}.</b> Doubling it halves the height; the shape of the
     * low end never moves either way, which is the point of splitting them.</p>
     */
    public static final float CAP_FALL = TAIL + (BONUS_CEILING - KNEE_BONUS) / TAIL_SLOPE;

    private MaceFall() {
    }

    /**
     * How much a fall is worth, capped.
     *
     * @param fallDistance blocks fallen, as the game counts them
     * @return the bonus, never above {@link #BONUS_CEILING} and never below zero
     */
    public static float bonusFor(float fallDistance) {
        if (!(fallDistance > THRESHOLD)) {
            return 0.0F;
        }
        // Vanilla's own three segments, scaled, up to the knee.
        if (fallDistance <= TAIL) {
            return vanillaShape(fallDistance) * SCALE;
        }
        // **And then a tail of our own, because vanilla's is unreachable under a ceiling this
        // high.** Vanilla's tail is `22 + fall - 8`; scaled by 0.5455 it would need fifty blocks to
        // reach thirty five, so the borrowed slope is not the one that gets there. Ours climbs one
        // point a block from the knee, which is the same slope at a third of the distance.
        return Math.min(BONUS_CEILING, KNEE_BONUS + (fallDistance - TAIL) * TAIL_SLOPE);
    }

    /**
     * Vanilla's three segments, unchanged, and then scaled.
     *
     * <p>Written as three cases rather than a formula because <b>that is the shape being
     * borrowed</b>, and a smooth curve fitted through three points stops being the thing that was
     * tuned and starts being this project's guess at it.</p>
     */
    private static float vanillaShape(float fall) {
        if (fall <= KNEE) {
            return 4.0F * fall;
        }
        if (fall <= TAIL) {
            return 12.0F + 2.0F * (fall - KNEE);
        }
        return VANILLA_CEILING + fall - TAIL;
    }

    /**
     * The damage one maza strike does, which is the base plus whatever the fall paid.
     *
     * @param fallDistance blocks fallen
     * @return never below {@link #BASE_DAMAGE}, never above {@link #MAX_DAMAGE}
     */
    public static float damageFor(float fallDistance) {
        return BASE_DAMAGE + bonusFor(fallDistance);
    }

    /**
     * How far into the fall the wielder is, 0..1, for the viewmodel's meter.
     *
     * <p><b>The fall's own ratio and not the damage's</b>, which is the opposite of what this was,
     * and the playtest is what corrected it. The damage's ratio is zero until one and a half
     * blocks, because that is where a smash begins -- and a meter that sits at rest for the whole
     * of an ordinary jump is a meter the player never sees move, which is exactly how it was
     * reported: "no aparece la animacion mientras se carga en el aire". The meter's question is
     * <em>how much of the fall have I got</em>; the threshold is a rule about a hit. Two questions,
     * and this one has to answer the first.</p>
     *
     * <p>It reaches full at {@link #CAP_FALL}, so the cock and the damage top out together and the
     * player is measuring the thing the hit will pay.</p>
     *
     * @param fallDistance blocks fallen, as the game counts them
     * @param airborne     whether the wielder is genuinely in the air, and not merely off the
     *                     ground: swimming is not falling. See {@code MaceAir#airborne}
     * @return 0 for every weapon but the maza, and 0..1 for it
     */
    public static float progressFor(WeaponProfile profile, float fallDistance, boolean airborne) {
        if (!reads(profile) || !airborne || !(fallDistance > 0.0F)) {
            return 0.0F;
        }
        return Math.min(1.0F, fallDistance / CAP_FALL);
    }

    /**
     * Whether this weapon reads a fall at all.
     *
     * <p>Asked of the profile and not of a set, because the answer is a property of the weapon and
     * the profile is where the rest of its properties live. One weapon answers yes.</p>
     */
    public static boolean reads(WeaponProfile profile) {
        return profile == WeaponProfile.MACE;
    }
}
