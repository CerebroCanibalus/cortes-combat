package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.config.ClientConfig;
import com.cortes.combat.core.weapon.ReachProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
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
 * the widest thing on screen and a ropera at 12 is visibly the tightest, which is exactly the
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

    /**
     * Bar thickness.
     *
     * <p>Was three, which the player read as a slab laid over the middle of the screen. Two is
     * the compromise: a one pixel line is one physical pixel at GUI scale one, where it can fall
     * between two display pixels and break up, and the bar has to survive that. Two survives
     * every scale and still reads as a line rather than as a plate.</p>
     */
    private static final int BAR_THICKNESS = 2;

    /**
     * The bar runs from the crosshair's edge to just short of the bracket, so the two never
     * touch even when the crosshair jumps on a hit. The gap is {@link ConeGauge#BAR_INSET} and it
     * lives with the geometry rather than here, so the diagnostic and the drawing cannot read
     * two different numbers.
     */

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientConfig.HUD_COOLDOWN_BRACKETS.get()) {
            return;
        }
        if (HudIdle.barsHidden()) {
            // El jugador dejo de pelear: los brackets se van con las barras.
            return;
        }
        if (!ClientDebugFlags.hudVisible()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().options.hideGui || player.isSpectator()) {
            return;
        }

        // **Is this a weapon at all, and it is the door the click asks.**
        //
        // <p>This layer did not ask, and the playtest reported the consequence exactly: holding a
        // block, a potion or a piece of food drew a cooldown bracket and a bar, because
        // {@code NONE} still has a reach row -- three and a half degrees of cone and three
        // blocks of distance -- and the geometry does not care what it is measuring.</p>
        //
        // Same door, same reason, third time. The press asks it, the tooltip asks it, and
        // now the HUD asks it. A widget that reports "what your button will do" for an item that
        // has no button is not showing a wrong number, it is inventing one.</p>
        if (!com.cortes.combat.item.WeaponKinds.fights(
                WeaponStats.of(player.getMainHandItem()).profile())) {
            return;
        }

        int centreX = HudAnchor.centreX(graphics);
        int centreY = HudAnchor.centreY(graphics);
        // **displayedReach and not reach.** The brackets measure what the player's button will do, and on the arcabuz that is the barrel: six and a half blocks at thirty two degrees, while the left click is a two and a half block bayonet at fifty. A crosshair that described the bayonet while the player is aiming the gun is a marker lying about the thing they are about to do.
        ReachProfile reach = com.cortes.combat.attack.MaceAir.displayedFor(
                WeaponStats.of(player.getMainHandItem()), player,
                com.cortes.combat.enchant.CombatEnchantments.barredorLevel(player));

        // One geometry, computed once, and the same one the /cc hud diagnostic prints. The bar
        // and the brackets both measure from it, and a second copy of it is a second chance to
        // have them disagree by a pixel -- which is exactly the report this geometry came from.
        ConeGauge.Geometry geometry = ConeGauge.geometry(reach.horizontalAngle(),
                graphics.guiWidth(), graphics.guiHeight(),
                Minecraft.getInstance().options.fov().get(), HudAnchor.crosshairReach());
        float ratio = clamp01(ClientCombatState.cooldownRatio());
        // **El color de reposo es config; el flash no.** Un golpe que aterriza sigue pintando
        // su color (rojo, bloqueo, muerte): ese color es informacion. Lo que el jugador elige es
        // como se ve el bracket cuando no esta contando nada.
        int rgb = HudColors.flashOr(CrosshairFeedback.rgb(), CrosshairFeedback.active(),
                HudPalette.bracket());

        RenderSystem.enableBlend();
        drawBar(graphics, centreX, centreY, geometry, ratio, rgb, -1);
        drawBar(graphics, centreX, centreY, geometry, ratio, rgb, 1);
        drawBrackets(graphics, centreX, centreY, geometry.halfSpan(), rgb);
        RenderSystem.disableBlend();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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
    private static void drawBar(GuiGraphics graphics, int centreX, int centreY,
                                ConeGauge.Geometry geometry, float ratio, int rgb, int side) {
        int length = geometry.barLength();
        if (length <= 0) {
            return;
        }
        int filled = Math.round(length * ratio);
        int y = centreY - BAR_THICKNESS / 2;
        // The pixel column the bar starts at, next to the crosshair. Going outward from here
        // means moving toward this side's bracket, which is +x on the right and -x on the left.
        // Getting this sign backwards draws the whole bar outside the bracket, which is
        // invisible, and which this exact layer shipped once.
        int originX = centreX + side * (geometry.barOuter() + 1);

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
     * The brackets, filled rather than blitted.
     *
     * <p>They never scale with the crosshair. The crosshair growing on a hit is feedback about
     * one swing; the brackets growing with the same number would make a landed hit look like
     * the weapon changed.</p>
     *
     * <p>The sprite is not drawn with {@code blit} any more, and the reason is in
     * {@link BracketMask}: {@code blit} bypasses the GUI pipeline, so a tint on it does nothing
     * and the brackets were opaque at every alpha the playtest tried. Filling them means this
     * widget's two halves are drawn by the same call, which is worth having on its own.</p>
     */
    private static void drawBrackets(GuiGraphics graphics, int centreX, int centreY,
                                     int halfSpan, int rgb) {
        List<BracketMask.Run> runs = BracketMask.runs();
        BracketMask.Anchors anchors = BracketMask.anchors();
        if (runs.isEmpty() || anchors.left() == BracketMask.NO_INK) {
            return;
        }
        float presence = ConeGauge.bracketAlpha();
        int spriteHeight = BracketMask.height();
        int y0 = centreY - spriteHeight / 2;
        // Each bracket's ink centre is placed on the cone's projected edge, so the pair is
        // symmetric about the crosshair by construction. Nothing here knows the sprite's width or
        // where in it the ink sits: BracketMask measured both, once, when it read the file.
        int leftBase = centreX - halfSpan - anchors.left();
        int rightBase = centreX + halfSpan - anchors.right();
        for (BracketMask.Run run : runs) {
            int y = y0 + run.y();
            int leftX = leftBase + run.leftX();
            int rightX = rightBase + run.rightX();
            // The art's own alpha, taken as a fraction of the art's own strongest pixel. See
            // BracketMask#normalised: the sprite's peak is 38 of 255, and multiplying that by an
            // opacity produced eleven, which is not a bracket.
            float shape = BracketMask.normalised(run.alpha());
            graphics.fill(leftX, y, leftX + run.width(), y + 1, pack(shape, rgb, presence));
            graphics.fill(rightX, y, rightX + run.width(), y + 1, pack(shape, rgb, presence));
        }
    }

    /** The art's shape times the widget's presence, as a packed GUI colour. */
    private static int pack(float normalisedAlpha, int rgb, float presence) {
        int a = Math.round(normalisedAlpha * presence * 255.0F);
        if (a <= 0) {
            return 0;
        }
        return (a << 24) | rgb;
    }
}
