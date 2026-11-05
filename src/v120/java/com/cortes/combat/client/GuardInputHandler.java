package com.cortes.combat.client;

import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.guard.GuardRules;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.net.GuardInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Turns the player's <b>use binding</b> into guard input.
 *
 * <p><b>"Use" and not "right mouse", and that distinction cost a report.</b> Vanilla's item use
 * is {@code options.keyUse}; a player who rebinds it keeps using shields, doors and food through
 * that binding. This class listened for {@code MOUSE_BUTTON_RIGHT} instead, so a rebinded player
 * got vanilla's shield animation started by the real use key with our guard never raised --
 * blocking nothing and still paying the movement penalty.</p>
 *
 * <p>Predicted locally so the guard pose and the orbs react on the same frame as the press, and
 * sent to the server, which is the only thing that counts. Same shape as
 * {@link SwingInputHandler}, on purpose: two input systems that differ in how they lie to the
 * client are two things to debug.</p>
 *
 * <p>It ignores the press entirely unless a guard item is in hand, so holding use with bread
 * does not put the guard up and does not send a packet.</p>
 */
public final class GuardInputHandler {

    private static boolean predictedHolding;

    private GuardInputHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!UseInput.matchesMouse(event.getButton())) {
            return;
        }
        handle(event.getAction() == GLFW.GLFW_PRESS);
    }

    /**
     * La otra mitad del binding: la tecla de usar puede ser una tecla de teclado.
     *
     * <p>Sin este metodo la guardia volvia a depender del raton, que es el bug entero. Los
     * {@code REPEAT} se ignoran: son la tecla sostenida, no un press nuevo. (⌒‿⌒)</p>
     */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_REPEAT) {
            return;
        }
        if (!UseInput.matchesKey(event.getKey(), event.getScanCode())) {
            return;
        }
        handle(event.getAction() == GLFW.GLFW_PRESS);
    }

    private static void handle(boolean pressed) {
        if (!SyncedSettings.combatRework()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Same reason as the charge handler: the button can be pressed on the title screen or
        // while a world loads, and a null connection there takes the render thread with it.
        //
        // **Y una pantalla abierta consume la tecla, no la accion.** Vanilla no mueve el item
        // mientras hay interfaz; una tecla de usar bindeada a una letra escribiria en el chat y
        // levantaria la guardia a la vez sin este gate. (￣ε￣＠)
        if (mc.getConnection() == null || mc.player == null || mc.screen != null) {
            return;
        }
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
        // Through the channel, not the connection: on 1.20.1 a payload is not a packet,
        // so `connection.send` has nothing it accepts.
        com.cortes.combat.platform.CombatPackets.sendToServer(new GuardInputPayload(holding));
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
        if (!SyncedSettings.combatRework()) {
            return false;
        }
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
        // La pregunta va al binding de usar, no al boton derecho: ver UseInput. (§3)
        return UseInput.isDown();
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
        if (!SyncedSettings.combatRework()) {
            return;
        }
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
