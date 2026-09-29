package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-only entry point.
 *
 * <p>Separate from the common class because {@code InputEvent} and the GUI events do not
 * exist on a dedicated server: referencing them from common code fails to load there.
 * NeoForge allows several {@code @Mod} classes with the same id as long as their
 * {@code dist} differs.</p>
 */
@Mod(value = CortesCombat.MODID, dist = Dist.CLIENT)
public class CortesCombatClient {

    public CortesCombatClient(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(ChargeInputHandler.class);
        NeoForge.EVENT_BUS.register(ClientTasks.class);
        NeoForge.EVENT_BUS.register(ConeDebugRenderer.class);
        modEventBus.addListener(CortesCombatClient::registerLayers);
    }

    /**
     * Layer order is taken from the real {@link VanillaGuiLayers} order, verified against the
     * 1.21.1 sources:
     * {@code CAMERA_OVERLAYS -> CROSSHAIR -> HOTBAR -> ... -> PLAYER_HEALTH}.
     *
     * <p>So: the vignette goes <b>below</b> the health bar (it is a screen effect, and
     * covering the health would hide the thing it is warning about), the cooldown bar sits
     * <b>above</b> the hotbar, and the guard orbs sit <b>above</b> the crosshair. Using
     * {@code registerAboveAll} for any of them would be wrong.</p>
     */
    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "damage_overlay"),
                new DamageOverlayLayer());
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "cooldown_bar"),
                new CooldownBarLayer());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "guard_orbs"),
                new GuardOrbsLayer());
    }
}
