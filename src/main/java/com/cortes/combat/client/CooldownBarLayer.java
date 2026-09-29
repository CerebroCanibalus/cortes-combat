package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The horizontal cooldown bar under the hotbar.
 *
 * <p>This bar is the permission to attack, which is why it is the honest one: it is drawn
 * from the server's own value, not from a client prediction. If it is full, you may swing.
 * Charge lives in the vanilla indicator instead, so the two never compete for the same
 * attention.</p>
 *
 * <p>Registered above the hotbar, not above everything, so it cannot cover the inventory
 * or the chat.</p>
 */
public class CooldownBarLayer implements LayeredDraw.Layer {

    private static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "textures/gui/sprites/hud/hotbar_attack_indicator_full");

    private static final int WIDTH = 182;
    private static final int HEIGHT = 5;
    private static final int Y_OFFSET = 22;

    private float shown;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            shown = 0.0F;
            return;
        }
        if (Minecraft.getInstance().options.hideGui) {
            return;
        }

        float target = ClientCombatState.cooldownRatio();
        // Exponential approach against real delta time, the same trick damageoverlay uses.
        // Linear interpolation here makes the bar look like it stutters on uneven framerates.
        shown += (target - shown) * Math.min(1.0F, delta.getRealtimeDeltaTicks() * 12.0F);

        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() - 22 - Y_OFFSET;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0x80000000);
        int fill = Math.round(WIDTH * Math.max(0.0F, Math.min(1.0F, shown)));
        graphics.fill(x, y, x + fill, y + HEIGHT, readyColor(shown));

        RenderSystem.disableBlend();
    }

    /** Green once it is actually usable, so the "can I hit now" read is instant. */
    private static int readyColor(float ratio) {
        if (ratio >= 0.999F) {
            return 0xFF7BD66B;
        }
        if (ratio <= 0.0F) {
            return 0xFF4A4A4A;
        }
        return 0xFFC8B45A;
    }
}
