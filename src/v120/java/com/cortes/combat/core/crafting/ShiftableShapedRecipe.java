package com.cortes.combat.core.crafting;

import com.cortes.combat.CortesCombat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Locale;
import java.util.Map;

/**
 * The 1.20.1 body of the shiftable shaped recipe. Same placements, older container.
 *
 * <p>1.20.1 has no {@code CraftingInput} (grids arrive as a {@code CraftingContainer} with a
 * linear slot list) and no {@code ShapedRecipePattern} (width, height and the ingredient list
 * live on the recipe itself). The geometry is untouched: {@code RecipeGrid}, which is pure
 * and shared, still decides the placements, and the cells around the pattern still have to
 * be empty. (⌒‿⌒)</p>
 *
 * <p>The file format is vanilla's {@code crafting_shaped} field for field, with ours in the
 * {@code type}: a file written for one loads in the other, because the names never changed.</p>
 */
public class ShiftableShapedRecipe extends ShapedRecipe {

    private final CraftingBookCategory ownCategory;

    public ShiftableShapedRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                                 int width, int height, NonNullList<Ingredient> ingredients,
                                 ItemStack result, boolean showNotification) {
        super(id, group, category, width, height, ingredients, result, showNotification);
        this.ownCategory = category;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        // No empty-stack trick on this leg: the constructor takes the plain result, so
        // super's is already the truth. The copy stays, because a recipe whose result is
        // the same instance every time is a stack a player can damage by using it.
        return super.getResultItem(registries).copy();
    }

    /**
     * Whether the pattern is made at this placement, mirrored or not.
     *
     * <p><b>It walks the whole grid, not the pattern.</b> Same reason as the shared twin:
     * once the placement moves, the cells around the pattern have to be checked too.</p>
     */
    private boolean fits(CraftingContainer input, int x, int y, boolean mirrored) {
        int width = getWidth();
        int height = getHeight();
        int gridWidth = input.getWidth();

        for (int row = 0; row < input.getHeight(); row++) {
            for (int column = 0; column < gridWidth; column++) {
                ItemStack stack = input.getItem(column + row * gridWidth);
                boolean inside = column >= x && column < x + width && row >= y && row < y + height;

                if (!inside) {
                    // **Anything outside the pattern has to be nothing.**
                    if (!stack.isEmpty()) {
                        return false;
                    }
                    continue;
                }

                int offset = mirrored ? width - 1 - (column - x) : column - x;
                Ingredient wanted = getIngredients().get(offset + (row - y) * width);
                if (!wanted.test(stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        // **Vanilla first**, which is the exact-size match. Then every placement, both
        // orientations: a mirrored pattern in a different corner is a different thing to look
        // at and the same thing to make.
        if (super.matches(input, level)) {
            return true;
        }
        for (int[] at : RecipeGrid.placements(
                getWidth(), getHeight(), input.getWidth(), input.getHeight())) {
            if (fits(input, at[0], at[1], false) || fits(input, at[0], at[1], true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The bench this can be made on, and the point of the whole class.
     */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        // **Ours, and not vanilla's.** The JSON names the type; the manager calls this to find
        // the serializer for the next file, and returning vanilla's would hand back a plain
        // ShapedRecipe that matches in one corner again.
        return ModRecipes.serializer();
    }

    /**
     * Reads and writes the <em>same file format</em> as {@code crafting_shaped}, field for field.
     */
    public static class Serializer implements RecipeSerializer<ShiftableShapedRecipe> {

        @Override
        public ShiftableShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            CraftingBookCategory category = CraftingBookCategory.valueOf(
                    GsonHelper.getAsString(json, "category", "misc").toUpperCase(Locale.ROOT));
            Map<Character, Ingredient> key = keyFromJson(GsonHelper.getAsJsonObject(json, "key"));
            JsonArray rows = GsonHelper.getAsJsonArray(json, "pattern");
            if (rows.size() == 0) {
                throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
            }
            String[] pattern = new String[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                pattern[i] = GsonHelper.convertToString(rows.get(i), "pattern[" + i + "]");
            }
            int width = pattern[0].length();
            int height = pattern.length;
            NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
            for (int row = 0; row < height; row++) {
                String line = pattern[row];
                if (line.length() != width) {
                    throw new JsonSyntaxException("Invalid pattern: row " + row
                            + " has " + line.length() + " columns, expected " + width);
                }
                for (int col = 0; col < width; col++) {
                    char symbol = line.charAt(col);
                    // **The space is vanilla's empty cell, not a symbol.** Vanilla's own
                    // reader maps it to `Ingredient.EMPTY` without asking the key; throwing
                    // here fails every grid with a hole, which is all five canonical ones.
                    // Same rule as the datagen writer in `RecipeJson`. (￣ε￣＠)
                    Ingredient ingredient;
                    if (symbol == ' ') {
                        ingredient = Ingredient.EMPTY;
                    } else {
                        ingredient = key.get(symbol);
                        if (ingredient == null) {
                            throw new JsonSyntaxException("Pattern references symbol '" + symbol
                                    + "' but it's not defined in the key");
                        }
                    }
                    ingredients.set(col + row * width, ingredient);
                }
            }
            JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
            ItemStack result = new ItemStack(ShapedRecipe.itemFromJson(resultJson),
                    GsonHelper.getAsInt(resultJson, "count", 1));
            boolean notification = GsonHelper.getAsBoolean(json, "show_notification", true);
            return new ShiftableShapedRecipe(id, group, category, width, height, ingredients,
                    result, notification);
        }

        private static Map<Character, Ingredient> keyFromJson(JsonObject key) {
            Map<Character, Ingredient> map = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : key.entrySet()) {
                if (entry.getKey().length() != 1) {
                    throw new JsonSyntaxException("Invalid key entry: '" + entry.getKey()
                            + "' is not a single character");
                }
                map.put(entry.getKey().charAt(0), Ingredient.fromJson(entry.getValue()));
            }
            return map;
        }

        @Override
        public ShiftableShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            String group = buf.readUtf();
            CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
            for (int i = 0; i < ingredients.size(); i++) {
                ingredients.set(i, Ingredient.fromNetwork(buf));
            }
            ItemStack result = buf.readItem();
            boolean notification = buf.readBoolean();
            return new ShiftableShapedRecipe(id, group, category, width, height, ingredients,
                    result, notification);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ShiftableShapedRecipe recipe) {
            buf.writeUtf(recipe.getGroup());
            buf.writeEnum(recipe.ownCategory);
            buf.writeVarInt(recipe.getWidth());
            buf.writeVarInt(recipe.getHeight());
            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredient.toNetwork(buf);
            }
            buf.writeItem(recipe.getResultItem(null));
            buf.writeBoolean(recipe.showNotification());
        }
    }

    /**
     * Registration, through the mod bus: built-in registries are frozen before mods
     * construct, so direct registration would die with "Registry is already frozen".
     * Same window the shared twin documents, one version down.
     */
    public static final class ModRecipes {

        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
                DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, CortesCombat.MODID);

        /**
         * {@code cortescombat:shiftable_shaped}.
         *
         * <p><b>And the property lives here, in the type.</b> A recipe is shiftable because its
         * JSON says so, not because the result item happens to be ours.</p>
         */
        private static final RegistryObject<RecipeSerializer<ShiftableShapedRecipe>> SHIFTABLE_SHAPED =
                SERIALIZERS.register("shiftable_shaped", Serializer::new);

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
