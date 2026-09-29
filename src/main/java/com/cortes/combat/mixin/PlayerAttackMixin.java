package com.cortes.combat.mixin;

import com.cortes.combat.attack.CombatEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the entire vanilla attack flow.
 *
 * <p>Cancelling at HEAD means vanilla never runs its damage calculation, its crosshair raycast
 * or its sweep attack. That is the point: this mod has priority over the vanilla combat
 * system, and over any mod that also tries to own it (see the blocklist in
 * ARCHITECTURE.md section 3).</p>
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void cortescombat$replaceAttackFlow(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (CombatEngine.handleAttack(player, target)) {
            ci.cancel();
        }
    }
}
