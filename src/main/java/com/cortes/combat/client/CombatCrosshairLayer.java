package com.cortes.combat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/**
 * Owns the crosshair, replacing the vanilla layer rather than drawing over it.
 *
 * <p>{@code RegisterGuiLayersEvent#replaceLayer} hands this class the slot vanilla already
 * occupied, so the two can never both draw. That matters here: the vanilla crosshair also draws
 * the attack cooldown as a shrinking cross around itself, and this mod has its own cooldown bar
 * under the hotbar. Leaving both would be two cooldowns on screen saying different things.</p>
 *
 * <p>The crosshair itself is still the vanilla sprite, on purpose. Replacing the crosshair is a
 * design decision that has not been made yet, and until it is, the honest thing is to own the
 * layer and keep drawing what the player is used to, so nothing about the current playtest
 * changes. Swapping the art is then one call, not a change of architecture.</p>
 *
 * <p>The charge indicator is the four bars growing outward from the cross. Same idea as the
 * vanilla cooldown cross, deliberately: it is a shape players already read without being taught,
 * and it reuses no texture at all, so the prototype assets stay a smaller liability.</p>
 */
public class CombatCrosshairLayer implements LayeredDraw.Layer {

    private static final ResourceLocation CROSSHAIR =
            ResourceLocation.fromNamespaceAndPath("hud", "crosshair");
    private static final int SPRITE = 15;

    private static final int BAR_LENGTH = 4;
    private static final int BAR_THICKNESS = 2;
    private static final int BAR_GAP = 3;

    private static final int COLOUR_CHARGING = 0xFFFFD24A;
    private static final int COLOUR_FULL = 0xFFFF5A3C;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientDebugFlags.hudVisible()) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.blitSprite(CROSSHAIR,
                centerX - SPRITE / 2, centerY - SPRITE / 2, SPRITE, SPRITE);

        renderCharge(graphics, centerX, centerY);

        RenderSystem.disableBlend();
    }

    /**
     * The four bars, one per direction, growing outward with the charge.
     *
     * <p>Length is driven by the server value, not the client prediction, for the same reason the
     * cooldown bar is: an indicator that shows a hit you cannot land is worse than a laggy one.</p>
     */
    private static void renderCharge(GuiGraphics graphics, int centerX, int centerY) {
        float charge = ClientCombatState.chargeRatio();
        if (charge <= 0.0F) {
            return;
        }
        int length = Math.max(1, Math.round(BAR_LENGTH * charge));
        int start = BAR_GAP;
        int end = start + length;
        int colour = charge >= 1.0F ? COLOUR_FULL : COLOUR_CHARGING;

        // North and south.
        graphics.fill(centerX - BAR_THICKNESS / 2, centerY - end, centerX + (BAR_THICKNESS + 1) / 2,
                centerY - start, colour);
        graphics.fill(centerX - BAR_THICKNESS / 2, centerY + start,
                centerX + (BAR_THICKNESS + 1) / 2, centerY + end, colour);
        // East and west.
        graphics.fill(centerX + start, centerY - BAR_THICKNESS / 2, centerX + end,
                centerY + (BAR_THICKNESS + 1) / 2, colour);
        graphics.fill(centerX - end, centerY - BAR_THICKNESS / 2, centerX - start,
                centerY + (BAR_THICKNESS + 1) / 2, colour);
    }
}
