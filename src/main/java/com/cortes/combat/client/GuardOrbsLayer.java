package com.cortes.combat.client;

import com.cortes.combat.platform.ResourceIds;
import com.cortes.combat.config.ClientConfig;
import com.cortes.combat.guard.GuardHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The three guard orbs above the crosshair.
 *
 * <p>Discreet, and only drawn when a blocking item is actually in hand. Each orb is one hit
 * you can still block, so the read is a count, not a judgement call about how far a bar has
 * moved.</p>
 *
 * <p>They also carry the guard being up or down. The design says covering is free, so there is
 * no meter for "holding" and the only way to spend stamina is to get hit; but the player still
 * has to be able to see whether the guard is up, otherwise the 3 orbs say nothing about the one
 * question that matters. Full and bright means up, spent means down.</p>
 */
public class GuardOrbsLayer implements LayeredDraw.Layer {

    private static final ResourceLocation FULL = ResourceIds.rl("textures/gui/sprites/hud/crosshair_shield_indicator_full.png");
    private static final ResourceLocation DISABLED = ResourceIds.rl("textures/gui/sprites/hud/crosshair_shield_indicator_disabled.png");

    private static final int ORB_SIZE = 9;
    private static final int SPACING = 3;
    private static final int VERTICAL_OFFSET = 14;

    /** Alpha for the orbs while the guard is up. Down is dimmer, so the state reads at a glance. */
    private static final int ALPHA_UP = 255;
    private static final int ALPHA_DOWN = 110;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientConfig.HUD_GUARD_ORBS.get()) {
            return;
        }
        if (HudIdle.barsHidden()) {
            // Los orbes son una barra mas: sin pelea, se van.
            return;
        }
        if (!ClientDebugFlags.hudVisible()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().options.hideGui) {
            return;
        }
        if (!GuardHandler.bestFor(player).canGuard()) {
            return;
        }

        int stamina = ClientCombatState.guardStamina();
        int max = ClientCombatState.guardMax();
        if (max <= 0) {
            return;
        }
        boolean held = ClientCombatState.guardHeld();
        // Read straight off the client's own synced cooldowns rather than a payload field: the
        // vanilla bar already draws the countdown, and duplicating that number in a packet
        // would be a second copy of a fact both sides already have.
        boolean staggered = player.getCooldowns()
                .isOnCooldown(GuardHandler.bestFor(player).item().getItem());

        int totalWidth = max * ORB_SIZE + (max - 1) * SPACING;
        int startX = HudAnchor.centreX(graphics) - totalWidth / 2;
        int y = HudAnchor.centreY(graphics) - VERTICAL_OFFSET;
        // Staggered is not the same as down. Dimming the orbs while they are visibly refilling
        // says the opposite of what the player is watching, so they stay at full brightness and
        // the vanilla item cooldown carries the "you cannot block yet" part.
        int alpha = held || staggered ? ALPHA_UP : ALPHA_DOWN;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // White with a varying alpha: the orbs have no colour of their own, they only dim. The
        // crosshair and the brackets are the two things that carry the hit colour, and leaving
        // these untinted is what keeps a blocked hit from painting three different things red.
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);

        for (int i = 0; i < max; i++) {
            int x = startX + i * (ORB_SIZE + SPACING);
            ResourceLocation sprite = i < stamina ? FULL : DISABLED;
            graphics.blit(sprite, x, y, ORB_SIZE, ORB_SIZE,
                    0.0F, 0.0F, ORB_SIZE, ORB_SIZE, ORB_SIZE, ORB_SIZE);
        }

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
