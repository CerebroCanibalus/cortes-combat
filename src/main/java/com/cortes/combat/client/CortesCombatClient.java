package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

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

    public CortesCombatClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.register(SwingInputHandler.class);
        NeoForge.EVENT_BUS.register(ItemViewModel.class);
        NeoForge.EVENT_BUS.register(GuardInputHandler.class);
        NeoForge.EVENT_BUS.register(ClientTasks.class);
        NeoForge.EVENT_BUS.register(ConeDebugRenderer.class);
        modEventBus.addListener(CortesCombatClient::registerLayers);
        modEventBus.addListener(CortesCombatClient::onClientSetup);
    }

    /**
     * Layer order is taken from the real {@link VanillaGuiLayers} order, verified against the
     * 1.21.1 sources:
     * {@code CAMERA_OVERLAYS -> CROSSHAIR -> HOTBAR -> ... -> PLAYER_HEALTH}.
     *
     * <p>So: the vignette goes <b>below</b> the health bar (it is a screen effect, and
     * covering the health would hide the thing it is warning about), and everything that lives
     * around the crosshair sits <b>above</b> it. Using {@code registerAboveAll} for any of them
     * would be wrong.</p>
     *
     * <p>The crosshair is <b>replaced</b>, not joined. The slot already exists, and the vanilla
     * layer draws the attack cooldown into it, which would put a second cooldown readout on
     * screen next to the brackets.</p>
     *
     * <p>Relative order above the crosshair: brackets, then orbs. The brackets are the widest
     * thing on screen and the orbs are the smallest; drawing the orbs last keeps them readable
     * in the rare case a 9 orb row and a bracket column overlap, and costs nothing otherwise.</p>
     */
    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "damage_overlay"),
                new DamageOverlayLayer());
        event.replaceLayer(VanillaGuiLayers.CROSSHAIR, new CombatCrosshairLayer());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "cooldown_brackets"),
                new CooldownBracketLayer());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "guard_orbs"),
                new GuardOrbsLayer());
    }

    /**
     * Raises the starting FOV, once.
     *
     * <p>Only while the option still reads as vanilla's own 70. That single comparison is the
     * whole mechanism: it means a fresh install gets a sane view, and a player who has already
     * picked their own number is never overruled, with no flag to remember and nothing to go
     * stale. The value is left in the options screen either way, so it stays adjustable.</p>
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        if (mc.options.fov().get() != ClientConfig.VANILLA_FOV) {
            return;
        }
        int wanted = ClientConfig.STARTING_FOV.get();
        if (wanted == ClientConfig.VANILLA_FOV) {
            return;
        }
        mc.options.fov().set(wanted);
    }
}
