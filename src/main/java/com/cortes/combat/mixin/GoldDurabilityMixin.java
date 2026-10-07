package com.cortes.combat.mixin;

import com.cortes.combat.core.weapon.WeaponMaterial;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's gold lasts as long as this game says it does.
 *
 * <h2>Why this needs a mixin at all</h2>
 *
 * <p>Because a durability is <b>baked into the item at registration</b>. {@code ToolItem} and
 * {@code SwordItem} call {@code Properties.durability(tier.getUses())} once, at startup, and from
 * then on the number lives in the {@code max_damage} data component. There is no NeoForge event for
 * it, no attribute modifier that reaches it, and the tier enum is Mojang's own -- so the only door
 * left is the one place every reader goes through.</p>
 *
 * <p><b>And it is one comparison, because only gold is different.</b> Our own thirty weapons read
 * {@link WeaponMaterial} directly and already have the new curve; the six vanilla tiers are
 * otherwise untouched -- wood, stone, iron, diamond and netherite keep Mojang's numbers, and
 * vanilla has no copper in 1.21.1. So this mixin exists for exactly one material, and it says so
 * rather than looping over a table that would be five no-ops and one real change.</p>
 *
 * <h2>Why it reads the constant instead of writing 190</h2>
 *
 * <p>Because a copy of a number is a second truth, and this project has paid for that three times.
 * {@link WeaponMaterial#GOLDEN} is where the design says what gold is worth, the tooltip reads it,
 * the item's own durability reads it, and this reads it -- so raising it again moves all four
 * together instead of leaving the vanilla golden sword a patch behind.</p>
 *
 * <h2>The target, and the round this cost</h2>
 *
 * <p><b>{@code ItemStack#getMaxDamage()}, no arguments -- and it is on {@code ItemStack} and not on
 * {@code Item}.</b> The first version of this file mixed into {@code Item.getMaxDamage(ItemStack)}
 * because that is what the decompiled sources show, and the game crashed at bootstrap with
 * {@code could not find any targets matching 'getMaxDamage' in net.minecraft.world.item.Item}.
 * {@code javap} on the merged jar is what settled it: {@code Item} declares no method with
 * {@code Damage} in its name at all, and {@code ItemStack} declares {@code public int
 * getMaxDamage()}.</p>
 *
 * <p><b>So the lesson is the project's own, paid again:</b> the sources jar is a readable
 * <em>view</em> and the runtime class is the truth. {@code javap} before a mixin, always -- a
 * decompiled method that does not exist at runtime is not a near miss, it is a hard crash on the
 * bootstrap thread with a stack trace that names the mixin and not the file that guessed.</p>
 *
 * <p>Mixing here also covers everything at once: the durability bar, the anvil, the tooltip and
 * {@code isDamageableItem} all read this one method.</p>
 */
@Mixin(ItemStack.class)
public abstract class GoldDurabilityMixin {

    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void cortescombat$goldLastsLonger(CallbackInfoReturnable<Integer> cir) {
        // `(Object)` because a mixin's `this` is the mixin class at compile time and has no declared
        // relation to the target. At runtime `this` IS the stack, which is the whole point of a
        // mixin -- and the cast is the one line that says so.
        ItemStack self = (ItemStack) (Object) this;
        if (!(self.getItem() instanceof TieredItem tiered) || tiered.getTier() != Tiers.GOLD) {
            return;
        }
        cir.setReturnValue(WeaponMaterial.GOLDEN.uses());
    }
}
