package com.cortes.combat.client;

import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.guard.GuardRules;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.net.GuardInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        // Covering abandons a swing that was already charging, before the packet goes out. The
        // server does the same to the charge it is holding, and the two halves have to happen on
        // the same frame: if only the server's half did, the client would show a wind-up and a
        // filling bar for a swing the server has already thrown away, which is the one thing
        // this project has decided is worse than a swing that does nothing.
        if (holding) {
            SwingInputHandler.abandon("cubriendose");
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
        if (player == null) {
            return;
        }
        // Only a blocking use may be stopped, and that is the whole fix. The old scope was "the
        // hands hold something that can guard", which a shield in the off hand satisfies while
        // you are halfway through a steak in the main hand -- so breaking the guard cancelled the
        // dinner, and the interaction the playtest asked to keep was the thing being destroyed.
        //
        // It also only ever had anything to stop for a shield. A sword guard starts no item use
        // at all, so there was never a stale shield animation to take back on that side.
        if (GuardRules.mayCancelItemUse(player.isUsingItem(), GuardHandler.usedItemBlocks(player))) {
            player.stopUsingItem();
        }
    }

    public static boolean predictedHolding() {
        return predictedHolding;
    }

    /**
     * Whether the right button is physically down right now, asked of the window.
     *
     * <p>The identical hole to {@link SwingInputHandler#reconcileWithTheButton()} and on the
     * other button: GLFW delivers no release event when the window is deactivated with a button
     * held, so a guard raised on the way to alt-tab is still "raised" when the player comes
     * back.</p>
     *
     * <p>It matters more here than it does for the swing, because of what a stuck flag here
     * refuses. A press that arrives while {@code predictedHolding} is true is refused with
     * {@code Claim.REFUSED}, and refused is invisible on purpose -- and the mixin cancels vanilla
     * for it too. So a guard flag that outlived its button does not merely show a raised shield,
     * it silently eats the attack button for the rest of the session. That is the "cannot attack,
     * cannot break, cannot interact" report, and this flag is one of its two causes.</p>
     */
    private static boolean buttonIsPhysicallyDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputLiveness.buttonIsDown(window != null,
                window != null && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                        window.getWindow(),
                        org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                        != org.lwjgl.glfw.GLFW.GLFW_RELEASE);
    }

    /**
     * Lowers a guard whose button is no longer down.
     *
     * <p>Called from the client tick. A guard is input state by definition, so the same rule that
     * has applied to it three times in this project applies here too: <b>a button that the player
     * is no longer holding must not survive something the player did not do.</b> Losing focus is
     * something the player did not do to the button, and the button is not held any more.</p>
     *
     * <p>Goes through {@link #setHolding} rather than assigning the field, so the server is told
     * and the two halves do not disagree. A client that lowered the guard silently would leave the
     * server still blocking, which is the mirror image of the stale-shield-animation bug this
     * class already fixes.</p>
     */
    public static void reconcileWithTheButton() {
        if (InputLiveness.shouldLowerGuard(predictedHolding, buttonIsPhysicallyDown())) {
            setHolding(false);
        }
    }

    /** For {@code /cc input}. Reads the field, recomputes nothing. */
    public static boolean buttonDownForDebug() {
        return buttonIsPhysicallyDown();
    }

    /**
     * The client's half of "the shield does not go up without points".
     *
     * <p>Same event and same {@link GuardRules#mayStartItemUse} as {@code GuardHandler}'s server
     * branch, but reading the <em>synced</em> stamina: common code cannot reach a client class,
     * and the client has no live copy of the truth.</p>
     *
     * <p>Being here as well as there is not a nicety, and the reason is in the bytecode.
     * {@code MultiPlayerGameMode#useItem} fires {@code CommonHooks.onItemRightClick} and returns
     * before it touches {@code ItemStack#use} or builds the packet, so cancelling here means the
     * packet is never sent. Server-side only, the client sends it anyway and vanilla re-sends it
     * every tick the button is held -- twenty refused packets a second for as long as the player
     * holds the button on an empty guard.</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        LocalPlayer player = event.getEntity() instanceof LocalPlayer local ? local : null;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        boolean guardReady = ClientCombatState.guardStamina() > 0 && !GuardHandler.isEating(player);
        if (!GuardRules.mayStartItemUse(GuardStats.of(stack).canGuard(), guardReady)) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether the guard can go up at all right now.
     *
     * <p>Checks the synced points as well as the item, so the client does not predict a guard
     * the server is going to refuse in the same tick. That refusal is instant rather than a
     * flicker, because the two sides agree on the rule and only the timing is different.</p>
     *
     * <p>The eating refusal is the third input and it is the one that is easy to leave out. A
     * client that predicts the raise anyway sends a packet, the server refuses it, and the
     * resulting state sync comes back as "the guard is down" -- which used to mean
     * {@code stopUsingItem} and a cancelled meal. The rule is cheap to apply here and it is
     * {@link GuardHandler#setGuard}'s job to be sure of it.</p>
     */
    private static boolean canGuard(LocalPlayer player) {
        return GuardRules.mayRaiseGuard(
                GuardHandler.bestFor(player).canGuard() && ClientCombatState.guardStamina() > 0,
                GuardHandler.isEating(player));
    }
}
