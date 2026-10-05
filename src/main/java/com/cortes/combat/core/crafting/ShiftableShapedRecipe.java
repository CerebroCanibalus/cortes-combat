package com.cortes.combat.core.crafting;

import com.cortes.combat.CortesCombat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;


/**
 * A shaped recipe that can be made anywhere in the grid, not only in the corner it was drawn in.
 *
 * <h2>Why this is a subclass and not a mixin</h2>
 *
 * <p><b>Because {@link ShapedRecipe} is not final and its match method is not either.</b> The first
 * design said a mixin, and that was wrong: it would have replaced a rule for every recipe in the
 * game in order to change one property of thirty of ours. The technique is a subclass that overrides
 * one method, and it is what a production mod does.</p>
 *
 * <p>Three things that had to be checked rather than assumed, read out of 21.1.250:</p>
 * <ul>
 *   <li>{@code ShapedRecipe} is {@code public class}, not {@code final}.</li>
 *   <li>{@code public boolean matches(CraftingInput, Level)} is overridable, and the constructor
 *       takes the pattern as a parameter.</li>
 *   <li>{@code public final ShapedRecipePattern pattern} is <b>public</b>, so a subclass reads it
 *       without an access transformer. (A mod doing this on an older version needs an AT; this one
 *       does not, and the reason is in the field declaration rather than in a config.)</li>
 * </ul>
 *
 * <h2>What vanilla already does, and is not repeated here</h2>
 *
 * <p><b>The horizontal mirror.</b> {@code ShapedRecipePattern#matches} tries the mirrored pattern
 * before the plain one, unconditionally except when the pattern is already a palindrome
 * per row. So "and also the other way round" is already true and this class does not re-implement
 * it &mdash; {@link #matches} calls {@code super} first, which is that.</p>
 *
 * <p><b>What is added is only the placement</b>, and both orientations at every placement, because
 * a mirrored pattern in a different corner is a different thing to look at and the same thing to
 * make.</p>
 *
 * <h2>The part that is easy to get wrong</h2>
 *
 * <p><b>The cells around the pattern have to be empty.</b> Checking only the cells the pattern
 * covers means the daga matches a bench with a block of iron in the corner next to it, and a recipe
 * that fires for a different arrangement of the table is worse than a recipe that does not fire:
 * the first one costs the player items.</p>
 *
 * @see RecipeGrid for the geometry, which is a pure function and is where the bug would be
 */
public class ShiftableShapedRecipe extends ShapedRecipe {

    /**
     * Our own copy of the result, because vanilla's is package-private.
     *
     * <p><b>{@code getResultItem} exists but is not usable here.</b> It returns
     * {@code this.result.copy()}, and it takes a {@code HolderLookup.Provider} that a codec does
     * not have at the point where it would need to read the field back. The alternatives were an
     * access transformer on a package-private field, or a fake provider; keeping the item is the
     * third option and it is the one the technique this class follows uses.</p>
     *
     * <p>So {@code super} gets an empty stack and this is the truth. That is safe because
     * {@code getResultItem} is the only thing anyone asks, and it is overridden below.</p>
     */
    private final ItemStack ownResult;

    public ShiftableShapedRecipe(String group, CraftingBookCategory category,
                                 ShapedRecipePattern pattern, ItemStack result,
                                 boolean showNotification) {
        super(group, category, pattern, ItemStack.EMPTY, showNotification);
        this.ownResult = result;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        // **Ours, not the empty stack that was handed to super.** And it is a copy, because vanilla
        // copies too and a recipe whose result is the same ItemStack instance every time is a stack
        // that a player can damage by using it.
        return ownResult.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        // The pattern is the one public field, so this needs no transformer either.
        return pattern.ingredients();
    }

    /**
     * Whether the pattern is made at this placement, mirrored or not.
     *
     * <p><b>It walks the whole grid, not the pattern.</b> That is the difference between this and
     * {@code ShapedRecipePattern#matches}, which walks the pattern and can therefore ignore whatever
     * is outside it: vanilla can do that because it has already proved the grid is the same size as
     * the pattern. Once the placement moves, that proof is gone and the cells around the pattern
     * have to be checked too.</p>
     *
     * @param mirrored read the pattern right to left, which is what vanilla calls symmetry
     */
    private boolean fits(CraftingInput input, int x, int y, boolean mirrored) {
        int width = pattern.width();
        int height = pattern.height();

        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                ItemStack stack = input.getItem(column, row);
                boolean inside = column >= x && column < x + width && row >= y && row < y + height;

                if (!inside) {
                    // **Anything outside the pattern has to be nothing.**
                    if (!stack.isEmpty()) {
                        return false;
                    }
                    continue;
                }

                int offset = mirrored ? width - 1 - (column - x) : column - x;
                Ingredient wanted = pattern.ingredients().get(offset + (row - y) * width);
                if (!wanted.test(stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // **Vanilla first**, and that call is not a courtesy: it is the exact-size match with the
        // horizontal mirror, which is the behaviour everything in the game already relies on.
        if (super.matches(input, level)) {
            return true;
        }
        for (int[] at : RecipeGrid.placements(
                pattern.width(), pattern.height(), input.width(), input.height())) {
            if (fits(input, at[0], at[1], false) || fits(input, at[0], at[1], true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The bench this can be made on, and the point of the whole class.
     *
     * <p><b>Overridden to {@code true}, and it has to be.</b> Vanilla asks this to decide whether
     * to show the recipe in the book and which slice of the grid to draw it in. Left alone it
     * reports the pattern's own size, so the book keeps drawing the daga in the top-left corner
     * even though the recipe now works in all four &mdash; and the thing the player reads would be
     * describing a rule the recipe no longer follows.</p>
     */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        // **Ours, and not vanilla's.** This is the line that decides whether the behaviour survives
        // a world reload: the JSON names a type, the manager calls this to find the serializer to
        // read the next line with, and returning the vanilla one hands back a plain ShapedRecipe
        // that matches in one corner again.
        return ModRecipes.serializer();
    }

    /**
     * Reads and writes the <em>same file format</em> as {@code crafting_shaped}, field for field.
     *
     * <p><b>Written out rather than {@code xmap}-ed over vanilla's codec, and the reason is
     * concrete.</b> The conversion would have to read the result out of a {@code ShapedRecipe}, and
     * that field is package-private with the only accessor demanding a provider a codec does not
     * have. So the five fields are declared here instead, with the same names, the same defaults
     * and the same codecs &mdash; which means a file written for one is readable by the other, and
     * a change to the format would have to be made in two places. That is the cost of not needing
     * an access transformer, and it is cheaper than the alternative.</p>
     */
    public static class Serializer implements RecipeSerializer<ShiftableShapedRecipe> {

        private static final MapCodec<ShiftableShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.STRING.optionalFieldOf("group", "")
                                .forGetter(ShiftableShapedRecipe::getGroup),
                        CraftingBookCategory.CODEC.fieldOf("category")
                                .orElse(CraftingBookCategory.MISC)
                                .forGetter(ShiftableShapedRecipe::category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result")
                                .forGetter(recipe -> recipe.ownResult),
                        Codec.BOOL.optionalFieldOf("show_notification", Boolean.TRUE)
                                .forGetter(ShiftableShapedRecipe::showNotification)
                ).apply(instance, ShiftableShapedRecipe::new));

        @Override
        public MapCodec<ShiftableShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShiftableShapedRecipe> streamCodec() {
            return StreamCodec.of(
                    (buf, recipe) -> {
                        buf.writeUtf(recipe.getGroup());
                        buf.writeEnum(recipe.category());
                        ShapedRecipePattern.STREAM_CODEC.encode(buf, recipe.pattern);
                        ItemStack.STREAM_CODEC.encode(buf, recipe.ownResult);
                        buf.writeBoolean(recipe.showNotification());
                    },
                    buf -> new ShiftableShapedRecipe(
                            buf.readUtf(),
                            buf.readEnum(CraftingBookCategory.class),
                            ShapedRecipePattern.STREAM_CODEC.decode(buf),
                            ItemStack.STREAM_CODEC.decode(buf),
                            buf.readBoolean()));
        }
    }

    /**
     * Registration, and the one thing about it that is not obvious.
     *
     * <p><b>{@code DeferredRegister}, not {@code Registry.register}, and not because of style.</b>
     * The first version called
     * {@code Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ...)} from the mod's
     * constructor, the way vanilla's own package-private {@code RecipeSerializer#register} does,
     * and it failed at datagen with
     * {@code Registry is already frozen (trying to add key .../cortescombat:shiftable_shaped)}:
     * the built-in registries are frozen <em>before</em> FML constructs the mods.</p>
     *
     * <p>So the registry is right and the timing is not. {@code BuiltInRegistries:160} builds that
     * registry <em>from</em> the key {@code Registries.RECIPE_SERIALIZER}, and a
     * {@code DeferredRegister} over that key registers during the mod event bus, which is the
     * window that is still open. Same registry, different moment.</p>
     */
    public static final class ModRecipes {

        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, CortesCombat.MODID);

        /**
         * {@code cortescombat:shiftable_shaped}.
         *
         * <p><b>And the property lives here, in the type.</b> A recipe is shiftable because its JSON
         * says so, not because the result item happens to be ours: that is a question about the
         * recipe's identity rather than about its output, and it means adding a shiftable recipe
         * later is a change in a file and not in code.</p>
         */
        private static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ShiftableShapedRecipe>>
                SHIFTABLE_SHAPED = SERIALIZERS.register("shiftable_shaped", Serializer::new);

        public static RecipeSerializer<ShiftableShapedRecipe> serializer() {
            return SHIFTABLE_SHAPED.get();
        }

        public static void register(IEventBus modEventBus) {
            SERIALIZERS.register(modEventBus);
        }

        private ModRecipes() {
        }
    }
}