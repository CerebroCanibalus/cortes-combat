package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ReachProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/**
 * Prints the cooldown widget's geometry, in pixels, on request from {@code /cc hud}.
 *
 * <p>It exists because of a report that could not be answered by reading the code: "the brackets
 * are off centre with respect to the bars". The code said the two were measured from one number,
 * the screenshot was a crop that had thrown away the only reference the question needed (where
 * the centre is), and three rounds of tuning a constant had already failed. Guessing from a lossy
 * picture is how the wrong number gets tuned twice.</p>
 *
 * <p>So the game is asked instead. Every number below comes from the same
 * {@link ConeGauge#geometry} the layer draws from, which is the only reason the output can be
 * trusted: a diagnostic that recomputes the arithmetic is a second copy of it, and a diagnostic
 * that lies is worse than none.</p>
 */
public final class HudGeometryDump {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("cortescombat");

    private HudGeometryDump() {
    }

    public static void print() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            say("no hay jugador");
            return;
        }
        // A GuiGraphics is only needed for its two size accessors, and the widget reads nothing
        // else from it, so a throwaway is not a lie about what is being measured.
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        ReachProfile reach = WeaponStats.of(player.getMainHandItem()).reach();
        double fov = mc.options.fov().get();

        ConeGauge.Geometry g = ConeGauge.geometry(reach.horizontalAngle(), guiWidth, guiHeight,
                fov, HudAnchor.crosshairReach());
        int centreX = guiWidth / 2;
        BracketMask.Anchors anchors = BracketMask.anchors();

        say("item=" + player.getMainHandItem().getItem()
                + " angulo=" + reach.horizontalAngle());
        say("pantalla=" + guiWidth + "x" + guiHeight
                + " guiScale=" + mc.options.guiScale().get()
                + " fov=" + fov
                + " fovDe=" + reach.horizontalAngle());
        say("centroX=" + centreX + " centroY=" + (guiHeight / 2)
                + " crosshairReach=" + HudAnchor.crosshairReach()
                + " crosshairHalf=" + HudAnchor.crosshairHalf());
        say("halfSpan=" + g.halfSpan()
                + "  barra: inner=" + g.barInner() + " outer=" + g.barOuter()
                + " largo=" + g.barLength() + "  termina en +-" + g.barEnd());
        say("hueco barra->corchete=" + g.gapToBracket() + "px"
                + "   (si esto no es pequeno, la barra no llega al corchete)");
        say("corchetes: tinta izq=" + anchors.left() + " der=" + anchors.right()
                + "  quedan en centroX-" + g.halfSpan() + " y centroX+" + g.halfSpan()
                + "  -> " + (centreX - g.halfSpan()) + " y " + (centreX + g.halfSpan()));
        say("corchete sprite: " + BracketMask.runs().size() + " runs, alto="
                + BracketMask.height() + ", pico alpha=" + BracketMask.peakAlpha()
                + " -> " + Math.round(ConeGauge.bracketAlpha() * 255.0F) + "/255");
    }

    private static void say(String line) {
        LOG.info("[hud] " + line);
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("[hud] " + line));
    }
}
