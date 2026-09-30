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
 * {@link SwingInputHandler}, on purpose: two input systems that differ in how they lie to the
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

    /**
     * The server owns the truth; this only decides when the client draws.
     *
     * <p>When the server says the guard is down, the client drops the item use as well. The item
     * use animation is predicted on the client and the server cannot take it back:
     * {@code ServerPlayer} does not override {@code stopUsingItem}, so nothing goes over the
     * wire. Without this the shield stayed visibly raised for as long as the button was held,
     * which is exactly "you can keep covering and take the hit looking like an idiot".</p>
     */
    public static void acceptServerState(boolean active) {
        predictedHolding = active;
        if (active) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        // Scoped to guard items on purpose. A player eating bread is also "using an item" and
        // the state sync runs every tick, so an unscoped stop would cancel their dinner.
        if (player != null && player.isUsingItem() && GuardHandler.bestFor(player).canGuard()) {
            player.stopUsingItem();
        }
    }

    public static boolean predictedHolding() {
        return predictedHolding;
    }

    /**
     * Whether the guard can go up at all right now.
     *
     * <p>Checks the synced points as well as the item, so the client does not predict a guard
     * the server is going to refuse in the same tick. That refusal is instant rather than a
     * flicker, because the two sides agree on the rule and only the timing is different.</p>
     */
    private static boolean canGuard(LocalPlayer player) {
        return GuardHandler.bestFor(player).canGuard()
                && ClientCombatState.guardStamina() > 0;
    }
}
