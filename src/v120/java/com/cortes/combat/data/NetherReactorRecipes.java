package com.cortes.combat.data;

import com.cortes.combat.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The 1.20.1 writer of the nether reactor. Same one-off grid, older folders.
 *
 * <p>Netherite, pistons, copper, obsidian and a netherite block -- the machine's grid is a
 * shape nothing else uses, and it carries an advancement unlocked by holding netherite,
 * like every other discovery. (⌒‿⌒)</p>
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

            ResourceLocation id = RecipeJson.rl("nether_reactor");
            com.google.gson.JsonObject recipe = RecipeJson.shaped(
                    "cortescombat:shiftable_shaped", CraftingBookCategory.REDSTONE, "",
                    List.of("NPN", "COC", "NBN"), key,
                    new ItemStack(ModBlocks.NETHER_REACTOR_ITEM.get()), true);
            com.google.gson.JsonObject advancement = RecipeJson.advancement(
                    new ItemStack(ModBlocks.NETHER_REACTOR_ITEM.get()),
                    "block.cortescombat.nether_reactor", "advancements.cortescombat.weapon",
                    id, "has_netherite", RecipeJson.hasItems(Items.NETHERITE_INGOT));
            return CompletableFuture.allOf(
                    RecipeJson.write(output, cached, id, "recipes", recipe),
                    RecipeJson.write(output, cached, id, "advancements", advancement));
        });
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/nether_reactor";
    }
}
