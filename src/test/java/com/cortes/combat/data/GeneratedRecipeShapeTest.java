package com.cortes.combat.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three recipes a playtest found wrong, asserted against the file the game reads.
 *
 * <h2>Why the filesystem and not the provider</h2>
 *
 * <p>Because every one of these was a provider that looked right. The nugget asked for nine ingots
 * because its helper hardcoded nine ingredients for both directions; the martillo was a row of
 * three because its grid string said so; the arcabuz's bamboo sat one column over from the design.
 * A test that asked the provider would have asked the code that was wrong. These read the JSON the
 * game loads, which is the only place the three are the same question.</p>
 */
class GeneratedRecipeShapeTest {

    private static final Path RECIPES =
            Path.of("src", "generated", "resources", "data", "cortescombat", "recipe");

    private static String read(String name) throws IOException {
        return Files.readString(RECIPES.resolve(name + ".json"), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /**
     * The copper nugget is one ingot, like iron and gold.
     *
     * <p><b>It shipped asking for nine.</b> The helper that writes both directions hardcoded nine
     * ingredients, so the spread recipe wanted nine ingots for nine nuggets -- a conversion no
     * player would ever make, and the one they would read as a bug the first time they tried.</p>
     */
    @Test
    @DisplayName("the copper nugget is one ingot, not nine")
    void theNuggetIsOneIngot() throws IOException {
        String json = read("copper_nugget");

        assertEquals(1, countOccurrences(json, "\"item\": \"minecraft:copper_ingot\""),
                "**one ingot in, nine out**, the way vanilla's own nugget conversions work. Nine "
                        + "was the hardcoded count in the helper, applied to a direction that is "
                        + "not the nine");
        assertTrue(json.contains("\"count\": 9"),
                "and it still yields nine, so the pair is the two halves of one conversion");
    }

    /**
     * The martillo is a three-wide head with a block at its centre, over a two-stick haft.
     *
     * <p><b>The centre cell is the whole correction, and it is a BLOCK.</b> The head was already
     * three wide -- the same silhouette as a pickaxe, which is unavoidable -- and what tells them
     * apart at the table is the middle: an iron block reads as a block, where an iron ingot read as
     * one more of the same three. Netherite takes a gold block.</p>
     */
    @Test
    @DisplayName("the martillo is a three-wide head with an iron block at its centre")
    void theMartilloHeadHasABlockAtItsCentre() throws IOException {
        String json = read("iron_martillo");

        assertTrue(json.contains("\"pattern\": [ \"X*X\", \" / \", \" / \" ]"),
                "**the head is three wide over a two-stick haft**, the way the design drew it. Got: "
                        + json);
        assertTrue(json.contains("\"*\": { \"item\": \"minecraft:iron_block\" }"),
                "**the centre is an iron BLOCK**, not an ingot: the head is one solid piece, and a "
                        + "block is how the recipe says denser");
        assertTrue(json.contains("\"/\": { \"item\": \"minecraft:stick\" }"),
                "and the haft is still two sticks, which is what makes the head sit on top of it");

        assertTrue(read("netherite_martillo").contains("\"*\": { \"item\": \"minecraft:gold_block\" }"),
                "and netherite is the one tier whose core is gold");
    }

    /**
     * The arcabuz's bamboo is in the first column, where the design drew it.
     *
     * <p>The design's grid is {@code /#V} over {@code #O#} over {@code #MM}, and the code had the
     * bamboo one column to the right -- so the pattern on disk was a different shape from the one
     * in the design document and the playtest image, and the two had been disagreeing quietly.</p>
     */
    @Test
    @DisplayName("the arcabuz's bamboo is in the first column, and the planks close the bottom")
    void theArcabuzMatchesTheDesign() throws IOException {
        String json = read("arcabuz");

        assertTrue(json.contains("\"pattern\": [ \"/ V\", \" O \", \" MM\" ]"),
                "the bamboo at the left of the top row and the firer at the right, the body in "
                        + "the middle and two planks closing the bottom. Got: " + json);
        assertTrue(json.contains("\"V\": { \"item\": \"minecraft:flint_and_steel\" }"),
                "and the firer is still the flint and steel: a black powder gun without one reads "
                        + "as a mistake");
    }
}
