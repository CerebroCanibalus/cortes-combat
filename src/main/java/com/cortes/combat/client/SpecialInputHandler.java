package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.net.SpecialHeldPayload;
import com.cortes.combat.net.SpecialPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The right button, when it means a special instead of a guard.
 *
 * <p><b>The guard is asked first and wins if it can.</b> The guard reads either hand, so a player
 * holding a halberd with a shield in the off hand presses the right button and gets a block: that
 * is what the unified guard is, and a special that stole the button from it would be a special
 * that changed behaviour by what happened to be in the other hand.</p>
 *
 * <p>With nothing to block with, the weapon's special takes it. Four of the five new families
 * cannot guard at all, and the halberd is one of them, which is the whole reason this class
 * exists: those weapons used to have a right button that raised a shield and did nothing else.</p>
 *
 * <p>Same refusals as the attack button, in the same order, because the same reasons apply: a
 * screen owns the click, eating owns the click, and a spectator owns nothing.</p>
 */
public final class SpecialInputHandler {

    /**
     * What the server was last told about the right button.
     *
     * <p>The client-side mirror of the attachment's flag, and it exists so the tick can decide
     * whether a change is worth a packet. It is reset on logout by {@link #forget()} rather than
     * left to the next join: a new session has a new window, and whatever the old one was doing is
     * not a fact about this one.</p>
     */
    private static boolean lastSentHeld;

    private SpecialInputHandler() {
    }

    /**
     * The release, handled as its own event and not as the absence of a press.
     *
     * <p>The one thing this class must not do is send a packet per {@code MouseButton} event,
     * because that event fires on press <em>and</em> on release. One click of the right button
     * then became two: the press loaded the arcabuz, and the release -- a few hundredths of a
     * second later -- found it loaded and lit the fuse. The gun fired on its own a second after
     * being reloaded.</p>
     *
     * <p>{@code SwingInputHandler} has had the {@code getAction() == GLFW_PRESS} filter for the
     * whole life of the project. The arcabuz is the only weapon where the damage showed up, because
     * it is the only weapon where a press and a release twenty ticks apart mean two different
     * things.</p>
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return;
        }
        press();
    }


    /**
     * The press, split out so the tick can ask the same question and reach the same answer.
     *
     * <p>{@link #reconcileWithTheButton()} answers "should the server hear about the button" every
     * tick, and on the tick the button goes down that answer is yes and the press is sent from
     * here. One place builds the packet, so a second route to it cannot disagree about the
     * refusals.</p>
     */
    private static void press() {
        Minecraft mc = Minecraft.getInstance();
        // The button can be pressed on the title screen or while a world loads, and a null
        // connection there takes the render thread with it.
        if (mc.getConnection() == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isSpectator() || player.isUsingItem()) {
            return;
        }

        // **Air is the only case decided here.** Everything else waits for the world.
        //
        // Read out of `Minecraft#startUseItem` in 1.21.1: a right click resolves in three
        // possible orders and the *first* one that consumes the action ends the click.
        //
        //   1. `hitResult.getType() == ENTITY` -> `gameMode.interactAt` / `interact`. Consumes -> done.
        //   2. `hitResult.getType() == BLOCK`  -> `gameMode.useItemOn`.       Consumes -> done.
        //   3. and only if neither consumed   -> `gameMode.useItem`, which is the item's own use.
        //
        // So a crafting table, a chest, a door, a lever and a villager all take the button before
        // the item ever gets asked, and a weapon with a right click that fires anyway is spending
        // the same press twice. **And that is why the decision is not made here any more:** which
        // of the three consumed the action is a fact the world produces, not a fact the crosshair
        // can guess. Guessing needs "is this block interactive?", and there is no side-effect-free
        // way to ask that on the client -- vanilla's answer comes from running the interaction, so
        // asking meant doing, and the old guess ("every block consumes") meant a musket aimed at a
        // wall of stone was a musket aimed at nothing.
        //
        // The press then only fires on air; the other two cases come back through
        // `SpecialRightClickMixin`, which reads the result the world already produced.
        if (mc.hitResult != null
                && mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return;
        }
        afterTheWorldDeclined();
    }

    /**
     * The special, fired once the world has been given its chance at the click.
     *
     * <p>Called from {@link #press()} when the crosshair is on nothing, and from
     * {@code SpecialRightClickMixin} when the block or the entity under it did not consume the
     * action. <b>Both routes end here, and only here,</b> so the guard, the packet and the
     * predictions are decided once and cannot disagree with each other.</p>
     */
    public static void afterTheWorldDeclined() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isSpectator() || player.isUsingItem()) {
            return;
        }
        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        if (stats.special() == SpecialType.NONE) {
            return;
        }

        // The guard is asked before the special, from either hand. Anything that can block takes
        // the button, and only a player holding nothing that blocks gets the special.
        if (GuardStats.best(player).canGuard()) {
            return;
        }

        PacketDistributor.sendToServer(new SpecialPayload(player.getMainHandItem()));

        // **And the button itself, before the press lands.**
        //
        // The server's reload advances on a flag this class owns, and that flag starts false. If
        // the press arrived without it, the reload would begin and then sit at fifty ticks until
        // the player let go and pressed again -- which is the "no reload time at all" bug wearing
        // a different hat.
        //
        // Setting the local mirror here too, so the tick's reconciliation sees no change and does
        // not send this a second time.
        lastSentHeld = true;
        PacketDistributor.sendToServer(new SpecialHeldPayload(true));

        // The sweep's arc, predicted on the press.
        //
        // **Only this one special, and only while the weapon is actually ready.**
        //
        // The lunge and the parry are driven by the server's own state because both are more than
        // a weapon moving -- one is the body going somewhere and the other is a window the server
        // is holding open -- and drawing them on the press would show a step the server refused.
        // A sweep is a weapon travelling, so predicting it on the press is right, and that is
        // exactly what made it wrong: the sweep costs four halberd swings now, so the button is
        // pressed and refused constantly, and the arc played every single time.
        //
        // **`cooldownRatio() >= 1.0F` is the same gate the lunge's prediction already used**, and
        // `cooldownRatio` is the readiness, not the remaining cooldown: one means ready to swing.
        // Without it the client animates a swing that no enemy ever saw.
        if (stats.special() == SpecialType.MULTI_HIT && ClientCombatState.cooldownRatio() >= 1.0F) {
            ItemViewModel.onSweep();
        }

        // The lunge's animation is predicted on the press, and the prediction is only ever wrong
        // in a way that costs a packet.
        //
        // Unlike the parry -- where the server owns a five second lockout the client cannot see
        // into -- the lunge's price is the ordinary attack cooldown, which the client already has
        // synced. So it can be gated on the same number the server gates on, and if the two ever
        // disagree the player sees a step that hit nothing rather than a hit with no step. The
        // first one is recoverable and the second one is a lie about a swing the player took.
        //
        // The reason it is the client's own clock and not a packet: the step has to begin on the
        // frame of the press or the blade is extended while the player is still standing where
        // they were.
        // `cooldownRatio` is READINESS, not progress: 1 means the next swing is allowed. Gating on
        // "ready" as if it were zero is the mirror of the parameter-naming bug this project has
        // paid for once, and it made the lunge predict itself only while the ropera was on
        // cooldown -- which is exactly never, at the moment the player would use it.
        if (stats.special() == SpecialType.LUNGE && ClientCombatState.cooldownRatio() >= 1.0F) {
            ClientCombatState.startLunge();
            // **The movement happens here, on the client.** The server cannot do it: a position it
            // sets is overwritten by the movement report the client sends every tick, and a velocity
            // it sets is simply ignored for a player. Teleporting around that worked but juddered,
            // because ten to thirty discrete corrections are ten to thirty jerks. The client's own
            // physics is smooth and it already knows where the player is.
            int tier = player.getMainHandItem().getItem()
                    instanceof com.cortes.combat.item.CortesWeaponItem weapon
                    ? weapon.material().rank() : 0;
            // **Retirada: agachado, y el lunge sale para atras.**
            //
            // Se decide aca porque es donde se decide el impulso, y el cliente es quien lo da. Los
            // dos lados leen lo mismo -- el agachado esta sincronizado y el encantamiento esta en el
            // stack -- asi que no hay paquete nuevo ni un segundo reloj: es la misma pregunta
            // hecha dos veces a los mismos datos.
            boolean retreating = player.isShiftKeyDown()
                    && com.cortes.combat.enchant.CombatEnchantments.level(
                            player.level().registryAccess(), player.getMainHandItem(),
                            com.cortes.combat.enchant.CombatEnchantments.RETIRADA) > 0;
            LungeMotion.begin(player, tier, retreating);
        }
    }

    /**
     * Every tick, ask the window what the right button is doing.
     *
     * <p><b>Not the event stream, for the reason {@link SwingInputHandler} says it out loud.</b>
     * GLFW does not deliver a release when the window loses focus with a button down, and a flag
     * that only moves on events leaves the server believing a button is still held. The arcabuz's
     * reload is the thing that would hang: a gun at forty of fifty ticks, waiting for a release
     * that is never coming, until the player touched the mouse again.</p>
     */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        reconcileWithTheButton();
        // The arcabuz's report, drawn on the client's own tick rather than on the packet's
        // arrival: a particle wants a level, and arming on the packet and drawing on a tick
        // means the effect happens at a frame the client chose.
        ClientArcabuzShot.drawIfArmed();
    }

    /**
     * The reconciliation, separate from the subscription so {@code /cc} can call it and so the
     * decision can be reasoned about without a window.
     *
     * @return whether a packet was sent
     */
    public static boolean reconcileWithTheButton() {
        Minecraft mc = Minecraft.getInstance();

        // A window that is gone answers "not down", which sends the release and stops a reload
        // rather than leaving it waiting. Same safe direction as every other question in
        // InputLiveness, and for the same reason.
        var window = mc.getWindow();
        boolean down = InputLiveness.buttonIsDown(window != null,
                window != null
                        && org.lwjgl.glfw.GLFW.glfwGetMouseButton(window.getWindow(),
                                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                        != org.lwjgl.glfw.GLFW.GLFW_RELEASE);

        if (mc.getConnection() == null || mc.level == null || mc.player == null) {
            // Nothing to talk to. The mirror is cleared rather than kept, because the window it
            // described belongs to a session that has ended, and a stale true here would swallow
            // the first press of the next one.
            lastSentHeld = false;
            return false;
        }

        if (!InputLiveness.shouldSendHeldChange(lastSentHeld, down)) {
            return false;
        }
        lastSentHeld = down;
        PacketDistributor.sendToServer(new SpecialHeldPayload(down));
        return true;
    }
}
