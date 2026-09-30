package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ReachProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The cooldown brackets: how ready the swing is, and how far it reaches, in one object.
 *
 * <p>It replaces the bar under the hotbar rather than joining it. That bar answered one question
 * ("may I swing yet?") with a hundred and eighty two pixels of screen for an answer the player
 * already reads in a glance. The brackets answer the same question and one more, which is the
 * one that actually changes how you fight: <em>how far does this reach</em>. A player holding a
 * halberd and a player holding a dagger need to know their spacing differs, and a bar under the
 * hotbar cannot say that at any width.</p>
 *
 * <p><b>Position and length are both read off the weapon's own reach profile.</b> That is the
 * whole point of the widget: the brackets sit further out for a longer weapon, and the bar grows
 * proportionally between them, so the shape of the indicator <em>is</em> the shape of the cone.
 * Nothing here is a per-weapon lookup table, so a foreign weapon with a sane reach profile draws
 * a sane indicator without this project knowing the weapon exists.</p>
 *
 * <p>The bar fades to nothing at the crosshair end. A hard line pointing at the crosshair reads
 * as a crosshair arm, and a player aiming between two enemies has to decide which of the three
 * bright things is the one they are aiming with. The fade keeps the bar attached to its bracket
 * and out of the centre.</p>
 */
public class CooldownBracketLayer implements LayeredDraw.Layer {

    private static final ResourceLocation BRACKETS = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "textures/gui/sprites/hud/cooldown_corchetes.png");

    private static final int TEX_WIDTH = 21;
    private static final int TEX_HEIGHT = 15;

    /**
     * The two brackets live in one symmetric sprite, ink in columns 1-7 and 13-19. They are
     * blitted as two crops rather than splitting the file, because the sprite is already
     * mirror-symmetric about its centre column: cropping is the same operation and it keeps one
     * source of truth for the art instead of two files that have to be kept in step.
     */
    private static final int LEFT_CROP_X = 0;
    private static final int LEFT_CROP_W = 10;
    private static final int RIGHT_CROP_X = 10;
    private static final int RIGHT_CROP_W = 11;

    /** Column of the left crop that the bracket is centred on, and of the right crop. */
    private static final int LEFT_ANCHOR = 4;
    private static final int RIGHT_ANCHOR = 6;

    /** How far the brackets sit from the centre, at the shortest reach in the game. */
    private static final int BRACKET_DISTANCE = 30;

    /** Extra pixels the brackets move out at full reach, on top of the base distance. */
    private static final int REACH_SPREAD = 6;

    /** Reach window the indicator is stretched across, in blocks. */
    private static final float REACH_FLOOR = 2.0F;
    private static final float REACH_CEILING = 4.5F;

    /** Bar thickness. One pixel: this is a line between two brackets, not a second bar. */
    private static final int BAR_THICKNESS = 1;

    /** Alpha at the bracket end and at the crosshair end of the bar. */
    private static final int ALPHA_AT_BRACKET = 235;
    private static final int ALPHA_AT_CROSSHAIR = 8;

    /** The unfilled part of the bar, so the weapon's reach is readable while on cooldown. */
    private static final int ALPHA_SOCKET = 70;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientDebugFlags.hudVisible()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().options.hideGui || player.isSpectator()) {
            return;
        }

        int centreX = graphics.guiWidth() / 2;
        int centreY = graphics.guiHeight() / 2;
        ReachProfile reach = WeaponStats.of(player.getMainHandItem()).reach();

        // One geometry, computed once, and passed down. The three pieces below all measure from
        // the same bracket position, and a second copy of that number is a second chance to have
        // the bar and the bracket disagree by a pixel.
        int distance = distanceFor(reach.distance());
        // The crosshair's own grown size, so the bar never runs underneath it on a hit.
        int crosshairHalf = Math.round(CrosshairSprites.SIZE * CrosshairFeedback.growth() / 2.0F);
        int barLength = Math.max(0, distance - LEFT_ANCHOR - crosshairHalf - 1);
        float ratio = clamp01(ClientCombatState.cooldownRatio());
        int rgb = CrosshairFeedback.rgb();

        RenderSystem.enableBlend();
        drawBar(graphics, centreX, centreY, distance, barLength, ratio, rgb, -1);
        drawBar(graphics, centreX, centreY, distance, barLength, ratio, rgb, 1);
        drawBrackets(graphics, centreX, centreY, distance, rgb);
        RenderSystem.disableBlend();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /**
     * One side's bar, drawn one pixel column at a time so the alpha can ramp along it.
     *
     * <p>Per column rather than as a few segments because a ramp in eight steps over fifteen
     * pixels is visibly a ramp in eight steps. It is fifteen quads a side, which is nothing, and
     * the alternative is a gradient texture that would have to be authored per bar length.</p>
     *
     * <p>The fade is measured from <em>this side's</em> bracket outward in both cases. Measuring
     * it from the centre instead would mirror the left bar's ramp on the right one, and the two
     * halves would meet brightest in the middle, which is the opposite of what the fade is
     * for.</p>
     *
     * <p>Two passes in one loop: the whole length at socket alpha so the reach is readable even
     * mid cooldown, then the filled prefix at full alpha. The fill grows from the bracket inward,
     * which puts the moving edge next to the thing that grows and away from the crosshair.</p>
     *
     * @param side -1 for the left bar, 1 for the right
     */
    private static void drawBar(GuiGraphics graphics, int centreX, int centreY, int distance,
                                int length, float ratio, int rgb, int side) {
        if (length <= 0) {
            return;
        }
        int filled = Math.round(length * ratio);
        int y = centreY - BAR_THICKNESS / 2;

        for (int i = 0; i < length; i++) {
            // i is the distance from this side's bracket. Bracket is 0, crosshair end is length-1.
            float t = length <= 1 ? 0.0F : (float) i / (length - 1);
            int alpha = Math.round(ALPHA_AT_BRACKET
                    + (ALPHA_AT_CROSSHAIR - ALPHA_AT_BRACKET) * t);
            if (i >= filled) {
                alpha = Math.min(alpha, ALPHA_SOCKET);
            }
            int bracketX = side < 0
                    ? centreX - distance + LEFT_ANCHOR + 1
                    : centreX + distance - RIGHT_ANCHOR - 1;
            int x = bracketX + side * i;
            graphics.fill(x, y, x + 1, y + BAR_THICKNESS, (alpha << 24) | rgb);
        }
    }

    /**
     * The brackets, cropped and placed at the reach-scaled distance.
     *
     * <p>They never scale with the crosshair. The crosshair growing on a hit is feedback about
     * one swing; the brackets growing with the same number would make a landed hit look like
     * the weapon changed.</p>
     */
    private static void drawBrackets(GuiGraphics graphics, int centreX, int centreY,
                                     int distance, int rgb) {
        graphics.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, 1.0F);

        int y = centreY - TEX_HEIGHT / 2;
        graphics.blit(BRACKETS,
                centreX - distance - LEFT_ANCHOR, y, LEFT_CROP_W, TEX_HEIGHT,
                (float) LEFT_CROP_X, 0.0F, LEFT_CROP_W, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        graphics.blit(BRACKETS,
                centreX + distance - RIGHT_ANCHOR, y, RIGHT_CROP_W, TEX_HEIGHT,
                (float) RIGHT_CROP_X, 0.0F, RIGHT_CROP_W, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * How far the brackets sit from the centre for a given reach.
     *
     * <p>A reach, not a weapon. Anything between {@link #REACH_FLOOR} and {@link #REACH_CEILING}
     * blocks gets a proportional distance, and anything outside is clamped, so a modded weapon
     * with a longer profile is drawn as "further out" rather than as "wrong".</p>
     */
    static int distanceFor(float reachDistance) {
        float t = clamp01((reachDistance - REACH_FLOOR) / (REACH_CEILING - REACH_FLOOR));
        // The bar between the brackets carries most of the reading; the brackets spread a little
        // too, so the overall width of the indicator also grows with the weapon. Six pixels of
        // spread, which is enough to see and small enough that the brackets never crowd the
        // crosshair's own grown silhouette.
        return BRACKET_DISTANCE + Math.round(t * REACH_SPREAD);
    }
}
