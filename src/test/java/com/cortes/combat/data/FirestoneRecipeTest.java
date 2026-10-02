package com.cortes.combat.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the firestone is actually obtainable, and that the arcabuz is not.
 *
 * <p><b>This class exists because of a bug that shipped looking finished.</b> The ropera's
 * copper tip names a nugget, the tag points at it, the recipe book has an entry -- and there was
 * no way in the world to obtain one, so the one weapon in the catalogue that could not be
 * crafted looked complete on paper. The lesson {@code NuggetRecipes} writes down is "a tag is
 * not a source", and this is that lesson as a test.</p>
 *
 * <p>It is also the honest place to record the thing that is <em>not</em> here: the arcabuz has
 * no recipe. Inventing a crafting grid for it would be deciding the design, and a weapon nobody
 * can make is a gap to be named rather than papered over.</p>
 */
class FirestoneRecipeTest {

    private static final Path RECIPES =
            Path.of("src", "generated", "resources", "data", "cortescombat", "recipes");

    private static final Path RECIPE = RECIPES.resolve("firestone.json");

    private static String read(Path path) {
        assertTrue(Files.isRegularFile(path), "missing: " + path
                + " -- a recipe datagen reports writing and the player cannot craft is exactly the "
                + "shape of bug this test was written for. Run gradlew runData");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /**
     * One gunpowder and one glowstone make two.
     *
     * <p>All four facts in one read, because a recipe that loses any one of them is a recipe
     * that means something other than what the design says: it is shapeless rather than a grid,
     * it is the two ingredients rather than one of them, it yields two rather than one, and it is
     * under {@code data/} rather than {@code assets/} where the loader never looks.</p>
     */
    @Test
    @DisplayName("one gunpowder and one glowstone make two firestones")
    void theRecipeIsWhatTheDesignSays() {
        String json = read(RECIPE);

        assertTrue(json.contains("\"minecraft:crafting_shapeless\""), json
                + "\n\nshapeless: the pair has no shape, it is a sack of powder and a sack of dust");
        assertTrue(json.contains("\"minecraft:gunpowder\""), json + "\n\nno gunpowder in it");
        assertTrue(json.contains("\"minecraft:glowstone_dust\""), json + "\n\nno glowstone in it");
        assertTrue(json.contains("\"count\": 2"), json
                + "\n\nand the yield is TWO. A yield of one makes a shot cost a gunpowder and a "
                        + "glowstone outright and the gun's rhythm stops being an economy");
        assertTrue(json.contains("cortescombat:firestone"), json);
        assertEquals(2, countOccurrences(json, "\"item\""),
                "exactly two ingredients. A third would be a recipe the design did not ask for: "
                        + json);
    }

    /** It lands under data/, which is the one half of the "silent failure" that is testable here. */
    @Test
    @DisplayName("and it is written under data/, not assets/")
    void itIsInTheDataPack() {
        assertTrue(Files.isRegularFile(RECIPE), RECIPE.toString());
        assertTrue(!RECIPE.toString().contains("assets"),
                "a recipe emitted into assets/ is a file the recipe loader never reads. The file "
                        + "exists, it parses, and no recipe appears in game");
    }

    /**
     * The arcabuz has no recipe, and that is a gap rather than a decision.
     *
     * <p>Written as a test so it is a <em>named</em> gap. The day someone adds the grid, this
     * fails and says so, and whoever adds it has to decide what it is -- which is the point.
     * Silently leaving it out is how a weapon ends up existing only in a creative tab.</p>
     */
    @Test
    @DisplayName("the arcabuz still has no recipe, which is a known gap")
    void theArcabuzHasNoRecipe() {
        assertTrue(!Files.isRegularFile(RECIPES.resolve("arcabuz.json")),
                "there IS an arcabuz recipe now. Good -- delete this test and say in the commit "
                        + "what the grid is, because it was decided here rather than asked for");
    }

    /** The nugget recipes next door are the precedent and they still have to be there. */
    @Test
    @DisplayName("and the nugget conversions are still generated")
    void theNuggetRecipesAreStillThere() {
        assertTrue(Files.isRegularFile(RECIPES.resolve("copper_nugget.json")));
        assertTrue(Files.isRegularFile(RECIPES.resolve("copper_ingot_from_nuggets.json")));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
