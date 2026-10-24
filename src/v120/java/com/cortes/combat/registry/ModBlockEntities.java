package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.block.NetherReactorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * The mod's block entity types, which today is one.
 *
 * <p><b>The registry is {@code Registries.BLOCK_ENTITY_TYPE}, and that is the whole of the type
 * lookup.</b> There is no {@code BlockEntityRegistry} and no {@code BLOCK_ENTITY_REGISTRY} key --
 * the name was read off {@code Registries} rather than remembered, because a wrong key here is a
 * compile error only if the constant does not exist, and the one that does exist is spelled in a way
 * that invites the wrong guess.</p>
 *
 * <p><b>The capability is registered here, on the type, not on the block.</b>
 * {@code RegisterCapabilitiesEvent.registerBlockEntity} takes the block entity type, so every
 * instance of the reactor answers the item-handler query with its own automation wrapper -- the
 * thirty-cap door. A block-level registration would hand out one shared handler, which is the shape
 * of a bug that only shows up with two reactors.</p>
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CortesCombat.MODID);

    /**
     * The nether reactor's block entity.
     *
     * <p>The {@code null} passed to {@code build} is the data fixer type, and vanilla passes it too:
     * a block entity whose saved shape has never changed needs no data fixer, and inventing one
     * would be a promise to maintain it.</p>
     */
    public static final RegistryObject<BlockEntityType<NetherReactorBlockEntity>>
            NETHER_REACTOR = BLOCK_ENTITIES.register("nether_reactor",
                    () -> BlockEntityType.Builder.of(
                            NetherReactorBlockEntity::new,
                            ModBlocks.NETHER_REACTOR.get()).build(null));

    private ModBlockEntities() {
    }

    /** Hands the register to the mod bus. Called from the mod's constructor. */
    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }

    /**
     * The reactor's item handler, as automation sees it: firestones up to thirty, never the star.
     *
     * <p>The provider returns the block entity's own wrapper, so the cap is per reactor and the
     * handler is the same object every query -- no allocation on the capability path.</p>
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NETHER_REACTOR.get(),
                (reactor, side) -> reactor.automationHandler());
    }
}
