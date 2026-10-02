package com.cortes.combat.item;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Every default number a weapon has, in one place, and nowhere else.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>The tables used to live in {@code WeaponStats}, which reads data components off an
 * {@code ItemStack} and derives a profile from vanilla tags. That class cannot be loaded in a bare
 * JVM, and <b>a table nobody can load is a table nobody can check</b>. So the numbers went in
 * unchecked, and two of them were wrong in ways that only a player would ever find:</p>
 *
 * <ul>
 *   <li>{@link WeaponProfile#ROPERA} had no row in the special table. A loop at the bottom of the
 *       static block filled the gap with {@link SpecialType#NONE} via {@code putIfAbsent}, which is
 *       silent, so the ropera arrived with no right button at all -- and the family enum, one
 *       field away, said {@code LUNGE} the whole time.</li>
 *   <li>There were two copies of the special, one per class, and only one of them was read. A test
 *       asserted the copy nobody read and stayed green for the whole life of the bug.</li>
 * </ul>
 *
 * <p>So there is one table, it is here, and it is <b>pure</b>: the project's own enums and records,
 * no Minecraft, no registries, no item stacks. That is not tidiness, it is the whole point -- a
 * class a test can open is a class a test can assert is complete, which is what
 * {@code WeaponTablesTest} does.</p>
 *
 * <h2>Completeness is the invariant</h2>
 *
 * <p>Every table has a row for <b>every</b> {@link WeaponProfile}, including the ones that should be
 * inert. An empty row is a decision somebody wrote down; a missing row is a bug that reads as a
 * default. {@code WeaponProfile.NONE} having no special is a decision. The ropera having no special
 * was an accident wearing the same clothes.</p>
 *
 * <h2>What still overrides these</h2>
 *
 * <p>Nothing here is a hardcode in the gameplay sense: every table is a <i>default</i>, and a data
 * component on the item replaces it. {@code WeaponStats} is the four-layer resolver and it reads
 * this class as its bottom layer.</p>
 */
public final class WeaponTables {

    private static final Map<WeaponProfile, ReachProfile> REACH = new HashMap<>();
    private static final Map<WeaponProfile, ChargeProfile> CHARGE = new HashMap<>();
    private static final Map<WeaponProfile, KnockbackProfile> KNOCKBACK = new HashMap<>();
    private static final Map<WeaponProfile, SpecialType> SPECIAL = new HashMap<>();

    private WeaponTables() {
    }

    // ---------------------------------------------------------------- reach --
    //
    // Horizontal and vertical angles are independent, and the vertical is deliberately tighter so
    // a swing does not connect with something at the attacker's feet or something flying overhead.

    static {
        // Not a weapon: a block, a stick, anything held that is in no tool tag. It swings and
        // reaches about as far as a punch, but has no charge and no viewmodel. The reach is
        // vanilla's 3.0 rather than bare hands' 2.5 because swinging a block is a heavier gesture
        // than swinging a fist.
        REACH.put(WeaponProfile.NONE, new ReachProfile(3.0F, 25.0F, 20.0F));
        // Bare hands are the shortest reach in the game, below even the dagger's 2.4. A punch that
        // reaches as far as a sword is not a punch, it is a worse sword. Short, fast and nearly
        // harmless is the whole character: the speed is what makes it a real option with nothing
        // else, and it is the only thing that makes it one.
        REACH.put(WeaponProfile.UNARMED, new ReachProfile(2.5F, 28.0F, 22.0F));
        // 3.2 -> 3.6. The sword outranges an axe (3.0), which inverts vanilla's relationship
        // between the two and is deliberate: the sword is the weapon you are expected to have, so
        // it should be the one that reaches far enough to matter.
        REACH.put(WeaponProfile.SWORD, new ReachProfile(3.6F, 35.0F, 30.0F));
        REACH.put(WeaponProfile.AXE, new ReachProfile(3.0F, 45.0F, 35.0F));
        REACH.put(WeaponProfile.PICKAXE, new ReachProfile(3.0F, 35.0F, 30.0F));
        REACH.put(WeaponProfile.SHOVEL, new ReachProfile(3.0F, 35.0F, 30.0F));
        REACH.put(WeaponProfile.MACE, new ReachProfile(3.4F, 30.0F, 40.0F));
        REACH.put(WeaponProfile.TRIDENT, new ReachProfile(4.0F, 15.0F, 20.0F));

        // Ropera: the narrowest cone in the game, and it buys that back with speed. Twelve by
        // fifteen is small enough that the player has to mean it -- and there is deliberately no
        // smaller cone inside it. The precision bonus that used to live there asked the player to
        // aim inside a cone inside the one that was already asking them to aim.
        //
        // Three point five, and it was three point four. The lunge was nerfed for being overpowered
        // and the first attempt took a tenth off this number rather than the lunge's own cube,
        // which is the axis the player actually reads as the weapon's reach. A tenth of a block
        // gives it back, and stops it short of the sword's three point six on purpose: the sword is
        // the weapon the design assumes you have, and the ropera being the shortest-ranged of the
        // committed weapons is the reason its speed matters.
        REACH.put(WeaponProfile.ROPERA, new ReachProfile(3.5F, 12.0F, 15.0F));
        // Dagger: tiny range, wide cone.
        REACH.put(WeaponProfile.DAGGER, new ReachProfile(2.4F, 45.0F, 30.0F));
        // Halberd: widest and longest.
        REACH.put(WeaponProfile.HALBERD, new ReachProfile(4.2F, 55.0F, 35.0F));
        // 2.6 -> 2.9 and 40/40 -> 30/30. The playtest read the warhammer as a weapon that could
        // never reach anything, and it was right for the wrong reason: the cone was wide enough
        // that at its own range the useful part of it was a wall. Moving the reach out and the
        // angles in is the trade the sabre makes in the other direction.
        REACH.put(WeaponProfile.WARHAMMER, new ReachProfile(2.9F, 30.0F, 30.0F));
        // Sabre: the widest cone in the game, shortest range. It pays for that cone with a parry.
        REACH.put(WeaponProfile.SABRE, new ReachProfile(2.5F, 75.0F, 25.0F));
    }

    // --------------------------------------------------------------- charge --
    //
    // Full charge is a BONUS on top of the base and never a penalty: the floor is forced in code,
    // not trusted to the number, because a config override can write any bonus at all.

    static {
        // The fists are the fastest thing in the game -- four ticks between swings against a
        // sword's twelve -- and they were taking exactly as long to charge as a sword. The
        // capability to vary this per weapon already existed. Nobody had set it.
        CHARGE.put(WeaponProfile.NONE, ChargeProfile.NONE);
        CHARGE.put(WeaponProfile.UNARMED, new ChargeProfile(true, 0.3F, 12));
        CHARGE.put(WeaponProfile.SWORD, new ChargeProfile(true, 0.5F, 25));
        CHARGE.put(WeaponProfile.AXE, new ChargeProfile(true, 0.6F, 40));
        CHARGE.put(WeaponProfile.PICKAXE, new ChargeProfile(true, 0.4F, 33));
        CHARGE.put(WeaponProfile.SHOVEL, new ChargeProfile(true, 0.4F, 33));
        CHARGE.put(WeaponProfile.MACE, new ChargeProfile(true, 0.7F, 40));
        CHARGE.put(WeaponProfile.TRIDENT, new ChargeProfile(true, 0.5F, 25));
        // The ropera and the dagger do not charge. Neither of them is missing a charge profile;
        // it is a deliberate absence, and the system queries the component rather than branching.
        CHARGE.put(WeaponProfile.ROPERA, ChargeProfile.NONE);
        CHARGE.put(WeaponProfile.DAGGER, ChargeProfile.NONE);
        CHARGE.put(WeaponProfile.HALBERD, new ChargeProfile(true, 0.6F, 36));
        CHARGE.put(WeaponProfile.WARHAMMER, new ChargeProfile(true, 0.6F, 32));
        // The sabre's full charge lands TWICE and each cut is at charged damage. The only weapon in the
        // game that does both.
        //
        // **The bonus is one, and it has to be.** It was zero for two rounds, on the reasoning that
        // "doble" means a second cut rather than a heavier one -- and the consequence was that
        // charged damage and base damage became the same number, so the charge did nothing at all
        // and the sabre's window was twenty ticks of waiting for a second identical swing. The
        // playtest named it: "the double hit is using the weapon's BASE damage, not the damage when
        // charged, which would be the logical thing".
        //
        // So each of the two cuts carries the full multiplier. A wooden sabre lands two cuts of six
        // for twelve, and an iron one lands two cuts of ten for twenty -- which kills a husk in one
        // fully charged swing. That is the weapon's whole identity: the worst reach in the game,
        // bought back by being the only thing in the set that can kill in a single committed hit.
        CHARGE.put(WeaponProfile.SABRE, new ChargeProfile(true, 1.0F, 20));
    }

    // ------------------------------------------------------------- knockback --
    //
    // Horizontal is what a hit actually is: a shove. Vertical is a separate, much smaller number,
    // because scaling the two together is what made every hit feel like a trebuchet.

    static {
        KNOCKBACK.put(WeaponProfile.NONE, new KnockbackProfile(0.12F, 0.02F));
        KNOCKBACK.put(WeaponProfile.UNARMED, new KnockbackProfile(0.10F, 0.02F));
        // 0.28/0.05 -> 0.55/0.10. The playtest said the sword's knockback was "too small for a
        // charge" and it was, by a factor of two. The vertical stays far under the horizontal on
        // purpose: nothing about hitting someone with a sword should put them in the air.
        KNOCKBACK.put(WeaponProfile.SWORD, new KnockbackProfile(0.55F, 0.10F));
        KNOCKBACK.put(WeaponProfile.AXE, new KnockbackProfile(0.34F, 0.08F));
        KNOCKBACK.put(WeaponProfile.PICKAXE, new KnockbackProfile(0.22F, 0.05F));
        KNOCKBACK.put(WeaponProfile.SHOVEL, new KnockbackProfile(0.22F, 0.05F));
        KNOCKBACK.put(WeaponProfile.MACE, new KnockbackProfile(0.30F, 0.10F));
        KNOCKBACK.put(WeaponProfile.TRIDENT, new KnockbackProfile(0.30F, 0.12F));
        // The ropera is the narrow one: it moves you out of the way, it does not move them.
        KNOCKBACK.put(WeaponProfile.ROPERA, new KnockbackProfile(0.16F, 0.03F));
        KNOCKBACK.put(WeaponProfile.DAGGER, new KnockbackProfile(0.14F, 0.03F));
        // The halberd is the widest and the heaviest at the tip.
        KNOCKBACK.put(WeaponProfile.HALBERD, new KnockbackProfile(0.45F, 0.14F));
        // The warhammer's whole character is that it lifts.
        KNOCKBACK.put(WeaponProfile.WARHAMMER, new KnockbackProfile(0.55F, 0.30F));
        // The sabre is fast and light: a lot of push for very little lift.
        KNOCKBACK.put(WeaponProfile.SABRE, new KnockbackProfile(0.38F, 0.06F));
    }

    // --------------------------------------------------------------- special --
    //
    // The one table that used to exist twice. There is now exactly one, and it is this one.
    //
    // The five vanilla profiles keep a special on paper, and it is worth being honest about why:
    // a vanilla item has no way to carry our component, so those rows cannot be reached by the
    // game today. They are here as the declaration of intent -- "the axe is supposed to have a
    // slam" -- rather than as working code, and the day someone wires the component they are
    // already written. None of them may block: the right button is the guard's first, and a
    // special that stole it from a weapon that could block would be a special that changed
    // behaviour by what was in the other hand.

    static {
        SPECIAL.put(WeaponProfile.NONE, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.UNARMED, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.SWORD, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.AXE, SpecialType.SLAM);
        SPECIAL.put(WeaponProfile.PICKAXE, SpecialType.LUNGE);
        SPECIAL.put(WeaponProfile.SHOVEL, SpecialType.SWEEP);
        SPECIAL.put(WeaponProfile.MACE, SpecialType.THROW);
        SPECIAL.put(WeaponProfile.TRIDENT, SpecialType.THROW);
        // The three families of the mod that answer the right button.
        SPECIAL.put(WeaponProfile.ROPERA, SpecialType.LUNGE);
        SPECIAL.put(WeaponProfile.HALBERD, SpecialType.MULTI_HIT);
        SPECIAL.put(WeaponProfile.SABRE, SpecialType.PARRY);
        // And the two that deliberately do not: a dagger that lunges is a ropera that is worse, and
        // a warhammer with a right button has no commitment left to make.
        SPECIAL.put(WeaponProfile.DAGGER, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.WARHAMMER, SpecialType.NONE);
    }

    // ----------------------------------------------------------- the lookups --

    public static ReachProfile reach(WeaponProfile profile) {
        return REACH.get(profile);
    }

    public static ChargeProfile charge(WeaponProfile profile) {
        return CHARGE.get(profile);
    }

    public static KnockbackProfile knockback(WeaponProfile profile) {
        return KNOCKBACK.get(profile);
    }

    public static SpecialType special(WeaponProfile profile) {
        return SPECIAL.get(profile);
    }

    /**
     * The reach table, read-only.
     *
     * <p>Unmodifiable on purpose. A map a caller can write to is a second copy of this table that
     * nobody else can see, and a disagreement between two copies is exactly what {@code WeaponStats}
     * was rewritten to stop.</p>
     */
    public static Map<WeaponProfile, ReachProfile> reachTable() {
        return Collections.unmodifiableMap(REACH);
    }
}
