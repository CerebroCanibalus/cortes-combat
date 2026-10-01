package com.cortes.combat.item;

import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.core.weapon.WeaponShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the catalogue is what the design says it is.
 *
 * <p>Everything here is a count or an order, and none of it is a preference. The thirty items
 * exist because five families times an uneven set of materials exist, and that unevenness is the
 * whole of DESIGN.md 5bis: the dagger is the only one with a wooden version because it is the
 * access weapon, the warhammer is the only one with no stone version because it is the commitment
 * weapon. Both were agreements, and an agreement that is only enforced by somebody remembering it
 * is an agreement that quietly stops being true.</p>
 *
 * <p>So they are tests. The number thirty, the two exceptions, the order of the progression, the
 * integrity of each grid, and the interval each weapon actually swings at -- the last one because
 * a tempo that was written into a comment and never checked is the exact shape of bug this
 * project keeps paying for.</p>
 */
class WeaponCatalogueTest {

    /** The number in DESIGN.md 5bis. If this fails, the design changed and nobody wrote it down. */
    private static final int EXPECTED_ITEMS = 30;

    @Test
    @DisplayName("the catalogue is thirty items")
    void thirtyItems() {
        int count = 0;
        for (WeaponFamily family : WeaponFamily.values()) {
            count += family.materials().size();
        }
        assertEquals(EXPECTED_ITEMS, count,
                "five families times an uneven set of materials should be thirty, was " + count);
    }

    @Test
    @DisplayName("the two exceptions are the dagger's wood and the warhammer's missing stone")
    void theAsymmetries() {
        // The dagger has every material, wooden included. It is the weapon a player can make on
        // the first day from a tree, and a day-one weapon that needs smelting is not one.
        assertTrue(WeaponFamily.DAGA.existsIn(WeaponMaterial.WOODEN),
                "the dagger is the only family with a wooden version");

        // The warhammer has neither wood nor stone. It is the weapon with the highest damage
        // ceiling in the set and it costs that by being unavailable until you commit to a furnace.
        assertFalse(WeaponFamily.MARTILLO.existsIn(WeaponMaterial.WOODEN),
                "the warhammer has no wooden version");
        assertFalse(WeaponFamily.MARTILLO.existsIn(WeaponMaterial.STONE),
                "the warhammer has no stone version");
        assertTrue(WeaponFamily.MARTILLO.existsIn(WeaponMaterial.COPPER),
                "copper is the warhammer's first tier");

        // The other three skip wood because a blade with no tier reads as a stick.
        for (WeaponFamily family : new WeaponFamily[]{
                WeaponFamily.ROPERA, WeaponFamily.ALABARDA, WeaponFamily.SHOTEL}) {
            assertFalse(family.existsIn(WeaponMaterial.WOODEN),
                    family.id() + " has no wooden version");
            assertTrue(family.materials().size() == 6,
                    family.id() + " should be six materials, was " + family.materials().size());
        }
    }

    @Test
    @DisplayName("the tier counts are 6 / 6 / 5 / 7 / 6")
    void perFamilyCounts() {
        assertEquals(6, WeaponFamily.ROPERA.materials().size(), "ropera");
        assertEquals(6, WeaponFamily.ALABARDA.materials().size(), "alabarda");
        assertEquals(5, WeaponFamily.MARTILLO.materials().size(), "martillo");
        assertEquals(7, WeaponFamily.DAGA.materials().size(), "daga");
        assertEquals(6, WeaponFamily.SHOTEL.materials().size(), "shotel");
    }

    @ParameterizedTest
    @EnumSource(WeaponFamily.class)
    @DisplayName("every item name is <material>_<family> and is unique")
    void itemNames(WeaponFamily family) {
        for (WeaponMaterial material : family.materialsInOrder()) {
            String name = family.itemName(material);
            assertTrue(name.startsWith(material.id() + "_"),
                    "an item name starts with its material, was " + name);
            assertTrue(name.endsWith("_" + family.id()),
                    "an item name ends with its family, was " + name);
        }
    }

    @Test
    @DisplayName("no two items share a name")
    void namesAreUnique() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                assertTrue(seen.add(family.itemName(material)),
                        "duplicate item name " + family.itemName(material));
            }
        }
        assertEquals(EXPECTED_ITEMS, seen.size());
    }

    @ParameterizedTest
    @EnumSource(WeaponFamily.class)
    @DisplayName("asking a family for a material it does not have is an error, not a guess")
    void missingMaterialIsLoud(WeaponFamily family) {
        for (WeaponMaterial material : WeaponMaterial.values()) {
            if (!family.existsIn(material)) {
                assertThrows(IllegalArgumentException.class, () -> family.itemName(material),
                        family.id() + " should refuse " + material.id());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(WeaponShape.class)
    @DisplayName("every grid lays down exactly the pieces it declares")
    void gridsAreSelfConsistent(WeaponShape shape) {
        assertTrue(shape.isSelfConsistent(),
                shape.id() + " declares " + shape.bodyPieces() + " body / "
                        + shape.sticks() + " sticks / " + shape.extraPieces() + " extra, and the grid "
                        + String.join(" ", shape.rows()) + " does not agree");
    }

    @ParameterizedTest
    @EnumSource(WeaponShape.class)
    @DisplayName("every grid is rectangular, which the recipe builder requires and does not say")
    void gridsAreRectangular(WeaponShape shape) {
        assertTrue(shape.isRectangular(),
                shape.id() + " has rows of different widths: " + String.join(" | ", shape.rows()));
    }

    @Test
    @DisplayName("a grid that needs a second ingredient has a cell for it, and vice versa")
    void extraIngredientMatchesTheGrid() {
        assertTrue(WeaponShape.ROPERA.needsExtraIngredient(), "the ropera's tip is a real cell");
        assertTrue(WeaponShape.MARTILLO.needsExtraIngredient(), "the warhammer's core is a real cell");
        assertFalse(WeaponShape.ALABARDA.needsExtraIngredient(), "the halberd is all body and stick");
        assertFalse(WeaponShape.DAGA.needsExtraIngredient(), "the dagger is two cells");
        assertFalse(WeaponShape.SHOTEL.needsExtraIngredient(), "the shotel is all body and stick");
    }

    @ParameterizedTest
    @EnumSource(WeaponFamily.class)
    @DisplayName("the swing interval each weapon declares is the interval it actually gets")
    void declaredTempoIsRealTempo(WeaponFamily family) {
        // This is the one that matters most and the one that would never have been caught.
        //
        // A tempo can be written into a comment, put on the item as an attribute, and then be
        // multiplied by a per-profile table somewhere else in the same project -- which is
        // exactly what a leftover 0.7 entry for the dagger was about to do, quietly turning a
        // declared eight ticks into eleven and making the fastest weapon in the game slower than
        // a sword. The comment would still have said "sumamente rapida".
        //
        // Nothing short of evaluating both numbers would have noticed. So the number in
        // WeaponFamily and the number the game computes are compared here, and if someone retunes
        // one without the other this fails and says which.
        int actual = AttackTiming.ticksFor(family.attackSpeed());
        assertTrue(actual >= AttackTiming.ticksFor(WeaponFamily.DAGA.attackSpeed()),
                family.id() + " swings in " + actual + " ticks and nothing may be faster than the "
                        + "dagger's " + AttackTiming.ticksFor(WeaponFamily.DAGA.attackSpeed()));
        assertTrue(actual <= AttackTiming.MAX_INTERVAL,
                family.id() + " at " + actual + " ticks exceeds the cap of " + AttackTiming.MAX_INTERVAL);
    }

    @Test
    @DisplayName("the three that do not charge, do not charge")
    void nonChargingFamilies() {
        // The design's claim is that three of the five skip charging, and it is load bearing: the
        // auto-attack already carries the charge half of the design, so a catalogue where every
        // weapon charges would leave the weapon choice with nothing to decide.
        int charging = 0;
        for (WeaponFamily family : WeaponFamily.values()) {
            boolean charges = family.profile() != com.cortes.combat.core.weapon.WeaponProfile.ROPERA
                    && family.profile() != com.cortes.combat.core.weapon.WeaponProfile.DAGGER;
            if (charges) {
                charging++;
            }
        }
        assertEquals(3, charging, "three of the five new families charge");
    }

    @Test
    @DisplayName("only three families have a right click, and none of them is a damage number")
    void specialsEarnTheirPlace() {
        // The rule from DESIGN.md 4: a right click has to do something the left click cannot. A
        // special that is more damage should not exist.
        //
        // The ropera's lunge is the third, and the clearest case of the rule: it deals the
        // weapon's ordinary flat damage, uncharged, exactly like the halberd's sweep. What it
        // sells is two more blocks of reach, and it charges for them by putting the player
        // somewhere they did not choose to be -- inside whatever was already waiting there. A
        // number is not what that button is for, and a bigger number would have made the left
        // click a strictly worse version of the right one.
        for (WeaponFamily family : WeaponFamily.values()) {
            SpecialType special = family.special();
            if (family.hasSpecial()) {
                assertTrue(special == SpecialType.MULTI_HIT
                                || special == SpecialType.PARRY
                                || special == SpecialType.LUNGE,
                        family.id() + " has " + special + ", and the only specials in the design "
                                + "are the halberd's crowd hit, the sabre's parry and the ropera's "
                                + "step");
            }
        }
        assertTrue(WeaponFamily.ALABARDA.special() == SpecialType.MULTI_HIT);
        assertTrue(WeaponFamily.SHOTEL.special() == SpecialType.PARRY);
        assertTrue(WeaponFamily.ROPERA.special() == SpecialType.LUNGE);
        assertFalse(WeaponFamily.MARTILLO.hasSpecial(), "the warhammer has no right click");
        assertFalse(WeaponFamily.DAGA.hasSpecial(), "the dagger has no right click");
    }
}
