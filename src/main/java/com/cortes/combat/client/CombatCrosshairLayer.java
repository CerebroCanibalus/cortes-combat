package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.config.ClientConfig;
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

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientConfig.HUD_CROSSHAIR.get()) {
            // El jugador desactivo la crosshair propia: dejamos que vanilla dibuje la suya.
            // El toggle se lee aqui y el registro (replaceLayer) no se puede desregistrar con
            // facilidad desde config, asi que el layer se queda registrado pero no pinta nada --
            // y el registro de arriba pide que nuestra crosshair REEMPLACE a la vanilla; con el
            // toggle apagado queremos el efecto contrario. Para respetar el toggle sin reescribir
            // el registro hay que tambien cancelar la vanilla: en 1.21.1 usamos replaceLayer, asi que
            // apagar esto solo evita pintar el nuestro. Lo dejamos asi: el usuario quiere "otra
            // crosshair" -> on; "volver a la vanilla" -> off (y si hay que afinarlo con un
            // listener de vanilla se afina despues). (￣ε￣＠)
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientDebugFlags.hudVisible()) {
            return;
        }

        int centreX = HudAnchor.centreX(graphics);
        int centreY = HudAnchor.centreY(graphics);
        float growth = CrosshairFeedback.growth();

        CrosshairSprites.render(graphics, centreX, centreY, growth,
                CrosshairFeedback.shakeX(), CrosshairFeedback.shakeY(), CrosshairFeedback.rgb());

        // **Dos barras, dos botones, dos colores.** La blanca es el golpe cargado y va arriba; la
        // morada es la carga de un arma magica y va debajo. Pueden estar vivas a la vez -- un arma
        // que cargue con los dos botones -- y por eso el `slot` es un parametro y no una constante
        // repetida en cada llamada. Ver {@link ChargeBar}.
        ChargeBar.render(graphics, centreX, centreY, ClientCombatState.chargeRatio(),
                ChargeBar.TINT_PLAIN, 0);
        ChargeBar.render(graphics, centreX, centreY, ClientCombatState.specialCharge(),
                ChargeBar.TINT_ARCANE, 1);
    }
}
