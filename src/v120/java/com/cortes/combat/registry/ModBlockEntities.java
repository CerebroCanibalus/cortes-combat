package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.block.NetherReactorBlockEntity;
import com.cortes.combat.platform.ResourceIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
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
 * <p><b>The capability is attached per instance, not per type.</b>
 * {@code AttachCapabilitiesEvent} fires for every block entity and this handler gates on our own
 * type, so every instance of the reactor answers the item-handler query with its own automation
 * wrapper -- the thirty-cap door. A block-level registration would hand out one shared handler,
 * which is the shape of a bug that only shows up with two reactors.</p>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = CortesCombat.MODID)
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
     * <p>1.20.1 has no block-entity capability registry, so the handler attaches per instance
     * through the generic attach event, gated on our own type. The provider hands out the block
     * entity's own wrapper, so the cap is per reactor and the handler is the same object every
     * query -- no allocation on the capability path. Invalidated with the block entity, by the
     * event's own listener contract.</p>
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> event) {
        if (!(event.getObject() instanceof NetherReactorBlockEntity reactor)) {
            return;
        }
        LazyOptional<IItemHandler> handler = LazyOptional.of(reactor::automationHandler);
        event.addCapability(ResourceIds.rl("automation"), new ICapabilityProvider() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> LazyOptional<T> getCapability(Capability<T> cap,
                    net.minecraft.core.Direction side) {
                if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
                    return handler.cast();
                }
                return LazyOptional.empty();
            }
        });
        event.addListener(handler::invalidate);
    }
}
