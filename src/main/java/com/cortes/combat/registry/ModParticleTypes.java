package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.particle.SlashParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One particle type for every slash in the game.
 *
 * <p>Deliberately one and not thirty. The weapon is data, and the sprite it draws with is
 * carried in the payload, so a new weapon is a new PNG and one line in {@code SlashSprite} and
 * nothing else -- no registration, no provider, no class. Thirty registrations would be the same
 * mistake the item catalogue already avoided.</p>
 *
 * <p>The description JSON that vanilla's own particles use is not needed here: sprite binding for
 * a type registered from code goes through the sprite set handed to the provider, so the only
 * file a weapon has to ship is its texture.</p>
 */
public final class ModParticleTypes {

    /**
     * Registered into the vanilla registry, not a NeoForge one.
     *
     * <p>Particle types moved to vanilla in 1.20 and the key lives on {@code Registries} here.
     * {@code NeoForgeRegistries.Keys} has no particle entry at all, and
     * {@code DeferredRegister.create} takes a {@code ResourceKey}, so the wrong one is a compile
     * error rather than a silent no-op.</p>
     */
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE,
                    CortesCombat.MODID);

    /** The animated slash. One type, one class, every weapon. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<SlashParticleOptions>> SLASH =
            PARTICLE_TYPES.register("slash", () -> new ParticleType<SlashParticleOptions>(false) {
                @Override
                public com.mojang.serialization.MapCodec<SlashParticleOptions> codec() {
                    return SlashParticleOptions.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<
                        ? super net.minecraft.network.RegistryFriendlyByteBuf, SlashParticleOptions>
                        streamCodec() {
                    return SlashParticleOptions.STREAM_CODEC;
                }
            });

    private ModParticleTypes() {
    }
}