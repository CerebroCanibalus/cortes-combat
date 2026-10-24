package com.cortes.combat.data;

import com.cortes.combat.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The 1.20.1 writer of the arcabuz recipe. Same vertical grid, older folders.
 *
 * <p>The grid is {@code "/ V", " O ", " MM"} with bamboo, flint and steel, a dispenser and
 * planks -- a gun built like a tool, which is what the shape says. Unlocked by holding a
 * dispenser: the entry appears at roughly the moment the gun becomes craftable. Own
 * description line rather than the shared "Hecho a mano.", because this one has to say
 * what the gun is. (⌒‿⌒)</p>
 */
public final class ArcabuzRecipes implements DataProvider {

    private static final Map<Character, Ingredient> KEY = new LinkedHashMap<>();
    private static final List<String> PATTERN = List.of("/ V", " O ", " MM");

    static {
        KEY.put('/', Ingredient.of(Items.BAMBOO));
        KEY.put('V', Ingredient.of(Items.FLINT_AND_STEEL));
        KEY.put('O', Ingredient.of(Items.DISPENSER));
        // Any wood. `#minecraft:planks` rather than logs: the cell is two planks' worth of stock.
        KEY.put('M', Ingredient.of(TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.withDefaultNamespace("planks"))));
    }

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public ArcabuzRecipes(PackOutput output,
                          CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        return registries.thenCompose(provider -> {
            ResourceLocation id = RecipeJson.rl("arcabuz");
            com.google.gson.JsonObject recipe = RecipeJson.shaped(
                    "cortescombat:shiftable_shaped", CraftingBookCategory.EQUIPMENT, "",
                    PATTERN, KEY, new ItemStack(ModItems.ARCABUZ.get()), true);
            com.google.gson.JsonObject advancement = RecipeJson.advancement(
                    new ItemStack(ModItems.ARCABUZ.get()),
                    "item.cortescombat.arcabuz", "advancements.cortescombat.arcabuz",
                    id, "has_dispenser", RecipeJson.hasItems(Items.DISPENSER));
            return CompletableFuture.allOf(
                    RecipeJson.write(output, cached, id, "recipes", recipe),
                    RecipeJson.write(output, cached, id, "advancements", advancement));
        });
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/arcabuz";
    }
}
