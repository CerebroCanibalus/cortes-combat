package com.cortes.combat.data;

import com.cortes.combat.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.CompletableFuture;

/**
 * The 1.20.1 writer of the firestone recipe. Same powder, same book entry, older folders.
 *
 * <p>Shapeless, two cells, and a yield of two -- a sack of powder and a sack of dust, not a
 * grid. The advancement unlocks on gunpowder with no toast: the entry is what puts the
 * ammunition in the book, and the toast is what would interrupt the player for it. (⌒‿⌒)</p>
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
        return registries.thenCompose(provider -> {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(Ingredient.of(Items.GUNPOWDER));
            ingredients.add(Ingredient.of(Items.GLOWSTONE_DUST));

            ResourceLocation id = RecipeJson.rl("firestone");
            com.google.gson.JsonObject recipe = RecipeJson.shapeless(
                    CraftingBookCategory.MISC, "", ingredients,
                    new ItemStack(ModItems.FIRESTONE.get(), 2));

            com.google.gson.JsonObject advancement = RecipeJson.advancement(
                    new ItemStack(ModItems.FIRESTONE.get()),
                    "item.cortescombat.firestone", "advancements.cortescombat.weapon",
                    id, "has_gunpowder", RecipeJson.hasItems(Items.GUNPOWDER));

            return CompletableFuture.allOf(
                    RecipeJson.write(output, cached, id, "recipes", recipe),
                    RecipeJson.write(output, cached, id, "advancements", advancement));
        });
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/firestone";
    }
}
