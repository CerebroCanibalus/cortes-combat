package com.cortes.combat.particle;

import com.cortes.combat.registry.ModParticleTypes;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * The 1.20.1 body of the hit-readout options. Same record, same two fields, older wire format.
 *
 * <p>Same move as {@link SlashParticleOptions}: no {@code StreamCodec} on this leg, so the record
 * writes itself and reads itself back through a {@code Deserializer}. Same field names and
 * defaults as the shared twin, so the command form means the same thing on both legs. (⌒‿⌒)</p>
 */
public record HurtParticleOptions(int rgb, boolean heavy) implements ParticleOptions {

    public static final Codec<HurtParticleOptions> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("rgb").forGetter(HurtParticleOptions::rgb),
                    Codec.BOOL.optionalFieldOf("heavy", false).forGetter(HurtParticleOptions::heavy)
            ).apply(instance, HurtParticleOptions::new));

    public static final ParticleOptions.Deserializer<HurtParticleOptions> DESERIALIZER =
            new ParticleOptions.Deserializer<HurtParticleOptions>() {
                @Override
                public HurtParticleOptions fromCommand(ParticleType<HurtParticleOptions> type,
                        StringReader reader) throws CommandSyntaxException {
                    int readRgb = reader.readInt();
                    boolean readHeavy = reader.canRead() && reader.readBoolean();
                    return new HurtParticleOptions(readRgb, readHeavy);
                }

                @Override
                public HurtParticleOptions fromNetwork(ParticleType<HurtParticleOptions> type,
                        FriendlyByteBuf buf) {
                    return new HurtParticleOptions(buf.readInt(), buf.readBoolean());
                }
            };

    /** El caso común: un golpe normal, del color que sea. */
    public static HurtParticleOptions of(int rgb) {
        return new HurtParticleOptions(rgb, false);
    }

    /** El golpe pesado: mismo color, otra textura. */
    public static HurtParticleOptions heavy(int rgb) {
        return new HurtParticleOptions(rgb, true);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticleTypes.HURT.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(rgb);
        buf.writeBoolean(heavy);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%d %s", rgb, heavy);
    }
}
