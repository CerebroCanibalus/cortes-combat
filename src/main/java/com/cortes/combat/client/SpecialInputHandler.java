package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.guard.GuardStats;
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

    private SpecialInputHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }
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
            LungeMotion.begin(player, tier);
        }
    }
}