package com.cortes.combat.client;

import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.net.GuardInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Turns the right mouse button into guard input.
 *
 * <p>Predicted locally so the guard pose and the orbs react on the same frame as the click, and
 * sent to the server, which is the only thing that counts. Same shape as
 * {@link ChargeInputHandler}, on purpose: two input systems that differ in how they lie to the
 * client are two things to debug.</p>
 *
 * <p>It ignores the click entirely unless a guard item is in hand, so holding right with bread
 * does not put the guard up and does not send a packet.</p>
 */
public final class GuardInputHandler {

    private static boolean predictedHolding;

    private GuardInputHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Same reason as the charge handler: the button can be pressed on the title screen or
        // while a world loads, and a null connection there takes the render thread with it.
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }
        boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
        if (pressed) {
            if (!canGuard(mc.player)) {
                setHolding(false);
                return;
            }
        } else if (!predictedHolding) {
            return;
        }
        setHolding(pressed);
    }

    public static void setHolding(boolean holding) {
        if (predictedHolding == holding) {
            return;
        }
        predictedHolding = holding;
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new GuardInputPayload(holding));
        }
    }

    /** The server owns the truth; this only decides when the client draws. */
    public static void acceptServerState(boolean holding) {
        predictedHolding = holding;
    }

    public static boolean predictedHolding() {
        return predictedHolding;
    }

    private static boolean canGuard(LocalPlayer player) {
        return GuardHandler.canGuardItem(player.getMainHandItem())
                || GuardHandler.canGuardItem(player.getOffhandItem());
    }
}
