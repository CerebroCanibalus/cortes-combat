package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

/**
 * The firestone block's loot table: it drops itself.
 *
 * <p><b>The mod's first block loot table, and the first thing it has to get right is the folder.</b>
 * 1.21 reads {@code data/<ns>/loot_table/blocks/<id>.json} -- singular {@code loot_table}, and the
 * {@code blocks/} subfolder is not decoration: the game builds a block's loot id as
 * {@code <block id path>} under the {@code blocks/} prefix. A table written to {@code loot_tables/}
 * or to the root of {@code loot_table/} is a file the game never looks for, and a block with no loot
 * table drops nothing at all with no error anywhere.</p>
 *
 * <p><b>Written as JSON rather than through a provider.</b> Vanilla generates this shape from
 * {@code BlockLootSubProvider}, whose {@code run} is concrete and walks every block in the game;
 * there is no seam to add one of ours through, which is the same reason the model and recipe
 * providers in this package are plain {@code DataProvider}s.</p>
 *
 * <p>The table is the one vanilla writes for a block that drops itself: a single pool, a single
 * item, and the {@code survives_explosion} condition so a block caught in a blast behaves like every
 * other block caught in one. The block's own detonation never reaches this table, because
 * {@code FirestoneBlock.detonate} removes the block before the blast that would have spawned its
 * drops.</p>
 */
public final class FirestoneBlockLoot implements DataProvider {

    private final PackOutput output;

    public FirestoneBlockLoot(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        ResourceLocation block = CortesCombat.rl("firestone_block");

        JsonObject explosion = new JsonObject();
        explosion.addProperty("condition", "minecraft:survives_explosion");

        JsonArray conditions = new JsonArray();
        conditions.add(explosion);

        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", block.toString());

        JsonArray entries = new JsonArray();
        entries.add(entry);

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1.0D);
        pool.addProperty("bonus_rolls", 0.0D);
        pool.add("conditions", conditions);
        pool.add("entries", entries);

        JsonArray pools = new JsonArray();
        pools.add(pool);

        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:block");
        table.add("pools", pools);
        // The sequence vanilla's own tables carry, and the id is the same shape: the block's own
        // name under `blocks/`. It makes a blast's drops reproducible, which is not cosmetic.
        table.addProperty("random_sequence",
                CortesCombat.MODID + ":blocks/firestone_block");

        return DataProvider.saveStable(cached, table,
                output.createPathProvider(PackOutput.Target.DATA_PACK, "loot_table/blocks")
                        .json(block));
    }

    @Override
    public String getName() {
        return "cortescombat:loot/firestone_block";
    }
}
