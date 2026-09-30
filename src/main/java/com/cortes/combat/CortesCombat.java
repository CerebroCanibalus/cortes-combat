package com.cortes.combat;

import com.cortes.combat.attack.CombatTicker;
import com.cortes.combat.command.CortesCommand;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.registry.ModComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Entry point. Deliberately thin: everything real lives in the packages below, so this file
 * never becomes the place where two systems talk to each other directly.
 *
 * <p>See ARCHITECTURE.md for the rules this class exists to enforce.</p>
 */
@Mod(CortesCombat.MODID)
public class CortesCombat {

    public static final String MODID = "cortescombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Console switch for the development overlays, so a tester can be told to run one command
     * instead of editing anything. Off means the mod draws nothing it was not asked to draw.
     */
    public static volatile boolean debug = false;

    public CortesCombat(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.register(CombatTicker.class);
        NeoForge.EVENT_BUS.register(GuardHandler.class);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> CortesCommand.register(event.getDispatcher()));
        LOGGER.info("Cortés Combat loading");
    }
}
