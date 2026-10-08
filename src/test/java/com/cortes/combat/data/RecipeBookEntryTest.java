package com.cortes.combat.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every recipe this mod writes is one the recipe book will actually show.
 *
 * <h2>The bug, and why a test is the only honest way to hold it</h2>
 *
 * <p><b>A recipe with no advancement behind it does not appear in the book, and nothing says
 * so.</b> Verified against the 1.21.1 sources rather than remembered, because the symptom names
 * nothing:</p>
 *
 * <ul>
 *   <li>{@code RecipeCollection#canCraft} gates every recipe on
 *       {@code recipe.canCraftInDimensions(w, h) && book.contains(recipeholder)};</li>
 *   <li>{@code RecipeBook#contains} is {@code known.contains(recipe.id())};</li>
 *   <li>and {@code known} is filled from the recipes an advancement <em>grants</em> --
 *       {@code AdvancementRewards#recipes}, written under the JSON's {@code "recipes"} key.</li>
 * </ul>
 *
 * <p>So an advancement that names no recipe grants no recipe, and the book drops it one line before
 * drawing it. The catalogue was writing perfectly valid recipe advancements -- right parent, right
 * category, right criterion, the item as the icon -- that granted nothing at all.</p>
 *
 * <p><b>The failure is total and silent.</b> The recipe is in the jar, it parses (1290 of them and
 * zero errors), it is in the right tab, the crafting table makes it, {@code /give} hands it over,
 * and a player opening the book sees an empty tab. There is no log line at any level, and a JSON
 * this structurally valid is not something anybody re-reads looking for a missing field.</p>
 *
 * <p><b>And it was three files.</b> The catalogue, the arcabuz, and the firestone -- the last of
 * which had no advancement <em>at all</em>, by a written decision ("ingredients, not
 * discoveries") that was right about a toast and wrong about the book, because
 * {@code display.show_toast} and "names a recipe" are two different fields and only the first one
 * was being thought about. That made the game's only ammunition invisible.</p>
 *
 * <h2>Why the JSON is read by hand</h2>
 *
 * <p><b>Gson is not on this project's test classpath and the rule says it must not go there.</b>
 * {@code src/test} reaches nothing that belongs to Minecraft -- that is what keeps the pure maths
 * runnable on a bare JVM -- and Gson comes in with Minecraft. So the parse here is text, and it
 * looks for one key inside one object rather than building a tree: a hand parser that answers one
 * question is smaller than the dependency that would answer it properly.</p>
 */
class RecipeBookEntryTest {

    private static final Path ADVANCEMENTS =
            Path.of("src", "generated", "resources", "data", "cortescombat", "advancement");

    private static final Path RECIPES =
            Path.of("src", "generated", "resources", "data", "cortescombat", "recipe");

    /** The two conversions vanilla also hides, hidden here on purpose and by name. */
    private static final List<String> DELIBERATELY_HIDDEN =
            List.of("copper_nugget", "copper_ingot_from_nuggets");

    /** The firestone, filed under MISC because it is an ingredient and has no shape. */
    private static final String MISC_RECIPE = "firestone";

    /**
     * The recipes that are not weapons, and the book tab each one belongs in.
     *
     * <p><b>The equipment assertion below is about weapons, and it used to be about every recipe
     * that is not the firestone.</b> That held while the mod was thirty weapons and an ingredient;
     * the firestone block's two recipes are neither, and they belong in the tabs vanilla puts a
     * storage block and its unpacking in -- {@code building} for nine-into-one, {@code misc} for
     * one-into-nine. A test that demanded {@code equipment} of them would be asking the book to file
     * a block of firestone next to a sword.</p>
     *
     * <p>The map is by name with the expected category, so this stays a check: a recipe that drifted
     * to the wrong tab still fails, and the failure names the tab it should have been in.</p>
     */
    private static final Map<String, String> NOT_A_WEAPON = Map.of(
            MISC_RECIPE, "misc",
            "firestone_block", "building",
            "firestone_from_block", "misc",
            "nether_reactor", "redstone");

    private static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> found = new ArrayList<>(files
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList());
            found.sort(Path::compareTo);
            return found;
        }
    }

    private static String text(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Whether the file grants at least one recipe.
     *
     * <p><b>Looks for the key, and not for the literal.</b> The check that matters is
     * {@code "recipes": [ ... ]} with something inside, because an empty array grants nothing and
     * reads the same at a glance as a populated one. Anything that can tell a full array from an
     * empty one does; counting quotes would not.</p>
     */
    private static boolean namesSomeRecipe(String json) {
        int key = json.indexOf("\"recipes\"");
        if (key < 0) {
            return false;
        }
        int open = json.indexOf('[', key);
        if (open < 0) {
            return false;
        }
        int close = json.indexOf(']', open);
        if (close < 0) {
            return false;
        }
        return json.substring(open + 1, close).replaceAll("\\s", "").length() > 2;
    }

    /** The recipes this advancement grants, as the strings the array holds. */
    private static List<String> grantedRecipes(String json) {
        List<String> granted = new ArrayList<>();
        int key = json.indexOf("\"recipes\"");
        if (key < 0) {
            return granted;
        }
        int open = json.indexOf('[', key);
        int close = json.indexOf(']', open);
        if (open < 0 || close < 0) {
            return granted;
        }
        for (String part : json.substring(open + 1, close).split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
                granted.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return granted;
    }

    /**
     * Every advancement we write names at least one recipe.
     *
     * <p><b>The direct reading of the rule.</b> Not "the weapons" and not "the recipes we expect":
     * every file in the folder, because the failure mode is a file that was never given the field
     * and the only way to notice a whole category is to walk the whole category.</p>
     */
    @Test
    @DisplayName("every advancement names a recipe, or the book shows nothing")
    void everyAdvancementNamesARecipe() throws IOException {
        List<Path> advancements = jsonFiles(ADVANCEMENTS);
        assertFalse(advancements.isEmpty(),
                "no advancements found at " + ADVANCEMENTS.toAbsolutePath() + ". Either datagen "
                        + "has not run or it writes somewhere else -- and a test that passes "
                        + "because it found nothing is worse than no test");

        List<String> orphans = new ArrayList<>();
        for (Path file : advancements) {
            if (!namesSomeRecipe(text(file))) {
                orphans.add(file.getFileName().toString());
            }
        }
        assertTrue(orphans.isEmpty(),
                orphans.size() + " advancement(s) name no recipe, so the recipe book hides those "
                        + "recipes with nothing in the log to say why: " + orphans + ". The fix is "
                        + "one line -- AdvancementRewards.Builder.recipe(id) -- and the symptom "
                        + "without it is a player holding a musket and no way to learn what it eats");
    }

    /**
     * Every recipe we write is named by an advancement.
     *
     * <p><b>The other direction, and the one that would have caught the firestone.</b> The test
     * above cannot see a recipe with no advancement at all: there is no file to walk, so an
     * orphaned recipe is invisible to a guard that only looks at advancements. This one walks the
     * recipes and asks which ones nothing claims.</p>
     */
    @Test
    @DisplayName("every recipe is claimed by an advancement, except the two nugget conversions")
    void everyRecipeIsClaimedByAnAdvancement() throws IOException {
        List<Path> recipes = jsonFiles(RECIPES);
        assertFalse(recipes.isEmpty(),
                "no recipes found at " + RECIPES.toAbsolutePath() + ", so this test is asserting "
                        + "nothing about anything");

        List<String> unclaimed = new ArrayList<>();
        for (Path recipe : recipes) {
            String name = fileName(recipe);
            if (DELIBERATELY_HIDDEN.contains(name)) {
                continue;
            }
            Path advancement = ADVANCEMENTS.resolve(name + ".json");
            if (!Files.exists(advancement)) {
                unclaimed.add(name + " (no advancement file at all)");
            } else if (!namesSomeRecipe(text(advancement))) {
                unclaimed.add(name + " (advancement names no recipe)");
            }
        }
        assertTrue(unclaimed.isEmpty(),
                unclaimed.size() + " recipe(s) are craftable at the table and invisible in the "
                        + "book: " + unclaimed);
    }

    /**
     * The named recipe is <em>this</em> recipe, and one that exists.
     *
     * <p><b>Because "names one recipe" is satisfiable by a typo.</b> Thirty-two files, each pointing
     * at a name built from a material and a family, and a single misspelled id would leave an
     * advancement granting a recipe that does not exist -- which is the same silent failure with an
     * extra step, and one that passes the two tests above.</p>
     */
    @Test
    @DisplayName("each advancement names its own recipe, and that recipe exists")
    void eachAdvancementNamesItsOwnRecipe() throws IOException {
        for (Path file : jsonFiles(ADVANCEMENTS)) {
            String name = fileName(file);
            for (String claimed : grantedRecipes(text(file))) {
                assertTrue(claimed.endsWith(":" + name),
                        name + ".json grants the recipe \"" + claimed + "\", which is not its own. "
                                + "A recipe id that resolves to nothing is the same invisible "
                                + "failure as one that was never written");
                assertTrue(Files.exists(RECIPES.resolve(name + ".json")),
                        name + ".json grants " + claimed + " but data/cortescombat/recipes/"
                                + name + ".json does not exist");
            }
        }
    }

    /**
     * A weapon recipe sits in the equipment tab, which is the only tab the book reads it into.
     *
     * <p><b>Read out of {@code ClientRecipeBook#getCategory}.</b> A shaped recipe becomes
     * {@code CRAFTING_EQUIPMENT} from {@code CraftingRecipe#category()}, and the tab a recipe lands
     * in is decided there alone -- the advancement is not consulted. So a weapon recipe filed under
     * {@code misc} is not an inconsistency the game notices; it is one file deciding where the entry
     * appears, with the other file saying nothing about it.</p>
     */
    @Test
    @DisplayName("a weapon recipe sits in the equipment tab, which is where the book reads it from")
    void aWeaponRecipeSitsInTheEquipmentTab() throws IOException {
        for (Path recipe : jsonFiles(RECIPES)) {
            String name = fileName(recipe);
            if (DELIBERATELY_HIDDEN.contains(name)) {
                continue;
            }
            String json = text(recipe);
            int at = json.indexOf("\"category\"");
            assertTrue(at >= 0, name + ".json declares no category, and the book needs one to "
                    + "decide which tab to file it under");
            int colon = json.indexOf(':', at);
            // **Three finds rather than a "find any of".** `String` has no `indexOfAnyOf` -- the
            // compiler says so -- and a three-line loop over the three delimiters is smaller than
            // the helper that would have replaced it. Datagen writes one field per line, so the
            // newline is the terminator that actually matters; the other two are belt and braces for
            // a file someone has since reformatted.
            int end = -1;
            for (char delimiter : new char[]{'\n', '\r', ','}) {
                int at1 = json.indexOf(delimiter, colon);
                if (at1 >= 0 && (end < 0 || at1 < end)) {
                    end = at1;
                }
            }
            String category = json.substring(colon + 1, end < 0 ? json.length() : end).trim();
            // A weapon goes in the equipment tab; the few recipes that are not weapons go where
            // vanilla files their kind, named in the map above so this is still a check.
            String expected = NOT_A_WEAPON.get(name);
            assertEquals("\"" + (expected == null ? "equipment" : expected) + "\"", category,
                    name + ".json is filed under " + category + " and should be under "
                            + (expected == null ? "equipment" : expected) + ". The book reads the "
                            + "category off the RECIPE and not off the advancement, so this one "
                            + "file decides which tab it appears in");
        }
    }

    private static String fileName(Path path) {
        return path.getFileName().toString().replace(".json", "");
    }
}
