package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.platform.ResourceIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

/**
 * The 1.20.1 twin of the two custom damage types. Same keys, same helpers.
 *
 * <p>1.21.1 builds the source with {@code DamageSources#source(key, entity)}; 1.20.1 builds it
 * directly from the type's holder, which is what that helper did internally. Player as both
 * the causing and the direct entity, exactly like {@code playerAttack}, so kill credit,
 * enchantments and death messages read the same two fields. (⌒‿⌒)</p>
 */
public final class ModDamageTypes {

    public static final ResourceKey<DamageType> WARHAMMER_IGNORED_ARMOUR = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceIds.rl("warhammer_ignored_armour"));

    public static final ResourceKey<DamageType> AXE_CHARGED = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceIds.rl("axe_charged"));

    private ModDamageTypes() {
    }

    public static DamageSource warhammerIgnoredArmour(Player player) {
        return new DamageSource(holderFor(player, WARHAMMER_IGNORED_ARMOUR), player, player);
    }

    public static DamageSource axeCharged(Player player) {
        return new DamageSource(holderFor(player, AXE_CHARGED), player, player);
    }

    private static net.minecraft.core.Holder<DamageType> holderFor(Player player,
            ResourceKey<DamageType> key) {
        return player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
    }

    public static boolean isAxeCharged(DamageSource source) {
        return source != null && source.is(AXE_CHARGED);
    }
}
