package com.cortes.combat.client;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.pose.WeaponPose;
import com.cortes.combat.pose.WeaponPoseViews;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelProfile;
import com.cortes.combat.weaponpack.ResolvedWeapon;
import com.cortes.combat.weaponpack.WeaponPackPoses;
import com.cortes.combat.weaponpack.WeaponPackStore;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Which viewmodel profile a held item draws: the pack's pose when it names one that exists,
 * else the profile default.
 *
 * <p>The single door for all three builders ({@code ItemViewModel}, the local body dispatcher
 * and the remote clocks), so a pack pose cannot move the hand in first person and leave the
 * arm standing still in third -- that exact split already cost the sabre's parry once, with
 * the tracks living in two places instead of one.</p>
 *
 * <p>A pose id without a colon is ours ({@code sword} means {@code cortescombat:sword}):
 * the only poses that ship with the mod live under our namespace, and a pack author naming
 * their own writes the full id. A named pose with no file behind it warns once and draws
 * the default -- the design's rule for a missing resource is warning with origin, never
 * silence and never a crash. (⌒‿⌒)</p>
 */
public final class PackPose {

    private static final Set<String> WARNED = new HashSet<>();

    private PackPose() {
    }

    /**
     * The profile to evaluate this frame for this item.
     *
     * @param stack   what is held; empty or packless falls straight through to the default
     * @param profile the profile default, for when no pack pose claims the item
     */
    public static ViewModelProfile profileFor(ItemStack stack, WeaponProfile profile) {
        if (stack == null || stack.isEmpty()) {
            return ViewModelDefaults.of(profile);
        }
        ResolvedWeapon pack = WeaponPackStore.resolve(stack);
        if (pack == null || pack.pose() == null || pack.pose().isBlank()) {
            return ViewModelDefaults.of(profile);
        }
        String raw = pack.pose().trim();
        String id = raw.contains(":") ? raw
                : com.cortes.combat.CortesCombat.MODID + ":" + raw;
        WeaponPose pose = WeaponPackPoses.get(id);
        if (pose == null) {
            if (WARNED.add(id)) {
                com.cortes.combat.CortesCombat.LOGGER.warn(
                        "[weaponpack] pose '{}' nombrada y sin archivo en assets/*/poses/ -- "
                                + "se dibuja el perfil por defecto",
                        id);
            }
            return ViewModelDefaults.of(profile);
        }
        return WeaponPoseViews.toProfile(pose);
    }
}
