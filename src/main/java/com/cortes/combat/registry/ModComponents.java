package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The four data components that carry every gameplay number.
 *
 * <p>Kept as four separate components rather than one fat one on purpose: a foreign mod can
 * change the reach of an item without also having to restate, and potentially break, its
 * charge settings. See ARCHITECTURE.md section 2.</p>
 */
public final class ModComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CortesCombat.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponProfile>> WEAPON_PROFILE =
            component("weapon_profile", WeaponProfile.TYPE);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ReachProfile>> REACH_PROFILE =
            component("reach_profile", ReachProfile.CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChargeProfile>> CHARGE_PROFILE =
            component("charge_profile", ChargeProfile.CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpecialType>> SPECIAL =
            component("special", SpecialType.TYPE);

    private ModComponents() {
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> component(
            String name, com.mojang.serialization.Codec<T> codec) {
        return DATA_COMPONENT_TYPES.register(name, () -> DataComponentType.<T>builder()
                .persistent(codec)
                .networkSynchronized(ByteBufCodecs.fromCodec(codec))
                .build());
    }
}
