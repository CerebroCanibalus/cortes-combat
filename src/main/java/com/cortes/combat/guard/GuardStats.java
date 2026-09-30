package com.cortes.combat.guard;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.registry.ModComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

/**
 * Resolves how much guard an item is worth, and which item is providing it.
 *
 * <p>Three layers, in the same spirit as {@code WeaponStats}: the item's own component first,
 * then a sensible default for the vanilla item kinds, then nothing. The last layer is what makes
 * a weapon from another mod do something reasonable without a line of code here, and what makes
 * an item that cannot guard say so instead of inheriting a shield's three points.</p>
 *
 * <p>The item comes back alongside the profile because the stagger is a vanilla item cooldown,
 * and a cooldown belongs to an item. Returning a profile with no idea which stack it describes
 * would mean the handler had to go looking for the stack again and could pick a different one.</p>
 */
public final class GuardStats {

    private GuardStats() {
    }

    /** A profile with the stack it came from, or {@link GuardProfile#NONE} on an empty hand. */
    public record Resolved(GuardProfile profile, ItemStack item) {

        public boolean canGuard() {
            return profile.canGuard();
        }
    }

    public static GuardProfile of(ItemStack stack) {
        GuardProfile component = stack.get(ModComponents.GUARD_PROFILE);
        if (component != null) {
            return component;
        }
        return vanillaDefaultFor(stack);
    }

    /** The strongest guard the two hands are worth. */
    public static Resolved best(LivingItems items) {
        Resolved main = resolve(items.mainHand());
        Resolved off = resolve(items.offHand());
        return off.canGuard() && off.profile().points() > main.profile().points() ? off : main;
    }

    public static Resolved best(net.minecraft.world.entity.LivingEntity entity) {
        return best(LivingItems.of(entity));
    }

    private static Resolved resolve(ItemStack stack) {
        return new Resolved(of(stack), stack);
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
