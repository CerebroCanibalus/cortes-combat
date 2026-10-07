package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.cortes.combat.core.crafting.ShiftableShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.conditions.WithConditions;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The arcabuz's recipe, which is the last item in the game that had no way to be made.
 *
 * <h2>The grid the design drew</h2>
 *
 * <pre>
 *   /#V
 *   #O#
 *   #MM
 * </pre>
 *
 * <p>Read from the top: a bamboo on one shoulder, the firer on the other, the gun body between
 * them, and two planks of any wood closing the bottom. It is the only vertical grid in the game
 * -- the other five shapes are diagonal or horizontal, because they are blades and a blade is
 * held across the body. This one is a long gun and it reads as one standing up.</p>
 *
 * <p>The asymmetry is the shape, not a mistake. {@code /} and {@code V} sit on opposite sides of
 * the top row and {@code #O#} leaves them nothing between them but air, so the mechanism is
 * visibly <em>beside</em> the barrel rather than under it, which is how a shoulder-fired gun
 * looks from behind.</p>
 *
 * <h2>Why this one is not the catalogue's loop</h2>
 *
 * <p>The same reason {@code FirestoneRecipes} and {@code NuggetRecipes} are separate: a provider
 * whose job is "loop thirty families by material" has no way to hold a single fixed item without
 * the loop growing an if, and the catalogue's whole value is that it has none. The arcabuz is
 * the thirty-first item, it has no tiers, and there is exactly one of it.</p>
 *
 * <h2>Why it does get an advancement when the firestone does not</h2>
 *
 * <p>{@code FirestoneRecipes} writes none on purpose: a firestone is an ingredient, and a toast
 * the first time somebody stirs gunpowder into glowstone is noise. This is the opposite case. The
 * arcabuz is a weapon, it is the only one in the game with a two-step right button, and a player
 * who never finds this entry never learns the gun exists -- which is what actually happened for
 * as long as the recipe was missing.</p>
 */
public final class ArcabuzRecipes implements DataProvider {

    /**
     * The four cells, as a table rather than as strings.
     *
     * <p>Written once and read twice: {@link #PATTERN} puts them on the grid and this comment is
     * where the design's letters survive. {@code V} is a flint and steel and {@code O} is a
     * dispenser because those are the two objects the grid is drawn out of; the symbols are ours,
     * the player only ever sees the result.</p>
     */
    private static final Map<Character, Ingredient> KEY = new LinkedHashMap<>();

    /**
     * Any wood. {@code #minecraft:planks} rather than {@code #minecraft:logs} because the cell is
     * two planks' worth of stock: it is the one part of the gun a player can build out of whatever
     * tree is nearest, which is also the part that gets replaced when they upgrade.
     */
    private static final TagKey<Item> PLANKS = TagKey.create(Registries.ITEM,
            ResourceLocation.withDefaultNamespace("planks"));

    static {
        // The bamboo is the ramrod, and it is bamboo for a reason that is mechanical rather than
        // picturesque: it is the one material in the game that is neither a plank nor a stick and
        // has no recipe of its own beyond the block, so nothing competes for it. It also makes
        // the shape read as a barrel rather than as a second stick.
        KEY.put('/', Ingredient.of(Items.BAMBOO));
        // The firer. A flint and steel is the one vanilla item whose entire job is "makes sparks",
        // so a recipe for a black powder gun that does not contain one reads as a mistake.
        KEY.put('V', Ingredient.of(Items.FLINT_AND_STEEL));
        // The body. A dispenser is the closest thing vanilla has to a machined box with a barrel
        // on it, and it is a mid-game item, which is the gate the design wanted: the gun arrives
        // after the bow does.
        KEY.put('O', Ingredient.of(Items.DISPENSER));
        KEY.put('M', Ingredient.of(PLANKS));
    }

    /**
     * The grid, with the empty cells written as spaces.
     *
     * <p><b>The spaces are load-bearing and {@code #} does not mean "empty".</b> The design draws
     * this grid with {@code #} for the gaps -- that is how {@code DESIGN.md} and the weapon shapes
     * all write them -- and Minecraft's own {@code ShapedRecipePattern} has no idea what {@code #}
     * is. It is a symbol with no entry in the key, and it is rejected outright:</p>
     *
     * <pre>
     *   IllegalStateException: Pattern references symbol '#' but it's not defined in the key
     * </pre>
     *
     * <p>The error names the symbol and not the recipe, so it reads as a typo in a file nobody
     * edited. The empty cell is a <b>space</b>, and that is the whole of the conversion -- the
     * same one {@code WeaponShape#toRecipeRows} performs for the catalogue's five shapes, done
     * here by hand because this grid is not in {@code WeaponShape}.</p>
     *
     * <p>Minecraft writes the pattern as it was given, spaces and all, so what lands on disk is the
 * design's three columns of three rather than a two-wide bounding box. {@link
 * ShapedRecipePattern#of} validates the grid -- consistent row widths, a width within the
 * crafting table's, and no symbol without an entry in the key -- and a typo in one of these
 * strings fails here with Minecraft's message instead of shipping a recipe the player cannot
 * make.</p>
     */
    private static final List<String> PATTERN = List.of("/ V", " O ", " MM");

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public ArcabuzRecipes(PackOutput output,
                          CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        // Chained on the lookup future rather than unwrapped here, for the reason both neighbours
        // give: the future is not done when the provider is built, and getNow(null) is a
        // NullPointerException from inside the serialisation layer that names nothing.
        return registries.thenCompose(provider -> CompletableFuture.allOf(
                DataProvider.saveStable(cached, provider, Recipe.CONDITIONAL_CODEC,
                        Optional.of(new WithConditions<>(recipe())),
                        recipePath()),
                advancement(cached, provider)));
    }

    /**
     * The recipe itself, built as a real {@link ShapedRecipe}.
     *
     * <p>And not typed as thirty lines of JSON would be, for the reason {@code CortesRecipeProvider}
     * gives: the pattern and the result go through Minecraft's own validation and serialiser, so a
     * grid typo fails here with Minecraft's message instead of shipping a recipe the player cannot
     * make.</p>
     */
    private static ShapedRecipe recipe() {
        // **Shiftable, for the same reason as the other thirty-two** and with the same caveat:
        // the arcabuz grid is 3x3, so it has one placement and vanilla already mirrored it. What
        // this buys today is nothing visible, and what it costs is nothing; what it avoids is a
        // grid change later turning into a second edit here.
        return new ShiftableShapedRecipe("", CraftingBookCategory.EQUIPMENT,
                ShapedRecipePattern.of(KEY, PATTERN),
                new ItemStack(ModItems.ARCABUZ.get()), true);
    }

    private Path recipePath() {
        // DATA_PACK, and not RESOURCE_PACK, for the reason the other two give: the two write to
        // different roots and a recipe under assets/ is a file the loader never reads. It parses,
        // it exists, and no recipe appears in game.
        return output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe")
                .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "arcabuz"));
    }

    /**
     * The recipe book entry, unlocked by holding a dispenser.
     *
     * <p>The dispenser rather than the firestone: a flint and steel is gravel plus iron and
     * arrives in the first ten minutes, and an entry that unlocks when the player has been able
     * to make one all game teaches nothing. The dispenser needs a bow and cobblestone, so the
     * entry appears at roughly the moment the gun becomes craftable, which is the moment the
     * player is looking for it.</p>
     *
     * <p>Its own description rather than the shared "Hecho a mano.", because this is one item and
     * not thirty copies of one: the thirty are the same grid repeated six times and a line that
     * does not change earns its place once. This one has to say what the gun is, because a player
     * reading the recipe book is about to spend a dispenser on a guess.</p>
     */
    private CompletableFuture<?> advancement(CachedOutput cached,
                                             HolderLookup.Provider registries) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "arcabuz");

        Advancement advancement = Advancement.Builder.recipeAdvancement()
                .parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT)
                // The eight-argument constructor and not a builder: DisplayInfo has no builder in
                // 1.21.1. Both booleans that matter are false, the same as the catalogue's -- a
                // toast on the thirty would be thirty pieces of noise over the one thing the
                // player needed to read.
                .display(new DisplayInfo(new ItemStack(ModItems.ARCABUZ.get()),
                        Component.translatable("item.cortescombat.arcabuz"),
                        Component.translatable("advancements.cortescombat.arcabuz"),
                        Optional.empty(),
                        AdvancementType.TASK,
                        false, false, true))
                .addCriterion("has_dispenser", has(Items.DISPENSER))
                // **Names the recipe, which is the whole difference between an entry and nothing.**
                // See CortesRecipeProvider for the reading behind it: the recipe book gates every
                // recipe on `RecipeBook#contains`, and `known` is filled from the recipes an
                // advancement grants. An advancement that names none grants none, so the gun loaded
                // and crafteable at the table and invisible in the book, with no error anywhere.
                .rewards(AdvancementRewards.Builder.recipe(id))
                .build(id)
                .value();

        return DataProvider.saveStable(cached, registries, Advancement.CONDITIONAL_CODEC,
                Optional.of(new WithConditions<>(advancement)),
                output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement").json(id));
    }

    /**
     * {@code hasItems} takes an ItemLike and no TagKey overload in 1.21.1, so an item goes in
     * directly and only a tag needs the explicit predicate. Worth noting because the catalogue
     * takes the other branch on purpose: a criterion naming an item instead of a tag unlocks on
     * the wrong material when a datapack swaps one out.
     */
    private static Criterion<?> has(Item item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(item).build());
    }

    @Override
    public String getName() {
        return CortesCombat.MODID + ":recipes/arcabuz";
    }
}