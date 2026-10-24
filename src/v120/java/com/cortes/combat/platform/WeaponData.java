package com.cortes.combat.platform;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponTables;
import com.cortes.combat.item.ArcabuzItem;
import com.cortes.combat.item.CortesWeaponItem;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.core.weapon.AmmoProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.state.ArcabuzState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The 1.20.1 twin of the item-data facade. Same questions, answered without components.
 *
 * <p><b>Statics come from the item class, dynamics from NBT, and the tables cover the
 * rest</b> -- which is exactly the three layers the shared side already had (component, pack,
 * table), minus the first. A registration-time component was a claim the item made about
 * itself ("I am a ropera"), and the claim is still written down: it now lives in the item's
 * own class ({@code ArcabuzItem} is an arcabuz the way a class is anything) instead of in a
 * component the game attaches. The read side asks the class and the write side never existed
 * for statics, so nothing is lost and nothing is invented.</p>
 *
 * <p>Charge, reach, special, knockback, viewmodel and ammo have no per-stack source here and
 * return null, the way a stack without those components always did: the callers fall back to
 * tables and packs unchanged. The arcabuz's chamber is the one genuinely mutable value and
 * rides in the {@code cortescombat} sub-tag, encoded with the same codec the attachment used,
 * so a save written by either leg reads the same state. (⌒‿⌒)</p>
 */
public final class WeaponData {

    private WeaponData() {
    }

    public static WeaponProfile weaponProfile(ItemStack stack) {
        if (stack.getItem() instanceof ArcabuzItem) {
            return WeaponProfile.ARCABUZ;
        }
        if (stack.getItem() instanceof CortesWeaponItem weapon) {
            return weapon.family().profile();
        }
        return null;
    }

    public static ChargeProfile chargeProfile(ItemStack stack) {
        return null;
    }

    public static ReachProfile reachProfile(ItemStack stack) {
        return null;
    }

    public static SpecialType special(ItemStack stack) {
        return null;
    }

    public static KnockbackProfile knockbackProfile(ItemStack stack) {
        return null;
    }

    public static ReachProfile shotReach(ItemStack stack) {
        if (stack.getItem() instanceof ArcabuzItem) {
            return WeaponTables.shotReach(WeaponProfile.ARCABUZ);
        }
        return null;
    }

    public static GuardProfile guardProfile(ItemStack stack) {
        if (stack.getItem() instanceof ArcabuzItem) {
            return GuardProfile.NONE;
        }
        if (stack.getItem() instanceof CortesWeaponItem weapon) {
            return weapon.family().guard();
        }
        return null;
    }

    public static com.cortes.combat.viewmodel.ViewModelProfile viewmodelProfile(ItemStack stack) {
        return null;
    }

    public static AmmoProfile ammoProfile(ItemStack stack) {
        return null;
    }

    /**
     * Whether two stacks are the same item with the same data.
     *
     * <p>1.20.1 spells the data "tags": same question as the shared twin, same answer.</p>
     */
    public static boolean sameStack(ItemStack stack, ItemStack other) {
        return ItemStack.isSameItemSameTags(stack, other);
    }

    public static ArcabuzState arcabuzState(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag().getCompound("cortescombat");
        if (!root.contains("arcabuz")) {
            return null;
        }
        return ArcabuzState.CODEC.parse(NbtOps.INSTANCE, root.get("arcabuz")).result().orElse(null);
    }

    public static void setArcabuzState(ItemStack stack, ArcabuzState state) {
        CompoundTag root = stack.getOrCreateTag().getCompound("cortescombat");
        root.put("arcabuz", ArcabuzState.CODEC.encodeStart(NbtOps.INSTANCE, state)
                .getOrThrow(false, s -> {
                }));
        stack.getOrCreateTag().put("cortescombat", root);
    }

    /**
     * Registration-time claims the 1.20.1 item cannot carry on itself. The read side above
     * asks the item class instead, so these are deliberate no-ops and not forgotten writes:
     * writing NBT here would stamp every fresh stack with defaults its table row already
     * provides, and a default written down is a default that can go stale.
     */
    public static Item.Properties tagArcabuz(Item.Properties properties) {
        return properties;
    }

    public static Item.Properties tagWeapon(Item.Properties properties, WeaponFamily family) {
        return properties;
    }
}
