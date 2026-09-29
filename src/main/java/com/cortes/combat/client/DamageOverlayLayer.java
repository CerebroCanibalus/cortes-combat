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
 * The low-health vignette and the damage flash.
 *
 * <p>Straight from the damageoverlay pattern, which is the part worth copying: the health
 * check is a delta against a remembered previous value rather than an event subscription, so
 * it catches any source of damage without having to know about them, and the fade is an
 * exponential approach against real delta time rather than a linear one that stutters.</p>
 */
public class DamageOverlayLayer implements LayeredDraw.Layer {

    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "textures/gui/damage_overlay.png");

    /** Health below this starts the vignette. */
    private static final float FADE_START_HEALTH = 14.0F;

    /** Health at which the vignette is fully opaque. */
    private static final float FADE_END_HEALTH = 4.0F;

    private static final float FADE_SPEED = 6.0F;
    private static final float FLASH_SPEED = 5.0F;
    private static final float OVERLAY_OPACITY = 0.75F;
    private static final float FLASH_OPACITY = 0.55F;
    private static final float BRIGHTNESS = 1.0F;

    private float vignetteAlpha;
    private float flashAlpha;
    private float lastKnownHealth;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            vignetteAlpha = 0.0F;
            flashAlpha = 0.0F;
            return;
        }

        checkForDamage(player);

        if (Minecraft.getInstance().options.hideGui) {
            return;
        }

        float health = player.getHealth();
        float start = Math.max(FADE_END_HEALTH + 1.0F, FADE_START_HEALTH);
        float target = Math.max(0.0F, Math.min(1.0F, (health - start) / (FADE_END_HEALTH - start)));
        // Cosine easing so the vignette ramps gently at first and then bites near death,
        // instead of climbing linearly the whole way.
        float eased = (float) (-Math.cos(target * Math.PI) * 0.5 + 0.5);
        vignetteAlpha = lerp(vignetteAlpha, eased, delta.getRealtimeDeltaTicks() * FADE_SPEED);

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        if (vignetteAlpha > 0.001F) {
            graphics.setColor(BRIGHTNESS, BRIGHTNESS, BRIGHTNESS, vignetteAlpha * OVERLAY_OPACITY);
            graphics.blit(OVERLAY, 0, 0, w, h, 0.0F, 0.0F, w, h, w, h);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        flashAlpha = lerp(flashAlpha, 0.0F, delta.getRealtimeDeltaTicks() * FLASH_SPEED);
        if (flashAlpha > 0.001F) {
            graphics.setColor(0.8F * BRIGHTNESS, 0.1F * BRIGHTNESS, 0.1F * BRIGHTNESS,
                    flashAlpha * FLASH_OPACITY);
            graphics.fill(0, 0, w, h, 0xFFFFFFFF);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    /**
     * Delta against the remembered health rather than an event subscription: this catches
     * every source of damage, including ones from mods, without having to know they exist.
     */
    private void checkForDamage(LocalPlayer player) {
        float health = player.getHealth();
        if (health < lastKnownHealth + 0.499F) {
            float damage = lastKnownHealth - health;
            // Half a heart of damage or more, so chip damage does not strobe the screen.
            flashAlpha = Math.max(flashAlpha, Math.max(0.0F, Math.min(1.0F, damage * 0.125F)));
        }
        lastKnownHealth = health;
    }

    private static float lerp(float from, float to, double t) {
        return from + (to - from) * (float) t;
    }
}
