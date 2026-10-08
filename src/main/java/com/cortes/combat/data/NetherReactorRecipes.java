package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.crafting.ShiftableShapedRecipe;
import com.cortes.combat.registry.ModBlocks;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.neoforge.common.conditions.WithConditions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The nether reactor's crafting grid.
 *
 * <p>The design's shape, read top to bottom: netherite and a piston across the top, copper around
 * an obsidian core, and netherite around a block of netherite closing the bottom. Every cell is
 * named, so there is no empty-space subtlety here -- the {@code #}-is-not-empty trap applies to the
 * grids that have gaps, and this one deliberately has none.</p>
 *
 * <p><b>{@code REDSTONE}, not {@code BUILDING}.</b> The recipe book files a recipe under the tab its
 * category names, and this is a redstone machine. A reactor filed under building would appear next
 * to a chest, and {@code RecipeBookEntryTest} pins the category for exactly that reason.</p>
 *
 * <p><b>It carries an advancement, like every recipe of ours that is a discovery.</b> Without one
 * the recipe is craftable and invisible in the book. The criterion is the netherite ingot, the
 * expensive ingredient a player has when the reactor becomes interesting.</p>
 */
public final class NetherReactorRecipes implements DataProvider {

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public NetherReactorRecipes(PackOutput output,
                                CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        return registries.thenCompose(provider -> {
            Map<Character, Ingredient> key = new LinkedHashMap<>();
            key.put('N', Ingredient.of(Items.NETHERITE_INGOT));
            key.put('P', Ingredient.of(Items.PISTON));
            key.put('C', Ingredient.of(Items.COPPER_INGOT));
            key.put('O', Ingredient.of(Items.OBSIDIAN));
            key.put('B', Ingredient.of(Items.NETHERITE_BLOCK));

            ShapedRecipePattern pattern = ShapedRecipePattern.of(key,
                    List.of("NPN", "COC", "NBN"));

            ShiftableShapedRecipe recipe = new ShiftableShapedRecipe(
                    "", CraftingBookCategory.REDSTONE, pattern,
                    new ItemStack(ModBlocks.NETHER_REACTOR_ITEM.get()), true);

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    CortesCombat.MODID, "nether_reactor");
            return CompletableFuture.allOf(
                    recipe(cached, provider, id, recipe),
                    advancement(cached, provider, id));
        });
    }

    private CompletableFuture<?> recipe(CachedOutput cached, HolderLookup.Provider registries,
                                        ResourceLocation id, Recipe<?> recipe) {
        return DataProvider.saveStable(cached, registries, Recipe.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(recipe)),
                output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe").json(id));
    }

    private CompletableFuture<?> advancement(CachedOutput cached, HolderLookup.Provider registries,
                                             ResourceLocation id) {
        Advancement advancement = Advancement.Builder.recipeAdvancement()
                .parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
                .display(new DisplayInfo(new ItemStack(ModBlocks.NETHER_REACTOR_ITEM.get()),
                        Component.translatable("block.cortescombat.nether_reactor"),
                        Component.translatable("advancements.cortescombat.weapon"),
                        Optional.empty(),
                        AdvancementType.TASK,
                        false, false, true))
                .addCriterion("has_netherite", has(Items.NETHERITE_INGOT))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .build(id)
                .value();

        return DataProvider.saveStable(cached, registries, Advancement.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(advancement)),
                output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement").json(id));
    }

    /** {@code hasItems} has no {@code TagKey} overload in 1.21.1; the predicate is the same three lines. */
    private static Criterion<?> has(Item item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(item).build());
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/nether_reactor";
    }
}
