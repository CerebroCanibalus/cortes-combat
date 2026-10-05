package com.cortes.combat.item;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

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
        assertEquals(14, EnumSet.allOf(WeaponProfile.class).size(),
                "a profile was added and the tables were not told. Fourteen is NONE, UNARMED, the "
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
        assertTrue(WeaponTables.reach(WeaponProfile.HALBERD).distance()
                        > WeaponTables.reach(WeaponProfile.TRIDENT).distance() + 0.5F,
                "the halberd is the longest thing in the game at five blocks, three quarters more "
                        + "than the trident's four. It is the weapon whose whole argument is "
                        + "standing off, and it now also pays for it by being the slowest");
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
}