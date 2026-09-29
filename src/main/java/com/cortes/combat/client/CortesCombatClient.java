package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point.
 *
 * <p>Separate from the common class because {@code InputEvent} does not exist on a
 * dedicated server: referencing it from common code fails to load there. NeoForge allows
 * several {@code @Mod} classes with the same id as long as their {@code dist} differs.</p>
 */
@Mod(value = CortesCombat.MODID, dist = Dist.CLIENT)
public class CortesCombatClient {

    public CortesCombatClient(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(ChargeInputHandler.class);
        NeoForge.EVENT_BUS.register(ClientTasks.class);
    }
}
