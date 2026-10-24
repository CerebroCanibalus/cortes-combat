package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The 1.20.1 JSON shapes, written by hand, in one place.
 *
 * <p>1.20.1 datagen has no {@code ShapedRecipePattern}, no {@code WithConditions} wrapper and
 * no conditional codecs: recipes and advancements are plain {@code JsonObject}s saved with
 * {@code saveStable}, exactly the bytes the loaders read. Every provider twin below builds
 * its files through here, so the folders ({@code recipes} and {@code advancements},
 * <b>plural</b> on this leg), the criterion shape and the result shape are decided once.
 * (⌒‿⌒)</p>
 */
public final class RecipeJson {

    private RecipeJson() {
    }

    /**
     * A shaped recipe file. The type travels as a name because the serializer is what the
     * <em>game</em> reads; datagen only writes the string.
     */
    public static JsonObject shaped(String type, CraftingBookCategory category, String group,
                                    List<String> rows, Map<Character, Ingredient> key,
                                    ItemStack result, boolean notification) {
        if (rows.isEmpty()) {
            throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
        }
        int width = rows.get(0).length();
        JsonArray pattern = new JsonArray();
        for (int row = 0; row < rows.size(); row++) {
            String line = rows.get(row);
            if (line.length() != width) {
                throw new JsonSyntaxException("Invalid pattern: row " + row
                        + " has " + line.length() + " columns, expected " + width);
            }
            for (int col = 0; col < width; col++) {
                char symbol = line.charAt(col);
                // **The space is vanilla's empty cell, not a symbol.** `ShapedRecipe` reserves
                // it and never asks the key for it; requiring it here fails every grid with
                // a hole, which is all five canonical ones. (￣ε￣＠)
                if (symbol == ' ') {
                    continue;
                }
                if (!key.containsKey(symbol)) {
                    throw new JsonSyntaxException("Pattern references symbol '"
                            + symbol + "' but it's not defined in the key");
                }
            }
            pattern.add(line);
        }
        // **The stick goes in only when the grid uses one.** Same rule as the shared twin's
        // `ShapedRecipePattern.of`: a key symbol the pattern never mentions fails datagen.
        for (char symbol : key.keySet()) {
            boolean used = false;
            for (String line : rows) {
                if (line.indexOf(symbol) >= 0) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                throw new JsonSyntaxException(
                        "Key defines symbol '" + symbol + "' that isn't used in pattern");
            }
        }
        JsonObject keyJson = new JsonObject();
        for (Map.Entry<Character, Ingredient> entry : key.entrySet()) {
            keyJson.add(String.valueOf(entry.getKey()), entry.getValue().toJson());
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("category", category.getSerializedName());
        json.addProperty("group", group);
        json.add("pattern", pattern);
        json.add("key", keyJson);
        json.add("result", resultJson(result));
        json.addProperty("show_notification", notification);
        return json;
    }

    /** A shapeless recipe file, in vanilla's shape. */
    public static JsonObject shapeless(CraftingBookCategory category, String group,
                                       NonNullList<Ingredient> ingredients, ItemStack result) {
        JsonArray list = new JsonArray();
        for (Ingredient ingredient : ingredients) {
            list.add(ingredient.toJson());
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        json.addProperty("category", category.getSerializedName());
        json.addProperty("group", group);
        json.add("ingredients", list);
        json.add("result", resultJson(result));
        return json;
    }

    private static JsonObject resultJson(ItemStack result) {
        JsonObject json = new JsonObject();
        json.addProperty("item",
                BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        if (result.getCount() > 1) {
            json.addProperty("count", result.getCount());
        }
        return json;
    }

    /** A recipe-book advancement: parent, icon, one criterion, and the recipe it grants. */
    public static JsonObject advancement(ItemStack icon, String titleKey, String descKey,
                                         ResourceLocation recipeId, String criterionName,
                                         JsonElement criterion) {
        JsonObject display = new JsonObject();
        JsonObject iconJson = new JsonObject();
        iconJson.addProperty("item", BuiltInRegistries.ITEM.getKey(icon.getItem()).toString());
        display.add("icon", iconJson);
        JsonObject title = new JsonObject();
        title.addProperty("translate", titleKey);
        display.add("title", title);
        JsonObject desc = new JsonObject();
        desc.addProperty("translate", descKey);
        display.add("description", desc);
        display.addProperty("frame", "task");
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", true);

        JsonObject criteria = new JsonObject();
        criteria.add(criterionName, criterion);

        JsonArray requirements = new JsonArray();
        JsonArray one = new JsonArray();
        one.add(criterionName);
        requirements.add(one);

        JsonObject rewards = new JsonObject();
        JsonArray recipes = new JsonArray();
        recipes.add(recipeId.toString());
        rewards.add("recipes", recipes);

        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:recipes/root");
        json.add("display", display);
        json.add("criteria", criteria);
        json.add("requirements", requirements);
        json.add("rewards", rewards);
        return json;
    }

    /** An inventory-changed criterion over a tag, in vanilla's shape. */
    public static JsonObject hasItems(TagKey<Item> tag) {
        // **The tag rides in `tag`, not in `items`.** An entry like `"#ns:path"` inside the
        // `items` array parses as an item id and dies on the `#` ("Non [a-z0-9_.-] character
        // in namespace"); vanilla's `ItemPredicate` reads the tag from its own field.
        // (￣ε￣＠)
        JsonObject predicate = new JsonObject();
        predicate.addProperty("tag", tag.location().toString());
        return wrap(predicate);
    }

    /** An inventory-changed criterion over one item, in vanilla's shape. */
    public static JsonObject hasItems(ItemLike item) {
        return criterion(BuiltInRegistries.ITEM.getKey(item.asItem()).toString());
    }

    private static JsonObject criterion(String id) {
        JsonObject predicate = new JsonObject();
        JsonArray names = new JsonArray();
        names.add(id);
        predicate.add("items", names);
        return wrap(predicate);
    }

    private static JsonObject wrap(JsonObject predicate) {
        JsonArray list = new JsonArray();
        list.add(predicate);
        JsonObject conditions = new JsonObject();
        conditions.add("items", list);
        JsonObject criterionObj = new JsonObject();
        criterionObj.addProperty("trigger", "minecraft:inventory_changed");
        criterionObj.add("conditions", conditions);
        return criterionObj;
    }

    /** Saves one JSON file under {@code data/<ns>/<folder>/<id>.json}. */
    public static CompletableFuture<?> write(PackOutput output, CachedOutput cached,
                                             ResourceLocation id, String folder, JsonObject json) {
        Path path = output.createPathProvider(PackOutput.Target.DATA_PACK, folder).json(id);
        return DataProvider.saveStable(cached, json, path);
    }

    /** Our namespaced ids, the same call the shared twins make. */
    public static ResourceLocation rl(String path) {
        return new ResourceLocation(CortesCombat.MODID, path);
    }
}
