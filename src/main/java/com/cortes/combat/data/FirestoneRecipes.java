package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
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
 * <p>No advancement, like the nugget conversions. These are ingredients, not discoveries, and a
 * toast the first time somebody stirs up their first glowstone is noise.</p>
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

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    CortesCombat.MODID, "firestone");
            ShapelessRecipe recipe = new ShapelessRecipe(
                    "", CraftingBookCategory.MISC,
                    new ItemStack(ModItems.FIRESTONE.get(), 2), ingredients);

            // Both of the same two rules NuggetRecipes follows: CONDITIONAL_CODEC with
            // WithConditions because that is what vanilla's own RecipeProvider saves with, and
            // saveStable's future has to be waited for because it writes when it resolves rather
            // than when it returns. Returning a completed future ends the provider with the file
            // unwritten: the log says finished, nothing complains, and there is no recipe.
            return DataProvider.saveStable(cached, provider, Recipe.CONDITIONAL_CODEC,
                    Optional.of(new WithConditions<>(recipe)),
                    output.createPathProvider(PackOutput.Target.DATA_PACK, "recipes").json(id));
        });
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/firestone";
    }
}
