package com.cortes.combat;

import com.mojang.logging.LogUtils;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.registry.ModComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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

    public CortesCombat(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        LOGGER.info("Cortés Combat loading");
    }
}
