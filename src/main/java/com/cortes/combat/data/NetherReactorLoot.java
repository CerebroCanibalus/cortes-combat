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
 * The nether reactor's loot table: it drops itself.
 *
 * <p>Written by hand for the same reason {@link FirestoneBlockLoot} is: vanilla's
 * {@code BlockLootSubProvider} walks every block in the game and has no seam for one of ours. The
 * folder and the shape are the ones the game reads -- {@code data/<ns>/loot_table/blocks/<id>.json},
 * with the {@code survives_explosion} condition so a blast behaves like it does for every other
 * block.</p>
 *
 * <p><b>The firestones are not here.</b> The block entity's inventory is spilled by the block's own
 * {@code onRemove}, not by this table: a loot table can describe the block, not the contents of the
 * thing that was standing in it. Keeping the two apart is what stops a broken reactor from dropping
 * its firestones twice.</p>
 */
public final class NetherReactorLoot implements DataProvider {

    private final PackOutput output;

    public NetherReactorLoot(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        ResourceLocation block = CortesCombat.rl("nether_reactor");

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
        table.addProperty("random_sequence", CortesCombat.MODID + ":blocks/nether_reactor");

        return DataProvider.saveStable(cached, table,
                output.createPathProvider(PackOutput.Target.DATA_PACK, "loot_table/blocks")
                        .json(block));
    }

    @Override
    public String getName() {
        return "cortescombat:loot/nether_reactor";
    }
}
