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
 * The cooldown brackets: how ready the swing is, and how wide the cone is, in one object.
 *
 * <p>It replaces the bar under the hotbar. That bar answered one question ("may I swing yet?")
 * with 182 pixels of screen for an answer the player reads in a glance. The brackets answer
 * that and one more, which is the one that changes how you fight: <em>how wide is my cone</em>.
 * A halberd player and a dagger player need to know their spacing differs, and a bar under the
 * hotbar cannot say that at any width.</p>
 *
 * <p><b>Size comes from the cone's margins, not its reach.</b> The widget answers "how wide",
 * so it is driven by {@link ReachProfile#horizontalAngle()}: a sabre at 75 degrees is visibly
 * the widest thing on screen and a rapier at 12 is visibly the tightest, which is exactly the
 * difference the player needs to feel in their spacing. Distance is a different question and
 * the widget does not try to answer it — a long thin cone and a short wide one are both
 * plausible, and a widget that mixed the two numbers would be describing neither.</p>
 *
 * <p>The fill grows from the crosshair outward and the fade runs the other way, so the two
 * together say one thing: the bar reaches out from where you aim, and it is reaching further
 * the closer you are to being able to swing. Fading <em>toward</em> the crosshair keeps the
 * moving edge out of the middle of the screen, where it would compete with the thing the
 * player is actually aiming.</p>
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

    /** Column of each crop that the bracket is centred on. */
    private static final int LEFT_ANCHOR = 4;
    private static final int RIGHT_ANCHOR = 6;

    /**
     * Bar thickness.
     *
     * <p>Was three, which the player read as a slab laid over the middle of the screen. Two is
     * the compromise: a one pixel line is one physical pixel at GUI scale one, where it can fall
     * between two display pixels and break up, and the bar has to survive that. Two survives
     * every scale and still reads as a line rather than as a plate.</p>
     */
    private static final int BAR_THICKNESS = 2;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientDebugFlags.hudVisible()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().options.hideGui || player.isSpectator()) {
            return;
        }

        int centreX = HudAnchor.centreX(graphics);
        int centreY = HudAnchor.centreY(graphics);
        ReachProfile reach = WeaponStats.of(player.getMainHandItem()).reach();

        // One geometry, computed once. The bar and the brackets both measure from this number,
        // and a second copy of it is a second chance to have them disagree by a pixel.
        int halfSpan = halfSpanFor(reach.horizontalAngle(), graphics.guiWidth());
        // The bar runs from the crosshair's edge to the bracket's inner edge, so the two never
        // overlap even when the crosshair jumps on a hit.
        int inner = halfSpan - LEFT_ANCHOR - 1;
        int outer = HudAnchor.crosshairReach();
        int barLength = Math.max(0, inner - outer);
        float ratio = clamp01(ClientCombatState.cooldownRatio());
        int rgb = CrosshairFeedback.rgb();

        RenderSystem.enableBlend();
        drawBar(graphics, centreX, centreY, halfSpan, barLength, ratio, rgb, -1);
        drawBar(graphics, centreX, centreY, halfSpan, barLength, ratio, rgb, 1);
        drawBrackets(graphics, centreX, centreY, halfSpan, rgb);
        RenderSystem.disableBlend();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /**
     * How far the brackets sit from the centre, for a cone of the given half-angle.
     *
     * <p>Thin alias kept so the widget reads as one thing. The arithmetic, the curve and the
     * screen cap live in {@link ConeGauge}, where they can be tested without a game.</p>
     */
    private static int halfSpanFor(float horizontalAngle, int guiWidth) {
        return ConeGauge.halfSpan(horizontalAngle, guiWidth);
    }

    /**
     * One side's bar, drawn one pixel column at a time.
     *
     * <p>Per column so the alpha can ramp and crossfade along it, which no texture can do
     * without a separate asset per possible length. It is a couple of dozen quads a side and
     * they all land in one draw call.</p>
     *
     * <p>It is one continuous line, not a socket and a fill drawn over each other: the whole
     * length gets one alpha per column from {@link ConeGauge#barAlpha}, which folds the
     * gradient and the fill level together. Drawing two passes produced a visible step at the
     * moving edge and made the unfilled half look like a separate, broken object.</p>
     *
     * @param side -1 for the left bar, 1 for the right
     */
    private static void drawBar(GuiGraphics graphics, int centreX, int centreY, int halfSpan,
                                int length, float ratio, int rgb, int side) {
        if (length <= 0) {
            return;
        }
        int filled = Math.round(length * ratio);
        int y = centreY - BAR_THICKNESS / 2;
        // The pixel column the bar starts at, next to the crosshair. Going outward from here
        // means moving toward this side's bracket, which is +x on the right and -x on the left.
        // Getting this sign backwards draws the whole bar outside the bracket, which is
        // invisible, and which this exact layer shipped once.
        int originX = centreX + side * (HudAnchor.crosshairReach() + 1);

        for (int i = 0; i < length; i++) {
            int alpha = ConeGauge.barAlpha(i, length, filled);
            if (alpha <= 0) {
                continue;
            }
            int x = originX + side * i;
            graphics.fill(x, y, x + 1, y + BAR_THICKNESS, (alpha << 24) | rgb);
        }
    }

    /**
     * The brackets, cropped and placed at the cone-width-scaled span.
     *
     * <p>They never scale with the crosshair. The crosshair growing on a hit is feedback about
     * one swing; the brackets growing with the same number would make a landed hit look like
     * the weapon changed.</p>
     */
    private static void drawBrackets(GuiGraphics graphics, int centreX, int centreY,
                                     int halfSpan, int rgb) {
        graphics.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, 1.0F);

        int y = centreY - TEX_HEIGHT / 2;
        graphics.blit(BRACKETS,
                centreX - halfSpan - LEFT_ANCHOR, y, LEFT_CROP_W, TEX_HEIGHT,
                (float) LEFT_CROP_X, 0.0F, LEFT_CROP_W, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        graphics.blit(BRACKETS,
                centreX + halfSpan - RIGHT_ANCHOR, y, RIGHT_CROP_W, TEX_HEIGHT,
                (float) RIGHT_CROP_X, 0.0F, RIGHT_CROP_W, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
