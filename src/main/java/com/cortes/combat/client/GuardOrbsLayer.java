package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

/**
 * The three guard orbs above the crosshair.
 *
 * <p>Discreet, and only drawn when a blocking item is actually in hand. Each orb is one hit
 * you can still block, so the read is a count, not a judgement call about how far a bar has
 * moved.</p>
 */
public class GuardOrbsLayer implements LayeredDraw.Layer {

    private static final ResourceLocation FULL = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "textures/gui/sprites/hud/crosshair_shield_indicator_full.png");

    private static final int ORB_SIZE = 9;
    private static final int SPACING = 3;
    private static final int VERTICAL_OFFSET = 14;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientDebugFlags.hudVisible()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().options.hideGui) {
            return;
        }
        if (!isHoldingBlockingItem(player)) {
            return;
        }

        int stamina = ClientCombatState.guardStamina();
        int max = ClientCombatState.guardMax();
        if (max <= 0) {
            return;
        }

        int totalWidth = max * ORB_SIZE + (max - 1) * SPACING;
        int startX = (graphics.guiWidth() - totalWidth) / 2;
        int y = graphics.guiHeight() / 2 - VERTICAL_OFFSET;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int i = 0; i < max; i++) {
            int x = startX + i * (ORB_SIZE + SPACING);
            if (i < stamina) {
                graphics.blit(FULL, x, y, ORB_SIZE, ORB_SIZE,
                        0.0F, 0.0F, ORB_SIZE, ORB_SIZE, ORB_SIZE, ORB_SIZE);
            } else {
                // Spent: draw a dim outline so the player can still count what is left.
                graphics.fill(x, y, x + ORB_SIZE, y + ORB_SIZE, 0x40FFFFFF);
            }
        }

        RenderSystem.disableBlend();
    }

    private static boolean isHoldingBlockingItem(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.is(Items.SHIELD) || off.is(Items.SHIELD)
                || main.getItem() instanceof SwordItem
                || off.getItem() instanceof SwordItem;
    }
}
