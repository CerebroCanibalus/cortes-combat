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

    /**
     * A flash brighter than this is left alone by smaller hits.
     *
     * <p>Without a floor here, every hit in a burst re-raised the alpha to its own value and
     * the overlay never got to decay between them: taking four hits in a second looked like a
     * red strobe, which reads as a rendering fault rather than as four things hurting.</p>
     */
    private static final float FLASH_REARM = 0.35F;
    private static final float OVERLAY_OPACITY = 0.75F;
    private static final float FLASH_OPACITY = 0.55F;
    private static final float BRIGHTNESS = 1.0F;

    private float vignetteAlpha;
    private float flashAlpha;
    private float lastKnownHealth;

    /**
     * The player the remembered health belongs to.
     *
     * <p>Respawning and rejoining both hand the client a brand new {@code LocalPlayer}, and the
     * remembered health stayed from whoever was there before. The first read after that compared
     * a full 20 health bar against a remembered 0, called it twenty hearts of damage, and set
     * the flash to maximum — which is the red strobe on respawn. Keying the memory to the player
     * it describes is the fix, and it is the same class of bug as the {@code SyncState} one: a
     * value remembered across a lifecycle boundary it was never meant to cross.</p>
     */
    private LocalPlayer tracked;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            vignetteAlpha = 0.0F;
            flashAlpha = 0.0F;
            tracked = null;
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
        if (player != tracked) {
            // A different player object: respawn, dimension change, or a rejoin. Adopt its
            // current health without calling it damage, and drop any flash still in flight so a
            // stale red does not carry into a fresh life.
            tracked = player;
            lastKnownHealth = player.getHealth();
            flashAlpha = 0.0F;
            return;
        }
        float health = player.getHealth();
        if (health < lastKnownHealth + 0.499F) {
            float damage = lastKnownHealth - health;
            // Half a heart of damage or more, so chip damage does not strobe the screen.
            float incoming = Math.max(0.0F, Math.min(1.0F, damage * 0.125F));
            // Only a flash that has mostly faded gets re-armed. Four hits in a second used to
            // each re-raise the alpha, so the overlay never decayed between them and read as a
            // strobe rather than as one hit that hurt. A single stronger hit still overrides,
            // which is the case that matters: the second hit of a burst should not be quieter
            // than the first.
            if (incoming > flashAlpha || flashAlpha < FLASH_REARM) {
                flashAlpha = Math.max(flashAlpha, incoming);
            }
        }
        lastKnownHealth = health;
    }

    private static float lerp(float from, float to, double t) {
        return from + (to - from) * (float) t;
    }
}
