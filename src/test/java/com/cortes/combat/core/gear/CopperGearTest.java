package com.cortes.combat.core.gear;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The copper set's identity, as numbers a test can read.
 *
 * <p>Copper is a material with a trade rather than a tier: it protects more than iron and lasts
 * less, and the tools are between stone and iron. Those are three sentences, and each one is a
 * comparison between two numbers -- which is exactly the kind of thing that drifts when someone
 * retunes one of the pair and forgets the other. A guard per sentence.</p>
 */
class CopperGearTest {

    /** Iron is fifteen and chainmail twelve; copper sits above both. */
    private static final int IRON_TOTAL_DEFENSE = 15;

    /** Iron's armour multiplier, and the number copper has to stay under. */
    private static final int IRON_ARMOR_MULTIPLIER = 15;

    @Test
    @DisplayName("copper armour protects more than iron and chainmail")
    void armourProtectsMoreThanIron() {
        assertTrue(CopperGear.TOTAL_DEFENSE > IRON_TOTAL_DEFENSE,
                "copper's " + CopperGear.TOTAL_DEFENSE + " has to beat iron's " + IRON_TOTAL_DEFENSE
                        + ". The design put copper's armour above iron and paid for it with "
                        + "durability; equal protection would be the trade disappearing.");
    }

    @Test
    @DisplayName("copper armour lasts less than iron's, by a lot")
    void armourLastsLessThanIron() {
        assertTrue(CopperGear.ARMOR_DURABILITY_MULTIPLIER < IRON_ARMOR_MULTIPLIER,
                "copper's multiplier is " + CopperGear.ARMOR_DURABILITY_MULTIPLIER
                        + " and iron's is " + IRON_ARMOR_MULTIPLIER
                        + ". Above iron's would make the armour strictly better than iron's, which "
                        + "is not what the design asked for.");
    }

    @Test
    @DisplayName("the tools mine between stone and iron")
    void toolsMineBetweenStoneAndIron() {
        assertTrue(CopperGear.MINING_SPEED > 4.0F && CopperGear.MINING_SPEED < 6.0F,
                "speed " + CopperGear.MINING_SPEED + " is not between stone's 4 and iron's 6. "
                        + "Copper's tools are the cheap half of the set and the speed is the only "
                        + "stat that says so.");
    }

    @Test
    @DisplayName("every copper name is unique and none collides with the catalogue's")
    void namesAreUnique() {
        HashSet<String> seen = new HashSet<>();
        for (String name : CopperGear.ALL_NAMES) {
            assertTrue(seen.add(name), "duplicate copper item name: " + name);
        }
        assertEquals(9, CopperGear.ALL_NAMES.size(),
                "five tools and four armour pieces, and a ninth would be a name nobody registered");
    }
}
