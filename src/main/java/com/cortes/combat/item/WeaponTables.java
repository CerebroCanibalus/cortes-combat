package com.cortes.combat.item;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;

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
        // The trident: 4.0 -> 5.3, the longest reach in the game, on the narrowest cone here at fifteen.
//
// **Fifteen degrees is a corridor, not an opening.** At the sword's thirty five the trident would
// be a long sword; at fifteen it is a spear, and the reason the reach can be five and a half is
// that the player has to be aimed. Two thirds of a sword's cone would cover everything at that
// distance and take the aim out of the weapon.
//
// The vertical goes to twenty-five with it, because a thrust aimed at chest height and a cone
// that stops at the knees are two different weapons.
REACH.put(WeaponProfile.TRIDENT, new ReachProfile(5.3F, 15.0F, 25.0F));
        // The hoe: the widest cone in the game, on a reach that is only average.
        //
        // Sixty degrees and a little over half as wide again as the halberd's forty five, which is
        // what six targets need: at the halberd's arc a group of mobs is mostly out of it, and six
        // targets on a narrow cone is a number the player never sees reached. The distance is three
        // and two, between the sword's three six and the dagger's two four, because the whole
        // weapon works at arm's length and nothing about it is about reaching.
        //
        // **The vertical is the widest here too**, at fifty, and that is deliberate: a crowd of mobs
        // is at your feet as often as at your chest, and a ground weapon that cannot hit what is
        // directly in front of it is not an area weapon.
        REACH.put(WeaponProfile.HOE, new ReachProfile(3.2F, 60.0F, 50.0F));

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
        // **3.5 -> 3.6, 12 -> 13 and 15 -> 16.** The playtest with a second player asked for "5%
        // more radius and 15% more range", and three point six is where that lands: the sword is
        // also three point six, which is not a copy -- the ropera's cone is thirteen degrees wide
        // against the sword's thirty five, so the two weapons have the same distance and nothing
        // else in common.
        //
        // **It stops exactly at the sword on purpose.** "15% more" off three point five is three
        // and a quarter, and passing the sword would take away the one thing the ropera's speed
        // was paying for.
        REACH.put(WeaponProfile.ROPERA, new ReachProfile(3.6F, 13.0F, 16.0F));
        // Dagger: tiny range, wide cone. **2.4 -> 2.9, 45 -> 49, 30 -> 31.**
        //
        // "20% more radius and 20% less damage", and the two halves are the same trade: a wider
        // pocket the player cannot miss in, paying for it with every hit landing softer. Two point
        // nine is a fifth more reach and it puts the dagger past the unarmed hands' two point five
        // and level with the sabre's two point five -- which is the point, because the dagger is
        // now the fastest thing that can still hurt *and* it reaches as far as the sabre.
        REACH.put(WeaponProfile.DAGGER, new ReachProfile(2.9F, 49.0F, 31.0F));
        // Halberd: the widest cone. **4.2 -> 5.0, 55 -> 60.** No longer the longest -- see the trident.
        //
        // "20% more range", taken literally: four point two times one point two is five point zero
        // four, and this is the weapon whose whole argument is standing off. It was the longest
        // thing in the game by three quarters of a block over the trident's four; the trident is
        // now at five point three, so the halberd keeps the width and loses the crown.
        //
        // **And that is the difference between the two, not a demotion.** The halberd's reach is
        // paid for with a cone: five blocks of standing off that still catches what is beside you.
        // The trident's is paid for with a fifteen degree corridor, which at five and a half is a
        // spear and not a long sword. One covers a crowd at range and the other is aimed, and
        // neither of those arguments works on the other's terms.
        REACH.put(WeaponProfile.HALBERD, new ReachProfile(5.0F, 60.0F, 35.0F));
        // 2.6 -> 2.9 and 40/40 -> 30/30. The playtest read the warhammer as a weapon that could
        // never reach anything, and it was right for the wrong reason: the cone was wide enough
        // that at its own range the useful part of it was a wall. Moving the reach out and the
        // angles in is the trade the sabre makes in the other direction.
        //
        // **2.9 -> 3.4 and 30 -> 36 by 36.** A second playtest, with somebody else swinging at it,
        // asked for "a bit more radius and 20% more range", and the two halves are the same
        // observation: it was whiffing, and it was whiffing because the *cone* was too narrow for
        // how far the arm has to travel. Three point four is a fifth more than two point nine and it
        // lands just under the sword's three point six -- the warhammer is slower than a sword by
        // seven ticks, so being a little shorter on reach is the honest cost of that.
        REACH.put(WeaponProfile.WARHAMMER, new ReachProfile(3.4F, 36.0F, 36.0F));
        // Sabre: the widest cone in the game, shortest range. It pays for that cone with a parry.
        //
        // **2.5 -> 2.25 and 75 stays at 75.** "-10% of range" on the sabre, which is the one
        // request here that makes the weapon *worse* to hold and it is still the right call: the
        // sabre's problem was never the reach on its own, it is that seventy five degrees of cone
        // at arm's length is a bet, and this makes the bet smaller without touching the payoff.
        // The parry is what pays for the weapon and it is untouched.
        REACH.put(WeaponProfile.SABRE, new ReachProfile(2.25F, 75.0F, 25.0F));
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
        // The pickaxe and the maza cannot be charged, and the two reasons are different.
        //
        // **The pickaxe is not a weapon.** It was removed from combat at `WeaponKinds`, and this
        // row is what it looked like while that was still undecided: a charge nobody could reach,
        // bought by a profile the press now refuses before it reads a charge. It reads NONE so
        // that nothing anywhere can find a charge on a shovel-shaped lie.
        CHARGE.put(WeaponProfile.PICKAXE, ChargeProfile.NONE);
        CHARGE.put(WeaponProfile.SHOVEL, new ChargeProfile(true, 0.4F, 33));
        // **The maza is a weapon and cannot be charged because it is too heavy.** Its row used to
        // be 0.7 over forty ticks, which was invented -- nothing in vanilla's maza charges, because
        // vanilla's maza does not need to: the damage is the fall.
        //
        // **That is also why it is the one weapon `WeaponKinds.autoRepeats` refuses.** Vanilla
        // reads `fallDistance` and calls `resetFallDistance()` after every hit, so a repeated
        // strike spends the fall and does not collect it.
        CHARGE.put(WeaponProfile.MACE, ChargeProfile.NONE);
        // The hoe cannot be charged, and the reason is the other one from the maza's: the maza is
        // too heavy, and this one has nothing to commit to. Six targets, low damage and no shove
        // is a weapon whose skill expression is *where you stand*, and a charge would make it a
        // weapon whose skill expression is *when you let go*, which is a different weapon.
        //
        // Which means it repeats, and that is the point: **the hoe is the weapon the held button
        // was asked for.** Holding it is a clearing motion and nothing else happens.
        CHARGE.put(WeaponProfile.HOE, ChargeProfile.NONE);
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
        // The shovel: 0.22/0.05 -> 1.05/0.12, and the horizontal is nearly double the sword's.
//
// **The vertical is deliberately small, and that is the whole correction.** It was 0.28 in the
// first version of this, which would have put a shovel's lift within a hundredth of the warhammer's
// 0.30 -- and the warhammer is the heaviest thing in the game. The design's argument for the shovel
// is putting something off a cliff, and a cliff is horizontal: a shovel that launches people upward
// is a weapon that can also send them over a wall, which is a different and much less legible tool.
// Low vertical, high horizontal, and the two numbers disagree on purpose.
KNOCKBACK.put(WeaponProfile.SHOVEL, new KnockbackProfile(1.365F, 0.156F));
KNOCKBACK.put(WeaponProfile.PICKAXE, new KnockbackProfile(0.22F, 0.05F));
KNOCKBACK.put(WeaponProfile.MACE, new KnockbackProfile(0.30F, 0.10F));
KNOCKBACK.put(WeaponProfile.TRIDENT, new KnockbackProfile(0.30F, 0.12F));
// The hoe is the lowest in the game, below the fist's 0.10 -- deliberately. Its whole argument is
// that six things walk away from it slowly rather than being thrown out of the fight, and a shove
// it cannot do without is what makes clearing a room a decision rather than an accident.
KNOCKBACK.put(WeaponProfile.HOE, new KnockbackProfile(0.06F, 0.01F));
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
        KNOCKBACK.put(WeaponProfile.ARCABUZ, new KnockbackProfile(1.10F, 0.18F));
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
        // ---------------------------------------------------------------------------------------
        // THE VANILLA REWORK. All five read NONE, and **that is the honest state, not a decision**.
        //
        // They did not all read NONE before. They read:
        //
        //   axe      SLAM      the charged axe's own slam
        //   pickaxe  LUNGE     **the ropera's lunge**, copied
        //   shovel   SWEEP     **the halberd's sweep**, copied
        //   mace     THROW     vanilla throws it, so it seemed safe
        //   trident  THROW     same
        //
        // Four of those five were a decision nobody took. The shovel had a halberd's arc because
        // that is what a shovel reaches for, the pickaxe had a rapier's step because a tool with
        // a reach problem seemed like it needed one, and "vanilla throws it" is an argument about
        // how vanilla works rather than about what a shovel should do here. A NONE is an absence;
        // a copied special is a decision made by omission, and it is the more expensive one,
        // because it looks chosen.
        //
        // **The pickaxe one is now dead code rather than wrong.** It stopped being a weapon at
        // `WeaponKinds.fights`, so its LUNGE row can never be reached -- and it is worth saying
        // out loud that a row nobody can read is a row somebody will read later and believe.
        //
        // What each one owes the design, which is not decided and is not this file's job:
        //   axe      the fastest close weapon in the game should not pay for its speed twice
        //   shovel   a wide, cheap arc -- but a shovel is not a halberd, so how is it different
        //   mace     vanilla's throw is a real one; what does it cost here
        //   trident   three projectiles, and none of them are in the cone
        //   sword    the smallest rework in the game: it already is a sword
        // ---------------------------------------------------------------------------------------
        SPECIAL.put(WeaponProfile.SWORD, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.AXE, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.PICKAXE, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.SHOVEL, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.MACE, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.TRIDENT, SpecialType.NONE);
        SPECIAL.put(WeaponProfile.HOE, SpecialType.NONE);
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

    /**
     * Whether holding the button is one strike over and over instead of one hold.
     *
     * <h2>Why this is a question and not a list</h2>
     *
     * <p>Because the design said "every weapon without a charge", and that is a fact about the
     * charge table rather than about a set somebody wrote down.</p>
     *
     * <p><b>It was a set for one round, and a test found it in a minute.</b> It said the bare
     * fist repeats &mdash; and the fist charges, 0.3 over twelve ticks. The rule is not "the ones
     * that fight" and it is not "the ones without a charge": it is the second one, which is
     * narrower, and a fist that charges is not a weapon you hold down.</p>
     *
     * <h2>And one exclusion, which is not a preference</h2>
     *
     * <p><b>The maza cannot be charged -- it is too heavy -- and it still must not repeat.</b>
     * Vanilla reads {@code fallDistance} in the damage bonus and calls {@code resetFallDistance()}
     * in {@code postHurtEnemy}, so <b>a repeated strike spends the fall without collecting it</b>.
     * The one weapon in the game that lives in the air would be the one standing still.</p>
     *
     * <p>So the criteria are two questions: <b>is it a weapon, and does it charge</b>. The maza
     * answers yes and yes, and still is refused, which is why the exclusion is named.
     *</p>
     */
    /**
     * How many things one strike of this weapon can hit.
     *
     * <p>One for everything except the two area weapons, and that is what makes it a number rather
     * than a set: "one strike, one target" is the rule and the exceptions are additions to it, so a
     * new weapon is a single-target weapon until somebody says otherwise. A set would have made
     * every profile a decision, and a profile nobody decided is a profile nobody thought about.</p>
     *
     * <h2>It is not the same number as the halberd's sweep</h2>
     *
     * <p>The sweep costs four swings and reaches further and opens wider than the weapon's own
     * cone, so it reads a derived reach from {@link com.cortes.combat.special.HalberdSweep}. The
     * maza's three targets are in <b>its own cone</b>: this is the left button, it costs one swing,
     * and a weapon that is also the strongest in the set does not need the extra reach on top.</p>
     */
    public static int maxTargets(WeaponProfile profile) {
        Integer declared = MULTI_TARGET.get(profile);
        return declared == null ? 1 : declared;
    }

    /** Whether this strike hits more than one thing, asked rather than answered by comparing. */
    public static boolean hitsSeveral(WeaponProfile profile) {
        return maxTargets(profile) > 1;
    }

    private static final Map<WeaponProfile, Integer> MULTI_TARGET = new HashMap<>();

    /**
     * The weapons that strike more than one thing at a time.
     *
     * <p>These are the two area weapons and they do not work the same way. The maza covers what is
     * in front of it and can reach three, and what separates it from the shovel is not the arc:
     * it is that the maza has a body and the shovel does not. Six on the hoe, because its whole
     * argument is attrition against a crowd &mdash; it clears a room slowly and asks nothing of
     * the player but the patience to keep holding the button.</p>
     */
    static {
        MULTI_TARGET.put(WeaponProfile.MACE, 3);
        MULTI_TARGET.put(WeaponProfile.HOE, 6);
        // Everything else is one, and that is the default rather than a row: see maxTargets.
    }

    public static boolean autoRepeats(WeaponProfile profile, boolean cocks) {
        return WeaponKinds.fights(profile)
                && !charge(profile).canCharge()
                && !NEVER_REPEATS.contains(profile)
                // **The cock is the second gate, and a playtest is what found it.**
                //
                // <p>"Does not charge" was the whole rule, and it put the ropera, the dagger and
                // the arcabuz on the held button. All three read a wind-up -- a ropera and a
                // dagger cock a thrust, the arcabuz has a bayonet -- and the loop restarts the
                // wind-up on every strike, so all three played their one motion over and over
                // instead of once.</p>
                //
                // <p><b>A cock is a preparation, and a preparation that restarts is not a
                // preparation.</b> What broke is not a number: the player asked for one thing and
                // got a loop of it, and the weapon's weight was the single committed stroke.</p>
                && !cocks;
    }

    /** The question without the viewmodel half, for anything that only wants "no charge". */
    public static boolean canHoldDown(WeaponProfile profile) {
        return autoRepeats(profile, false);
    }

    /**
     * The weapons that cannot be held down even though they do not charge.
     *
     * <p><b>One entry.</b> An empty list would have been defensible and would have meant "nobody
     * thought about the maza" &mdash; the same shape as the copied specials, one level down.</p>
     */
    private static final Set<WeaponProfile> NEVER_REPEATS = EnumSet.of(WeaponProfile.MACE);

    /**
     * How far a full charge drags a target toward the player, in blocks.
     *
     * <p>Zero for everything else. <b>It is a separate number and not a negative knockback</b>,
     * because {@link KnockbackProfile#clampedHorizontal()} clamps to zero and a negative lands
     * there: loosening that clamp would trade one bound for another on every weapon in the game
     * to serve one of them.</p>
     *
     * <p>It is also <b>not scaled by the charge ramp</b>, and that is the point of the weapon. The
     * trident's argument is that its charged thrust does the opposite of everybody else's push, and
     * a tap that dragged at three fifths of the strength would make the difference a rounding error
     * instead of a choice.</p>
     */
    public static float pullFor(WeaponProfile profile) {
        Float pull = PULL.get(profile);
        return pull == null ? 0.0F : pull;
    }

    private static final Map<WeaponProfile, Float> PULL = new HashMap<>();

    static {
        // One point three. The trident reaches further than anything else in the game at five
        // point three, and a thrust that still pushed the target away would spend that reach on a
        // gap it then made worse. Dragging brings the second cut inside five point three, which is
        // the whole combo: the long one lands, the short one follows.
        PULL.put(WeaponProfile.TRIDENT, 1.3F);
        // Everything else is zero, and that is the default rather than thirteen rows.
    }

    /** Every profile that may be held down, for a test that has to walk it. */
    public static Set<WeaponProfile> repeatingProfiles() {
        Set<WeaponProfile> repeats = EnumSet.copyOf(WeaponKinds.combatProfiles());
        repeats.removeAll(NEVER_REPEATS);
        return Set.copyOf(repeats);
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
