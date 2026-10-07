package com.cortes.combat.item;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tables are complete.
 *
 * <p><b>This class is the answer to a bug that shipped.</b> {@code WeaponProfile.ROPERA} had no row
 * in the special table, a {@code putIfAbsent} loop at the bottom of the static block filled the gap
 * with {@code NONE}, and the ropera's right button was dead -- while the family enum one field away
 * said {@code LUNGE} and a test read the family. The declared number and the number that ran had
 * been different for the whole life of the feature, and nothing could see it.</p>
 *
 * <p>Two things make it seeable now, and only the second one is the test:</p>
 *
 * <ol>
 *   <li>The tables moved into a class a bare JVM can load, so they can be asserted at all.</li>
 *   <li>Every profile must have a row in every table, <b>explicitly</b>. Not "has a default" --
 *       an actual entry. The empty row is a decision somebody wrote down; the missing row is the
 *       bug, and only the second one is what happened.</li>
 * </ol>
 */
class WeaponTablesTest {

    @Test
    @DisplayName("every profile has a reach, and it is a real cone")
    void everyProfileHasAReach() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            ReachProfile reach = WeaponTables.reach(profile);
            assertNotNull(reach, profile + " has no reach row");
            assertTrue(reach.distance() > 0.0F,
                    profile + " reaches nothing: " + reach.distance());
            assertTrue(reach.horizontalAngle() > 0.0F && reach.verticalAngle() > 0.0F,
                    profile + " has a closed cone: " + reach.horizontalAngle() + " by "
                            + reach.verticalAngle());
        }
    }

    @Test
    @DisplayName("every profile has a charge, and NONE is a written-down answer")
    void everyProfileHasACharge() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            assertNotNull(WeaponTables.charge(profile), profile + " has no charge row");
            assertNotNull(WeaponTables.knockback(profile), profile + " has no knockback row");
        }
        // Two of the mod's own weapons deliberately do not charge, and that is a decision rather
        // than a gap. Asserted so that "NONE here" reads as a choice: it is what makes the ropera
        // and the daga fall out of the charge system without an if anywhere.
        assertSame(ChargeProfile.NONE, WeaponTables.charge(WeaponProfile.ROPERA));
        assertSame(ChargeProfile.NONE, WeaponTables.charge(WeaponProfile.DAGGER));
    }

    @Test
    @DisplayName("every profile has a special, and the ropera's is the lunge")
    void everyProfileHasASpecial() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            assertNotNull(WeaponTables.special(profile),
                    profile + " has NO special row. This is the ropera's bug: the row was missing, "
                            + "a putIfAbsent filled it with NONE, and the right button did nothing "
                            + "while the family enum said LUNGE");
        }
        // The three that answer the right button, by name, because a count cannot say which.
        assertSame(SpecialType.LUNGE, WeaponTables.special(WeaponProfile.ROPERA));
        assertSame(SpecialType.PARRY, WeaponTables.special(WeaponProfile.SABRE));
        assertSame(SpecialType.MULTI_HIT, WeaponTables.special(WeaponProfile.HALBERD));
        // And the two that deliberately do not: a dagger that lunges is a ropera that is worse.
        assertSame(SpecialType.NONE, WeaponTables.special(WeaponProfile.DAGGER));
        assertSame(SpecialType.NONE, WeaponTables.special(WeaponProfile.WARHAMMER),
                "the warhammer is the commitment weapon, and a right button would take that away");
    }

    /**
     * The family asks the table, and this is what makes that true.
     *
     * <p>Asserted in both directions, because the failure was a pair of copies that agreed about
     * nothing: the family said LUNGE and the table said NONE, and each looked correct on its own.
     * A test that only checked the family would have stayed green through all of it.</p>
     */
    @Test
    @DisplayName("a family and its profile answer the same question the same way")
    void theFamilyIsNotASecondCopy() {
        for (WeaponFamily family : WeaponFamily.values()) {
            assertSame(WeaponTables.special(family.profile()), family.special(),
                    family + " disagrees with the table for " + family.profile()
                            + ". If the family stores a special again, that is a second copy and "
                            + "this is the test that would have caught it");
        }
    }

    /**
     * Only the sabre charges hard enough to land twice, and it is the only one.
     *
     * <p>Paired deliberately, and both directions matter. A bonus that reaches the threshold on a
     * weapon nothing else watches would double hits nobody is looking at; and a weapon the design
     * calls a doubler whose bonus fell below the line would quietly stop doubling, which is exactly
     * the bug the playtest reported -- the sabre said it hit double and did not.</p>
     */
    @Test
    @DisplayName("the sabre is the only weapon whose full charge lands twice")
    void onlyTheSabreLandsTwice() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            float bonus = WeaponTables.charge(profile).bonus();
            boolean doubles = com.cortes.combat.particle.SlashTiming.chargesToDouble(
                    profile, WeaponTables.charge(profile));
            assertEquals(profile == WeaponProfile.SABRE, doubles,
                    profile + " has a charge bonus of " + bonus + " and lands "
                            + (doubles ? "two" : "one") + " hit(s) at full charge. The sabre is the "
                            + "only doubler in the game");
        }
        // Its bonus is ZERO: the sabre's charge buys a second cut, not a heavier one. Every other
        // charging weapon in the game spends its window making one hit worth more, and this one
        // spends it landing two. It has the worst reach in the game, so it cannot afford to be
        // the strongest as well.
        // Its bonus is ONE, so each of the two cuts carries the full multiplier. It was zero for two
        // rounds, on the reasoning that "doble" means a second cut rather than a heavier one --
        // and the consequence was that charged damage and base damage became the same number, so
        // the twenty tick window bought a second identical swing and nothing else. The playtest
        // named it exactly.
        assertEquals(1.0F, WeaponTables.charge(WeaponProfile.SABRE).bonus(), 1.0E-6,
                "each of the two cuts has to be at CHARGED damage. A zero bonus makes the charge "
                        + "do nothing, which is the bug the playtest reported");
        assertEquals(2, com.cortes.combat.particle.SlashTiming.hitsAt(
                        WeaponProfile.SABRE, WeaponTables.charge(WeaponProfile.SABRE), 1.0F));
    }

    @Test
    @DisplayName("the reach table cannot be written from outside")
    void theTableIsReadOnly() {
        // A map a caller can write to is a second copy of this table that nobody else can see, and
        // a disagreement between two copies is the bug this class was written to remove.
        try {
            WeaponTables.reachTable().put(WeaponProfile.SWORD, ReachProfile.UNARMED);
            org.junit.jupiter.api.Assertions.fail("the reach table accepted a write");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }

    /** Nothing may sit in a table that is not a profile. Orphans are how tables go stale. */
    @Test
    @DisplayName("and the profiles are all there in the first place")
    void theProfileListIsComplete() {
        assertEquals(15, EnumSet.allOf(WeaponProfile.class).size(),
                "a profile was added and the tables were not told. Fifteen is NONE, UNARMED, the "
                        + "six vanilla, the five new families, and the arcabuz -- which is a "
                        + "profile and NOT a family, because a firearm is one item rather than a "
                        + "(family, material) pair, and the tables are keyed by profile");
    }

    /** Sanity on the three families' numbers, so a bad edit is loud and not subtle. */
    @Test
    @DisplayName("the reach numbers are the ones the design settled on")
    void theReachNumbers() {
        // **Three point six, thirteen and sixteen.** The playtest with a second player asked for
        // "5% more radius and 15% more range" on the ropera, and three point six is where that
        // lands -- level with the sword on distance and nothing else in common, since the cone is
        // thirteen degrees against the sword's thirty five.
        assertEquals(3.6F, WeaponTables.reach(WeaponProfile.ROPERA).distance(), 1.0E-6);
        assertEquals(13.0F, WeaponTables.reach(WeaponProfile.ROPERA).horizontalAngle(), 1.0E-6);
        assertEquals(16.0F, WeaponTables.reach(WeaponProfile.ROPERA).verticalAngle(), 1.0E-6);
        assertEquals(3.6F, WeaponTables.reach(WeaponProfile.SWORD).distance(), 1.0E-6,
                "the sword outranges the axe, which inverts vanilla on purpose");
        // **Three point four, thirty six by thirty six.** "A bit more radius and 20% more range",
        // and the two halves are one observation: the warhammer whiffed because its cone was too
        // narrow for how far the arm travels.
        assertEquals(3.4F, WeaponTables.reach(WeaponProfile.WARHAMMER).distance(), 1.0E-6);
        assertEquals(36.0F, WeaponTables.reach(WeaponProfile.WARHAMMER).horizontalAngle(), 1.0E-6);
        KnockbackProfile hammer = WeaponTables.knockback(WeaponProfile.WARHAMMER);
        assertTrue(hammer.vertical() > WeaponTables.knockback(WeaponProfile.SWORD).vertical(),
                "the warhammer lifts, because that is what a warhammer is for");

        // **The other four retunes, as relationships and not as figures.**
        //
        // A retune round is about a weapon's place in the table, and a table is relationships.
        // Asserting the exact number pins the design to today's arithmetic and says nothing about
        // whether the weapon ended up in the right place; asserting "the halberd outranges
        // everything by more than half a block" says it and survives the next round.
        // **The trident is the longest thing in the game, and the assertion changed sides.**
        //
        // It used to say the halberd outranges the trident by more than half a block, and it was
        // written that way deliberately -- relationships, not figures, so it survives a retune.
        //
        // <p>It also went stale the moment the design gave the trident the longest reach, and a
        // stale relationship reads exactly like a design that was never revisited: the number in
        // the message still said "the trident's four" long after it stopped being four.</p>
        //
        // <p><b>The new one is a relationship in the other direction and says why both are
        // right.</b> The halberd's five is paid for with a sixty degree cone that catches what is
        // beside you; the trident's five and a half is paid for with fifteen degrees, which at
        // that length is a spear. Asserting only "the trident is longest" would have let the
        // fifteen widen to sixty without a word, and that is the pair of numbers that has to move
        // together.</p>
        assertTrue(WeaponTables.reach(WeaponProfile.TRIDENT).distance()
                        > WeaponTables.reach(WeaponProfile.HALBERD).distance(),
                "the trident is the longest thing in the game at five and a half, over the "
                        + "halberd's five. The design says so, and it is not a demotion for the "
                        + "halberd: its reach is paid for with a cone and the trident's is paid "
                        + "for with a corridor");
        assertTrue(WeaponTables.reach(WeaponProfile.TRIDENT).horizontalAngle()
                        <= WeaponTables.reach(WeaponProfile.HALBERD).horizontalAngle() * 0.5F,
                "**and the fifteen degrees is the half of it that has to move with it.** A "
                        + "five-and-a-half reach on a sixty degree cone is a long sword, and the "
                        + "trident's argument is that it is a spear: it has to be aimed, or the "
                        + "reach is free. The pair is the weapon");
        // **The halberd did NOT take the widest cone, and the retune was not supposed to give it
        // one.** Sixty against the sabre's seventy five. The first version of this assertion said
        // the halberd was wider, which is false and which the comment two lines above it also
        // said was false -- the sabre is the widest cone in the game and stayed that way. A guard
        // written from memory of the design rather than from the table is the same bug as a number
        // written from memory.
        assertTrue(WeaponTables.reach(WeaponProfile.SABRE).horizontalAngle()
                        > WeaponTables.reach(WeaponProfile.HALBERD).horizontalAngle(),
                "the sabre keeps the widest cone in the game at seventy five, and the halberd's "
                        + "sixty is second. The halberd paid for five blocks of reach with the "
                        + "slowest swing in the game and did not also buy a cone it does not need");
        assertTrue(WeaponTables.reach(WeaponProfile.DAGGER).distance()
                        > WeaponTables.reach(WeaponProfile.UNARMED).distance(),
                "the dagger reaches past a bare hand now. It is the access weapon and it has to beat "
                        + "what you have with nothing equipped at all");
        assertTrue(WeaponTables.reach(WeaponProfile.SABRE).distance()
                        < WeaponTables.reach(WeaponProfile.DAGGER).distance(),
                "and the sabre is the shortest reach in the game after the retune, which is the "
                        + "price of a seventy five degree cone at arm's length. The parry is what "
                        + "pays for the weapon and it is untouched by this");
    }

    /**
     * The vanilla six answer NONE, and that has to be deleted by hand.
     *
     * <h2>Why a test insists on an absence</h2>
     *
     * <p>Because the absence is the finding. The vanilla arms did not all read NONE before the
     * rework: the shovel carried the halberd's sweep and the pickaxe the ropera's lunge, both
     * copied without anybody deciding them, and "vanilla throws the mace" is an argument about how
     * vanilla works rather than about what these should do here.</p>
     *
     * <p>So this is deliberately annoying in the good way. <b>Giving the shovel its special back
     * has to be the deletion of one line in {@link WeaponTables} and of one name here</b> &mdash;
     * which means the diff that introduces it says "the shovel sweeps now, on purpose", next to
     * the number it is using. A copied special is the expensive kind: a NONE is an absence and a
     * copy looks chosen.</p>
     */
    @Test
    @DisplayName("the vanilla six have no special, and that has to be deleted by hand")
    void theVanillaSixHaveNoSpecial() {
        for (WeaponProfile profile : new WeaponProfile[]{
                WeaponProfile.SWORD, WeaponProfile.AXE, WeaponProfile.PICKAXE,
                WeaponProfile.SHOVEL, WeaponProfile.MACE, WeaponProfile.TRIDENT}) {
            assertSame(SpecialType.NONE, WeaponTables.special(profile),
                    profile + " has a special again: " + WeaponTables.special(profile)
                            + ". **If this is the decision, it has to cost a line in WeaponTables "
                            + "and a name here**, so the diff says it was chosen. What it had "
                            + "before was a copy -- the shovel had the halberd's sweep, the "
                            + "pickaxe the ropera's lunge -- and a copy looks like a decision "
                            + "nobody took");
        }
    }

    /**
     * Not fighting and having a special are the same fact stated twice, so the test says so.
     *
     * <h2>Why this is not redundancy</h2>
     *
     * <p>{@code WeaponKinds} says the pickaxe does not fight and this file says its special is
     * NONE, and <b>right now the pickaxe is safe for two unrelated reasons</b>.
     * {@code SpecialInputHandler} does not ask {@code fights()} -- it asks whether the special is
     * NONE and otherwise claims the right button. So a pickaxe that had its LUNGE back would not
     * swing on the left any more and would still step on the right.</p>
     *
     * <p>Either fix closes it and both are one line. The invariant that matters is <b>"what does
     * not fight cannot answer a button"</b>, and a test of each half separately asserts an offset
     * compound of it.</p>
     */
    @Test
    @DisplayName("whatever does not fight cannot answer the right button either")
    void notFightingMeansNoSpecial() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            if (!WeaponKinds.fights(profile)) {
                assertSame(SpecialType.NONE, WeaponTables.special(profile),
                        profile + " does not fight and yet answers the right button with "
                                + WeaponTables.special(profile) + ". **SpecialInputHandler does "
                                + "not ask WeaponKinds**, it asks whether the special is NONE -- so "
                                + "this is the only thing saying a tool that stopped being a "
                                + "weapon also stopped answering the button");
            }
        }
    }

    /**
     * Holding the button is one strike repeated &mdash; with one real exception.
     *
     * <h2>Why this test lives here and not beside the weapon set</h2>
     *
     * <p>Because it was there, in {@link WeaponKinds}, and it was wrong there.</p>
     *
     * <p>Answered from the wrong table it said <b>the bare fist repeats</b> &mdash; and the fist
     * charges, 0.3 over twelve ticks. The rule is not "the ones that fight" and it is not "the
     * ones without a charge": it is the second, which is narrower. A weapon that charges is not a
     * weapon you hold down, and the fist is the cheapest proof of it in the game.</p>
     *
     * <p>It also sat beside a set of profiles, which is exactly why it could not see a charge. It
     * reads {@link WeaponTables#charge(WeaponProfile)} now, which is the table the question was
     * always about.</p>
     */
    @Test
    @DisplayName("the bare fist does not repeat, and the sword does not either")
    void theWeaponsThatChargeDoNotRepeat() {
        // **The first version of this asserted "everything that fights repeats except the maza",
        // and the sword failed it.** The sword charges at 0.5 over twenty five ticks, so holding
        // it is a charge -- and the version that passed it was the one reading the wrong table,
        // where "does it charge" was not answerable at all.
        //
        // The rule is the whole design: **no charge means it repeats.** The maza is the exception
        // and it is named. Everything that charges -- sword, axe, shovel, trident, halberd,
        // warhammer, sabre, and the fist -- does not repeat, because a tap on any of them is
        // already a charge in progress.
        assertFalse(WeaponTables.canHoldDown(WeaponProfile.UNARMED),
                "**the bare fist does not repeat.** It charges -- 0.3 over twelve ticks -- so "
                        + "holding it is a charge, and this is the case the first version got "
                        + "wrong by answering from the wrong table");
        assertFalse(WeaponTables.canHoldDown(WeaponProfile.SWORD),
                "and the sword does not either, which is the case that broke the first version's "
                        + "assertion: charging is not a subset of not repeating, it is the reason "
                        + "for it");
        assertFalse(WeaponTables.canHoldDown(WeaponProfile.PICKAXE),
                "and a pickaxe does not repeat, but for the first reason: it does not fight");
        assertTrue(WeaponTables.canHoldDown(WeaponProfile.DAGGER),
                "**the dagger repeats**, which is the decision: holding the button is a "
                        + "convenience and not a commitment. It is the fastest weapon in the game "
                        + "and it cannot be charged, so without this a player mashes anyway");
        assertTrue(WeaponTables.canHoldDown(WeaponProfile.ROPERA),
                "and so does the ropera, by the same decision");
        assertFalse(WeaponTables.canHoldDown(WeaponProfile.MACE),
                "**and the maza is the one that neither charges nor repeats.** Vanilla reads "
                        + "fallDistance and resets it in postHurtEnemy, so a repeated strike pays "
                        + "the cost without collecting the damage");
    }

    /**
     * The exclusion is one weapon, and it is named rather than derived.
     *
     * <p><b>An empty list would have been defensible and would have meant "nobody thought about
     * the maza"</b> &mdash; the same shape as the copied specials, one level down.</p>
     */
    @Test
    @DisplayName("the exclusion list is exactly the maza, not an oversight")
    void theExclusionIsNamed() {
        assertEquals(1,
                WeaponKinds.combatProfiles().size() - WeaponTables.repeatingProfiles().size(),
                "one weapon fights and cannot be held down. If this number changes somebody "
                        + "decided something, and the reason belongs in the new one's name the "
                        + "way the maza's is");
        assertFalse(WeaponTables.repeatingProfiles().contains(WeaponProfile.PICKAXE),
                "sanity: the pickaxe is absent because it never entered, so adding it to the "
                        + "exclusion list would change nothing and would look like a decision");
    }

    /**
     * Three facts per profile, in two places, and they have to agree.
     *
     * <h2>The one that was missing, and the two sabotages that found it</h2>
     *
     * <p>Nothing asserted that {@code fights} and the charge table were consistent, and
     * <b>both sabotage attempts passed</b>: giving the maza a charge profile again, and giving the
     * pickaxe one.</p>
     *
     * <p>What they produced is worse than either bug alone. A maza with a charge profile is a
     * <b>third state nobody decided</b>: it charges, it does not repeat, and no rule in the
     * project says what that is. It is not "no charge" and it is not "holds down".</p>
     *
     * <p><b>The rule in one sentence:</b> a weapon that does not charge may be held down, except
     * the maza; and a profile that does not fight has no opinion on either.</p>
     */
    @Test
    @DisplayName("no charge means it repeats, the maza aside, and nothing is a third state")
    void chargeAndRepetitionAgree() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            boolean fights = WeaponKinds.fights(profile);
            boolean charges = WeaponTables.charge(profile).canCharge();
            boolean repeats = WeaponTables.canHoldDown(profile);

            if (!fights) {
                assertFalse(repeats, profile + " does not fight and yet repeats");
                continue;
            }
            if (charges) {
                assertFalse(repeats, profile + " charges and also repeats. **That is a third state "
                        + "nobody decided**: a tap is already a charge on a weapon that charges, "
                        + "so holding the button would mean two things at once");
            } else {
                assertEquals(profile != WeaponProfile.MACE, repeats,
                        profile + " cannot be charged, so holding it is "
                                + (profile == WeaponProfile.MACE
                                        ? "**not** one strike repeated -- and the maza's reason "
                                                + "is that its damage is the fall, which every "
                                                + "strike spends"
                                        : "one strike repeated"));
            }
        }
    }

    /**
     * The two charge rows that had to go, and the reason each one went.
     *
     * <h2>The test above could not catch this, and that is the interesting part</h2>
     *
     * <p>Giving the maza a charge profile back <b>passed every test in the class</b>, and it should
     * have: with {@code NEVER_REPEATS} still holding the maza, a maza that charges is internally
     * consistent. It charges, and it does not repeat, and both of those follow from rules that are
     * each true.</p>
     *
     * <p>So the shape of the hole is: <b>two rules that are each right compose into a weapon
     * nobody chose.</b> "Charging weapons do not repeat" is true. "The maza does not repeat" is
     * true. Nothing said the maza cannot be charged &mdash; and its charge row, 0.7 over forty
     * ticks, was invented: nothing in vanilla's maza charges, because vanilla's maza does not need
     * to, since its damage is the fall.</p>
     *
     * <p><b>A composition of correct rules is not a proof that the result was chosen.</b></p>
     */
    @Test
    @DisplayName("the maza and the pickaxe cannot be charged, and the maza's row was invented")
    void theTwoChargeRowsThatWent() {
        assertFalse(WeaponTables.charge(WeaponProfile.MACE).canCharge(),
                "**the maza cannot be charged: it is too heavy.** Its row was 0.7 over forty "
                        + "ticks and nothing invented it -- vanilla's maza does not charge either, "
                        + "because vanilla's maza does not need to, since its damage is the fall. "
                        + "The axe is the slow charge in this game at sixty ticks; the maza is not "
                        + "a slow charge, it is not a charge at all");
        assertFalse(WeaponTables.charge(WeaponProfile.PICKAXE).canCharge(),
                "**and the pickaxe does not either.** It is not a weapon -- it lost combat at "
                        + "WeaponKinds -- and this row is what it looked like while that was still "
                        + "undecided: a charge the press could never reach, bought by a profile it "
                        + "now refuses before it reads one. NONE so that nothing anywhere can find "
                        + "a charge on a shovel-shaped lie");
    }
}