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
            Path.of("src", "generated", "resources", "data", "cortescombat", "recipe");

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
     * The arcabuz is obtainable, and this is the grid the design drew.
     *
     * <p>This method used to assert the opposite -- that there was <em>no</em> arcabuz recipe --
     * because a weapon nobody can make is a gap, and a named gap is better than a silent one. The
     * grid is now decided, so the test has been turned around rather than deleted: an assertion
     * that a file is missing goes quiet the moment it appears, and this one says what the file
     * has to contain instead.</p>
     *
     * <pre>
     *   /#V      bamboo / flint and steel
     *   #O#      dispenser
     *   #MM      any planks
     * </pre>
     */
    @Test
    @DisplayName("the arcabuz is craftable, in the vertical grid the design drew")
    void theArcabuzIsCraftable() {
        String json = read(RECIPES.resolve("arcabuz.json"));

        assertTrue(json.contains("\"cortescombat:shiftable_shaped\""), json
                + "\n\nshaped: the silhouette is the recipe, three rows of three. And it is OURS and "
                + "not minecraft:crafting_shaped -- same format, same fields, a class that also "
                + "accepts the pattern in another corner, which a 3x3 pattern in a 3x3 bench has "
                + "exactly one of");
        assertTrue(json.contains("\"minecraft:bamboo\""), json + "\n\nno bamboo: the barrel/ramrod");
        assertTrue(json.contains("\"minecraft:flint_and_steel\""), json
                + "\n\nno flint and steel: the only vanilla item whose whole job is making sparks, "
                + "and its absence from a black powder recipe reads as a mistake");
        assertTrue(json.contains("\"minecraft:dispenser\""), json
                + "\n\nno dispenser: the body, and the item that gates when the gun arrives");
        assertTrue(json.contains("minecraft:planks"), json
                + "\n\nthe wood cell is a TAG and not an item, so the recipe works with any plank "
                + "mod adds. Hard-coding oak would make every other wood in the pack wrong");
        assertTrue(json.contains("cortescombat:arcabuz"), json);

        // The shape, not just the ingredients: four materials arranged wrong is a different
        // weapon, and the silhouette is the whole argument for this recipe existing separately
        // from the catalogue's five grids.
        //
        // The rows carry their spaces, and the spaces are the assertion. An earlier version of
        // this test expected the trimmed two-wide form and failed on all three rows, because
        // ShapedRecipePattern writes what it was given rather than trimming the empty border --
        // so the file on disk is the design's three columns of three, gaps included.
        //
        // And it cannot be "#": that symbol has no entry in the key and the generator rejects it
        // by name before it ever writes a file.
        assertTrue(json.contains("\"pattern\""), json + "\n\nno pattern block");
        assertTrue(json.contains("\"/ V\""), json
                + "\n\nthe top row is the ramrod on one shoulder and the firer on the other, with "
                + "air between them: the asymmetry is the shape. **The bamboo is in the FIRST "
                + "column**, which is where the design drew it (`/#V`) and where the code did not "
                + "put it for two rounds -- the pattern on disk and the pattern in the design "
                + "document disagreed quietly until a playtest image showed it");
        assertTrue(json.contains("\" O \""), json
                + "\n\nthe body is alone on its own row, air either side");
        assertTrue(json.contains("\" MM\""), json + "\n\nand the stock is two planks wide");
        assertEquals(3, countOccurrences(json, "\"/ V\"")
                        + countOccurrences(json, "\" O \"")
                        + countOccurrences(json, "\" MM\""),
                "three rows of three, and the vertical silhouette is the recipe:\n" + json);
    }

    /**
     * And the weapon it makes is the one item in the game that is not a catalogue entry.
     *
     * <p>Stated because the grid could be added to {@code CortesRecipeProvider} instead, which
     * would mean looping thirty families by material and special-casing the thirty-first. The
     * recipe loading is not the assertion; the shape of the code that produces it is.</p>
     */
    @Test
    @DisplayName("and its recipe book entry exists, which the firestone's deliberately does not")
    void theArcabuzHasAnAdvancement() {
        Path advancement = RECIPES.resolve("..").resolve("advancement").resolve("arcabuz.json");
        assertTrue(Files.isRegularFile(advancement),
                "missing: " + advancement + " -- a weapon with a recipe and no entry appears in "
                        + "the book as something already discovered, which teaches nothing. The "
                        + "firestone has none on purpose because it is an ingredient; this is a "
                        + "weapon and a player who never sees the entry never learns it exists");
        String json = read(advancement);
        assertTrue(json.contains("minecraft:dispenser"), json
                + "\n\nunlocked by a dispenser, not by a flint and steel: gravel plus iron arrives "
                + "in the first ten minutes, and an entry that unlocks when the player has been "
                + "able to make one all game teaches nothing");
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
