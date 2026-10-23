package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.crafting.ShiftableShapedRecipe;
import com.cortes.combat.registry.ModBlocks;
import com.cortes.combat.registry.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.WithConditions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The firestone block, both ways.
 *
 * <p><b>Nine firestones into a block, and a block back into nine.</b> The canonical block shape
 * every metal and mineral in the game already uses, so a player who knows how to make an iron block
 * knows how to make this one. The reverse is shapeless, because one item in one cell has no shape to
 * get wrong.</p>
 *
 * <p><b>Two realms, two categories.</b> The nine-into-one is {@code BUILDING}, which is where
 * vanilla files a storage block; the one-into-nine is {@code MISC}, which is where vanilla files the
 * unpacking. The recipe book reads the category off the recipe and files the entry there, so getting
 * these backwards puts the block in the wrong tab with nothing to say so.</p>
 *
 * <p><b>Both carry an advancement</b>, and that is not the nugget conversions' decision: those are
 * hidden on purpose because they are already-known conversions. A block of firestone is a discovery
 * -- and a recipe with no advancement appears in the book as something the player already knows,
 * which is what {@code RecipeBookEntryTest} exists to catch. The criterion is the ingredient the
 * player already has when the recipe becomes interesting: a firestone for the block, the block for
 * the unpacking.</p>
 */
public final class FirestoneBlockRecipes implements DataProvider {

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public FirestoneBlockRecipes(PackOutput output,
                                 CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        return registries.thenCompose(provider -> CompletableFuture.allOf(
                block(cached, provider),
                unpack(cached, provider)));
    }

    /** {@code 9 firestone -> 1 firestone_block}, in the full three-by-three. */
    private CompletableFuture<?> block(CachedOutput cached, HolderLookup.Provider registries) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('X', Ingredient.of(ModItems.FIRESTONE.get()));
        ShapedRecipePattern pattern = ShapedRecipePattern.of(key,
                List.of("XXX", "XXX", "XXX"));

        // Shiftable, like every shaped recipe of ours: a full three-by-three has exactly one
        // placement, but "a recipe of ours crafts wherever it is put down" is a rule that does not
        // need a per-recipe exception to stay true.
        ShiftableShapedRecipe recipe = new ShiftableShapedRecipe(
                "", CraftingBookCategory.BUILDING, pattern,
                new ItemStack(ModBlocks.FIRESTONE_BLOCK.get()), true);

        ResourceLocation id = CortesCombat.rl("firestone_block");
        return CompletableFuture.allOf(
                recipe(cached, registries, id, recipe),
                advancement(cached, registries, id,
                        new ItemStack(ModBlocks.FIRESTONE_BLOCK.get()),
                        Component.translatable("block.cortescombat.firestone_block"),
                        ModItems.FIRESTONE.get()));
    }

    /** {@code 1 firestone_block -> 9 firestone}, shapeless. */
    private CompletableFuture<?> unpack(CachedOutput cached, HolderLookup.Provider registries) {
        NonNullList<Ingredient> ingredients =
                NonNullList.withSize(1, Ingredient.of(ModBlocks.FIRESTONE_BLOCK_ITEM.get()));
        ShapelessRecipe recipe = new ShapelessRecipe(
                "", CraftingBookCategory.MISC,
                new ItemStack(ModItems.FIRESTONE.get(), 9), ingredients);

        ResourceLocation id = CortesCombat.rl("firestone_from_block");
        return CompletableFuture.allOf(
                recipe(cached, registries, id, recipe),
                advancement(cached, registries, id,
                        new ItemStack(ModItems.FIRESTONE.get()),
                        Component.translatable("item.cortescombat.firestone"),
                        ModBlocks.FIRESTONE_BLOCK_ITEM.get()));
    }

    private CompletableFuture<?> recipe(CachedOutput cached, HolderLookup.Provider registries,
                                        ResourceLocation id, Recipe<?> recipe) {
        return DataProvider.saveStable(cached, registries, Recipe.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(recipe)),
                output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe").json(id));
    }

    /** The book entry. Not shown as a toast -- see {@code FirestoneRecipes} for why those differ. */
    private CompletableFuture<?> advancement(CachedOutput cached, HolderLookup.Provider registries,
                                             ResourceLocation recipeId, ItemStack icon,
                                             Component title, Item criterion) {
        Advancement advancement = Advancement.Builder.recipeAdvancement()
                .parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
                .display(new DisplayInfo(icon, title,
                        Component.translatable("advancements.cortescombat.weapon"),
                        Optional.empty(),
                        AdvancementType.TASK,
                        false, false, true))
                .addCriterion("has_material", has(criterion))
                .rewards(AdvancementRewards.Builder.recipe(recipeId))
                .build(recipeId)
                .value();

        return DataProvider.saveStable(cached, registries, Advancement.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(advancement)),
                output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement").json(recipeId));
    }

    /**
     * {@code hasItems} has no {@code TagKey} overload in 1.21.1, and the explicit predicate is the
     * same three lines every other provider here uses.
     */
    private static Criterion<?> has(Item item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(item).build());
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/firestone_block";
    }
}
