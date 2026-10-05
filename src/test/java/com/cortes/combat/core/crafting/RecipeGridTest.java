package com.cortes.combat.core.crafting;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That {@link RecipeGrid} says where a pattern fits, and says nothing when it does not.
 *
 * <h2>Why the numbers and not the behaviour</h2>
 *
 * <p>The recipe that asks these questions needs a {@code CraftingInput}, which is a Minecraft type,
 * and this project's tests run without Minecraft. So what is testable here is the arithmetic, and it
 * is the half where the bug lives: a loop with four bounds and one off-by-one decides whether a
 * weapon can be crafted at all, and neither failure throws.</p>
 *
 * <p><b>The two failures are opposite and both silent.</b> Too tight and the daga only works in the
 * corner it was drawn in &mdash; which is the complaint this whole thing answers. Too loose and a
 * pattern matches a grid with items in the cells around it, which is worse: a recipe that gives you
 * something for a different arrangement of the bench.</p>
 */
class RecipeGridTest {

    /**
     * The two shapes the project actually has, in the two grids they meet.
     *
     * <p><b>2x2 in 3x3 is the daga.</b> It is the only reason this class exists: vanilla requires
     * the grid to be the same size as the pattern, so a two-by-two recipe in a crafting table has
     * to be placed in one exact corner and works nowhere else.</p>
     */
    @Test
    @DisplayName("a 2x2 pattern has four places to sit in a 3x3 grid")
    void aTwoByTwoHasFourPlaces() {
        assertEquals(4, RecipeGrid.placementsFor(2, 2, 3, 3),
                "and it is exactly four, the four corners. Not three, not nine: three would leave "
                        + "the player guessing which corner works, and nine would let it match a "
                        + "grid with a block of items in the middle of it");
    }

    /** The other four weapons are 3x3, which is the crafting table's own size. */
    @Test
    @DisplayName("a 3x3 pattern has exactly one place in a 3x3 grid")
    void aThreeByThreeHasOnePlace() {
        assertEquals(1, RecipeGrid.placementsFor(3, 3, 3, 3),
                "the four big shapes already fill the bench, so shifting them changes nothing and "
                        + "the recipe still behaves exactly like vanilla's");
    }

    /** The same size means one placement, and that is the case vanilla already handled. */
    @Test
    @DisplayName("a pattern the size of the grid has one place")
    void sameSizeHasOnePlace() {
        assertEquals(1, RecipeGrid.placementsFor(2, 2, 2, 2),
                "the 2x2 player inventory is the smallest grid in the game and a 2x2 recipe has to "
                        + "work in it, or the daga is a crafting-table item and not a weapon");
    }

    /**
     * A pattern bigger than the grid is zero and never negative.
     *
     * <p><b>The failure that throws nothing.</b> {@code (W - w + 1) * (H - h + 1)} on a 3x3 pattern in
     * a 2x2 inventory is {@code 0 * 0}, which is fine, but {@code 2 - 3 + 1} alone is zero and a
     * 4-wide pattern in a 3-wide grid is {@code -1}, which multiplied by anything sensible is either
     * negative or zero depending on the other factor. A negative count loops zero times, so the
     * recipe would silently never match and the item would be uncraftable with no error anywhere.</p>
     */
    @Test
    @DisplayName("a pattern bigger than the grid has no places, and never a negative number")
    void tooBigHasNoPlaces() {
        assertEquals(0, RecipeGrid.placementsFor(3, 3, 2, 2),
                "a crafting-table shape in a player inventory does not fit, and the answer has to "
                        + "be a number the caller can loop over");
        assertEquals(0, RecipeGrid.placementsFor(4, 1, 3, 3),
                "and a pattern WIDER than the grid, which is the case that would come out negative "
                        + "if the formula were used unguarded");
        assertEquals(0, RecipeGrid.placementsFor(1, 4, 3, 3),
                "and TALLER than it, which is the same arithmetic on the other axis");
    }

    /** The coordinates, because a count can be right while the list is wrong. */
    @Test
    @DisplayName("the four places are the four corners, top-left first")
    void theFourPlacesAreTheCorners() {
        List<int[]> at = RecipeGrid.placements(2, 2, 3, 3);

        assertEquals(4, at.size(), "four placements");
        assertTrue(at.stream().anyMatch(p -> p[0] == 0 && p[1] == 0),
                "top-left corner. **This is the one the recipe book draws**, so it is the one a "
                        + "test can name: if the list started anywhere else, the book would show the "
                        + "recipe in a place that is not a place");
        assertTrue(at.stream().anyMatch(p -> p[0] == 1 && p[1] == 0), "top-right corner");
        assertTrue(at.stream().anyMatch(p -> p[0] == 0 && p[1] == 1), "bottom-left corner");
        assertTrue(at.stream().anyMatch(p -> p[0] == 1 && p[1] == 1),
                "bottom-right corner, and its top-left corner is at 1,1 which is the largest x "
                        + "and y that still fits a two-by-two in a three-by-three");
    }

    /**
     * Every placement keeps the pattern inside the grid.
     *
     * <p><b>The invariant, and it is what "too loose" breaks.</b> A placement whose {@code x} plus
     * the pattern width exceeds the grid width means the recipe reads a cell that does not exist,
     * and on this side that is an exception out of a crafting screen rather than a recipe that
     * quietly does not work.</p>
     */
    @Test
    @DisplayName("no placement puts the pattern outside the grid")
    void noPlacementEscapesTheGrid() {
        for (int pw = 1; pw <= 3; pw++) {
            for (int ph = 1; ph <= 3; ph++) {
                for (int gw = 1; gw <= 3; gw++) {
                    for (int gh = 1; gh <= 3; gh++) {
                        List<int[]> at = RecipeGrid.placements(pw, ph, gw, gh);
                        assertEquals(RecipeGrid.placementsFor(pw, ph, gw, gh), at.size(),
                                "the count and the list disagree for a " + pw + "x" + ph
                                        + " pattern in a " + gw + "x" + gh + " grid");
                        for (int[] p : at) {
                            assertTrue(p[0] >= 0 && p[1] >= 0 && p[0] + pw <= gw && p[1] + ph <= gh,
                                    "placement " + p[0] + "," + p[1] + " puts a " + pw + "x" + ph
                                            + " pattern outside a " + gw + "x" + gh + " grid");
                        }
                    }
                }
            }
        }
    }
}