package com.cortes.combat.mixin;

import com.cortes.combat.client.ConeTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
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
 * <p>Only the entity branch is replaced. Pointing at a block still mines, and pointing at
 * nothing still swings, because the cone is a combat reach and block breaking is a separate
 * system vanilla already gets right.</p>
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
        // Pointing at a block means the player is mining, not attacking. Leave it to vanilla.
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }

        Optional<LivingEntity> target = ConeTargeting.findTargetForHeldItem(player);
        if (target.isEmpty()) {
            // Nothing in the cone: fall through so vanilla still swings at air.
            return;
        }

        if (ConeTargeting.sendAttack(player, target.get())) {
            player.resetAttackStrengthTicker();
            cir.setReturnValue(true);
        }
    }
}
