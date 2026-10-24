package com.cortes.combat.data;

import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The 1.20.1 writer of the thirty recipes. Same grids, older folders, hand JSON.
 *
 * <p>1.20.1 has no {@code ShapedRecipePattern}, no conditional codecs and no singular
 * folders: patterns validate in {@link RecipeJson}, the type travels as a string
 * ({@code cortescombat:shiftable_shaped}, read back by the runtime twin), and files land
 * in {@code data/<ns>/recipes/} and {@code data/<ns>/advancements/}. The grids still come
 * from {@code WeaponShape}, so a grid edited in one place still cannot disagree with the
 * thirty. (⌒‿⌒)</p>
 */
public class CortesRecipeProvider implements DataProvider {

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public CortesRecipeProvider(PackOutput output,
                                CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        return registries.thenCompose(provider -> {
            java.util.List<CompletableFuture<?>> futures = new java.util.ArrayList<>();
            for (WeaponFamily family : WeaponFamily.values()) {
                for (WeaponMaterial material : family.materialsInOrder()) {
                    futures.add(write(cached, family, material));
                }
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    private CompletableFuture<?> write(CachedOutput cached, WeaponFamily family,
                                       WeaponMaterial material) {
        ResourceLocation id = RecipeJson.rl(family.itemName(material));
        Item result = ModItems.weapon(family.itemName(material)).get();

        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('X', ingredient(family.bodyIngredient(material)));
        // **The stick goes in only when the grid uses one.** Same rule as the shared twin:
        // a key symbol the pattern never mentions fails datagen.
        if (family.shape().sticks() > 0) {
            key.put('/', Ingredient.of(net.minecraft.world.item.Items.STICK));
        }
        WeaponFamily.IngredientRef extra = family.extraIngredient(material);
        if (extra != null) {
            key.put('*', ingredient(extra));
        }

        com.google.gson.JsonObject recipe = RecipeJson.shaped(
                "cortescombat:shiftable_shaped", CraftingBookCategory.EQUIPMENT, "",
                family.shape().toRecipeRows(), key,
                new net.minecraft.world.item.ItemStack(result), true);

        TagKey<Item> bodyTag = TagKey.create(Registries.ITEM,
                ResourceLocation.withDefaultNamespace(material.repairTag()));
        String titleKey = "item." + BuiltInRegistries.ITEM.getKey(result)
                .toString().replace(':', '.');
        com.google.gson.JsonObject advancement = RecipeJson.advancement(
                new net.minecraft.world.item.ItemStack(result),
                titleKey, "advancements.cortescombat.weapon", id, "has_material",
                RecipeJson.hasItems(bodyTag));

        return CompletableFuture.allOf(
                RecipeJson.write(output, cached, id, "recipes", recipe),
                RecipeJson.write(output, cached, id, "advancements", advancement));
    }

    /** An item or a tag, from the pure descriptor the catalogue produced. */
    private static Ingredient ingredient(WeaponFamily.IngredientRef ref) {
        ResourceLocation id = ref.id().contains(":")
                ? ResourceLocation.parse(ref.id())
                : ResourceLocation.withDefaultNamespace(ref.id());
        if (ref.tag()) {
            return Ingredient.of(TagKey.create(Registries.ITEM, id));
        }
        return BuiltInRegistries.ITEM.getOptional(id)
                .map(Ingredient::of)
                .orElseThrow(() -> new IllegalStateException(
                        "recipe ingredient " + id + " is not a registered item"));
    }

    @Override
    public String getName() {
        return com.cortes.combat.CortesCombat.MODID + " Recipes";
    }
}
