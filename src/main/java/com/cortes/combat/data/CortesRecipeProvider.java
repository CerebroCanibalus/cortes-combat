package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.cortes.combat.core.crafting.ShiftableShapedRecipe;
import com.cortes.combat.core.crafting.ShiftableShapedRecipe;
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
 * The thirty recipes.
 *
 * <h2>Why this does not extend {@code RecipeProvider}</h2>
 *
 * <p>It cannot, on NeoForge 1.21.1. {@code RecipeProvider.getName()} is <b>{@code final}</b> and
 * returns the literal string {@code "Recipes"}. NeoForge registers its own
 * {@code NeoForgeRecipeProvider} into the same {@code DataGenerator} under that same name, and the
 * second provider to arrive is rejected:</p>
 *
 * <pre>
 *   java.lang.IllegalStateException: Duplicate provider: Recipes
 * </pre>
 *
 * <p>The message names neither the mod nor the class, so it reads like a broken generator. No
 * subclass avoids it and {@code --all} is not the cause: removing that flag changes nothing. The
 * method cannot be overridden and the class cannot be renamed, so a mod on this version writes
 * recipes as a plain {@link DataProvider}. This is the only route, not a preference.</p>
 *
 * <h2>What is still not hand-written</h2>
 *
 * <p>The recipe itself is built as a real {@link ShapedRecipe} and serialised through
 * {@code ShapedRecipe.Serializer.CODEC}, so the key/pattern/result layout is Minecraft's own
 * format rather than thirty strings typed by hand. A recipe that loads is a recipe a player can
 * craft; the failure mode this avoids is a JSON shape that parses and means nothing.</p>
 *
 * <p>The saving is the pattern: it comes from {@link WeaponShape}, so the recipe on disk is
 * exactly the grid the design drew, and a grid edited in one place cannot disagree with the
 * thirty recipes.</p>
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
        // Chain on the lookup future rather than unwrapping it in the constructor.
        //
        // getNow(null) returns null when the future has not completed, and it is not completed
        // when the provider is constructed -- it completes once the registries are frozen. The
        // result is a NullPointerException from deep inside DataFixerUpper's
        // createSerializationContext, which names neither this class nor the provider and reads
        // like a bug in the serialisation layer.
        return registries.thenCompose(provider -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (WeaponFamily family : WeaponFamily.values()) {
                for (WeaponMaterial material : family.materialsInOrder()) {
                    futures.add(write(cached, provider, family, material));
                }
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    private CompletableFuture<?> write(CachedOutput cached, HolderLookup.Provider registries,
                                       WeaponFamily family, WeaponMaterial material) {
        ResourceLocation id = CortesCombat.rl(family.itemName(material));
        Item result = ModItems.weapon(family.itemName(material)).get();

        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('X', ingredient(family.bodyIngredient(material)));
        // **The stick goes in only when the grid uses one.**
        //
        // `ShapedRecipePattern.of` rejects a key symbol the pattern never mentions --
        // "Key defines symbols that aren't used in pattern: {/}" -- and the warhammer's column is
        // material, core, material with no stick in it. So an unconditional `key.put('/', ...)` is
        // a datagen failure the moment a grid drops the haft, which is exactly what the martillo's
        // correction did. The `*` below is conditional for the same reason and always was.
        if (family.shape().sticks() > 0) {
            key.put('/', Ingredient.of(Items.STICK));
        }
        WeaponFamily.IngredientRef extra = family.extraIngredient(material);
        if (extra != null) {
            key.put('*', ingredient(extra));
        }

        // ShapedRecipePattern.of is Minecraft's own validation: it trims the empty border, checks
        // the width against getMaxWidth and rejects a pattern that uses a symbol nobody defined.
        // Calling it here means a grid typo fails at datagen with Minecraft's message instead of
        // shipping a recipe the player cannot make.
        //
        // toRecipeRows and not rows(): Minecraft spells an empty cell as a space, and DESIGN.md
        // draws it as '#'. Passing '#' straight through is what produced
        // "Pattern references symbol '#' but it's not defined in the key".
        ShapedRecipePattern pattern =
                ShapedRecipePattern.of(key, family.shape().toRecipeRows());

        // **ShiftableShapedRecipe and not ShapedRecipe**, and the JSON "type" changes by itself:
        // `Recipe.CODEC` is `RECIPE_SERIALIZER.byNameCodec().dispatch(Recipe::getSerializer,
        // RecipeSerializer::codec)`, so the type written is whichever `getSerializer()` reports.
        // Nothing here names the id.
        //
        // **On the grids of today only the daga gains anything** -- it is the one 2x2 shape and the
        // four others fill the bench. They were already accepting the mirrored pattern, because
        // vanilla tries the mirror before the plain one and that has not changed. This goes on all
        // of them so the property belongs to "a recipe of ours" rather than to "a grid that happens
        // to be smaller than the bench", which is not something you can tell by reading a shape.
        ShapedRecipe recipe = new ShiftableShapedRecipe(
                "", CraftingBookCategory.EQUIPMENT, pattern, new ItemStack(result), true);

        return CompletableFuture.allOf(
                // CONDITIONAL_CODEC, not the plain one: it is what vanilla's own RecipeProvider
                // saves with, and it is the codec that can carry an optional condition list. The
                // plain one still works today and stops working the day a recipe needs a loader.
                DataProvider.saveStable(cached, registries, Recipe.CONDITIONAL_CODEC,
                        Optional.of(new WithConditions<>(recipe)), recipePath(id)),
                advancement(cached, registries, id, result, material));
    }

    private Path recipePath(ResourceLocation id) {
        // DATA_PACK and not RESOURCE_PACK. The two targets write to different roots -- data/ and
        // assets/ -- and a recipe emitted into assets/ is a file the recipe loader never reads. It
        // is a silent failure: the file exists, it parses, and no recipe appears in game.
        //
        // **And `"recipe"` singular, because 1.21 renamed every datapack folder.** The vanilla jar
        // carries `data/minecraft/recipe/` and `data/minecraft/advancement/` and the plural of
        // neither, and a folder the loader does not read is that same silent nothing one layer up.
        // This line and the one in `advancement` were `"recipes"` and `"advancements"`, so the mod
        // shipped thirty-four recipes and thirty-four advancements that <b>never loaded once</b> --
        // and `Loaded 1290 recipes` in the log was vanilla's own count, which is why it read as
        // proof they had. The names the game reads are pinned in `DataFolderNamesTest`.
        return output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe").json(id);
    }

    /**
     * The advancement, written by hand because {@code RecipeProvider} would have done it.
     *
     * <p>It matters: a recipe with no advancement appears in the recipe book as something the
     * player has already discovered, which teaches nothing and looks like a bug. The recipe book
     * is where a player learns that the five grids are different shapes, so the entries have to
     * be there.</p>
     *
     * <p>Unlocked by having the tier's material and never by having the result, because a recipe
     * whose criterion is its own output can never be discovered.</p>
     */
    private CompletableFuture<?> advancement(CachedOutput cached, HolderLookup.Provider registries,
                                             ResourceLocation recipeId,
                                             Item result, WeaponMaterial material) {
        // DATA_PACK for the same reason as the recipe, and `"advancement"` singular for the same
        // reason too -- see recipePath. An advancement in `advancements/` is one the game never
        // loads, and without it the recipe never enters the book even when the recipe itself is
        // fine.
        Path path = output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement")
                .json(recipeId);

        TagKey<Item> bodyTag = TagKey.create(Registries.ITEM,
                ResourceLocation.withDefaultNamespace(material.repairTag()));

        Advancement advancement = Advancement.Builder.recipeAdvancement()
                // Vanilla's own root for every recipe. Hard-coding "minecraft:recipes/root" would
                // be the same string, but the constant is what vanilla's RecipeProvider uses and
                // it is the one that moves if Mojang moves it.
                .parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
                // The title and description cannot be null: DisplayInfo's constructor calls
                // Component.copy() on both, and a null there is a NullPointerException from
                // inside the advancement serialiser that names neither this class nor the recipe.
                //
                // They are translation keys rather than literals, so the lang file stays the one
                // place a name lives. Thirty entries of "Craft a Thing" would have been the wrong
                // kind of lazy; the item's own name key is the same string the hotbar shows, which
                // is what the player is looking for in the recipe book.
                //
                // DisplayInfo has no builder in 1.21.1, only the eight-argument constructor. The
                // two booleans that matter are showToast and announceToChat, both false: thirty
                // "Recipes unlocked!" toasts in the first second of a new world is thirty pieces
                // of noise over the one thing the player needed to read. The last is hidden,
                // which keeps the entry out of the toast list without touching the recipe book.
                .display(new DisplayInfo(new ItemStack(result),
                        Component.translatable("item." + BuiltInRegistries.ITEM
                                .getKey(result).toString().replace(':', '.')),
                        Component.translatable("advancements.cortescombat.weapon"),
                        Optional.empty(),
                        AdvancementType.TASK,
                        false, false, true))
                // Unlocked by having the tier's material, never by having the result: a recipe
                // whose criterion is its own output can never be discovered.
                .addCriterion("has_material", has(bodyTag))
                // **THE RECIPE, NAMED. Without this line the recipe book is empty and nothing in
                // the log says so.**
                //
                // Verified against 1.21.1 rather than remembered, because the symptom names nothing.
                // `RecipeCollection#canCraft` gates every recipe on
                // `recipe.canCraftInDimensions(w, h) && book.contains(recipeholder)`, and
                // `RecipeBook#contains` is `known.contains(recipe.id())` -- a set of ids the
                // server fills from `AdvancementRewards#recipes` when an advancement is granted.
                //
                // An advancement that names no recipe grants no recipe. So this file was writing
                // a perfectly valid recipe advancement -- the right parent, the right category, the
                // right criterion, the item as the icon -- that granted nothing at all. The recipe
                // loaded (1290 of them, zero parse errors), it was even in the right tab, and the
                // book dropped it on the floor one line before drawing it.
                //
                // The failure is total and silent: `/give` works, the crafting table works, the
                // JSON is in the jar, and a player opening the book sees an empty tab and no
                // error anywhere. Which is why this is a comment rather than a one-liner.
                .rewards(AdvancementRewards.Builder.recipe(recipeId))
                // build(ResourceLocation) is the only build on Builder, and it returns the holder.
                // The holder is what carries the id; value() unwraps the advancement out of it.
                .build(recipeId)
                .value();

        return DataProvider.saveStable(cached, registries, Advancement.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(advancement)), path);
    }

    /**
     * {@code hasItems} has no TagKey overload in 1.21.1, only ItemLike, so the tag goes in
     * through an explicit {@link ItemPredicate}. Worth the three lines: a criterion that named an
     * item instead would unlock on the wrong material and the recipe would sit in the book
     * unclaimed for a player using a datapack's material.
     */
    private static Criterion<?> has(TagKey<Item> tag) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(tag).build());
    }

    /** An item or a tag, from the pure descriptor the catalogue produced. */
    private static Ingredient ingredient(WeaponFamily.IngredientRef ref) {
        ResourceLocation id = ref.id().contains(":")
                ? ResourceLocation.parse(ref.id())
                : ResourceLocation.withDefaultNamespace(ref.id());
        if (ref.tag()) {
            return Ingredient.of(TagKey.create(Registries.ITEM, id));
        }
        // Ingredient.of has no ResourceLocation overload in 1.21.1, so a named item resolves
        // through the registry. getOptional rather than get: a typo in a hand-written cell would
        // otherwise throw from inside the registry naming neither the recipe nor the item.
        return BuiltInRegistries.ITEM.getOptional(id)
                .map(Ingredient::of)
                .orElseThrow(() -> new IllegalStateException(
                        "recipe ingredient " + id + " is not a registered item"));
    }

    @Override
    public String getName() {
        return CortesCombat.MODID + " Recipes";
    }
}
