package com.cortes.combat.mixin;

import com.cortes.combat.config.ServerConfig;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Liquids take twenty ticks to drink instead of thirty-two, on the 1.20.1 leg.
 *
 * <p>Same funnel as the 1.21.1 twin, older signature: 1.20.1 declares
 * {@code ItemStack#getUseDuration()} with no arguments (verified with
 * {@code javap} against the mapped 1.20.1 jar), while 1.21.1 takes the
 * {@code LivingEntity}. The handler therefore takes only the callback --
 * there is no entity parameter to receive. Everything else (the DRINK gate,
 * the config door, the javadoc's reasoning) is identical on purpose. (⌒‿⌒)</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDrinkDurationMixin {

    @Inject(method = "getUseDuration()I",
            at = @At("HEAD"), cancellable = true)
    private void cortescombat$drinkTicks(CallbackInfoReturnable<Integer> cir) {
        if (!ServerConfig.survivalRework()) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        if (self.getUseAnimation() == UseAnim.DRINK) {
            cir.setReturnValue(ServerConfig.drinkTicks());
        }
    }
}
