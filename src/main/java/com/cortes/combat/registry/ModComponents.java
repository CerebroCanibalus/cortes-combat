package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.mojang.serialization.Codec;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.viewmodel.ViewModelProfile;
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

    /**
     * Which of the arcabuz's five fuse frames the gun is showing, 0 to 4.
     *
     * <p><b>A data component and not a client field, and that is the whole design.</b> The frame has
     * to be the same on every client that can see the gun, and the only value that is guaranteed
     * to be is one the server wrote and the network carried.</p>
     *
     * <p>It exists because a texture <em>animation</em> cannot do this. The drawn art is one
     * 64x320 strip of five frames and a {@code .mcmeta} runs it at whatever rate it is told to,
     * freely, forever -- so the gun's fuse burned down on its own in the inventory whether or not
     * anything was happening. 1.21.1 gives a mod no way to choose a frame of an animated texture,
     * so the strip was cut into five files and the model picks one with a predicate on this
     * component.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            ARCABUZ_FUSE = component("arcabuz_fuse", Codec.INT);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ReachProfile>> REACH_PROFILE =
            component("reach_profile", ReachProfile.CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChargeProfile>> CHARGE_PROFILE =
            component("charge_profile", ChargeProfile.CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpecialType>> SPECIAL =
            component("special", SpecialType.TYPE);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GuardProfile>> GUARD_PROFILE =
            component("guard_profile", GuardProfile.CODEC);

    /**
     * The viewmodel of an item: a list of tracks, resolved once and evaluated per frame.
     *
     * <p>A fifth component, and separate on purpose. The other four are gameplay numbers that
     * the server resolves and that decide damage, reach and cooldown. This one is pure
     * presentation, only the client resolves it, and folding it into one of the others would
     * mean every combat system in the mod had to carry a field it never reads. Presentation data
     * that a server-side change could invalidate is the thing worth keeping apart.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ViewModelProfile>> VIEWMODEL_PROFILE =
            component("viewmodel_profile", ViewModelProfile.CODEC);

    /**
     * How hard the item pushes when it hits.
     *
     * <p>Its own component because the shove is per weapon and the hit is not. A sword, a
     * warhammer and a block in the hand were all pushing identically while the other four
     * components described the same three numbers, and a player who noticed could not change it
     * because there was nowhere to put the value.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KnockbackProfile>> KNOCKBACK_PROFILE =
            component("knockback_profile", KnockbackProfile.CODEC);

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
