package com.cortes.combat.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * The 1.20.1 body of the slash options. Same record, same validation, older wire format.
 *
 * <p>1.21.1 carries the network codec in a {@code StreamCodec} field and the type hands it out
 * through {@code streamCodec()}; 1.20.1 has neither class nor method. Here the record writes
 * itself ({@code writeToNetwork}/{@code writeToString}) and reads itself back through a
 * {@code Deserializer}, which is what the 1.20.1 {@code ParticleType} constructor takes. The
 * bytes are the same two fields in the same order -- UTF-8 sprite, var-int delay -- so a packet
 * captured on one leg would read clean on the other, even though no packet ever crosses legs.
 * (⌒‿⌒)</p>
 *
 * <p>The command codec keeps the shared twin's field names ({@code sprite}, {@code delay}) with
 * the same default, built with {@code RecordCodecBuilder.create} because 1.20.1's
 * {@code ParticleType.codec()} returns a plain {@code Codec}, not a {@code MapCodec}.</p>
 */
public record SlashParticleOptions(String sprite, int delayTicks) implements ParticleOptions {

    /** How long a slash waits before it is drawn. Zero is every ordinary hit. */
    public static final int NO_DELAY = SlashTiming.NO_DELAY;

    public static final Codec<SlashParticleOptions> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("sprite").forGetter(SlashParticleOptions::sprite),
                    Codec.INT.optionalFieldOf("delay", NO_DELAY)
                            .forGetter(SlashParticleOptions::delayTicks)
            ).apply(instance, SlashParticleOptions::new));

    public static final ParticleOptions.Deserializer<SlashParticleOptions> DESERIALIZER =
            new ParticleOptions.Deserializer<SlashParticleOptions>() {
                @Override
                public SlashParticleOptions fromCommand(ParticleType<SlashParticleOptions> type,
                        StringReader reader) throws CommandSyntaxException {
                    String readSprite = reader.readUnquotedString();
                    int readDelay = reader.canRead() ? reader.readInt() : NO_DELAY;
                    return new SlashParticleOptions(readSprite, readDelay);
                }

                @Override
                public SlashParticleOptions fromNetwork(ParticleType<SlashParticleOptions> type,
                        FriendlyByteBuf buf) {
                    return new SlashParticleOptions(buf.readUtf(32767), buf.readVarInt());
                }
            };

    public SlashParticleOptions {
        // An empty name would reach the atlas lookup and produce a null sprite, which fails later
        // and further away. There is no legitimate way to ask for one, so refuse it here.
        if (sprite == null || sprite.isBlank()) {
            sprite = SlashSprite.FALLBACK;
        }
        // A negative delay would be a slash waiting for a moment that has already passed, and the
        // particle's age counter would have to special-case it to avoid reading a sprite set at a
        // negative index. Clamping here means the particle itself stays a plain counter.
        delayTicks = SlashTiming.delayTicks(delayTicks);
    }

    /** The ordinary case: one slash, drawn on arrival. */
    public SlashParticleOptions(String sprite) {
        this(sprite, NO_DELAY);
    }

    /** The same slash, but late. Used for the second cut of a doubled strike. */
    public SlashParticleOptions delayed(int ticks) {
        return new SlashParticleOptions(sprite, ticks);
    }

    @Override
    public ParticleType<?> getType() {
        return com.cortes.combat.registry.ModParticleTypes.SLASH.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(sprite);
        buf.writeVarInt(delayTicks);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %d", sprite, delayTicks);
    }
}
