package com.cortes.combat.client;

import com.cortes.combat.net.ChargeInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns the left mouse button into charge input.
 *
 * <p>The client only reports intent. It starts a local charge so the indicator is instant,
 * and tells the server; the server accumulates on its own tick and is the only thing whose
 * charge counts for damage.</p>
 */
public final class ChargeInputHandler {

    /** Local mirror of charge, for drawing. Never the source of truth. */
    private static int predictedTicks;

    private static boolean predictedHolding;

    private ChargeInputHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_LEFT) {
            return;
        }
        // The mouse can be clicked while still on the title screen, in a world preview, or
        // while a server is loading, and getConnection() is null in all of those. Sending on
        // a null connection throws and takes the render thread with it.
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        boolean pressed = event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        setHolding(pressed);
    }

    public static void setHolding(boolean holding) {
        if (predictedHolding == holding) {
            return;
        }
        predictedHolding = holding;
        predictedTicks = holding ? predictedTicks : 0;
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ChargeInputPayload(holding));
        }
    }

    public static void tickPrediction() {
        if (predictedHolding) {
            predictedTicks++;
        }
    }

    public static int predictedTicks() {
        return predictedTicks;
    }

    public static boolean predictedHolding() {
        return predictedHolding;
    }

    /** Called when the server tells us the real values, so lag does not leave the bar lying. */
    public static void acceptServerState(float chargeRatio, int fullChargeTicks) {
        if (fullChargeTicks <= 0) {
            predictedTicks = 0;
            return;
        }
        // Snap toward the server value rather than overwriting, so a single late packet does
        // not make the indicator jump backwards.
        int serverTicks = Math.round(chargeRatio * fullChargeTicks);
        predictedTicks = Math.max(serverTicks, predictedTicks - 2);
        if (serverTicks < predictedTicks) {
            predictedTicks = serverTicks;
        }
    }
}
