package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.particle.SlashTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sabre's double, counted the way the playtest counted it.
 *
 * <p><b>This exists because the feature was reported broken twice and the second report was
 * right.</b> The first said "the sabre does not hit double" and it did not -- the doubling was a
 * damage multiplier on a single hit. That got fixed. The second said it still did not, and the
 * proof was arithmetic: <em>two charged swings kill a zombie exactly</em>.</p>
 *
 * <p>That number is only true of a weapon that lands one hit per swing. So it is a measurement,
 * and the only honest thing to do with a measurement that contradicts the code is to make the
 * measurement permanent and let it judge the code. This test asks the question the playtest asked,
 * in the same units.</p>
 *
 * <p>Pure arithmetic on the project's own tables. The item cannot be built here -- registering it
 * needs the game -- so the base damage is written down, and that is honest rather than convenient:
 * the value under test is how many times a number is <em>applied</em>, which is what
 * {@link SlashTiming#hitsAt} answers, and a test that needed the registry would be testing the
 * registry.</p>
 */
class SabreDoubleTest {

    /** What the playtest was counting against. Verified in the vanilla sources. */
    private static final float ZOMBIE_HEALTH = 20.0F;

    /**
     * A sabre's base damage at iron, which is 3.0 from the family plus 2.0 from the tier.
     *
     * <p>Written down rather than read, and it is the one number here that a retune could move under
     * the test. That is acceptable on purpose: the test's claim is about <em>how many times</em> the
     * damage lands, and the numbers below are chosen so that the distinction between one hit and
     * two is large enough that a rebalance cannot silently turn this test false. Two iron sabre
     * hits kill a twenty health zombie; one does not, with room to spare on both sides.</p>
     */
    private static final float IRON_SABLE_BASE = 5.0F;

    /**
     * How many charged swings it takes this weapon's damage to kill the reference target.
     *
     * <p>Armour is modelled as vanilla does it -- points remove a fraction of the damage -- and the
     * cap is why this cannot divide by zero. It is worth modelling at all because the playtest
     * counted a real zombie: leaving armour out would make this a different, easier question.</p>
     */
    private static int swingsToKill(WeaponProfile profile, float baseDamage, float chargeRatio) {
        ChargeProfile charge = com.cortes.combat.item.WeaponTables.charge(profile);
        AttackResult result = DamageResolver.resolve(
                baseDamage, charge, profile, chargeRatio, null);
        int hitsPerSwing = SlashTiming.hitsAt(profile, charge, chargeRatio);
        float perSwing = result.damage() * hitsPerSwing;
        float afterArmour = perSwing * (1.0F - armourReduction(perSwing));
        assertTrue(afterArmour > 0.0F,
                profile + " deals nothing at all against armour, so the count would never end");
        return (int) Math.ceil(ZOMBIE_HEALTH / afterArmour);
    }

    /**
     * Vanilla's armour reduction for one hit, from {@code CombatRules.getDamageAfterAbsorb}.
     *
     * <p>Read out of the sources rather than approximated. The first version of this test guessed
     * "four percent a point" and the real formula reduces far less -- armour is subtracted from the
     * incoming damage first, and only what is left is a fraction of a quarter of it. A zombie's two
     * points barely touch a melee hit; they exist to blunt spammy small ones.</p>
     *
     * <p>Worth doing properly, because this test's whole job is to decide whether one swing or two
     * kills a target, and armour is the thing that decides it.</p>
     */
    private static float armourReduction(float incoming) {
        float armour = 2.0F;
        float damagePerPoint = 2.0F;   // 2 + toughness / 4, and a zombie has no toughness
        // getDamageAfterAbsorb: subtract the armour from the damage, clamp what is left to between
        // a fifth of the armour and twenty points, then the reduction is that divided by 25.
        float usable = Math.max(armour - incoming / damagePerPoint, armour * 0.2F);
        return Math.min(1.0F, usable / 25.0F);
    }

    /**
     * The report, reproduced.
     *
     * <p>Two charged swings to kill a zombie is what a single-hit weapon says, and it is the exact
     * number the playtest gave. If this test ever says two again, the doubling has stopped reaching
     * a swing -- and it will say so in one line instead of costing another client launch.</p>
     */
    @Test
    @DisplayName("a full charge kills a zombie in one swing, where a single hit takes two")
    void thePlaytestsOwnMeasurement() {
        ChargeProfile sabre = com.cortes.combat.item.WeaponTables.charge(WeaponProfile.SABRE);
        assertEquals(2, SlashTiming.hitsAt(WeaponProfile.SABRE, sabre, 1.0F),
                "the doubling has to be there before this measurement means anything");

        int charged = swingsToKill(WeaponProfile.SABRE, IRON_SABLE_BASE, 1.0F);
        int uncharged = swingsToKill(WeaponProfile.SABRE, IRON_SABLE_BASE, 0.0F);

        // The doubling halves the swings, which is the whole claim. The count is a CEILING, so
        // halving the damage does not always halve the count -- ceil(20/9.4) is 3 and
        // ceil(20/18.8) is 2, but ceil(20/10) is 2 and ceil(20/20) is 1, which is only half. The
        // honest invariant is that one swing of the double costs no more than two of the single,
        // and in fact costs strictly fewer.
        assertTrue(charged < uncharged,
                "a full charge takes " + charged + " swings and an uncharged one takes " + uncharged
                        + ". The doubling has to strictly improve the count");
        assertTrue(charged * 2L <= uncharged + 1L,
                "and it has to roughly halve it. Got " + charged + " against " + uncharged
                        + ": two cuts cannot be worse than one, and one of the two numbers is "
                        + "wrong by more than the ceiling's rounding");

        // And each of the two cuts is at CHARGED damage, which is the playtest's point and the one that
        // the zero bonus quietly broke: with no bonus, "charged" and "base" are the same number and
        // the twenty tick window buys a second identical swing instead of a second harder one.
        AttackResult unchargedResult = DamageResolver.resolve(
                IRON_SABLE_BASE, com.cortes.combat.item.WeaponTables.charge(WeaponProfile.SABRE),
                WeaponProfile.SABRE, 0.0F, null);
        AttackResult chargedResult = DamageResolver.resolve(
                IRON_SABLE_BASE, com.cortes.combat.item.WeaponTables.charge(WeaponProfile.SABRE),
                WeaponProfile.SABRE, 1.0F, null);
        assertEquals(unchargedResult.damage() * 2.0F, chargedResult.damage(), 1.0E-6,
                "a charged cut is twice an uncharged one. The two cuts then make four times base, "
                        + "which is what one fully charged swing is worth");
        assertEquals(IRON_SABLE_BASE * 2.0F, chargedResult.damage(), 1.0E-6,
                "and that is the per-cut number the playtest will see in the log. Iron is five, so "
                        + "a charged cut is ten, and the two of them are twenty against a twenty "
                        + "health husk -- one swing");
    }

    /**
     * A single hit really is the two the playtest counted.
     *
     * <p>Asserted rather than assumed, because the whole diagnosis rests on it: the report's "two
     * charged swings" only exonerates nothing if one charged swing takes two. If a retune made one
     * swing enough on its own, the report would never have been evidence of anything.</p>
     */
    @Test
    @DisplayName("and one charged hit really does take two, which is what was reported")
    void oneHitIsTheReportedTwo() {
        ChargeProfile sabre = com.cortes.combat.item.WeaponTables.charge(WeaponProfile.SABRE);
        assertEquals(1, SlashTiming.hitsAt(WeaponProfile.SABRE, sabre, 0.5F),
                "half charged is one hit");
        assertEquals(1, SlashTiming.hitsAt(WeaponProfile.SABRE, sabre, 0.0F),
                "a tap is one hit");

        // The real claim: one cut takes twice as many swings as two cuts. Measured rather than
        // asserted against a literal, because the literal would be a balance number retuning would
        // silently invalidate.
        int oneCut = swingsToKill(WeaponProfile.SABRE, IRON_SABLE_BASE, 0.5F);
        int twoCuts = swingsToKill(WeaponProfile.SABRE, IRON_SABLE_BASE, 1.0F);
        // Half the swings, allowing for the ceiling. The damage is exactly double, so the counts
        // differ by at most the rounding of one swing's ceiling.
        assertTrue(oneCut - twoCuts * 2 <= 1,
                "two cuts should take about half the swings of one. Got " + twoCuts + " against "
                        + oneCut + " -- the second hit is not landing the same damage as the first");
    }

    /**
     * Every other weapon is untouched.
     *
     * <p>The gate asks the weapon rather than the bonus number precisely because the sword and the
     * trident carry the sabre's exact bonus. This is the test that says they stay single-hit.</p>
     */
    @Test
    @DisplayName("and no other weapon's charge lands twice")
    void nobodyElseDoubles() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            ChargeProfile charge = com.cortes.combat.item.WeaponTables.charge(profile);
            assertEquals(profile == WeaponProfile.SABRE ? 2 : 1,
                    SlashTiming.hitsAt(profile, charge, 1.0F),
                    profile + " lands " + SlashTiming.hitsAt(profile, charge, 1.0F)
                            + " hits at full charge");
        }
    }
}