package com.cortes.combat.data;

import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.registry.CortesTier;
import com.cortes.combat.registry.ModArmor;
import com.cortes.combat.registry.ModTools;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The 1.20.1 writer of the copper gear: five tools and four armour pieces.
 *
 * <p>Same grids as the shared twin -- sword {@code X/X//}, pickaxe, axe, shovel, hoe, and
 * the four armour shapes -- validated by the same symbol checks. Every one carries its
 * advancement (unlocked by the copper tool tag, never by the result), because a recipe
 * with no advancement appears in the book as already known. (⌒‿⌒)</p>
 */
public final class CopperGearRecipes implements DataProvider {

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public CopperGearRecipes(PackOutput output,
                             CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        return registries.thenCompose(provider -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            futures.add(shaped(cached, CopperGear.SWORD, ModTools.COPPER_SWORD.get(),
                    List.of("X", "X", "/")));
            futures.add(shaped(cached, CopperGear.PICKAXE, ModTools.COPPER_PICKAXE.get(),
                    List.of("XXX", " / ", " / ")));
            futures.add(shaped(cached, CopperGear.AXE, ModTools.COPPER_AXE.get(),
                    List.of("XX", "X/", " /")));
            futures.add(shaped(cached, CopperGear.SHOVEL, ModTools.COPPER_SHOVEL.get(),
                    List.of("X", "/", "/")));
            futures.add(shaped(cached, CopperGear.HOE, ModTools.COPPER_HOE.get(),
                    List.of("XX", " /", " /")));
            futures.add(shaped(cached, CopperGear.HELMET, ModArmor.COPPER_HELMET.get(),
                    List.of("XXX", "X X")));
            futures.add(shaped(cached, CopperGear.CHESTPLATE,
                    ModArmor.COPPER_CHESTPLATE.get(), List.of("X X", "XXX", "XXX")));
            futures.add(shaped(cached, CopperGear.LEGGINGS,
                    ModArmor.COPPER_LEGGINGS.get(), List.of("XXX", "X X", "X X")));
            futures.add(shaped(cached, CopperGear.BOOTS, ModArmor.COPPER_BOOTS.get(),
                    List.of("X X", "X X")));
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    private CompletableFuture<?> shaped(CachedOutput cached, String name, Item result,
                                        List<String> rows) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('X', Ingredient.of(Items.COPPER_INGOT));
        // **The stick goes in only when the grid uses one.** No armour piece has a haft.
        if (rows.stream().anyMatch(row -> row.indexOf('/') >= 0)) {
            key.put('/', Ingredient.of(Items.STICK));
        }
        ResourceLocation id = RecipeJson.rl(name);
        com.google.gson.JsonObject recipe = RecipeJson.shaped(
                "cortescombat:shiftable_shaped", CraftingBookCategory.EQUIPMENT, "",
                rows, key, new net.minecraft.world.item.ItemStack(result), true);

        TagKey<Item> material = CortesTier.repairTag(WeaponMaterial.COPPER);
        String titleKey = "item." + BuiltInRegistries.ITEM.getKey(result)
                .toString().replace(':', '.');
        com.google.gson.JsonObject advancement = RecipeJson.advancement(
                new net.minecraft.world.item.ItemStack(result),
                titleKey, "advancements.cortescombat.weapon", id, "has_material",
                RecipeJson.hasItems(material));

        return CompletableFuture.allOf(
                RecipeJson.write(output, cached, id, "recipes", recipe),
                RecipeJson.write(output, cached, id, "advancements", advancement));
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/copper_gear";
    }
}
