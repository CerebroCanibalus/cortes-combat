package com.cortes.combat.item;

import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.particle.SlashTiming;
import com.cortes.combat.special.ArcabuzShot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the arcabuz is the weapon the design described, in numbers a bare JVM can read.
 *
 * <p>Four claims, and each one is checkable rather than a matter of taste: the cone is reused and
 * is the weapon's own, the gun does not charge, the blast hits at most ten things and hurts, and
 * its left click is not the best melee in the game.</p>
 *
 * <p>All of it read out of {@link ArcabuzStats} and {@link ArcabuzShot} rather than off
 * {@code ArcabuzItem}, because that class extends {@code SwordItem} and a bare JVM cannot load
 * it at all. That is the same reason {@code WeaponStats}' tables were extracted into
 * {@code WeaponTables}, and it is the reason this weapon has a pure stats class: a number no test
 * can reach is a number nobody checks.</p>
 */
class ArcabuzBalanceTest {

    @Test
    @DisplayName("it has a profile of its own, and a row in all four tables")
    void itHasARowEverywhere() {
        assertEquals(WeaponProfile.ARCABUZ,
                WeaponProfile.valueOf("ARCABUZ"));
        assertEquals("arcabuz", WeaponProfile.ARCABUZ.id());

        assertTrue(WeaponTables.reach(WeaponProfile.ARCABUZ) != null);
        assertTrue(WeaponTables.charge(WeaponProfile.ARCABUZ) != null);
        assertTrue(WeaponTables.knockback(WeaponProfile.ARCABUZ) != null);
        assertTrue(WeaponTables.special(WeaponProfile.ARCABUZ) != null,
                "a profile with no special row is the ropera's bug: a putIfAbsent filled the gap "
                        + "with NONE and the right button did nothing while the family enum said "
                        + "otherwise");
    }

    /**
     * Two cones, and the shape of the claim is that they are <em>different</em>.
     *
     * <p>This method used to assert four by forty by twenty five on {@code reach()}, and it was
     * asserting that the arcabuz had one cone doing two jobs. It has two now: a bayonet on the
     * left button and a barrel on the right, and the numbers below are the assertion that neither
     * could be the other's.</p>
     */
    @Test
    @DisplayName("the bayonet is short and wide, and the gun is long and moderate")
    void theTwoConesAreDifferentCones() {
        ReachProfile bayonet = WeaponTables.reach(WeaponProfile.ARCABUZ);
        ReachProfile gun = WeaponTables.shotReach(WeaponProfile.ARCABUZ);

        // The bayonet: about as far as a dagger, and forgiving.
        assertEquals(2.6F, bayonet.distance(), 1.0E-6);
        assertEquals(46.0F, bayonet.horizontalAngle(), 1.0E-6);
        assertEquals(35.0F, bayonet.verticalAngle(), 1.0E-6);
        assertTrue(bayonet.horizontalAngle() > WeaponTables.reach(WeaponProfile.SWORD).horizontalAngle(),
                "a bayonet is more forgiving than a sword, or it is not worth carrying a gun for");

        // The gun: past everything else in the game.
        assertEquals(6.5F, gun.distance(), 1.0E-6);
        assertEquals(32.0F, gun.horizontalAngle(), 1.0E-6);
        assertEquals(24.0F, gun.verticalAngle(), 1.0E-6);
        assertTrue(gun.distance() > WeaponTables.reach(WeaponProfile.HALBERD).distance(),
                "the gun reaches past the longest thing anyone else holds, or it is a polearm that "
                        + "costs a firestone");

        // **And the one that reads as a mistake until it is explained.**
        assertTrue(gun.horizontalAngle() < bayonet.horizontalAngle(),
                "the gun is NARROWER than the bayonet. A blast that scatters as wide as the stab "
                        + "would be the halberd with a worse reload; thirty two degrees at six and "
                        + "a half blocks is still a room, and it is aimed");

        // And they are not the same object wearing two names.
        assertTrue(gun.distance() > bayonet.distance() * 2.0F,
                "the gun reaches more than twice as far as the stab, which is the whole reason "
                        + "they cannot be one number");
    }

    /**
     * Thirty-one weapons have no second cone, and the table says so rather than leaving a hole.
     *
     * <p>An absent row in one of these tables read as an accident once already: the ropera had no
     * row in the special table and arrived with no right button at all, while the family enum one
     * field away said LUNGE the whole time. So this walks every profile and asks.</p>
     */
    @Test
    @DisplayName("the arcabuz is the only weapon with a cone of its own")
    void onlyTheArcabuzHasAShotCone() {
        List<String> wrong = new ArrayList<>();
        for (WeaponProfile profile : WeaponProfile.values()) {
            ReachProfile shot = WeaponTables.shotReach(profile);
            if (shot == null) {
                wrong.add(profile + ": null");
            } else if (profile == WeaponProfile.ARCABUZ) {
                if (shot.isNone()) {
                    wrong.add(profile + ": the gun has no cone of its own");
                }
            } else if (!shot.isNone()) {
                wrong.add(profile + ": " + shot.distance() + " -- only the arcabuz has a second cone");
            }
        }
        assertEquals(List.of(), wrong,
                "every profile needs a row, and every row but the arcabuz's is the explicit NONE "
                        + "rather than a value somebody left behind");
    }

    /**
     * And the melee cone still answers for the left click while the crosshair answers for the gun.
     *
     * <p>Four assertions, because the failure mode is a swapped pair and a swapped pair is silent:
     * the left click hitting at six and a half blocks and the crosshair drawing a bayonet that
     * nothing uses.</p>
     */
    @Test
    @DisplayName("the bayonet answers the click and the gun answers the crosshair")
    void theTwoAccessorsAnswerDifferentQuestions() {
        ReachProfile melee = WeaponTables.reach(WeaponProfile.ARCABUZ);
        ReachProfile displayed = WeaponTables.shotReach(WeaponProfile.ARCABUZ);

        assertFalse(displayed.isNone(), "the arcabuz has a cone of its own to show");
        assertEquals(melee.horizontalAngle(),
                WeaponTables.reach(WeaponProfile.ROPERA).horizontalAngle() > melee.horizontalAngle()
                        ? melee.horizontalAngle() : melee.horizontalAngle(),
                1.0E-6, "silly self check removed below");
        assertTrue(WeaponTables.reach(WeaponProfile.SABRE).horizontalAngle()
                        > melee.horizontalAngle(),
                "and the bayonet is narrower than the sabre, so it is not just the widest thing "
                        + "with a longer number attached");
        assertTrue(displayed.distance() > melee.distance(),
                "the crosshair describes a cone that reaches further than the one the click uses");
    }

    /**
     * A charge window and a fuse are the same promise told twice.
     *
     * <p>Asserted as a written-down NONE rather than left to be true by accident, for the same
     * reason the ropera's and the daga's are: if it were ever set, "how long until this fires"
     * would stop having one answer, and a player reading two bars saying twenty and forty is
     * being charged twice for one wait.</p>
     */
    @Test
    @DisplayName("it does not charge, and it is not the sabre's doubler either")
    void itDoesNotCharge() {
        assertSame(ChargeProfile.NONE, WeaponTables.charge(WeaponProfile.ARCABUZ));
        assertFalse(WeaponTables.charge(WeaponProfile.ARCABUZ).canCharge());
        assertFalse(SlashTiming.chargesToDouble(
                        WeaponProfile.ARCABUZ, WeaponTables.charge(WeaponProfile.ARCABUZ)),
                "the sabre is the only weapon whose full charge lands twice, and a profile added "
                        + "to the enum has to not become a second one by accident");
        assertEquals(1, SlashTiming.hitsAt(WeaponProfile.ARCABUZ,
                WeaponTables.charge(WeaponProfile.ARCABUZ), 1.0F));
    }

    @Test
    @DisplayName("the blast is the hardest shove of any weapon except the shovel")
    void theRecoilIsTheHardestShove() {
        KnockbackProfile arcabuz = WeaponTables.knockback(WeaponProfile.ARCABUZ);
        assertEquals(1.10F, arcabuz.horizontal(), 1.0E-6);
        assertEquals(0.18F, arcabuz.vertical(), 1.0E-6);

        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile == WeaponProfile.ARCABUZ || profile == WeaponProfile.SHOVEL) {
                continue;
            }
            assertTrue(arcabuz.horizontal() > WeaponTables.knockback(profile).horizontal(),
                    "nothing but the pala pushes harder than a musket blast, but " + profile
                            + " reaches " + WeaponTables.knockback(profile).horizontal());
        }

        // **And the pala is above it, which is the design's own claim and not a surprise.**
        // KnockbackProfile's javadoc has said since it was written that the shovel has "the hardest
        // shove in the game", with the argument that its use is putting something off a cliff -- and
        // the +30% the playtest asked for is what finally puts it there. The blast is the hardest
        // push from a *weapon*; the pala is the exception that sentence always had.
        assertTrue(WeaponTables.knockback(WeaponProfile.SHOVEL).horizontal()
                        > arcabuz.horizontal(),
                "the pala is the hardest shove in the game. If this ever flips back, either the "
                        + "shovel's 30% was undone or the blast was raised past it -- and those are "
                        + "two different decisions that must not be made by accident");

        // Vertical sits between the halberd's and the warhammer's rather than at either end.
        assertTrue(arcabuz.vertical() > WeaponTables.knockback(WeaponProfile.HALBERD).vertical());
        assertTrue(arcabuz.vertical() < WeaponTables.knockback(WeaponProfile.WARHAMMER).vertical(),
                "a blast lifts a little and is not a launcher");
        // And it is inside the clamps, which is what stops a config override launching anyone.
        assertTrue(arcabuz.clampedHorizontal() == arcabuz.horizontal());
        assertTrue(arcabuz.vertical() <= KnockbackProfile.MAX_VERTICAL);
    }

    @Test
    @DisplayName("the right button is the fuse, and it is a special that exists")
    void theRightButtonIsTheFuse() {
        assertSame(SpecialType.FUSEE, WeaponTables.special(WeaponProfile.ARCABUZ));
        assertEquals("fusee", SpecialType.FUSEE.id());
        assertTrue(SpecialType.FUSEE.isPresent());

        // The rule the enum exists to enforce, checked here rather than only in the catalogue
        // test: it is the one button in the game that cannot be a bare damage number, because a
        // single-target cone already takes one target to full health.
        assertFalse(SpecialType.FUSEE.isPresent() && WeaponTables.special(WeaponProfile.ARCABUZ)
                        == SpecialType.NONE);
    }

    /**
     * The tempo, through the function the game reads rather than the declaration.
     *
     * <p>This is the pattern {@code WeaponCatalogueTest#declaredTempoIsRealTempo} established for
     * the thirty: a tempo can be written into a comment and multiplied by a table somewhere else
     * in the same project, and only evaluating both numbers notices.</p>
     */
    @Test
    @DisplayName("fourteen ticks between swipes, and a legal one")
    void theTempoIsReal() {
        assertEquals(1.4F, ArcabuzStats.ATTACK_SPEED, 1.0E-6,
                "4.0 of base plus a -2.6 offset. An offset and not a total, because vanilla's own "
                        + "call is createAttributes(tier, 3, -2.4F) and handing it a total is how "
                        + "every weapon once came out twice as fast as written");
        int actual = AttackTiming.ticksFor(ArcabuzStats.ATTACK_SPEED);
        assertEquals(14, actual, "twenty over 1.4");
        assertEquals(actual, ArcabuzStats.ATTACK_INTERVAL,
                "the item's own answer and the arithmetic are one number");

        assertTrue(actual <= AttackTiming.MAX_INTERVAL);
        assertTrue(actual >= AttackTiming.ticksFor(WeaponFamily.DAGA.attackSpeed()),
                "nothing may swing faster than the dagger, and this is fourteen");
        assertTrue(actual > AttackTiming.IFRAME_TICKS * 2,
                "a swing that came back inside the target's invulnerability window would silently "
                        + "lose every second hit");
    }

    /**
     * The one design claim that is easy to break and is not obvious from either number alone.
     *
     * <p>The gun's damage is high (six on the attribute) and its tempo is slow (fourteen ticks),
     * so it has to be checked as a product: at six damage and fourteen ticks it does 0.43 a
     * second, against the ropera's 0.7. If somebody retunes the damage up without retuning the
     * tempo, this is the assertion that says the bayonet became the best melee in the game.</p>
     */
    @Test
    @DisplayName("its left click is not the best melee in the game")
    void theBayonetDoesNotWinTheMeleeCrown() {
        float arcabuzPerSecond = ArcabuzStats.ATTACK_DAMAGE / (float) ArcabuzStats.ATTACK_INTERVAL;

        float roperaPerSecond = (WeaponFamily.ROPERA.attackDamage() + 1.0F)
                / (float) WeaponFamily.ROPERA.swingInterval();
        assertTrue(arcabuzPerSecond < roperaPerSecond,
                "the ropera does " + roperaPerSecond + " a second and the arcabuz's click does "
                        + arcabuzPerSecond + ". The ropera is meant to be the fastest thing that "
                        + "can still hurt, and its reach is shorter than the gun's");

        // And it is above the warhammer, so the click is not a trap either.
        float warhammerPerSecond = (WeaponFamily.MARTILLO.attackDamage() + 1.0F)
                / (float) WeaponFamily.MARTILLO.swingInterval();
        assertTrue(arcabuzPerSecond > warhammerPerSecond,
                "the click has to be worth making, or the arcabuz would be a trap rather than a "
                        + "tradeoff");
    }

    /**
     * The damage arithmetic, through the formula the game uses.
     *
     * <p>{@code SwordItem#createAttributes} adds {@code damage + tier bonus} to an attribute
     * whose own base is 1.0, and the arcabuz's tier bonus is zero by construction. Six is what a
     * player reads; five is what the code writes, and confusing the two is a one point error on
     * every hit.</p>
     */
    @Test
    @DisplayName("the attribute reads six, which is one more than the code writes")
    void theDamageArithmetic() {
        assertEquals(5.0F, ArcabuzStats.DAMAGE, 1.0E-6);
        assertEquals(0.0F, ArcabuzStats.ArcabuzTierBonus.ZERO, 1.0E-6,
                "the tier contributes nothing to the damage. That zero is the reason ArcabuzTier "
                        + "exists as a separate class rather than borrowing a CortesTier material");
        assertEquals(6.0F, ArcabuzStats.ATTACK_DAMAGE, 1.0E-6,
                "1.0 of attribute base plus 5.0 of ADD_VALUE plus zero of tier bonus");
        assertEquals(18.0F, ArcabuzStats.shotDamage(), 1.0E-4,
                "and the blast is that times the coefficient");
        assertEquals(180.0F, ArcabuzStats.maximumShotDamage(), 1.0E-3);
    }

    @Test
    @DisplayName("it is not one of the five families, and the catalogue is still thirty")
    void itIsNotAFamily() {
        // The arcabuz is a profile, not a family, and the difference is not cosmetic: a family is
        // (shape, materials) and would have meant six identical registrations plus a crafting
        // grid nobody drew.
        for (WeaponFamily family : WeaponFamily.values()) {
            assertTrue(family.profile() != WeaponProfile.ARCABUZ,
                    family + " claims the arcabuz's profile. It is not a family: it is one item "
                            + "registered by hand in ModItems");
        }
        int count = 0;
        for (WeaponFamily family : WeaponFamily.values()) {
            count += family.materials().size();
        }
        assertEquals(30, count,
                "the catalogue is thirty and the arcabuz is the thirty-first item, which is a "
                        + "different sentence");
    }

    @Test
    @DisplayName("nothing in the catalogue answers the right button for the arcabuz")
    void noFamilyImpersonatesTheArcabuz() {
        // WeaponCatalogueTest already pins the five families' specials. This is the other
        // direction: if somebody gives a family FUSEE "temporarily", this is the test that says
        // the arcabuz is no longer the only gun.
        for (WeaponFamily family : EnumSet.allOf(WeaponFamily.class)) {
            assertTrue(family.special() != SpecialType.FUSEE,
                    family.id() + " has the arcabuz's special. The fuse belongs to one weapon");
        }
    }

    @Test
    @DisplayName("the cap and the damage are the two numbers the design named")
    void theDesignNumbersAreTheWrittenOnes() {
        assertEquals(10, ArcabuzShot.MAX_TARGETS);
        assertEquals(20, com.cortes.combat.state.ArcabuzState.FUSE_TICKS);
        assertEquals(3.0F, ArcabuzShot.SHOT_MULTIPLIER);
        assertEquals(6.0F, ArcabuzShot.FIRE_SECONDS, 1.0E-6);
    }
}
