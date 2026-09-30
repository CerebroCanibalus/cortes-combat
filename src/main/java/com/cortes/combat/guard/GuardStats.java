package com.cortes.combat.guard;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.registry.ModComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

/**
 * Resolves how much guard an item is worth.
 *
 * <p>Three layers, in the same spirit as {@code WeaponStats}: the item's own component first,
 * then a sensible default for the vanilla item kinds, then nothing. The last layer is what makes
 * a weapon from another mod do something reasonable without a line of code here, and what makes
 * an item that simply cannot guard say so instead of inheriting a shield's three points.</p>
 */
public final class GuardStats {

    private GuardStats() {
    }

    public static GuardProfile of(ItemStack stack) {
        GuardProfile component = stack.get(ModComponents.GUARD_PROFILE);
        if (component != null) {
            return component;
        }
        return vanillaDefaultFor(stack);
    }

    /** The strongest guard the item in hand or in the off hand is worth. */
    public static GuardProfile ofBest(LivingItems items) {
        GuardProfile main = of(items.mainHand());
        GuardProfile off = of(items.offHand());
        return off.canGuard() && off.points() > main.points() ? off : main;
    }

    /** What a vanilla item means without anyone saying so in a data file. */
    private static GuardProfile vanillaDefaultFor(ItemStack stack) {
        if (stack.is(Items.SHIELD)) {
            return GuardProfile.SHIELD;
        }
        if (stack.getItem() instanceof SwordItem) {
            return GuardProfile.SWORD;
        }
        return GuardProfile.NONE;
    }

    /** A pair of hands, so the resolver does not need a LivingEntity and stays testable. */
    public record LivingItems(ItemStack mainHand, ItemStack offHand) {

        public static LivingItems of(net.minecraft.world.entity.LivingEntity entity) {
            return new LivingItems(entity.getMainHandItem(), entity.getOffhandItem());
        }
    }
}
