package com.cortes.combat.item.tooltip;

import com.cortes.combat.attack.DamageResolver;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.ConeSize;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.particle.SlashTiming;
import com.cortes.combat.state.StreakState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The seven numbers a weapon's tooltip shows, and the rules for which of them exist.
 *
 * <p><b>Pure, and that is the whole point.</b> Every input is a record or an enum from this
 * project and every output is a key and a string, so the questions that actually go wrong -- which
 * lines are printed, in what order, whether the sabre's charged damage is one cut or two, and
 * whether a block gets a tooltip at all -- are answerable in a bare JVM. The layer above turns
 * these into components and reads them off a stack.</p>
 *
 * <h2>Why the damage comes out of {@link DamageResolver} and not out of arithmetic here</h2>
 *
 * <p>Because this is the third time this project has been able to write a number a player reads
 * and have it quietly disagree with what the game does. {@code DamageResolver} is the formula
 * the swing actually runs, so calling it is what makes the tooltip a readout rather than a second
 * opinion. A line here that computed {@code damage * (1 + bonus)} would be green, correct today,
 * and wrong the first time somebody adds a weapon whose charge does something else.</p>
 *
 * <h2>Why a streak is passed as "none"</h2>
 *
 * <p>The warhammer's damage grows with consecutive hits, and a tooltip has no player attached to
 * it, so it has no streak to report. Printing the streakless number is the honest one: it is what
 * the weapon does on the first hit, and it is what the player is comparing against the others.
 * A number that assumed a streak the player may not be in would be the tooltip promising
 * something.</p>
 *
 * <h2>Why {@link WeaponProfile#NONE} gets nothing</h2>
 *
 * <p>A block, a stick, a bowl. It is held, it swings, and it reaches three blocks, and printing
 * "3 damage, medium size" on every dirt block in the inventory teaches the player that these
 * lines are wallpaper. That profile exists precisely because "if we do not recognise it, treat it
 * as the most common weapon" quietly turns the world into a weapon, and a tooltip is where that
 * mistake becomes visible on every single item.</p>
 */
public final class WeaponTooltip {

    /** Prefix for every key this class emits. */
    private static final String PREFIX = "tooltip.cortescombat.";

    private static final String DAMAGE = PREFIX + "damage";
    private static final String CHARGED_DAMAGE = PREFIX + "charged_damage";
    private static final String ATTACK_SPEED = PREFIX + "attack_speed";
    private static final String RANGE = PREFIX + "range";
    private static final String SIZE = PREFIX + "size.";
    private static final String DURABILITY = PREFIX + "durability";
    private static final String SPECIAL = PREFIX + "special.";

    /**
     * No charge and no special for {@code NONE}, and nothing at all. Declared as a constant so the
     * empty case is a value the class can return rather than a branch it has to re-derive.
     */
    private static final List<TooltipLine> NOTHING = List.of();

    private WeaponTooltip() {
    }

    /**
     * Every line this weapon shows, in the order it shows them.
     *
     * @param profile       which weapon this is; {@link WeaponProfile#NONE} returns nothing
     * @param attackDamage  what the item alone contributes to {@code ATTACK_DAMAGE}, which means
     *                      the player's base of 1.0 plus this item's own modifier and nothing else
     *                      -- no armour, no potions, no other hand
     * @param intervalTicks ticks between swings <em>after</em> the per-profile tempo, so this is
     *                      the number the cooldown bracket is actually counting
     * @param reach         the resolved cone, which a data component may have replaced
     * @param charge        the resolved charge, and the reason the charged line is sometimes absent
     * @param special       the resolved right button, and the reason that line is sometimes absent
     * @param maxDamage     the item's durability, or zero for something that cannot break
     */
    public static List<TooltipLine> linesFor(WeaponProfile profile,
                                             float attackDamage,
                                             int intervalTicks,
                                             ReachProfile reach,
                                             ChargeProfile charge,
                                             SpecialType special,
                                             int maxDamage) {

        if (profile == WeaponProfile.NONE || reach == null) {
            return NOTHING;
        }

        List<TooltipLine> lines = new ArrayList<>(7);

        lines.add(TooltipLine.of(DAMAGE, decimal(damageFor(profile, charge, attackDamage, 0.0F))));

        // Only for a weapon that charges. Printing "cargado" next to a ropera's damage would be a
        // line promising something the row says cannot happen, and the ropera and the dagger are
        // the two weapons in the set where a player most needs to know that.
        if (charge != null && charge.canCharge()) {
            lines.add(TooltipLine.of(CHARGED_DAMAGE,
                    decimal(chargedTotalFor(profile, charge, attackDamage))));
        }

        // Attacks per second rather than ticks, because that is what vanilla's own attribute is
        // and it is the unit two weapons can be compared in. The interval is derived from it, not
        // printed alongside it: two numbers for one fact is one more thing to be wrong.
        lines.add(TooltipLine.of(ATTACK_SPEED, decimal(attacksPerSecond(intervalTicks))));

        lines.add(TooltipLine.of(RANGE, decimal(reach.distance())));

        // The cone as a size, not as an angle. ConeSize's own comment has the derivation.
        //
        // A whole key per band rather than one format string with the band's id as its argument,
        // because a %s argument is rendered literally: "very_small" would appear on screen in
        // English. Six strings beat one string and a translation that is not applied.
        lines.add(TooltipLine.plain(SIZE + ConeSize.forAngle(reach.horizontalAngle()).id()));

        if (special != null && special.isPresent()) {
            lines.add(TooltipLine.plain(SPECIAL + special.id()));
        }

        // Zero means the item has no durability, which is most of the game. A tooltip that said
        // "0 durability" on a stick would be worse than saying nothing.
        if (maxDamage > 0) {
            lines.add(TooltipLine.of(DURABILITY, Integer.toString(maxDamage)));
        }

        return List.copyOf(lines);
    }

    /**
     * One swing at a given charge, per cut.
     *
     * <p>The game's own formula, at a given ratio, with no streak. The warhammer's escalation and
     * the sabre's second cut are both deliberately absent here: this is what the weapon does
     * before anything has stacked up.</p>
     */
    public static float damageFor(WeaponProfile profile, ChargeProfile charge,
                                  float attackDamage, float chargeRatio) {
        return DamageResolver.resolve(attackDamage, charge, profile, chargeRatio,
                StreakState.EMPTY).damage();
    }

    /**
     * A fully charged swing, <b>all of its cuts</b>.
     *
     * <p>The sabre lands two hits at charged damage and nothing else in the game lands more than
     * one, so per-cut damage is the wrong number for it by exactly a factor of two -- and it is
     * the wrong number in the way that reads as a bug: the tooltip would promise an iron sabre
     * ten and the swing would do twenty. The count comes from the same {@link SlashTiming} the
     * swing asks, so it is one number read twice rather than a second rule about which weapons
     * are special.</p>
     *
     * <p>Which is also why this is a method and not a {@code if (profile == SABRE) multiplier}:
     * the sabre is currently the only weapon that hits twice, and a rule that names it would be a
     * rule that has to be remembered for the next one.</p>
     */
    public static float chargedTotalFor(WeaponProfile profile, ChargeProfile charge,
                                        float attackDamage) {
        int cuts = SlashTiming.hitsAt(profile, charge, DamageResolver.FULL_CHARGE);
        return damageFor(profile, charge, attackDamage, DamageResolver.FULL_CHARGE) * cuts;
    }

    /** The inverse of the interval, which is what the game's own attack speed attribute means. */
    public static float attacksPerSecond(int intervalTicks) {
        if (intervalTicks <= 0) {
            // A zero or negative interval cannot happen through the game -- AttackTiming.scaleFor
            // clamps to at least one tick -- but this method is called from a tooltip, and a
            // division by zero there is an exception on the render thread rather than a wrong
            // number the player could report.
            return 0.0F;
        }
        return 20.0F / intervalTicks;
    }

    /**
     * One decimal, always, and never an exponent.
     *
     * <p>Fixed formatting rather than {@code String.valueOf} because the arithmetic above can land
     * on values like {@code 6.949999809265137}: float toString would print the whole thing, and a
     * damage number with fifteen decimals is not a number the player can read or we can defend.
     * {@link Locale#ROOT} because the tooltip language only supports {@code %s} and a
     * locale-sensitive separator would have to be chosen by hand anyway -- and this one is what
     * vanilla prints for its own attribute lines.</p>
     */
    static String decimal(float value) {
        if (Float.isNaN(value)) {
            return "0.0";
        }
        if (Float.isInfinite(value)) {
            return value > 0 ? "INF" : "-INF";
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}