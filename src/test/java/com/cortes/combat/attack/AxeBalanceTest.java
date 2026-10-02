package com.cortes.combat.attack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The axe: a weak base, a brutal charge, and armour that comes apart twice as fast.
 *
 * <p>Every number here is one of the seven the design asked for, and each is held against the
 * thing it has to be better or worse <em>than</em>. A weapon described in adjectives is a weapon
 * nobody can tune, because "brutal" and "not so good" do not compare to anything.</p>
 */
class AxeBalanceTest {

    private static final ChargeProfile AXE = WeaponTables.charge(WeaponProfile.AXE);
    private static final ReachProfile REACH = WeaponTables.reach(WeaponProfile.AXE);

    /** Vanilla's axe: seven damage at attack speed 1.0, unchanged by this mod. */
    private static final float VANILLA_AXE_DAMAGE = 7.0F;
    private static final float VANILLA_AXE_SPEED = 1.0F;

    /**
     * The slowest charge in the game, and the bare hands do not count.
     *
     * <p><b>The max, not the min.</b> The first version of this loop took the minimum and asserted
     * it was seventy, and it read twelve -- which is the bare hands, because punching has a
     * twelve tick charge. Bare hands are trivially fast at everything and are not a weapon.</p>
     */
    @Test
    @DisplayName("the charge is the slowest of any weapon in the game")
    void theChargeIsTheSlowest() {
        int slowest = 0;
        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile == WeaponProfile.UNARMED) {
                continue;
            }
            ChargeProfile charge = WeaponTables.charge(profile);
            if (!charge.canCharge()) {
                continue;
            }
            assertTrue(charge.fullChargeTicks() <= AXE.fullChargeTicks(),
                    profile + " charges in " + charge.fullChargeTicks()
                            + " ticks, which is slower than the axe's " + AXE.fullChargeTicks()
                            + ". The axe is meant to be the slowest charge in the game");
            slowest = Math.max(slowest, charge.fullChargeTicks());
        }
        assertEquals(60, slowest,
                "three seconds, and three times the sabre's twenty. It was forty, which tied the "
                        + "vanilla mace and was not a commitment at all");
    }

    /**
     * And the biggest thing the charge buys: exactly three uncharged swings.
     *
     * <p>A bonus of two is the largest in the game -- the sabre, the other commitment weapon, is
     * one -- and it turns seven damage into twenty one.</p>
     *
     * <p><b>And that number was found the hard way.</b> At a bonus of 1.2 the charged swing was
     * fifteen and a half damage over a ninety four tick cycle: 3.3 a second against the 5.8 of
     * tapping. A 44% loss, so the right play was never to charge, and the most distinctive thing
     * the weapon did would have been dead on arrival with the build green.</p>
     *
     * <p>A slow single hit always loses to tapping unless the multiplier is enormous. That is
     * arithmetic, so it is asserted rather than discovered in a playtest.</p>
     */
    @Test
    @DisplayName("the charge is the biggest, and worth three taps")
    void theChargeIsTheBiggest() {
        float biggest = 0.0F;
        for (WeaponProfile profile : WeaponProfile.values()) {
            ChargeProfile charge = WeaponTables.charge(profile);
            if (charge.canCharge()) {
                assertTrue(charge.bonus() <= AXE.bonus(),
                        profile + " has a bonus of " + charge.bonus() + ", over the axe's "
                                + AXE.bonus());
                biggest = Math.max(biggest, charge.bonus());
            }
        }
        assertEquals(2.0F, biggest, 1.0E-6,
                "and it is the largest. The sabre, which is the other commitment weapon, is 1.0");

        float charged = VANILLA_AXE_DAMAGE * AttackTiming.chargeMultiplier(AXE.bonus(), 1.0F);
        assertEquals(21.0F, charged, 1.0E-4,
                "seven base times three. The base is vanilla's and nothing scaled it");
        assertEquals(3.0F, charged / VANILLA_AXE_DAMAGE, 1.0E-4,
                "exactly three uncharged swings out of one charged one, which is the number the "
                        + "bonus exists to produce");
    }

    /**
     * The base is worse than everything else in the game, per swing and per second.
     *
     * <p><b>No damage scale was added, and this is the test that says so.</b> The brief asked for
     * "un daño que no sea tan bueno", and vanilla's axe is already seven damage at one attack per
     * second -- less than half the ropera's throughput. A multiplier on top of that would have been
     * tuning a number that already does the job, which is the third adjustment of a number that
     * does nothing being the thing that finally proves it does nothing.</p>
     */
    @Test
    @DisplayName("the base is worse than every other weapon without a multiplier")
    void theBaseIsTheWorstWithoutBeingScaled() {
        // Throughput per second, the axe included.
        int axeTicks = AttackTiming.scaleFor(VANILLA_AXE_SPEED, 0.85F);
        float axeDps = VANILLA_AXE_DAMAGE * 20.0F / axeTicks;

        assertEquals(24, axeTicks,
                "twenty four ticks from the 0.85 tempo. Vanilla's twenty is one tick slower than "
                        + "the warhammer's nineteen, which is not 'bastante lento', it is a "
                        + "rounding error a player cannot feel");

        // The ropera: eight damage at ten ticks.
        float roperaDps = 8.0F * 20.0F / 10;
        // The sword: nine at fifteen.
        float swordDps = 9.0F * 20.0F / 15;

        assertTrue(axeDps < roperaDps,
                "the axe does " + axeDps + " a second against the ropera's " + roperaDps);
        assertTrue(axeDps < swordDps,
                "and against the sword's " + swordDps);
        assertTrue(axeDps < 10.0F,
                "under ten a second. Anything higher and " + axeDps + " would be a slow sword "
                        + "rather than a weak axe with a brutal ceiling");

        // **And the charged version has to be within reach of tapping, not a trap.**
        //
        // Throughput is charge time PLUS the cooldown, because the player cannot swing again
        // until the swing they just made is over. Sixty plus twenty four is eighty four ticks for
        // twenty one damage.
        //
        // It is still slightly below tapping, and that is deliberate rather than a compromise: the
        // ropera's lunge and the halberd's sweep are both below it too, and what those buy is not
        // damage per second. What stops it being a trap is how close it is -- the first version of
        // this number was 44% below, and at that point the right play was to never charge.
        float chargedDps = 21.0F * 20.0F / (AXE.fullChargeTicks() + axeTicks);
        assertTrue(chargedDps > axeDps * 0.85F,
                "charging has to be within fifteen percent of tapping, and it is "
                        + chargedDps + " against " + axeDps
                        + ". Further below that and the charge is a trap rather than a choice, and "
                        + "the weapon's most distinctive mechanic is dead on arrival");
        assertTrue(chargedDps < axeDps,
                "and it stays below tapping on purpose. Same shape as the lunge and the sweep: "
                        + "the expensive option pays in what it does, not in damage per second");
    }

    /**
     * Longer than it is wide.
     *
     * <p>"Rango bueno, radio mediocre" is two different axes and the old row got both of them
     * backwards: three of reach with a forty five degree cone, i.e. the second widest cone in the
     * game on a short weapon.</p>
     */
    @Test
    @DisplayName("it reaches further than it is wide")
    void itReachesAndItIsNarrow() {
        assertEquals(3.4F, REACH.distance(), 1.0E-6,
                "up from three, and longer than the sword's three and a half. The axe closes "
                        + "what the ropera cannot");
        assertEquals(35.0F, REACH.horizontalAngle(), 1.0E-6,
                "down from forty five, and level with the sword. It is no longer the second widest "
                        + "cone in the game, which it was and should not have been");

        assertTrue(REACH.horizontalAngle() < WeaponTables.reach(WeaponProfile.SABRE).horizontalAngle(),
                "and nowhere near the sabre's seventy five");
        assertEquals(25.0F, REACH.verticalAngle(), 1.0E-6,
                "down from thirty five, and the tightest in the game after the ropera. A wide "
                        + "vertical would make an axe a thing that hits everything in front of it, "
                        + "which is the halberd");
        assertTrue(REACH.verticalAngle() < REACH.horizontalAngle(),
                "and it is narrower than it is wide, which is an axe and not a shovel");
    }

    /**
     * It breaks a guard in one charged hit and not in an uncharged one.
     *
     * <p>And it does not bypass armour, which is the difference from the warhammer. One weapon
     * reaches past a plate, one takes it apart; a single boolean for both would have made the axe
     * better than the warhammer at the warhammer's own thing.</p>
     */
    @Test
    @DisplayName("a full charge breaks a guard and an uncharged swing does not")
    void fullChargeBreaksTheGuard() {
        AttackResult uncharged = DamageResolver.resolve(
                VANILLA_AXE_DAMAGE, AXE, WeaponProfile.AXE, 0.5F, null);
        AttackResult charged = DamageResolver.resolve(
                VANILLA_AXE_DAMAGE, AXE, WeaponProfile.AXE, 1.0F, null);

        assertFalse(uncharged.axeCharged(),
                "half a charge is a normal swing. The guard takes its point the normal way, which "
                        + "is a point");
        assertTrue(charged.axeCharged(), "and a full one empties it");
        assertFalse(charged.ignoredArmour(),
                "without touching the armour. A charged axe hit is still reduced by a diamond "
                        + "plate; the weapon takes the plate apart faster rather than ignoring it");

        // And the two flags cannot both be true, because they belong to two different weapons.
        for (WeaponProfile profile : WeaponProfile.values()) {
            AttackResult result = DamageResolver.resolve(
                    7.0F, WeaponTables.charge(profile), profile, 1.0F, null);
            assertFalse(result.ignoredArmour() && result.axeCharged(),
                    profile + " resolved as both a warhammer and an axe at once");
        }
    }

    /** And the wear is doubled, not the damage. */
    @Test
    @DisplayName("the wear is doubled and the damage is not")
    void theWearIsDoubled() {
        assertEquals(2.0F, ArmorWearHandler.WEAR_MULTIPLIER, 1.0E-6,
                "and it is on the durability, not on the health loss. Doubling the damage too "
                        + "would have made the charged axe hit harder than the warhammer's through "
                        + "the same plate, which is the opposite of what a weaker weapon should do");
    }

    /** The knockback is the axe's own and unchanged: it does not lift things into the air. */
    @Test
    @DisplayName("the shove is its own and stays low")
    void theShoveIsItsOwn() {
        KnockbackProfile shove = WeaponTables.knockback(WeaponProfile.AXE);
        assertEquals(0.34F, shove.horizontal(), 1.0E-6);
        assertTrue(shove.vertical() < WeaponTables.knockback(WeaponProfile.WARHAMMER).vertical(),
                "and it lifts less than the warhammer's, which is the only weapon in the game "
                        + "whose job is putting things on the floor");
    }
}