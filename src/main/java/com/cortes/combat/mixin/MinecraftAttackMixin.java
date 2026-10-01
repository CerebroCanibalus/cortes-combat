package com.cortes.combat.mixin;

import com.cortes.combat.client.SwingInputHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops vanilla from attacking on a press that this mod has already taken over.
 *
 * <p>That is the entire job, and the mixin is this short on purpose. It used to also choose the
 * target with the cone, replay the tail of the vanilla method so the arm would still swing, and
 * send a packet to pay for a whiffed swing. All three moved somewhere that can be reasoned about
 * without reading bytecode: the target and the whiff are now resolved by
 * {@code CombatEngine.resolveSwing} from one payload, and the local swing is played by
 * {@code SwingInputHandler} on the release.</p>
 *
 * <p>What still has to happen here, and cannot happen anywhere else, is that vanilla does not
 * <em>also</em> fire. Vanilla chooses its own target by raycasting at the crosshair and only
 * enters the attack path when something sits under that ray, so a target well inside the cone
 * but a few degrees off screen centre was never sent to the server at all and the swing
 * silently did nothing. Cancelling at HEAD is what replaces that.</p>
 *
 * <p>Cancelling at HEAD deletes the arm swing too, which is invisible in the source of the
 * classes involved: {@code MultiPlayerGameMode#attack} and {@code Player#attack} do not swing,
 * they only send the packet and apply the damage. The swing in 1.21.1 is a separate statement at
 * the end of {@code startAttack}, behind {@code ClientHooks#onClickInput}, which is why the
 * input handler plays the local one itself.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void cortescombat$claimThePress(CallbackInfoReturnable<Boolean> cir) {
        if (SwingInputHandler.claim() != SwingInputHandler.Claim.NOT_OURS) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Stops the block breaking while a swing is open.
     *
     * <p>The second half of taking the button away from vanilla, and it was invisible until the
     * playtest: <b>mining does not go through {@code startAttack}</b>. It goes through
     * {@code continueAttack(boolean)}, which {@code handleKeybinds} calls <em>every tick</em> for
     * as long as the key is down, and which advances the break progress on its own. Cancelling
     * the press stops the first tick of it and every tick after that.</p>
     *
     * <p>The report was that a player charging an attack, who then looked at a block, started
     * mining, and kept mining until they let go. Two things wrong with that at once: the block
     * is being destroyed by a player who is winding up, and the release then resolves a swing
     * against a world the mining has already changed.</p>
     *
     * <p>Gating on the open swing rather than on the crosshair is the point. A player who wants
     * to mine presses on a block with nothing in the cone, the press is <em>not</em> claimed, no
     * swing opens, and mining runs exactly as vanilla intends. Only the button that is
     * committed to an attack stops mining, which is the only button that should.</p>
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void cortescombat$noMiningWhileCharging(boolean pSwingStart, CallbackInfo ci) {
        if (SwingInputHandler.holding()) {
            ci.cancel();
        }
    }
}
