package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.block.ReactorTags;
import com.cortes.combat.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * The block tags the firestone block needs, which is one.
 *
 * <p><b>{@code #minecraft:mineable/pickaxe} is what makes a pickaxe the fast tool.</b> It is the
 * block-side twin of the item tags {@link CortesItemTagProvider} writes, and without it the block is
 * still breakable -- hardness one is not gated on a tool -- but every tool mines it at the same
 * speed, so "it is a pickaxe block" would be true on paper and invisible in the hand.</p>
 *
 * <p><b>A separate provider from the item tags, and the reason is a registry and not a style.</b>
 * A tags provider is parameterised by one registry; {@code Block} and {@code Item} are two, and one
 * class cannot be both. Its constructor carries the mod id for the same reason the item one does --
 * the three-argument form defaults it to {@code "vanilla"} and collides with the vanilla block-tag
 * provider under a "Duplicate provider" that names nobody.</p>
 */
public final class CortesBlockTagProvider extends IntrinsicHolderTagsProvider<Block> {

    public CortesBlockTagProvider(PackOutput output,
                                  CompletableFuture<HolderLookup.Provider> registries,
                                  ExistingFileHelper existingFileHelper) {
        super(output, Registries.BLOCK, registries,
                block -> BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow(),
                CortesCombat.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // One entry into vanilla's own tag, which merges with vanilla's contents: this adds our
        // blocks to the pickaxe list rather than replacing it.
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.FIRESTONE_BLOCK.get(),
                ModBlocks.NETHER_REACTOR.get());

        tag(ReactorTags.SWITCH).add(
                Blocks.LEVER,
                Blocks.STONE_BUTTON,
                Blocks.OAK_BUTTON,
                Blocks.SPRUCE_BUTTON,
                Blocks.BIRCH_BUTTON,
                Blocks.JUNGLE_BUTTON,
                Blocks.ACACIA_BUTTON,
                Blocks.DARK_OAK_BUTTON,
                Blocks.MANGROVE_BUTTON,
                Blocks.CHERRY_BUTTON,
                Blocks.BAMBOO_BUTTON,
                Blocks.CRIMSON_BUTTON,
                Blocks.WARPED_BUTTON,
                Blocks.OAK_PRESSURE_PLATE,
                Blocks.SPRUCE_PRESSURE_PLATE,
                Blocks.BIRCH_PRESSURE_PLATE,
                Blocks.JUNGLE_PRESSURE_PLATE,
                Blocks.ACACIA_PRESSURE_PLATE,
                Blocks.DARK_OAK_PRESSURE_PLATE,
                Blocks.MANGROVE_PRESSURE_PLATE,
                Blocks.CHERRY_PRESSURE_PLATE,
                Blocks.BAMBOO_PRESSURE_PLATE,
                Blocks.STONE_PRESSURE_PLATE,
                Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);

        // **The reactor's filter, and it is ours rather than vanilla's.** Every block here is one
        // that reads redstone -- a lamp that lights, a piston that extends, a door that opens -- and
        // the reactor's sweep only spends a neighbour update on the positions this tag matches. The
        // list is the vanilla set of components; a datapack or another mod extends it by adding to
        // `#cortescombat:reactor_powerable` with no code from us.
        tag(ReactorTags.POWERABLE).add(
                Blocks.REDSTONE_LAMP,
                Blocks.REDSTONE_BLOCK,
                Blocks.REDSTONE_WIRE,
                Blocks.REDSTONE_TORCH,
                Blocks.REDSTONE_WALL_TORCH,
                Blocks.LEVER,
                Blocks.PISTON,
                Blocks.STICKY_PISTON,
                Blocks.DISPENSER,
                Blocks.DROPPER,
                Blocks.HOPPER,
                Blocks.OBSERVER,
                Blocks.NOTE_BLOCK,
                Blocks.TNT,
                Blocks.COMPARATOR,
                Blocks.REPEATER,
                Blocks.DAYLIGHT_DETECTOR,
                Blocks.TARGET,
                Blocks.TRIPWIRE_HOOK,
                Blocks.LIGHTNING_ROD,
                Blocks.RAIL,
                Blocks.POWERED_RAIL,
                Blocks.DETECTOR_RAIL,
                Blocks.ACTIVATOR_RAIL,
                Blocks.BELL,
                Blocks.CRAFTER,
                Blocks.COPPER_BULB,
                Blocks.EXPOSED_COPPER_BULB,
                Blocks.WEATHERED_COPPER_BULB,
                Blocks.OXIDIZED_COPPER_BULB,
                Blocks.WAXED_COPPER_BULB,
                Blocks.WAXED_EXPOSED_COPPER_BULB,
                Blocks.WAXED_WEATHERED_COPPER_BULB,
                Blocks.WAXED_OXIDIZED_COPPER_BULB,
                Blocks.OAK_DOOR,
                Blocks.SPRUCE_DOOR,
                Blocks.BIRCH_DOOR,
                Blocks.JUNGLE_DOOR,
                Blocks.ACACIA_DOOR,
                Blocks.DARK_OAK_DOOR,
                Blocks.MANGROVE_DOOR,
                Blocks.CHERRY_DOOR,
                Blocks.BAMBOO_DOOR,
                Blocks.CRIMSON_DOOR,
                Blocks.WARPED_DOOR,
                Blocks.IRON_DOOR,
                Blocks.OAK_TRAPDOOR,
                Blocks.SPRUCE_TRAPDOOR,
                Blocks.BIRCH_TRAPDOOR,
                Blocks.JUNGLE_TRAPDOOR,
                Blocks.ACACIA_TRAPDOOR,
                Blocks.DARK_OAK_TRAPDOOR,
                Blocks.MANGROVE_TRAPDOOR,
                Blocks.CHERRY_TRAPDOOR,
                Blocks.BAMBOO_TRAPDOOR,
                Blocks.CRIMSON_TRAPDOOR,
                Blocks.WARPED_TRAPDOOR,
                Blocks.IRON_TRAPDOOR,
                Blocks.OAK_FENCE_GATE,
                Blocks.SPRUCE_FENCE_GATE,
                Blocks.BIRCH_FENCE_GATE,
                Blocks.JUNGLE_FENCE_GATE,
                Blocks.ACACIA_FENCE_GATE,
                Blocks.DARK_OAK_FENCE_GATE,
                Blocks.MANGROVE_FENCE_GATE,
                Blocks.CHERRY_FENCE_GATE,
                Blocks.BAMBOO_FENCE_GATE,
                Blocks.CRIMSON_FENCE_GATE,
                Blocks.WARPED_FENCE_GATE,
                Blocks.COMMAND_BLOCK,
                Blocks.CHAIN_COMMAND_BLOCK,
                Blocks.REPEATING_COMMAND_BLOCK);
    }
}
