package com.cortes.combat.data;

import com.cortes.combat.platform.ResourceIds;

import com.cortes.combat.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.WithConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The two recipes that turn copper ingots into nuggets and back.
 *
 * <p>They were missing and nothing noticed. The ropera's copper tip names a nugget, the tag points
 * at it, the recipe book has an entry -- and there was no way in the world to obtain one, so the
 * one weapon in the catalogue that cannot be crafted looked finished on paper. <b>A tag is not a
 * source.</b></p>
 *
 * <p>Vanilla 1.21.1 has nuggets for gold and iron and <b>none for copper</b>. {@code Items} holds
 * {@code GOLD_NUGGET} and {@code IRON_NUGGET} and no copper equivalent, and the vanilla jar has
 * neither a {@code copper_nugget} model nor a {@code copper_nugget} texture. That is the whole
 * reason this is an item of ours and not an override, and it is also why the ropera's tip is a
 * nugget: it is the only metal tier whose tip material had to be invented.</p>
 *
 * <p>Nine each way, matching iron and gold, in the same shapeless nine cell grid, because the
 * thing a player already knows how to do should not have to be learned again. The nugget exists so
 * a copper tool can be repaired with its own metal without handing out a whole ingot per
 * repair.</p>
 *
 * <p>No advancement, like vanilla's own nugget conversions. These are not worth a toast the first
 * time a player sees one.</p>
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
        // Chained rather than unwrapped in the constructor: the future is not done when the
        // provider is built, it completes when the registries freeze, and getNow(null) here is a
        // NullPointerException from deep inside the serialisation layer that names nothing.
        return registries.thenCompose(provider -> {
            // One ingot spreads into nine, and nine go back into one. Written here rather than by
            // hand in data/ so runData validates them like every other recipe in the mod.
            //
            // **The count is a parameter and it used to be hardcoded at nine for both**, so the
            // nugget recipe asked for nine ingots and produced nine nuggets -- which is a recipe
            // nobody would ever make and, worse, one a player reads as a bug the first time they
            // try it. Vanilla's own nugget conversions are one ingot to nine, and the return trip
            // is the one that takes nine.
            CompletableFuture<?> down = spread(cached, provider, 1,
                    new ItemStack(Items.COPPER_INGOT),
                    new ItemStack(ModItems.COPPER_NUGGET.get(), 9), "copper_nugget");
            CompletableFuture<?> up = spread(cached, provider, 9,
                    new ItemStack(ModItems.COPPER_NUGGET.get()),
                    new ItemStack(Items.COPPER_INGOT), "copper_ingot_from_nuggets");
            // Both writes have to be waited for. saveStable does not write when it returns, it
            // returns a future that writes later, so returning a completed future here ends the
            // provider with the files unwritten: the log says "finished after 1ms", no error
            // anywhere, and no recipe. Same shape as a recipe emitted into assets/.
            return CompletableFuture.allOf(down, up);
        });
    }

    private CompletableFuture<?> spread(CachedOutput cached, HolderLookup.Provider registries,
                                   int inputCount, ItemStack input, ItemStack result, String name) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(inputCount,
                Ingredient.of(input));

        ResourceLocation id = ResourceIds.rl(name);
        ShapelessRecipe recipe = new ShapelessRecipe(
                "", CraftingBookCategory.MISC, result, ingredients);

        // CONDITIONAL_CODEC with WithConditions, which is what vanilla's own RecipeProvider saves
        // with. The plain codec works today and stops working the day a recipe needs a loader.
        return DataProvider.saveStable(cached, registries, Recipe.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(recipe)),
                output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe").json(id));
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/nuggets";
    }
}