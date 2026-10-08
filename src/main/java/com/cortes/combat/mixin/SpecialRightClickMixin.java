package com.cortes.combat.mixin;

import com.cortes.combat.client.SpecialInputHandler;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The right button, handed to the weapon only after the world has refused it.
 *
 * <h2>The one sentence</h2>
 *
 * <p>A right click resolves in vanilla in a fixed order -- entity, then block, then the item -- and
 * the <b>first</b> one that returns a consuming {@link InteractionResult} ends the click. This class
 * reads those two results and, when neither consumed, fires the special.</p>
 *
 * <h2>Why the result and not the crosshair</h2>
 *
 * <p>The version this replaces asked "is there something under the crosshair that will take the
 * click?" and had to guess, because <b>on the client there is no side-effect-free way to ask
 * whether a block is interactive</b>: vanilla's answer comes from running the interaction, so
 * asking meant doing, and a villager would have opened its trade twice. The guess it chose was
 * "every block consumes", and a player aiming the musket at a wall of stone therefore got nothing
 * -- which is exactly what was reported.</p>
 *
 * <p>The result is not a guess and not second-hand: it is the same number {@code
 * Minecraft#startUseItem} reads, arrived at by running the world for real. A furnace, a chest, a
 * door and a villager consume it and the weapon stays quiet; a block of stone, a zombie or the side
 * of a hill consume nothing and the weapon takes it. That is the behaviour the report asked for,
 * and it is vanilla's own rule rather than a list of blocks someone has to keep true.</p>
 *
 * <h2>Why only two injections</h2>
 *
 * <ul>
 *   <li>{@code useItemOn} -- every block click goes through it, and its return value is the whole
 *       answer for that case.</li>
 *   <li>{@code interact} -- {@code interactAt} runs first and only if it declines is this one
 *       reached, so by the time it returns the entity case has been settled as finally as vanilla
 *       settles it. An entity that is consumed stops the click here; an entity that consumes
 *       nothing lets the weapon through.</li>
 * </ul>
 *
 * <p><b>Air goes through neither</b> -- vanilla falls straight to the item's own use -- so the
 * press path in {@link SpecialInputHandler} keeps that case. The two routes are disjoint, which is
 * what stops one click from becoming two specials.</p>
 *
 * <p><b>Client only.</b> {@code MultiPlayerGameMode} is the client's copy of the game mode; a
 * dedicated server runs {@code ServerPlayerGameMode}, which this class cannot and must not see.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class SpecialRightClickMixin {

    /** The block under the crosshair was given the click; if it declined, the weapon takes it. */
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void cortescombat$specialAfterTheBlock(LocalPlayer player, InteractionHand hand,
                                                    BlockHitResult hit,
                                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) {
            return;
        }
        SpecialInputHandler.afterTheWorldDeclined();
    }

    /** Same question for an entity: consumed, or the weapon's. */
    @Inject(method = "interact", at = @At("RETURN"))
    private void cortescombat$specialAfterTheEntity(Player player, Entity target, InteractionHand hand,
                                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) {
            return;
        }
        SpecialInputHandler.afterTheWorldDeclined();
    }
}
