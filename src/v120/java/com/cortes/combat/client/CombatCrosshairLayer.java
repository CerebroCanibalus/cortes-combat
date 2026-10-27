package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraft.resources.ResourceLocation;

/**
 * The crosshair and the charge bar, stacked. 1.20.1 twin: same body as the shared one, only the
 * overlay interface differs ({@code IGuiOverlay} instead of {@code LayeredDraw.Layer}).
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
 *
 * <h2>Y la config, que es lo que esta clase ahora escucha</h2>
 *
 * <p>Tamano, colores, shake, la barra de carga y el apagado por inactividad. El toggle de la
 * crosshair lo respeta esta clase (no pinta) y la vanilla vuelve sola: en esta pata
 * {@code hideVanillaCrosshair} solo cancela la capa de vanilla con el toggle ENCENDIDO.
 * (￣ε￣＠)</p>
 */
public class CombatCrosshairLayer implements IGuiOverlay {

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!com.cortes.combat.config.ClientConfig.HUD_CROSSHAIR.get()) {
            // La vanilla se dibuja sola en esta pata: aca solo dejamos de pintar la nuestra.
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientDebugFlags.hudVisible()) {
            return;
        }

        int centreX = HudAnchor.centreX(graphics);
        int centreY = HudAnchor.centreY(graphics);

        if (!HudIdle.crosshairHidden()) {
            float growth = CrosshairFeedback.growth();
            // El shake es config; el reloj del golpe sigue corriendo (crecimiento y color quedan).
            int shakeX = com.cortes.combat.config.ClientConfig.HUD_CROSSHAIR_SHAKE.get()
                    ? CrosshairFeedback.shakeX() : 0;
            int shakeY = com.cortes.combat.config.ClientConfig.HUD_CROSSHAIR_SHAKE.get()
                    ? CrosshairFeedback.shakeY() : 0;
            int idle = HudColors.parse(
                    com.cortes.combat.config.ClientConfig.HUD_CROSSHAIR_COLOR.get(),
                    HudColors.WHITE);
            int aim = HudColors.parse(
                    com.cortes.combat.config.ClientConfig.HUD_CROSSHAIR_AIM_COLOR.get(),
                    HudColors.WHITE);
            int rgb = HudColors.stateOr(CrosshairFeedback.rgb(), CrosshairFeedback.active(),
                    ClientCombatState.aiming(), idle, aim);

            CrosshairSprites.render(graphics, centreX, centreY, growth, shakeX, shakeY, rgb);
        }

        // **Dos barras, dos botones, dos colores.** La blanca es el golpe cargado y va arriba; la
        // morada es la carga de un arma magica y va debajo. Pueden estar vivas a la vez -- un arma
        // que cargue con los dos botones -- y por eso el `slot` es un parametro y no una constante
        // repetida en cada llamada. Ver {@link ChargeBar}.
        if (!com.cortes.combat.config.ClientConfig.HUD_CHARGE_BAR.get() || HudIdle.barsHidden()) {
            return;
        }
        ChargeBar.render(graphics, centreX, centreY, ClientCombatState.chargeRatio(),
                ChargeBar.plainTint(), 0);
        ChargeBar.render(graphics, centreX, centreY, ClientCombatState.specialCharge(),
                ChargeBar.arcaneTint(), 1);
    }
}
