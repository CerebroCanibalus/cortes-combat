package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.WithConditions;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The firestone recipe: one gunpowder and one glowstone make two.
 *
 * <p><b>Separate from {@code NuggetRecipes} for the reason that one is separate from the
 * catalogue.</b> Neither of these is a weapon, and a provider whose job is "loop the catalogue"
 * cannot be the place a two-ingredient conversion lives without turning the loop into a
 * special case. So the two non-catalogue recipes have their own provider each, and the catalogue
 * keeps being a catalogue.</p>
 *
 * <p><b>Shapeless, two cells, and a yield of two.</b> Shapeless because the pair has no shape --
 * it is a sack of powder and a sack of dust, not a grid -- and the two cells are the whole
 * recipe, so a player sees two ingredients in two slots and nothing else to learn. The yield of
 * two is what makes it an economy rather than a curiosity: a shot costs one firestone, so twenty
 * shots cost ten gunpowder and ten glowstone, and the gun's rhythm is set by how deep a player
 * has dug into both trees and quarries.</p>
 *
 * <p><b>An advancement, unlike the nugget conversions, and the difference is what the recipe
 * makes.</b> The reasoning "these are ingredients, not discoveries" is right about a toast and
 * wrong about the recipe book, and the two are different things: an advancement's
 * {@code display.show_toast} controls the toast, while the recipes it names are what the book
 * shows at all.</p>
 *
 * <p>So the original "no advancement" put this recipe in the same place as the nugget conversions,
 * for the sake of a toast nobody would have minded, and the cost was that <b>the only ammunition
 * in the game was invisible</b>. A player handed a musket and no way to find out what it eats is
 * not a player reading a design, and no error appears anywhere to say the recipe is there.</p>
 *
 * <p>Toast off, entry present, and unlocked by gunpowder: the criterion is the ingredient the
 * player has to dig for anyway, which is also the moment the gun becomes worth loading.</p>
 */
public final class FirestoneRecipes implements DataProvider {

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public FirestoneRecipes(PackOutput output,
                            CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        // Chained rather than unwrapped in the constructor, for the reason NuggetRecipes gives:
        // the future is not done when the provider is built and getNow(null) here is a
        // NullPointerException from deep inside the serialisation layer that names nothing.
        return registries.thenCompose(provider -> {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(Ingredient.of(Items.GUNPOWDER));
            ingredients.add(Ingredient.of(Items.GLOWSTONE_DUST));

            ResourceLocation id = CortesCombat.rl("firestone");
            ShapelessRecipe recipe = new ShapelessRecipe(
                    "", CraftingBookCategory.MISC,
                    new ItemStack(ModItems.FIRESTONE.get(), 2), ingredients);

            // Both of the same two rules NuggetRecipes follows: CONDITIONAL_CODEC with
            // WithConditions because that is what vanilla's own RecipeProvider saves with, and
            // saveStable's future has to be waited for because it writes when it resolves rather
            // than when it returns. Returning a completed future ends the provider with the file
            // unwritten: the log says finished, nothing complains, and there is no recipe.
            return CompletableFuture.allOf(
                    DataProvider.saveStable(cached, provider, Recipe.CONDITIONAL_CODEC,
                            Optional.of(new WithConditions<>(recipe)),
                            output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe")
                                    .json(id)),
                    advancement(cached, provider, id));
        });
    }

    /**
     * The recipe book entry: gunpowder in the inventory, and no toast.
     *
     * <p><b>showToast false, and the entry still exists.</b> Those are separate booleans on separate
     * objects and the confusion between them is what hid this recipe for as long as it did. The
     * eight-argument {@code DisplayInfo} ends in {@code (showToast, announceToChat, hidden)} and all
     * three are false here: no popup, no chat line, not on the advancement screen. The recipe book
     * does not read any of the three -- it reads the recipes this advancement grants, which is the
     * {@code rewards} line below and nothing else.
     *
     * <p>The criterion is gunpowder rather than glowstone on purpose. Both are ingredients, so
     * either works as a gate; gunpowder is the one a player is already thinking about when they
     * first hold a musket, and glowstone turns into the entry only if they happen to be mining
     * it, which is a worse moment to be interrupted.
     */
    private CompletableFuture<?> advancement(CachedOutput cached,
                                             HolderLookup.Provider registries,
                                             ResourceLocation id) {
        Advancement advancement = Advancement.Builder.recipeAdvancement()
                .parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
                .display(new DisplayInfo(new ItemStack(ModItems.FIRESTONE.get()),
                        Component.translatable("item.cortescombat.firestone"),
                        Component.translatable("advancements.cortescombat.weapon"),
                        Optional.empty(),
                        AdvancementType.TASK,
                        false, false, true))
                .addCriterion("has_gunpowder", InventoryChangeTrigger.TriggerInstance.hasItems(
                        ItemPredicate.Builder.item().of(Items.GUNPOWDER).build()))
                // The line that puts the recipe in the book. See CortesRecipeProvider for why a
                // recipe advancement without it is an entry into nothing.
                .rewards(AdvancementRewards.Builder.recipe(id))
                .build(id)
                .value();

        return DataProvider.saveStable(cached, registries, Advancement.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(advancement)),
                output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement").json(id));
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/firestone";
    }
}
