package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The three crosshair sprites, and the tinting that turns them into a state machine.
 *
 * <p>One art asset per state instead of one asset plus drawn flourishes. A crosshair that
 * changes shape tells the player something a colour change cannot: "there is a target" and
 * "you just connected" are different facts, and drawing them as the same shape in two colours
 * makes the player learn the colour instead of reading it.</p>
 *
 * <p>All three are 15x15 and are drawn at the source size by default. The growth is scaling,
 * not a fourth sprite, because growth is continuous while the states are not: a scale can
 * interpolate, three sprites cannot.</p>
 */
public final class CrosshairSprites {

    /** Native size of every crosshair sprite. The growth multiplies this. */
    public static final int SIZE = 15;

    /**
     * Overall opacity of the crosshair.
     *
     * <p>Not one, deliberately. The crosshair sits on top of everything the player is trying to
     * aim at, and at full opacity a block of it is the brightest thing in a dark cave. Backing
     * it off lets the world show through without making the shape any harder to read, because
     * the sprite is a hard-edged mark and not a soft glow.</p>
     *
     * <p>Multiplied into the feedback colour rather than applied to alpha, so a hit still tints
     * the crosshair fully red and then dims it: the flash is a colour change, this is a constant
     * weight on whatever colour is showing.</p>
     */
    private static final float ALPHA = 0.85F;

    public static final ResourceLocation IDLE = sprite("crosshair");
    /** A target is reachable: the cone has something in it, or a shot is being drawn. */
    public static final ResourceLocation AIM = sprite("crosshair_aim");
    /** A hit just landed. Also the kill sprite's base: the colour carries that difference. */
    public static final ResourceLocation HIT = sprite("crosshair_hit");

    private CrosshairSprites() {
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                "textures/gui/sprites/hud/" + name + ".png");
    }

    /**
     * The sprite for the current state. The hit sprite outranks the aim one.
     *
     * <p>Asked as a question about the flash rather than guessed from the size, so the art and
     * the growth can be tuned independently. They are the same event and they were once derived
     * from the same number, which meant raising the aim growth silently turned a plain aim into
     * the hit sprite.</p>
     */
    public static ResourceLocation forState(boolean aiming) {
        if (CrosshairFeedback.hitFlashing()) {
            return HIT;
        }
        return aiming ? AIM : IDLE;
    }

    /**
     * Draws the crosshair centred, tinted, at the given growth.
     *
     * <p>The tint is applied through {@link GuiGraphics#setColor}, which is a multiply against
     * the sprite's own pixels. The sprites are drawn white, so multiplying by a colour gives
     * exactly that colour, and multiplying by white gives the untouched sprite. One mechanism
     * for both cases, instead of a special case for "no tint".</p>
     *
     * <p>Scaling goes through {@code blit} with an explicit destination size rather than by
     * resizing the texture. The blit path keeps the sprite's own UVs, so the growth is a stretch
     * of the same 15x15 art with the texture filter untouched, instead of a global filter change
     * that would leak into every other GUI layer and blur the guard orbs along with it.</p>
     *
     * @param shakeX horizontal shake in GUI pixels, 0 when nothing landed
     * @param shakeY vertical shake in GUI pixels
     * @param rgb 24 bit tint, {@code 0xFFFFFF} for untinted
     */
    public static void render(GuiGraphics graphics, int centreX, int centreY, float growth,
                              int shakeX, int shakeY, int rgb) {
        ResourceLocation texture = forState(ClientCombatState.aiming());

        int size = Math.max(1, Math.round(SIZE * growth));
        // Anchored, not just centred: the sprite is an odd number of pixels, so dividing the
        // grown size by two shifts it by half a pixel as it scales. Growing from the resting
        // position instead keeps the centre pixel on the canonical point at every size, which is
        // what makes the crosshair and the brackets stay aligned through a hit.
        int x = centreX - HudAnchor.crosshairHalf() + shakeX;
        int y = centreY - HudAnchor.crosshairHalf() + shakeY;

        RenderSystem.enableBlend();
        graphics.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, ALPHA);
        graphics.blit(texture, x, y, size, size,
                0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
