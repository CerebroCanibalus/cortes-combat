package com.cortes.combat.core.crafting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the shiftable recipe is registered, and that the provider writes its type.
 *
 * <h2>Why a test that reads the source, again</h2>
 *
 * <p>Same reason as {@code RecipeBookPopulationTest}, and the failure it guards is worse.
 * {@code ShiftableShapedRecipe} extends a Minecraft class and holds a Minecraft codec, so it cannot
 * be loaded here at all. What can be checked is whether it is <em>wired</em>, and wiring is exactly
 * what broke.</p>
 *
 * <h2>The bug this file exists for, and it is a good one</h2>
 *
 * <p>The serializer was first written as a {@code static final} field in a nested class. That is the
 * idiomatic shape for a registry entry and <b>it does not run</b>: a static initialiser happens when
 * the class is first touched, and the only reader was {@code getSerializer()}, which only runs on a
 * recipe that has already been built &mdash; and a recipe is built after the manager looks its
 * {@code "type"} up in the very registry the field initialiser would fill.</p>
 *
 * <p>So it was a loop: the lookup finds nothing, so the class never loads, so the field never
 * initialises. <b>Thirty weapons whose recipes silently do not exist, and no error anywhere.</b> It
 * compiles, the build is green, and the class in question is real.</p>
 */
class ShiftableShapedRecipeWiringTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "cortes", "combat");

    /** The recipe's own path, in one place: {@code Path#resolve} takes one argument, not four. */
    private static Path theRecipe() {
        return Path.of("src", "main", "java", "com", "cortes", "combat", "core",
                "crafting", "ShiftableShapedRecipe.java");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Registered from the mod's constructor.
     *
     * <p><b>The loop, stated as an assertion.</b> The register call has to be reachable from startup,
     * because the only alternative is a static initialiser nothing triggers.</p>
     */
    @Test
    @DisplayName("the serializer is registered from the mod's constructor")
    void theSerializerIsRegisteredAtStartup() {
        String mod = read(MAIN.resolve("platform").resolve("ModPlatform.java"));

        assertTrue(mod.contains("ShiftableShapedRecipe.ModRecipes.register(modEventBus)"),
                "**This is the whole test.** The serializer has to reach the mod event bus, and the "
                        + "reason is a wall, not a style: the built-in registries are frozen BEFORE "
                        + "FML constructs the mods, so the first version -- a plain "
                        + "Registry.register from the constructor, which is what vanilla's own "
                        + "package-private helper does -- died at datagen with 'Registry is already "
                        + "frozen'. Same registry, different moment.");
    }

    /** And the registration is not a static field again, which is how it broke the first time. */
    @Test
    @DisplayName("the serializer is not a static field waiting for someone to read it")
    void theSerializerIsNotAStaticField() {
        String recipe = read(theRecipe());

        assertFalse(recipe.contains("static final RecipeSerializer"),
                "a static final serializer field is the shape that does not run. It initialises "
                        + "when the class is first touched, and nothing touches it: the recipe "
                        + "manager would have to find it to build a recipe, and building a recipe is "
                        + "what would touch it");
        assertTrue(recipe.contains("DeferredRegister"),
                "and it goes through a DeferredRegister over Registries.RECIPE_SERIALIZER, which "
                        + "registers during the mod event bus -- the only window still open by then");
        // **And that the register method actually reaches it.** The first version of this only
        // asserted the DeferredRegister exists, and commenting out its `register(modEventBus)` left
        // the suite green: the caller is still there, calling a method that now does nothing.
        // Two halves of one fact, and only one of them was asserted.
        assertTrue(recipe.contains("SERIALIZERS.register(modEventBus)"),
                "the DeferredRegister has to be handed to the bus. A DeferredRegister that is "
                        + "created and never registered is a static field with extra steps: the "
                        + "entry stays a supplier, get() throws, and the failure lands on the first "
                        + "recipe instead of at startup");
    }

    /**
     * The recipe's own serializer, not vanilla's.
     *
     * <p><b>The line that decides whether the behaviour survives a reload.</b> The JSON names a type,
     * the manager calls {@code getSerializer()} to find what reads the next line, and returning
     * vanilla's hands back a plain {@code ShapedRecipe} that matches in one corner again &mdash;
     * with no error, because vanilla's serializer perfectly well describes the file.</p>
     */
    @Test
    @DisplayName("the recipe reports our serializer, so the behaviour survives a reload")
    void theRecipeReportsOurSerializer() {
        String recipe = read(theRecipe());

        assertTrue(recipe.contains("@Override") && recipe.contains("getSerializer"),
                "the method has to be overridden at all, or the file is read as vanilla's recipe");
        assertTrue(recipe.contains("ModRecipes.serializer()"),
                "and it has to return ours. Vanilla's reads the same file perfectly and produces a "
                        + "plain ShapedRecipe, which matches in one corner -- so the bug is not an "
                        + "error, it is the feature quietly switching off after a /reload");
    }

    /**
     * The book is told, so it draws the recipe where it now works.
     *
     * <p><b>Left alone it reports the pattern's own size</b>, so the book keeps drawing the daga in
     * the top-left corner even though the recipe now works in all four. The thing the player reads
     * would describe a rule the recipe no longer follows.</p>
     */
    @Test
    @DisplayName("the recipe says it can be made on any bench, so the book agrees")
    void theRecipeTellsTheBook() {
        String recipe = read(theRecipe());

        assertTrue(recipe.contains("canCraftInDimensions"),
                "vanilla asks this to decide whether to draw the recipe and which slice of the "
                        + "grid to draw it in. Unanswered, the book keeps showing the corner");
        // **And that it answers true, and not merely that it answers.**
        //
        // The first version of this asserted only that the method existed, and returning
        // `super.canCraftInDimensions` -- which reports the pattern's own size, exactly the
        // behaviour being replaced -- left the suite green. An override that exists and delegates
        // is the same as no override at all.
        assertFalse(recipe.contains("super.canCraftInDimensions(width, height)"),
                "delegating to super is the same as not overriding it: super reports the pattern's "
                        + "own size, which is the one-corner behaviour this class exists to "
                        + "replace. The book would keep drawing the daga in the top-left while the "
                        + "recipe worked in all four, and nothing would say so");
        assertTrue(recipe.contains("return true"),
                "and it has to answer true. There is no size the player can hold that a shiftable "
                        + "recipe should refuse");
    }

    /**
     * Both orientations, at every placement.
     *
     * <p><b>The half of the mirror that is ours.</b> Vanilla mirrors the pattern at its own size;
     * once the placement moves, a mirrored pattern in a different corner is a different thing to
     * look at and the same thing to make, and vanilla has no opinion about it because vanilla has
     * no other corner.</p>
     *
     * <p>The first version of this file asserted only that the grid was walked, and deleting the
     * mirrored call left the suite green &mdash; which is the sabre only accepting one handedness
     * in three of the four positions it accepts at all.</p>
     */
    @Test
    @DisplayName("both orientations, at every placement")
    void bothOrientationsAtEveryPlacement() {
        String recipe = read(theRecipe());

        assertTrue(recipe.contains("fits(input, at[0], at[1], false)")
                        && recipe.contains("fits(input, at[0], at[1], true)"),
                "the placement loop has to try the pattern right way round AND mirrored. Vanilla "
                        + "mirrors at the pattern's own size and says nothing about the other "
                        + "corners, because vanilla has no other corners -- so the mirrored "
                        + "placement is ours and dropping it means the weapon accepts one "
                        + "handedness in three of the four positions it accepts at all");
    }

    /**
     * The cells around the pattern have to be empty.
     *
     * <p><b>The failure that costs the player items.</b> Checking only the cells the pattern covers
     * means the daga matches a bench with a block of iron next to it. A recipe that fires for a
     * different arrangement of the table is worse than one that does not fire, because the first
     * costs materials.</p>
     */
    @Test
    @DisplayName("the match walks the whole grid, not just the pattern")
    void theMatchWalksTheWholeGrid() {
        String recipe = read(theRecipe());

        assertTrue(recipe.contains("input.getItem(column, row)"),
                "the check has to read cells the pattern does not cover, and that is only possible "
                        + "if the loop is over the grid rather than over the pattern");
        assertTrue(recipe.contains("!stack.isEmpty()"),
                "and a cell outside the pattern has to be rejected when it holds something. A recipe "
                        + "that fires for a different arrangement of the bench costs the player the "
                        + "items in it, which is worse than not matching");
        assertTrue(recipe.contains("super.matches(input, level)"),
                "and vanilla's own match runs first: it is the exact-size test with the horizontal "
                        + "mirror, which the whole game already relies on, and re-implementing it "
                        + "would be a second copy of a rule");
    }
}