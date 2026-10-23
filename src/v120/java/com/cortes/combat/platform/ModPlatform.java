package com.cortes.combat.platform;

import com.cortes.combat.CortesCombat;

/**
 * The 1.20.1 twin of the registrations. Same call site, Forge objects.
 *
 * <p>Two lines vanish here on purpose and not by omission: components and attachments have no
 * 1.20.1 equivalent to register. Item data travels in NBT (see {@code WeaponData}) and combat
 * state attaches through gameplay events (see the v120 {@code CombatState}), and neither needs
 * a bus registration at all. Every other line below gains its Forge twin as its registry
 * cluster lands; a cluster that is not here yet is simply not registered yet, which is exactly
 * what "the 1.20.1 leg does not have that system" means. (⌒‿⌒)</p>
 *
 * <p>The parameters arrive as {@code Object} because the two loaders do not share a bus or
 * container type, and the shared call site cannot name either one. The casts are checked by
 * compilation on this leg; a wrong object here fails the build, not the game.</p>
 */
public final class ModPlatform {

    private ModPlatform() {
    }

    public static void registerAll(Object modEventBus, Object modContainer) {
        net.minecraftforge.eventbus.api.IEventBus bus =
                (net.minecraftforge.eventbus.api.IEventBus) modEventBus;
        // Forge 1.20.1 registers configs through the loading context, not the container: the
        // shared call site hands both over and this leg takes the context. `modContainer`
        // arrives unused on purpose -- dropping the parameter would fork the call site instead.
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                com.cortes.combat.config.ServerConfig.SPEC);
        bus.addListener(com.cortes.combat.net.ModNetworking::register);
    }
}
