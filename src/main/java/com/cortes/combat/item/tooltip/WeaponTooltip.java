package com.cortes.combat.item.tooltip;

import com.cortes.combat.attack.DamageResolver;
import com.cortes.combat.attack.Knockback;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.ConeSize;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponCategory;
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
     * The push, base and charged, exactly the way the damage has two lines.
     *
     * <p><b>Two lines and not one, because a tap and a charge are two different shoves.</b>
     * {@code Knockback.TAP_PUSH} makes a tap 0.55 of the weapon's push and a full charge all of it,
     * so a weapon whose tooltip showed one number would be describing a swing nobody can throw --
     * and the playtest asked for the pair by name, in the same breath as the damage's own two.</p>
     *
     * <p>Both come out of {@code Knockback#pushFor}, which is the function the swing runs. See its
     * javadoc for why that matters more here than anywhere else.</p>
     */
    private static final String KNOCKBACK = PREFIX + "knockback";
    private static final String CHARGED_KNOCKBACK = PREFIX + "charged_knockback";

    /**
     * How many things one swing can reach, for the weapons that reach more than one.
     *
     * <p><b>Only for the multi-target weapons, and that is the line's whole value.</b> Four weapons
     * in the catalogue hit everything in the cone -- the halberd's sweep, the maza and the
     * trident -- and for those "how many" is the number that decides whether a swing is worth its
     * cooldown. For the other twenty seven it is a one, and printing "Targets: 1" under every sword
     * is the wallpaper this tooltip exists not to be.</p>
     *
     * <p>The number is {@code WeaponTables.maxTargets} and not a copy: it is the same row the swing
     * reads, so retuning a weapon's reach moves the line with it.</p>
     */
    private static final String TARGETS = PREFIX + "targets";

    /**
     * The line that explains a weapon's own mechanic, and the reason it is a key per weapon.
     *
     * <p><b>Seven of the thirty one weapons do something a number cannot say.</b> The maza's
     * damage <em>is</em> a fall; the trident's right button is a throw; the sabre's is a parry that
     * costs five seconds. Those are sentences, and a tooltip made only of numbers would leave the
     * player to find them by pressing the button and watching.</p>
     *
     * <p><b>The prose is prose and the numbers inside it are not.</b> The maza's line carries its
     * ceiling and its height, read from {@code MaceFall}; the arcabuz's carries its reload and its
     * fuse, read from {@code ArcabuzState} and {@code ArcabuzStats}. Only the sentence around them
     * is written by hand -- and that is the one part of this tooltip that cannot be read off a
     * table, because it is the only part that is language rather than a fact.</p>
     */
    private static final String MECHANIC = PREFIX + "mechanic.";

    /** The arcabuz's two numeric lines, and they are the only ones no other weapon shares. */
    private static final String ARCABUZ_RELOAD = PREFIX + "arcabuz.reload";
    private static final String ARCABUZ_SHOT = PREFIX + "arcabuz.shot";

    /**
     * The blue category line, and it goes first.
     *
     * <p>First because that is where vanilla puts its own -- directly under the name, before the
     * blank line and the numbers -- and because it is the one line that frames all the others: a
     * player who reads "Arma corta" knows how to read the reach below it. See
     * {@link WeaponCategory} for the taxonomy.</p>
     */
    private static final String CATEGORY = PREFIX + "category.";

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
     * @param knockback     the resolved push, and the reason the knockback lines have two numbers
     * @param damageLeft    the durability still on the stack, or zero for something that cannot break
     * @param maxDamage     the item's durability, or zero for something that cannot break
     * @param hasShotCone   whether the right button has a cone of its own, which is what makes a
     *                      weapon ranged rather than short
     */
    public static List<TooltipLine> linesFor(WeaponProfile profile,
                                             float attackDamage,
                                             int intervalTicks,
                                             ReachProfile reach,
                                             ChargeProfile charge,
                                             SpecialType special,
                                             KnockbackProfile knockback,
                                             int maxTargets,
                                             int damageLeft,
                                             int maxDamage,
                                             boolean hasShotCone) {

        if (profile == WeaponProfile.NONE || reach == null) {
            return NOTHING;
        }

        List<TooltipLine> lines = new ArrayList<>(10);

        // **The category first, in vanilla's own blue.** See WeaponCategory for why it exists and
        // why it is classified by capacity: it frames every line under it, so it has to be read
        // first -- "Arma corta" is what tells the player that a two point two five block reach is
        // the weapon working as intended and not a typo.
        lines.add(TooltipLine.plain(
                CATEGORY + WeaponCategory.forWeapon(profile, reach, intervalTicks, hasShotCone).id(),
                TooltipColor.CATEGORY));

        lines.add(TooltipLine.of(DAMAGE, decimal(damageFor(profile, charge, attackDamage, 0.0F)),
                TooltipColor.DAMAGE));

        // Only for a weapon that charges. Printing "cargado" next to a ropera's damage would be a
        // line promising something the row says cannot happen, and the ropera and the dagger are
        // the two weapons in the set where a player most needs to know that.
        if (charge != null && charge.canCharge()) {
            lines.add(TooltipLine.of(CHARGED_DAMAGE,
                    decimal(chargedTotalFor(profile, charge, attackDamage)),
                    TooltipColor.CHARGED));
        }

        // Attacks per second rather than ticks, because that is what vanilla's own attribute is
        // and it is the unit two weapons can be compared in. The interval is derived from it, not
        // printed alongside it: two numbers for one fact is one more thing to be wrong.
        lines.add(TooltipLine.of(ATTACK_SPEED, decimal(attacksPerSecond(intervalTicks)),
                TooltipColor.SPEED));

        lines.add(TooltipLine.of(RANGE, decimal(reach.distance()), TooltipColor.RANGE));

        // The cone as a size, not as an angle. ConeSize's own comment has the derivation.
        //
        // A whole key per band rather than one format string with the band's id as its argument,
        // because a %s argument is rendered literally: "very_small" would appear on screen in
        // English. Six strings beat one string and a translation that is not applied.
        //
        // **And the band's own text says "radio"**, which it did not before: "Medio" alone read as
        // a size of nothing in particular, and the playtest's note was that the line named no
        // quantity. It is a radius, and now it says so.
        lines.add(TooltipLine.plain(SIZE + ConeSize.forAngle(reach.horizontalAngle()).id(),
                TooltipColor.SIZE));

        // **The push, base and charged, exactly like the damage above it.** A tap shoves at
        // `TAP_PUSH` of the profile and a full charge at all of it, so one number would describe a
        // swing nobody can throw -- and the shovel's whole reason for existing is that its push is
        // the biggest in the game, which is a fact a single averaged number would hide.
        if (knockback != null) {
            lines.add(TooltipLine.of(KNOCKBACK,
                    decimal(knockback.clampedHorizontal() * Knockback.pushFor(0.0F)),
                    TooltipColor.SPECIAL));
            lines.add(TooltipLine.of(CHARGED_KNOCKBACK,
                    decimal(knockback.clampedHorizontal() * Knockback.pushFor(1.0F)),
                    TooltipColor.SPECIAL));
        }

        if (special != null && special.isPresent()) {
            lines.add(TooltipLine.plain(SPECIAL + special.id(), TooltipColor.SPECIAL));
        }

        // **How many things the swing reaches, and only when it is more than one.** See TARGETS:
        // "Targets: 1" under a sword is the wallpaper this tooltip exists not to be, and for the
        // four weapons that do sweep it is the number that decides whether the swing is worth its
        // cooldown.
        if (maxTargets > 1) {
            lines.add(TooltipLine.of(TARGETS, Integer.toString(maxTargets), TooltipColor.SPECIAL));
        }

        // Zero means the item has no durability, which is most of the game. A tooltip that said
        // "0 durability" on a stick would be worse than saying nothing.
        //
        // **And it is a counter now, not the maximum.** The playtest asked for "DUR. ACTUAL/Max."
        // by name, and the reason is that a number which never changes is not a stat: a sword at
        // 190/250 and a sword at 4/250 printed the same line, and the one moment the number matters
        // is the moment it is nearly gone. The colour turns red on the way down for the same reason.
        if (maxDamage > 0) {
            lines.add(TooltipLine.of(DURABILITY, damageLeft + "/" + maxDamage,
                    durabilityColor(damageLeft, maxDamage)));
        }

        // **The weapon's own mechanic, for the seven that have one.** Last, because it is the line
        // that explains everything above it rather than a stat of its own -- and in NOTE's dim grey
        // for the same reason: it is an aside the player reads once, not a number they compare.
        lines.addAll(mechanicLines(profile));

        return List.copyOf(lines);
    }

    /**
     * The mechanic a weapon explains, with the numbers read from the code that runs it.
     *
     * <p>Empty for the twenty four weapons whose whole story is the numbers above -- a sword is a
     * sword, and a line saying so would be a line the player learns to skip.</p>
     *
     * <p><b>Every number here comes from the class that owns it</b>: {@code MaceFall} for the
     * maza's ceiling and height, {@code ParryState} for the sabre's window and cooldown,
     * {@code StreakState} for the warhammer's, {@code ArcabuzState} and {@code ArcabuzStats} for
     * the gun's. Only the sentence around them is written by hand, and it is the only part of this
     * tooltip that could not be read off a table -- it is language, not a fact.</p>
     */
    static List<TooltipLine> mechanicLines(WeaponProfile profile) {
        return switch (profile) {
            // The fall, and both numbers are MaceFall's own: the ceiling the damage stops at and
            // the height it takes to get there.
            case MACE -> List.of(TooltipLine.of(MECHANIC + "mace",
                    decimal(com.cortes.combat.weapon.MaceFall.MAX_DAMAGE),
                    decimal(com.cortes.combat.weapon.MaceFall.CAP_FALL),
                    TooltipColor.NOTE));

            // The throw, which is the one thing about a trident that a reach cannot describe.
            case TRIDENT -> List.of(TooltipLine.plain(MECHANIC + "trident", TooltipColor.NOTE));

            // The parry, and the two numbers are ParryState's own window and cooldown, in seconds.
            case SABRE -> List.of(TooltipLine.of(MECHANIC + "sabre",
                    decimal(com.cortes.combat.state.ParryState.WINDOW_TICKS / 20.0F),
                    decimal(com.cortes.combat.state.ParryState.COOLDOWN_TICKS / 20.0F),
                    TooltipColor.NOTE));

            case HALBERD -> List.of(TooltipLine.plain(MECHANIC + "halberd", TooltipColor.NOTE));

            case ROPERA -> List.of(TooltipLine.plain(MECHANIC + "ropera", TooltipColor.NOTE));

            // The escalation, and the window it lives in, from StreakState.
            case WARHAMMER -> List.of(TooltipLine.of(MECHANIC + "warhammer",
                    decimal(com.cortes.combat.state.StreakState.WINDOW_TICKS / 20.0F),
                    TooltipColor.NOTE));

            // **The arcabuz, and its numbers are the only ones in the tooltip in seconds.** A
            // player counts a reload in seconds and a designer writes it in ticks; both are the
            // same number, and this is the side the player reads. The shot's damage is the ceiling
            // rather than the ordinary one, because the gun's claim is about the blast.
            case ARCABUZ -> List.of(
                    TooltipLine.of(ARCABUZ_RELOAD,
                            decimal(com.cortes.combat.state.ArcabuzState.RELOAD_TICKS / 20.0F),
                            TooltipColor.NOTE),
                    TooltipLine.of(ARCABUZ_SHOT,
                            decimal(com.cortes.combat.item.ArcabuzStats.maximumShotDamage()),
                            TooltipColor.NOTE),
                    TooltipLine.plain(MECHANIC + "arcabuz", TooltipColor.NOTE));

            default -> List.of();
        };
    }

    /**
     * Green while the weapon has life left, red when it is nearly out.
     *
     * <p>A fifth, which is where vanilla's own durability bar turns from green to red. Copied
     * rather than chosen so that the line and the bar under the hotbar agree -- two warnings for one
     * fact that disagree is worse than one.</p>
     */
    private static TooltipColor durabilityColor(int damageLeft, int maxDamage) {
        return damageLeft * 5 <= maxDamage ? TooltipColor.DURABILITY_LOW : TooltipColor.DURABILITY;
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