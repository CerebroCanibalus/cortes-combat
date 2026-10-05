package com.cortes.combat.core.crafting;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a pattern of one size can sit inside a grid of another.
 *
 * <h2>Why this is a separate class and not a loop inside the recipe</h2>
 *
 * <p><b>Because the loop is the bug.</b> The arithmetic is four nested bounds and one off-by-one
 * waiting to happen, and it decides whether a weapon can be crafted at all: too tight and the daga
 * only works in the corner it was drawn in, which is the exact complaint this exists to answer; too
 * loose and a recipe matches a grid that has items in it, which is worse than not matching.</p>
 *
 * <p>{@code ShiftableShapedRecipe} has to ask this question from inside a method that takes a
 * {@code CraftingInput}, which is a Minecraft type and therefore untestable in this project. So the
 * geometry lives here, takes four ints and returns coordinates, and the recipe asks it.</p>
 *
 * <h2>The rule, and why it is a rule</h2>
 *
 * <p>A placement is valid when the pattern fits inside the grid <em>at all</em>. So the number of
 * placements of a {@code w}x{@code h} pattern in a {@code W}x{@code H} grid is
 * {@code (W - w + 1) * (H - h + 1)}, and it is <b>zero</b> when the pattern is larger than the grid
 * &mdash; which is the case that has to be answered rather than clamped, because a negative answer
 * here is what stops a 3x3 pattern matching a 2x2 inventory.</p>
 *
 * <p>Pure, and it takes no Minecraft type, so it is the half of this that a test can reach.</p>
 */
public final class RecipeGrid {

    private RecipeGrid() {
    }

    /**
     * How many places a pattern can sit in a grid.
     *
     * <p><b>Zero when the pattern does not fit, and never negative.</b> A negative here would be
     * caught by nobody: the caller loops over the placements and a negative count simply produces
     * an empty list, so the recipe would silently never match and the item would be uncraftable
     * with no error anywhere.</p>
     */
    public static int placementsFor(int patternWidth, int patternHeight,
                                    int gridWidth, int gridHeight) {
        if (patternWidth > gridWidth || patternHeight > gridHeight) {
            return 0;
        }
        return (gridWidth - patternWidth + 1) * (gridHeight - patternHeight + 1);
    }

    /**
     * Every placement, as {@code [x, y]} pairs: the top-left corner of the pattern in the grid.
     *
     * <p><b>Row-major, top row first.</b> The order is not arbitrary and does not affect whether a
     * recipe matches, but it is written down because the first placement is the top-left corner and
     * that is the one the book shows, so a test can ask about it by name.</p>
     */
    public static List<int[]> placements(int patternWidth, int patternHeight,
                                         int gridWidth, int gridHeight) {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y + patternHeight <= gridHeight; y++) {
            for (int x = 0; x + patternWidth <= gridWidth; x++) {
                out.add(new int[]{x, y});
            }
        }
        return out;
    }
}