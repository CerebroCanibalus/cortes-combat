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
 * The 1.20.1 writer of the copper-nugget conversions. Same two recipes, older folders.
 *
 * <p>One ingot spreads into nine, and nine go back into one -- in the same shapeless grid
 * vanilla files its own nugget conversions under. No advancement, like vanilla's own:
 * these are not worth a toast. (⌒‿⌒)</p>
 */
public final class NuggetRecipes implements DataProvider {

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public NuggetRecipes(PackOutput output,
                         CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        return registries.thenCompose(provider -> {
            CompletableFuture<?> down = spread(cached, 1,
                    new ItemStack(Items.COPPER_INGOT),
                    new ItemStack(ModItems.COPPER_NUGGET.get(), 9), "copper_nugget");
            CompletableFuture<?> up = spread(cached, 9,
                    new ItemStack(ModItems.COPPER_NUGGET.get()),
                    new ItemStack(Items.COPPER_INGOT), "copper_ingot_from_nuggets");
            return CompletableFuture.allOf(down, up);
        });
    }

    private CompletableFuture<?> spread(CachedOutput cached, int inputCount, ItemStack input,
                                        ItemStack result, String name) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(inputCount,
                Ingredient.of(input));
        ResourceLocation id = RecipeJson.rl(name);
        return RecipeJson.write(output, cached, id, "recipes",
                RecipeJson.shapeless(CraftingBookCategory.MISC, "", ingredients, result));
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/nuggets";
    }
}
