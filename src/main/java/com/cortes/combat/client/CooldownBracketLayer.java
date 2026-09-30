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
     * Where the brackets sit at the narrowest cone in the game, and how much further out the
     * widest one goes. Both numbers are about the <em>width</em> of the cone, so a weapon with a
     * tight cone keeps the brackets in close where they belong.
     */
    private static final int MIN_HALF_SPAN = 12;
    private static final int MAX_HALF_SPAN = 34;

    /** Cone half-angle window the span is stretched across, in degrees. */
    private static final float ANGLE_FLOOR = 10.0F;
    private static final float ANGLE_CEILING = 80.0F;

    /** Bar thickness. One pixel: this is a line between two brackets, not a second bar. */
    private static final int BAR_THICKNESS = 1;

    /** Alpha at the crosshair end of the fill, and at the bracket end. */
    private static final int ALPHA_NEAR_CROSSHAIR = 30;
    private static final int ALPHA_NEAR_BRACKET = 240;

    /**
     * The empty part of the bar. Low on purpose: it has to be readable as "not ready yet"
     * without competing with the fill, and the fill's own fade already makes its far end dim.
     */
    private static final int ALPHA_SOCKET = 45;

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
        int halfSpan = halfSpanFor(reach.horizontalAngle());
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
     * <p>A cone angle, not a weapon. Anything between {@link #ANGLE_FLOOR} and
     * {@link #ANGLE_CEILING} degrees gets a proportional span, and anything outside is clamped,
     * so a modded weapon with a wider profile is drawn as "wider" rather than as "wrong".</p>
     */
    static int halfSpanFor(float horizontalAngle) {
        float t = clamp01((horizontalAngle - ANGLE_FLOOR) / (ANGLE_CEILING - ANGLE_FLOOR));
        return MIN_HALF_SPAN + Math.round(t * (MAX_HALF_SPAN - MIN_HALF_SPAN));
    }

    /**
     * One side's bar, drawn one pixel column at a time so the alpha can ramp along it.
     *
     * <p>Per column rather than in segments because a ramp in a few steps over thirty pixels is
     * visibly a ramp in a few steps. It is a couple of dozen quads a side, and the alternative
     * is a gradient texture that would have to be authored for every possible length.</p>
     *
     * <p>Two passes over the same loop: the socket for the whole length, then the fill over the
     * filled prefix. The fill starts at the crosshair and grows toward the bracket, and the
     * fade is brightest at the bracket end, so the moving edge is always the visible one.</p>
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
        // means moving in the direction of this side's bracket, which is +x on the right and -x
        // on the left. Getting this sign backwards draws the whole bar outside the bracket,
        // which is invisible and which this exact layer shipped once.
        int originX = centreX + side * (HudAnchor.crosshairReach() + 1);

        for (int i = 0; i < length; i++) {
            // i is the distance from the crosshair. 0 at the crosshair, length-1 at the bracket.
            float t = length <= 1 ? 0.0F : (float) i / (length - 1);
            int socket = ALPHA_SOCKET;
            int fill = Math.round(ALPHA_NEAR_CROSSHAIR
                    + (ALPHA_NEAR_BRACKET - ALPHA_NEAR_CROSSHAIR) * t);
            int alpha = i < filled ? fill : socket;
            graphics.fill(originX + side * i, y, originX + side * i + 1, y + BAR_THICKNESS,
                    (alpha << 24) | rgb);
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
