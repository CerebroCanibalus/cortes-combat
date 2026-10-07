package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponMaterial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the ropera's tip is the tier's own nugget, and not one metal's nugget for all of them.
 *
 * <h2>The bug this exists for</h2>
 *
 * <p>The ropera is the one grid whose two metal cells want different things, so its {@code *} is
 * written by hand rather than falling out of a tag. For the three metals below netherite that cell
 * was a single tag, {@code cortescombat:ropera_tips}, whose only member was our copper nugget --
 * so <b>an iron ropera and a golden ropera both asked for copper</b>. One tag is one set, and a set
 * cannot say "the metal's own nugget".</p>
 *
 * <p>The tag's own javadoc already said "copper nuggets on copper, iron nuggets on iron", which is
 * the intent and is exactly what a single tag cannot express. Three tiers that want three different
 * things are three ingredients.</p>
 *
 * <p>Pure, and reachable: {@code IngredientRef} is a record of a string and a boolean, and
 * {@code WeaponFamily} holds no Minecraft type, so this walks the real catalogue rather than a copy
 * of it.</p>
 */
class RoperaTipTest {

    private static WeaponFamily.IngredientRef tip(WeaponMaterial material) {
        return WeaponFamily.ROPERA.extraIngredient(material);
    }

    /** The three metals below netherite, and the whole point. */
    @Test
    @DisplayName("the tip is the metal's own nugget, per tier")
    void theTipIsTheMetalsOwnNugget() {
        assertEquals("cortescombat:copper_nugget", tip(WeaponMaterial.COPPER).id(),
                "a copper ropera is tipped with our copper nugget");
        assertEquals("minecraft:iron_nugget", tip(WeaponMaterial.IRON).id(),
                "**an iron ropera is tipped with iron, not copper.** This and the gold line below "
                        + "were one tag holding a single copper nugget, so both tiers wanted the "
                        + "same metal -- and a player reading 'Iron Ropera' would have been told "
                        + "to bring copper");
        assertEquals("minecraft:gold_nugget", tip(WeaponMaterial.GOLDEN).id(),
                "and a golden one with gold");

        assertFalse(tip(WeaponMaterial.IRON).tag(),
                "and they are items and not tags. A tag is the mechanism that could not express "
                        + "the per-tier rule: one tag is one set, and these three want three sets");
    }

    /**
     * The other three, unchanged, and named so a later edit has to mean them.
     *
     * <p>Stone, diamond and netherite never went through the tag: a flint, an emerald and a gold
     * ingot, because the rule is about the shape of the object and not about spending the most
     * expensive thing in the game on a decoration.</p>
     */
    @Test
    @DisplayName("the other three tips are what the design drew")
    void theOtherTiersAreUnchanged() {
        assertEquals("minecraft:flint", tip(WeaponMaterial.STONE).id());
        assertEquals("minecraft:emerald", tip(WeaponMaterial.DIAMOND).id());
        assertEquals("minecraft:gold_ingot", tip(WeaponMaterial.NETHERITE).id());
    }

    /**
     * Every material the ropera exists in has a tip, and the wooden one is not asked.
     *
     * <p>Walks the catalogue instead of a written list, so a material added to the ropera without a
     * tip fails here rather than at datagen with a message that names a character.</p>
     */
    @Test
    @DisplayName("every metal the ropera exists in has a tip, and wood is not one of them")
    void everyRoperaMaterialHasATip() {
        int seen = 0;
        for (WeaponMaterial material : WeaponMaterial.values()) {
            if (!WeaponFamily.ROPERA.existsIn(material)) {
                continue;
            }
            seen++;
            WeaponFamily.IngredientRef ref = tip(material);
            assertNotNull(ref, material + " has a ropera and no tip");
            assertTrue(!ref.id().isBlank(), material + " has a ropera and a blank tip");
        }
        assertTrue(seen > 0, "the ropera exists in something, or this test is about nothing");
        assertFalse(WeaponFamily.ROPERA.existsIn(WeaponMaterial.WOODEN),
                "**no wooden ropera.** A thin straight blade with no tier at the bottom is not "
                        + "something you make out of sticks, and the switch in extraIngredient "
                        + "throws for wood on that basis");
    }
}
