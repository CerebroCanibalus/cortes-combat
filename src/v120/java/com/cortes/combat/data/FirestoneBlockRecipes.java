package com.cortes.combat.data;

import com.cortes.combat.registry.ModBlocks;
import com.cortes.combat.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The 1.20.1 writer of the firestone block, both ways. Same canonical shapes, older folders.
 *
 * <p>Nine firestones into a block (a full three-by-three, {@code BUILDING} like every
 * storage block), and the block back into nine (shapeless, {@code MISC} like every
 * unpacking). Both carry an advancement, because a discovery with no book entry reads as
 * already known. (⌒‿⌒)</p>
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
                block(cached), unpack(cached)));
    }

    /** {@code 9 firestone -> 1 firestone_block}, in the full three-by-three. */
    private CompletableFuture<?> block(CachedOutput cached) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('X', Ingredient.of(ModItems.FIRESTONE.get()));
        ResourceLocation id = RecipeJson.rl("firestone_block");
        com.google.gson.JsonObject recipe = RecipeJson.shaped(
                "cortescombat:shiftable_shaped", CraftingBookCategory.BUILDING, "",
                java.util.List.of("XXX", "XXX", "XXX"), key,
                new ItemStack(ModBlocks.FIRESTONE_BLOCK_ITEM.get()), true);
        com.google.gson.JsonObject advancement = RecipeJson.advancement(
                new ItemStack(ModBlocks.FIRESTONE_BLOCK_ITEM.get()),
                "block.cortescombat.firestone_block", "advancements.cortescombat.weapon",
                id, "has_material", RecipeJson.hasItems(ModItems.FIRESTONE.get()));
        return CompletableFuture.allOf(
                RecipeJson.write(output, cached, id, "recipes", recipe),
                RecipeJson.write(output, cached, id, "advancements", advancement));
    }

    /** {@code 1 firestone_block -> 9 firestone}, shapeless. */
    private CompletableFuture<?> unpack(CachedOutput cached) {
        NonNullList<Ingredient> ingredients =
                NonNullList.withSize(1, Ingredient.of(ModBlocks.FIRESTONE_BLOCK_ITEM.get()));
        ResourceLocation id = RecipeJson.rl("firestone_from_block");
        com.google.gson.JsonObject recipe = RecipeJson.shapeless(
                CraftingBookCategory.MISC, "", ingredients,
                new ItemStack(ModItems.FIRESTONE.get(), 9));
        com.google.gson.JsonObject advancement = RecipeJson.advancement(
                new ItemStack(ModItems.FIRESTONE.get()),
                "item.cortescombat.firestone", "advancements.cortescombat.weapon",
                id, "has_material", RecipeJson.hasItems(ModBlocks.FIRESTONE_BLOCK_ITEM.get()));
        return CompletableFuture.allOf(
                RecipeJson.write(output, cached, id, "recipes", recipe),
                RecipeJson.write(output, cached, id, "advancements", advancement));
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/firestone_block";
    }
}
