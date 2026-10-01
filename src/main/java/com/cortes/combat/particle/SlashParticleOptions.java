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
 * The one piece of data a slash carries: which sprite to draw, and how long to wait first.
 *
 * <p>A name rather than the profile enum on purpose. The particle is spawned by the server and
 * travels to every client, and the thing every client needs is the file, not the weapon. Sending
 * an enum ordinal across the wire also means a reordered enum silently repaints everyone's
 * particles, and there is no upside to it.</p>
 *
 * <p><b>CustomParticleOptions does not exist in 1.21.1</b>, so this implements
 * {@link ParticleOptions} directly. It has exactly two members to write and the codecs are both
 * mandatory, and forgetting either is a load-time crash rather than a compile error.</p>
 *
 * <h2>Why the delay is here and not in a scheduler</h2>
 *
 * <p>The sabre's full charge lands twice, and two cuts that arrive on the same tick are one cut
 * with extra brightness -- which is precisely the complaint: the player could tell the damage was
 * doubled and could not see why.</p>
 *
 * <p>The obvious implementation is a server-side scheduler that fires the second slash two ticks
 * later. The problem is that the game has no such thing to hang it on: there is no delayed-task
 * queue in 1.21.1, and the version of the particle that crosses the wire already carries a
 * lifetime. <b>A particle that knows it is late is the same mechanism as a particle that knows it
 * is dying</b>, one field cheaper, and it needs no per-player state on a server that would then
 * have to clean it up if the player logged out mid-swing.</p>
 *
 * <p>The cost is honest and worth naming: the two cuts are timed by the <em>client's</em> clock,
 * so on a laggy connection they can land together anyway, and the damage is not gated on the
 * second slash arriving. Both cuts are cosmetic; the number was always the server's.</p>
 *
 * <p><b>No arithmetic lives on this record.</b> It implements {@code ParticleOptions}, so a bare
 * JVM cannot load it and every number on it would be untestable -- a test that touched it fails
 * with {@code cannot access ParticleOptions}, which is the game being absent rather than a value
 * being wrong. The timing is {@link SlashTiming}, which is pure, and this only carries it.</p>
 */
public record SlashParticleOptions(String sprite, int delayTicks) implements ParticleOptions {

    /** How long a slash waits before it is drawn. Zero is every ordinary hit. */
    public static final int NO_DELAY = SlashTiming.NO_DELAY;

    public static final MapCodec<SlashParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("sprite").forGetter(SlashParticleOptions::sprite),
                    Codec.INT.optionalFieldOf("delay", NO_DELAY)
                            .forGetter(SlashParticleOptions::delayTicks)
            ).apply(instance, SlashParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlashParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SlashParticleOptions::sprite,
                    ByteBufCodecs.VAR_INT, SlashParticleOptions::delayTicks,
                    SlashParticleOptions::new);

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
}