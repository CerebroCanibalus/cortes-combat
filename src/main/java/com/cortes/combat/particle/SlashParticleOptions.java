package com.cortes.combat.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;


/**
 * The one piece of data a slash carries: which sprite to draw.
 *
 * <p>A name rather than the profile enum on purpose. The particle is spawned by the server and
 * travels to every client, and the thing every client needs is the file, not the weapon. Sending
 * an enum ordinal across the wire also means a reordered enum silently repaints everyone's
 * particles, and there is no upside to it.</p>
 *
 * <p><b>{@code CustomParticleOptions} does not exist in 1.21.1</b>, so this implements
 * {@link ParticleOptions} directly. It has exactly two members to write and the codecs are both
 * mandatory, and forgetting either is a load-time crash rather than a compile error.</p>
 */
public record SlashParticleOptions(String sprite) implements ParticleOptions {

    public static final MapCodec<SlashParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("sprite").forGetter(SlashParticleOptions::sprite)
            ).apply(instance, SlashParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlashParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SlashParticleOptions::sprite,
                    SlashParticleOptions::new);

    public SlashParticleOptions {
        // An empty name would reach the atlas lookup and produce a null sprite, which fails later
        // and further away. There is no legitimate way to ask for one, so refuse it here.
        if (sprite == null || sprite.isBlank()) {
            sprite = SlashSprite.FALLBACK;
        }
    }

    @Override
    public ParticleType<?> getType() {
        return com.cortes.combat.registry.ModParticleTypes.SLASH.get();
    }
}