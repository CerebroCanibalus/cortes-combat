package com.cortes.combat.registry;

import net.minecraftforge.registries.RegistryObject;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.block.FirestoneBlock;
import com.cortes.combat.block.NetherReactorBlock;
import com.cortes.combat.block.ReactorCharge;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * The mod's blocks, which today is one.
 *
 * <p><b>Its own register, and a block item of its own, exactly like {@link ModTools} and
 * {@link ModArmor}.</b> {@code ModItems} is thirty weapons and two copper materials, built from the
 * catalogue, and a block is none of those things -- adding it there would have meant a third
 * hand-written registration beside the arcabuz and the firestone in a class whose whole argument is
 * that its contents come from a table.</p>
 *
 * <p><b>Two registers, this time, because a block and its item are two registry entries.</b>
 * {@code registerSimpleBlockItem} does not create the block; it creates the {@code BlockItem} that
 * places one, and it takes the block holder so the item and the block cannot be given different
 * names. Both register on the mod bus from {@link #register}.</p>
 */
public final class ModBlocks {

    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, CortesCombat.MODID);

    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, CortesCombat.MODID);

    /**
     * The block's light, and <b>it is the only thing here it emits.</b>
     *
     * <p>Ten, which is the design's "medium": the same as a torch is fourteen and a furnace is
     * thirteen, so this is a dim landmark rather than a light source. And it is the <em>only</em>
     * light in the block: no {@code ignitedByLava}, no fire spread, no flame block state. A block
     * that both glowed and lit fires would be a fire hazard, which is the opposite of the design's
     * "not a fire source by itself".</p>
     */
    public static final int LIGHT = 10;

    /**
     * The firestone block.
     *
     * <p>Hardness one and resistance one: mineable like netherrack, and low enough that any blast
     * can break it -- which is load-bearing, because an explosion can only trigger the detonation
     * if the explosion actually reaches the block, and blast resistance is what decides that. A
     * resistant block would swallow the very explosions that are meant to set it off.</p>
     */
    public static final RegistryObject<FirestoneBlock> FIRESTONE_BLOCK =
            BLOCKS.register("firestone_block", () -> new FirestoneBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0F, 1.0F)
                            .lightLevel(state -> LIGHT)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()));

    /** The item that places it, named after the block by construction. */
    public static final RegistryObject<BlockItem> FIRESTONE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("firestone_block", FIRESTONE_BLOCK);

    /**
     * The reactor's sounds: lodestone for the two a player causes -- placing it and breaking it --
     * and netherite's own for the rest, all at half pitch.
     *
     * <p><b>A {@code SoundType} carries its own pitch, and vanilla multiplies it by {@code 0.8}
     * both when the block is placed ({@code BlockItem.place}) and when it is broken
     * ({@code LevelRenderer}'s break event).</b> So one type gives lodestone's crystalline clack
     * pitched down to the reactor's weight on place and break, and the heavy thud of netherite on
     * step, hit and fall -- no custom sound event and no {@code sounds.json} entry. A nuclear
     * reactor should not sound like a stone block.</p>
     */
    public static final SoundType REACTOR_SOUND = new SoundType(1.0F, 0.5F,
            SoundEvents.LODESTONE_BREAK,
            SoundEvents.NETHERITE_BLOCK_STEP,
            SoundEvents.LODESTONE_PLACE,
            SoundEvents.NETHERITE_BLOCK_HIT,
            SoundEvents.NETHERITE_BLOCK_FALL);

    /**
     * The nether reactor: a wireless energy core, and the mod's first block entity.
     *
     * <p><b>It emits light and it is mineable like metal.</b> Hardness three and a half is iron's
     * neighbourhood -- it is a machine, not a powder -- and the pickaxe tag makes a pickaxe the
     * fast tool. The light is <em>dynamic</em>: {@code lightLevel} names a value for the charged
     * state so a state-only reader gets a sensible answer, and the block entity overrides
     * {@code getLightEmission} with the real number for how many firestones it holds.</p>
     *
     * <p><b>{@code emissiveRendering} is the full-bright switch, and it is per state.</b> While the
     * block is active the whole block renders at full brightness, which is what makes the glow
     * overlay read as light rather than as paint. See {@link NetherReactorBlock} for why the flag
     * being per-state rather than per-model is an acceptable limitation here.</p>
     */
    public static final RegistryObject<NetherReactorBlock> NETHER_REACTOR =
            BLOCKS.register("nether_reactor", () -> new NetherReactorBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.5F, 6.0F)
                            .lightLevel(state -> state.getValue(NetherReactorBlock.CHARGED)
                                    ? ReactorCharge.LIGHT_MAX : 0)
                            .emissiveRendering((state, level, pos) ->
                                    state.getValue(NetherReactorBlock.ACTIVE))
                            .sound(REACTOR_SOUND)
                            .requiresCorrectToolForDrops()));

    /** The item that places the reactor. */
    public static final RegistryObject<BlockItem> NETHER_REACTOR_ITEM =
            ITEMS.registerSimpleBlockItem("nether_reactor", NETHER_REACTOR);

    /** Hands both registers to the mod bus. Called from the mod's constructor. */
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private ModBlocks() {
    }
}
