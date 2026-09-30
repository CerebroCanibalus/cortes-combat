package com.cortes.combat.mixin;

import com.cortes.combat.client.ClientCombatState;
import com.cortes.combat.client.ConeTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Replaces vanilla's crosshair target selection with cone targeting.
 *
 * <p>Without this the cone is decoration. Vanilla picks the target on the client by raycasting
 * at the crosshair, and only enters the attack path when something sits under that ray. A
 * target well inside the cone but a few degrees off screen centre was never sent to the server
 * at all, so the swing silently did nothing: the player could watch the cone reach the enemy
 * and still deal no damage.</p>
 *
 * <p>It also replays the tail of the vanilla method, because in 1.21.1 the arm swing is not
 * part of the attack. It is one separate statement at the end of {@code startAttack}, behind
 * {@link ClientHooks#onClickInput}, and cancelling at HEAD deleted it along with everything
 * else. That is the whole reason nothing animated at all, and it is invisible in the source of
 * the classes involved: {@code MultiPlayerGameMode#attack} and {@code Player#attack} do not
 * swing, they only send the packet and apply the damage.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void cortescombat$coneTargeting(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (player.isSpectator() || player.isUsingItem()) {
            return;
        }
        // The hard cooldown, refused here rather than on the server.
        //
        // Letting the click through and having the server reject it was worse than useless: the
        // arm swung, the cooldown bar stayed empty, and nothing happened. A swing that visibly
        // happens and does nothing reads as the game dropping input, and the player's response
        // is to click harder. Refusing means the click does not visibly do anything either, and
        // the one thing that <em>does</em> move is the bracket bar filling up.
        //
        // This is a prediction, not the rule. The server still checks in handleAttack, and a
        // client that lied about being ready would gain nothing except a rejected packet.
        if (ClientCombatState.cooldownRatio() < 1.0F) {
            cir.setReturnValue(false);
            return;
        }

        Optional<LivingEntity> target = ConeTargeting.findTargetForHeldItem(player);
        if (target.isEmpty()) {
            // Nothing in the cone: fall through so vanilla still swings at air or mines.
            return;
        }

        LivingEntity victim = target.get();
        if (ConeTargeting.swingBlockedByBlock(mc, victim)) {
            // Solid geometry really is between the eye and the enemy. That is mining, and
            // vanilla already gets mining right.
            return;
        }

        // Vanilla fires this event before touching the target and still swings the hand when
        // it is cancelled. Replaying both halves keeps the swing cancellable by other mods
        // instead of forcing it on them.
        InputEvent.InteractionKeyMappingTriggered event =
                ClientHooks.onClickInput(0, mc.options.keyAttack, InteractionHand.MAIN_HAND);
        if (!event.isCanceled()) {
            mc.gameMode.attack(player, victim);
        }
        if (event.shouldSwingHand()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        cir.setReturnValue(true);
    }
}
