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

    /**
     * The cone a weapon's <em>right button</em> hits with, which is {@link ReachProfile#NONE} for
     * every weapon but the arcabuz.
     *
     * <p>A separate table and not a field on {@link ReachProfile}, because the two cones answer two
     * different questions and a melee profile carrying a "and also this" is a profile whose name no
     * longer says what it is. {@link WeaponStats#reach()} is what the left button hits with;
     * {@link WeaponStats#shotReach()} is what the right button hits with.</p>
     */
    private static final Map<WeaponProfile, ReachProfile> SHOT_REACH = new HashMap<>();

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
        // The axe: longer than it is wide, which is the opposite of what an axe is for.
//
// Three point four of reach against thirty five by twenty five. It was three of reach and
// forty five by thirty five, i.e. short and generous -- the second best cone in the game
// behind the sabre's seventy five, on a weapon whose whole argument is that it hits
// hard. What was asked for was "rango bueno, radio mediocre", and those are two
// different axes: the axe keeps the reach to close a distance the ropera cannot and
// gives up the width that lets it hit four things at once.
//
// The vertical at twenty five is the tightest in the game after the ropera. An axe
// lifts things and a wide vertical would make it a thing that hits everything in
// front of it at once, which is the halberd.
REACH.put(WeaponProfile.AXE, new ReachProfile(3.4F, 35.0F, 25.0F));
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
        // Arcabuz: the **bayonet**, and it is the melee cone, not the gun's.
        //
        // **Two point six and forty six by thirty five, and it is short on purpose.** The bayonet is
        // a stabbing weapon on the end of a two block gun: it reaches about as far as a dagger and
        // it has to be *short*, because a musket whose point-and-click also reaches four blocks is
        // a weapon that does two unrelated jobs and charges for neither. The radius is generous --
        // forty six degrees, wider than the sword's thirty five and only the dagger, the sabre and
        // the halberd are wider -- so the poke is the forgiving weapon it should be.
        //
        // **Forty six and not fifty, and that is a real constraint rather than taste.**
        // {@code ConeSize} draws its band edges at the midpoints of the gaps in this table, and
        // fifty is one of them. An angle that sits exactly on an edge puts the weapon in whichever
        // band the comparison picks, and a knife-edge number is a number that flips the tooltip
        // the next time somebody retunes anything next to it.
        //
        // **The four by forty by twenty five this row used to hold is now the gun's, not the
        // bayonet's.** It was one cone doing both jobs, and it was wrong for both: a left click that
        // could hit something four blocks away was a bayonet with a rifle's reach, and the shot
        // inherited a cone that belonged to a swing. See SHOT_REACH below for where those numbers
        // went.
        REACH.put(WeaponProfile.ARCABUZ, new ReachProfile(2.6F, 46.0F, 35.0F));
    }

    // --------------------------------------------------------------- shot reach --
    //
    // **The second cone, and only one weapon in the game has one.**
    //
    // The arcabuz hits with its bayonet on the left button and with its barrel on the right, and
    // those are two different weapons doing two different things. Sharing one cone between them
    // could not work: the numbers that make a stab forgiving make a shot useless, and the numbers
    // that make a shot reach across a room make a stab silly. So the gun's cone is its own row and
    // every other weapon carries ReachProfile.NONE.
    //
    // It is a table with a row for every profile rather than a nullable map because an absent row
    // reads as an accident and this project has been bitten by exactly that: the ropera had no row
    // in the special table and arrived with no right button at all, and the family enum one field
    // away said LUNGE the whole time. NONE is a decision somebody wrote down.

    static {
        for (WeaponProfile profile : WeaponProfile.values()) {
            SHOT_REACH.put(profile, ReachProfile.NONE);
        }

        // **Six and a half blocks at thirty two by twenty four.**
        //
        // Range: longer than anything else in the game, and it has to be. The halberd is the
        // longest thing anyone holds at four point two, and a gun whose blast reaches exactly as
        // far as a polearm is a polearm that costs a firestone.
        //
        // Radius: **thirty two, which is narrower than the melee cone the left click uses.** That
        // is the part worth reading twice. A blast that scatters wide is a shotgun, and a shotgun
        // that hits everything in front of you is the halberd with a worse reload. Thirty two at
        // six and a half blocks is still eleven metres across at the target, which is a room --
        // and it is aimed, so what the player points at is what the gun is for.
        //
        // The vertical stays tighter than the horizontal for the same reason it always has: you are
        // looking down a barrel, not swinging an arc, and a wide vertical would make the gun hit
        // the floor and the ceiling rather than what is in front of it.
        SHOT_REACH.put(WeaponProfile.ARCABUZ, new ReachProfile(6.5F, 32.0F, 24.0F));
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
        // The axe: the slowest charge in the game, and the biggest thing it buys.
//
// Seventy ticks is three and a half seconds. The sabre is twenty and the warhammer
// thirty two, so this is more than twice the sabre's and the sabre is meant to be the
// weapon with the heaviest commitment on the button.
//
// **The trade is the whole weapon, and getting it wrong makes the button a trap.**
//
// Sixty ticks is three seconds, three times the sabre's twenty and nearly twice the
// warhammer's thirty two, so the axe is the slowest charge in the game by a wide
// margin.
//
// **The bonus is two, and it is two because of arithmetic that was done wrong first.** At 1.2 the
// charged swing dealt fifteen and a half damage for a cycle of ninety four ticks -- charge plus
// the twenty four tick cooldown -- which is 3.3 damage a second against the 5.8 of tapping it. The
// charge was a 44% loss on raw throughput, so the correct play was to never use it and the most
// distinctive mechanic the weapon had would have been dead on arrival. A slow single hit ALWAYS
// loses to tapping unless the multiplier is enormous, which is arithmetic and not taste.
//
// At two it is twenty one damage -- exactly three uncharged swings -- for eighty four ticks
// against the seventy two three uncharged swings take. So it is still slightly below tapping on
// throughput, and that is deliberate: the lunge and the sweep are below it too, and what they
// buy is not damage per second. What the axe buys is a guard emptied in one hit, a plate that
// comes apart twice as fast, and a hit that ends a fight.
//
// The bar is honest the whole time the player is standing there waiting.
CHARGE.put(WeaponProfile.AXE, new ChargeProfile(true, 2.0F, 60));
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
        // The arcabuz does not charge, and the reason is the same twenty ticks as everything
        // else about it.
        //
        // **A charge window and a fuse are the same promise told twice.** If holding the button
        // bought anything, "how long until this fires" would stop having one answer: the player
        // would be reading a bar that says twenty ticks while a different bar says forty. The
        // fuse is the delay, it is fixed, and it is not something the player can spend time on
        // to make the shot better -- they spend time on it whether they like it or not, which is
        // the price.
        //
        // So this is a written-down NONE like the ropera's and the daga's, and the system asks
        // the component rather than branching. A click on the arcabuz is exactly vanilla's swing.
        CHARGE.put(WeaponProfile.ARCABUZ, ChargeProfile.NONE);
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
        // The arcabuz is the hardest shove in the game, and it is the "recoil" the weapon has.
        //
        // **0.70 horizontal, past every other weapon, and it comes off this table rather than
        // from the shot.** The recoil of a musket goes in two directions and only one of them is
        // the game's problem: the target is thrown, and the shooter is pushed back a step. The
        // second half is not here because a server cannot push a player -- the position is
        // driven by the movement report the client sends every tick, which is the whole reason
        // Lunge does not move anybody either. So what this row buys is the half that IS ours, and
        // the muzzle walking up in the viewmodel is the other half.
        //
        // Vertical is 0.18: a blast lifts a little and is not a launcher. The warhammer's 0.30 is
        // a hammer hitting one thing and the halberd's 0.14 is a polearm hitting a line; this is
        // ten things at once, so it sits between them rather than at either end.
        KNOCKBACK.put(WeaponProfile.ARCABUZ, new KnockbackProfile(0.70F, 0.18F));
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
        // And the sixth weapon, which is the first one whose special is twenty ticks late. The
        // right button lights the fuse or loads the gun depending on what is already in it, and
        // both halves are the same press, which is why this is one value and not two.
        SPECIAL.put(WeaponProfile.ARCABUZ, SpecialType.FUSEE);
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
     * The cone a weapon's right button hits with, or {@link ReachProfile#NONE}.
     *
     * <p>A caller that has {@code NONE} in hand wants {@link WeaponStats#reach()} instead, and
     * {@link WeaponStats#shotReach()} is the accessor that does that for it rather than making
     * thirty call sites remember.</p>
     */
    public static ReachProfile shotReach(WeaponProfile profile) {
        return SHOT_REACH.get(profile);
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
