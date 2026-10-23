package com.cortes.combat.platform;

import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Every component this mod reads or writes on an item stack, named once, here.
 *
 * <p><b>Reads return null when the stack carries nothing, exactly like a missing component
 * did.</b> Every call site already falls back to tables (and packs) on null, so the facade
 * never decides, it only delivers: the same {@code null} a hand without a component always
 * gave. Writes only exist for the arcabuz's mutable state; everything else is baked at
 * registration and read back here.</p>
 *
 * <p>The 1.20.1 twin answers from the item class and from NBT instead, because 1.20.1 has no
 * components. Registration-time components ({@code ArcabuzItem.properties},
 * {@code CortesWeaponItem.propertiesFor}) go through {@code tagArcabuz}/{@code tagWeapon} so
 * the 1.20.1 leg can skip what it cannot spell. (⌒‿⌒)</p>
 */
public final class WeaponData {

    private WeaponData() {
    }

    public static com.cortes.combat.core.weapon.WeaponProfile weaponProfile(ItemStack stack) {
        return stack.get(ModComponents.WEAPON_PROFILE);
    }

    public static com.cortes.combat.core.weapon.ChargeProfile chargeProfile(ItemStack stack) {
        return stack.get(ModComponents.CHARGE_PROFILE);
    }

    public static com.cortes.combat.core.weapon.ReachProfile reachProfile(ItemStack stack) {
        return stack.get(ModComponents.REACH_PROFILE);
    }

    public static com.cortes.combat.core.weapon.SpecialType special(ItemStack stack) {
        return stack.get(ModComponents.SPECIAL);
    }

    public static com.cortes.combat.core.weapon.KnockbackProfile knockbackProfile(ItemStack stack) {
        return stack.get(ModComponents.KNOCKBACK_PROFILE);
    }

    public static com.cortes.combat.core.weapon.ReachProfile shotReach(ItemStack stack) {
        return stack.get(ModComponents.SHOT_REACH);
    }

    public static com.cortes.combat.core.weapon.GuardProfile guardProfile(ItemStack stack) {
        return stack.get(ModComponents.GUARD_PROFILE);
    }

    public static com.cortes.combat.viewmodel.ViewModelProfile viewmodelProfile(ItemStack stack) {
        return stack.get(ModComponents.VIEWMODEL_PROFILE);
    }

    public static com.cortes.combat.core.weapon.AmmoProfile ammoProfile(ItemStack stack) {
        return stack.get(ModComponents.AMMO_PROFILE);
    }

    public static com.cortes.combat.state.ArcabuzState arcabuzState(ItemStack stack) {
        return stack.get(ModComponents.ARCABUZ);
    }

    public static void setArcabuzState(ItemStack stack, com.cortes.combat.state.ArcabuzState state) {
        stack.set(ModComponents.ARCABUZ, state);
    }

    public static Item.Properties tagArcabuz(Item.Properties properties) {
        return properties
                .component(ModComponents.WEAPON_PROFILE.get(),
                        com.cortes.combat.core.weapon.WeaponProfile.ARCABUZ)
                .component(ModComponents.SHOT_REACH.get(),
                        com.cortes.combat.item.WeaponTables.shotReach(
                                com.cortes.combat.core.weapon.WeaponProfile.ARCABUZ))
                .component(ModComponents.GUARD_PROFILE.get(),
                        com.cortes.combat.core.weapon.GuardProfile.NONE);
    }

    public static Item.Properties tagWeapon(Item.Properties properties, WeaponFamily family) {
        return properties
                .component(ModComponents.WEAPON_PROFILE.get(), family.profile())
                .component(ModComponents.GUARD_PROFILE.get(), family.guard());
    }
}
