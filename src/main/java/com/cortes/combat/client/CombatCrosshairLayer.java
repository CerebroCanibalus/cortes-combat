package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/**
 * The crosshair and the charge bar, stacked.
 *
 * <p>Replaces the vanilla layer rather than drawing over it. Vanilla's crosshair also draws the
 * attack cooldown as a shrinking cross around itself, and this mod has its own cooldown readout
 * on the brackets; leaving both would put two cooldowns on screen disagreeing with each other.</p>
 *
 * <p><b>The charge lives under the crosshair, not in it.</b> The old version grew four bars out of
 * the cross, which put a second indicator inside the one thing the player aims with: the exact
 * centre of the screen got busier the more important it was. Below it, with real clearance, the
 * cross stays clean and the charge is still in the eye's natural sweep.</p>
 *
 * <p>The gap is not a magic number, it is the crosshair's own half-height plus a margin, so the
 * two never touch no matter how far the crosshair grows. That coupling is the point: a constant
 * gap looks fine at 1x and collides the moment a hit makes the crosshair jump.</p>
 */
public class CombatCrosshairLayer implements LayeredDraw.Layer {

    private static final ResourceLocation CHARGE = sprite("charge_bar");
    private static final ResourceLocation CHARGE_EMPTY = sprite("charge_bar_emtpy");

    private static final int CHARGE_WIDTH = 30;
    private static final int CHARGE_HEIGHT = 15;

    /** Clearance between the bottom of the crosshair and the top of the charge bar. */
    private static final int CHARGE_GAP = 9;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientDebugFlags.hudVisible()) {
            return;
        }

        int centreX = HudAnchor.centreX(graphics);
        int centreY = HudAnchor.centreY(graphics);
        float growth = CrosshairFeedback.growth();

        CrosshairSprites.render(graphics, centreX, centreY, growth,
                CrosshairFeedback.shakeX(), CrosshairFeedback.shakeY(), CrosshairFeedback.rgb());

        renderCharge(graphics, centreX, centreY);
    }

    /**
     * The charge bar, below the crosshair.
     *
     * <p>Two sprites rather than a fill rectangle: the empty one is the socket and the full one
     * carries the splatter at the end of the stroke, so drawing a scaled fill would have had to
     * invent that detail. Scaling the full sprite by charge and drawing the empty one underneath
     * gives the splatter for free, and it only appears when the charge is actually full.</p>
     *
     * <p>Positioned off the crosshair's <em>grown</em> half-height, from {@link HudAnchor}. On a
     * hit the crosshair is 38% bigger and would otherwise be sitting on the bar for the six
     * ticks of the flash. Measuring from the anchor rather than from a literal is what keeps
     * the two from drifting apart when the crosshair art changes size.</p>
     */
    private static void renderCharge(GuiGraphics graphics, int centreX, int centreY) {
        float charge = ClientCombatState.chargeRatio();
        if (charge <= 0.0F) {
            return;
        }
        int x = centreX - CHARGE_WIDTH / 2;
        int y = centreY + HudAnchor.crosshairReach() + CHARGE_GAP;

        RenderSystem.enableBlend();
        graphics.blit(CHARGE_EMPTY, x, y, CHARGE_WIDTH, CHARGE_HEIGHT,
                0.0F, 0.0F, CHARGE_WIDTH, CHARGE_HEIGHT, CHARGE_WIDTH, CHARGE_HEIGHT);

        int filled = Math.max(1, Math.round(CHARGE_WIDTH * Math.min(1.0F, charge)));
        graphics.blit(CHARGE, x, y, filled, CHARGE_HEIGHT,
                0.0F, 0.0F, filled, CHARGE_HEIGHT, CHARGE_WIDTH, CHARGE_HEIGHT);
        RenderSystem.disableBlend();
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                "textures/gui/sprites/hud/" + name + ".png");
    }
}
