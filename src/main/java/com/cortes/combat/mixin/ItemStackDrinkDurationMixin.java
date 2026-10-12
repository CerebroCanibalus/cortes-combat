package com.cortes.combat.mixin;

import com.cortes.combat.config.ServerConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Liquids take twenty ticks to drink instead of thirty-two.
 *
 * <h2>Why {@code ItemStack} and not the items</h2>
 *
 * <p>Every drinkable wants this and none of them can be reached from one place by going down:
 * {@code PotionItem}, {@code MilkBucketItem} and {@code OminousBottleItem} each override
 * {@code getUseDuration} to return thirty-two, and {@code HoneyBottleItem} overrides it again.
 * Injecting into {@code Item#getUseDuration} would catch none of them, because an override does
 * not call super. {@code ItemStack#getUseDuration(LivingEntity)} is the single funnel &mdash; the
 * one method every caller in the game goes through, from {@code startUsingItem} to
 * {@code isBlocking} &mdash; so one injection covers all four items and every future liquid.</p>
 *
 * <p>And it is on both sides for free: the client computes the same duration when the synced
 * use flag arrives, from the same method, so the animation and the countdown agree without
 * anything being sent.</p>
 *
 * <h2>How a liquid is recognised</h2>
 *
 * <p>{@code ItemStack#getUseAnimation()} against {@code UseAnim.DRINK} &mdash; the same question
 * {@code GuardHandler#isEating} already asks, and the same one {@code docs/api-trampas.md}
 * records as the only discriminator left in 1.21.1, since {@code Item#isEdible} went away in
 * 1.20.5. Asking "is the player using an item" would also be true of a shield, which is
 * {@code UseAnim.BLOCK} and must keep vanilla's own timing.</p>
 *
 * <p>Verified with {@code javap} against {@code neoforge-21.1.250-merged.jar}:</p>
 *
 * <pre>
 *   public int getUseDuration(net.minecraft.world.entity.LivingEntity);
 *       descriptor: (Lnet/minecraft/world/entity/LivingEntity;)I
 * </pre>
 *
 * <p>Solid food is deliberately untouched: {@code DESIGN.md} section 5 sets a timing only for
 * liquids, and solids keep vanilla's thirty-two.</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDrinkDurationMixin {

    @Inject(method = "getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("HEAD"), cancellable = true)
    private void cortescombat$drinkTicks(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getUseAnimation() == UseAnim.DRINK) {
            cir.setReturnValue(ServerConfig.drinkTicks());
        }
    }
}
