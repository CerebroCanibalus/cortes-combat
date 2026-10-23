package com.cortes.combat.data;

import com.cortes.combat.platform.ResourceIds;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.crafting.ShiftableShapedRecipe;
import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.registry.CortesTier;
import com.cortes.combat.registry.ModArmor;
import com.cortes.combat.registry.ModTools;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.neoforge.common.conditions.WithConditions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The nine recipes that make the copper set: five tools and four armour pieces.
 *
 * <p>Every shape is vanilla's own for that item, with copper where the metal goes, because the
 * thing a player already knows how to make should not have to be learned again. The sword is the
 * sword grid, the chestplate is the chestplate grid, and a copper pickaxe is made the way an iron
 * one is.</p>
 *
 * <p>Built as real {@link ShapedRecipe} objects and serialised through
 * {@code Recipe.CONDITIONAL_CODEC}, the same as the catalogue, so the key/pattern/result layout is
 * Minecraft's own and a grid typo fails at datagen with Minecraft's message.</p>
 *
 * <p><b>{@link ShiftableShapedRecipe} rather than plain {@code ShapedRecipe}</b>, for the property
 * the catalogue recipes already have: a recipe of ours crafts wherever the player puts it in the
 * grid, mirrored or not. It costs nothing here -- only the two-by-two armour pieces can use it --
 * but "a recipe of ours" is a better rule than "a grid smaller than the bench", which is not
 * something you can tell by reading a shape.</p>
 *
 * <p>No advancements, like the nuggets and the firestone. {@code RecipeBookPopulation} puts every
 * recipe in our namespace into the book on login, so the gear reaches a survival player without
 * nine more files.</p>
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

            // The five tools, each in vanilla's own shape. A space is an empty cell; the `#` in
            // DESIGN.md's drawings is not, and passing it through is the datagen failure this
            // project has paid for more than once.
            futures.add(shaped(cached, provider, CopperGear.SWORD, ModTools.COPPER_SWORD.get(),
                    List.of("X", "X", "/")));
            futures.add(shaped(cached, provider, CopperGear.PICKAXE, ModTools.COPPER_PICKAXE.get(),
                    List.of("XXX", " / ", " / ")));
            futures.add(shaped(cached, provider, CopperGear.AXE, ModTools.COPPER_AXE.get(),
                    List.of("XX", "X/", " /")));
            futures.add(shaped(cached, provider, CopperGear.SHOVEL, ModTools.COPPER_SHOVEL.get(),
                    List.of("X", "/", "/")));
            futures.add(shaped(cached, provider, CopperGear.HOE, ModTools.COPPER_HOE.get(),
                    List.of("XX", " /", " /")));

            // And the four armour pieces, which use no stick at all.
            futures.add(shaped(cached, provider, CopperGear.HELMET, ModArmor.COPPER_HELMET.get(),
                    List.of("XXX", "X X")));
            futures.add(shaped(cached, provider, CopperGear.CHESTPLATE,
                    ModArmor.COPPER_CHESTPLATE.get(), List.of("X X", "XXX", "XXX")));
            futures.add(shaped(cached, provider, CopperGear.LEGGINGS,
                    ModArmor.COPPER_LEGGINGS.get(), List.of("XXX", "X X", "X X")));
            futures.add(shaped(cached, provider, CopperGear.BOOTS, ModArmor.COPPER_BOOTS.get(),
                    List.of("X X", "X X")));

            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    private CompletableFuture<?> shaped(CachedOutput cached, HolderLookup.Provider registries,
                                        String name, Item result, List<String> rows) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('X', Ingredient.of(Items.COPPER_INGOT));
        // **The stick goes in only when the grid uses one.** ShapedRecipePattern.of rejects a key
        // symbol the pattern never mentions, and no armour piece has a haft -- so an unconditional
        // key.put here is a datagen failure the moment a shape drops the stick.
        if (rows.stream().anyMatch(row -> row.indexOf('/') >= 0)) {
            key.put('/', Ingredient.of(Items.STICK));
        }

        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, rows);
        ShapedRecipe recipe = new ShiftableShapedRecipe(
                "", CraftingBookCategory.EQUIPMENT, pattern, new ItemStack(result), true);

        ResourceLocation id = ResourceIds.rl(name);
        // DATA_PACK and `"recipe"` singular: the two silent failures this project has paid for. A
        // recipe written under assets/ or under `recipes/` is a file the game never reads.
        return CompletableFuture.allOf(
                DataProvider.saveStable(cached, registries, Recipe.CONDITIONAL_CODEC,
                        Optional.of(new WithConditions<>(recipe)),
                        output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe").json(id)),
                advancement(cached, registries, id, result));
    }

    /**
     * The advancement that puts the recipe in the book, and <b>the recipe is named in it</b>.
     *
     * <p>A test said so, and it was right: every recipe of ours carries an advancement except the
     * two nugget conversions, because a recipe with no advancement appears in the book as
     * something the player already knows. My first pass skipped these on the theory that
     * {@code RecipeBookPopulation} covered them, and {@code RecipeBookEntryTest} caught the nine
     * that would have been craftable at the table and invisible in the book.</p>
     *
     * <p>Unlocked by holding the material and never by holding the result -- a recipe whose
     * criterion is its own output can never be discovered. The material here is the copper tool
     * tag rather than a named ingot, so a datapack that adds a copper source is honoured.</p>
     */
    private CompletableFuture<?> advancement(CachedOutput cached, HolderLookup.Provider registries,
                                             ResourceLocation recipeId, Item result) {
        Path path = output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement")
                .json(recipeId);

        Advancement advancement = Advancement.Builder.recipeAdvancement()
                .parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
                .display(new DisplayInfo(new ItemStack(result),
                        Component.translatable("item." + BuiltInRegistries.ITEM
                                .getKey(result).toString().replace(':', '.')),
                        Component.translatable("advancements.cortescombat.weapon"),
                        Optional.empty(),
                        AdvancementType.TASK,
                        false, false, true))
                .addCriterion("has_material", has(CortesTier.repairTag(WeaponMaterial.COPPER)))
                .rewards(AdvancementRewards.Builder.recipe(recipeId))
                .build(recipeId)
                .value();

        return DataProvider.saveStable(cached, registries, Advancement.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(advancement)), path);
    }

    /**
     * {@code hasItems} has no TagKey overload in 1.21.1, only ItemLike, so the tag goes in through
     * an explicit {@link ItemPredicate} -- the same three lines the catalogue's provider uses.
     */
    private static Criterion<?> has(TagKey<Item> tag) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(tag).build());
    }

    @Override
    public String getName() {
        return "cortescombat:recipes/copper_gear";
    }
}
