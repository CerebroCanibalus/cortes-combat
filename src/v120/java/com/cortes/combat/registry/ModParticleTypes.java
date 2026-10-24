package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.particle.HurtParticleOptions;
import com.cortes.combat.particle.SlashParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

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
    public static final RegistryObject<ParticleType<SlashParticleOptions>> SLASH =
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

    /**
     * The purification plume. A plain, parameterless particle: unlike the slash it carries no
     * sprite name, because it has exactly one sprite, so vanilla's own {@link SimpleParticleType}
     * already implements {@code ParticleOptions} with both codecs and there is nothing to
     * hand-roll. Its animation lives in an {@code .mcmeta} next to the texture.
     */
    public static final RegistryObject<SimpleParticleType> PURIFIED =
            PARTICLE_TYPES.register("purified_effect", () -> new SimpleParticleType(false));

    /**
     * Una gota de sangre.
     *
     * <p>Sin parametros, como la pluma de purificacion: el sprite es uno solo y todo el
     * comportamiento -- el peso, el choque, el fade -- vive en {@link SangreParticle}. El tipo no
     * lleva datos porque no hay nada que decirle que no sea "sangre".</p>
     */
    public static final RegistryObject<SimpleParticleType> SANGRE =
            PARTICLE_TYPES.register("sangre", () -> new SimpleParticleType(false));

    /** Una mosca revoloteando. Sin parametros por la misma razon. */
    public static final RegistryObject<SimpleParticleType> MOSCA =
            PARTICLE_TYPES.register("mosca", () -> new SimpleParticleType(false));

    /**
     * La lectura de un golpe: un punto de color.
     *
     * <p><b>Un tipo y no quince, y es la misma decision que el slash.</b> El color viaja en las
     * opciones, asi que los quince colores del diseno -- el split y las familias de tipo -- son
     * quince {@code HurtParticleOptions} sobre un solo registro. Un tipo por color seria el error
     * que el javadoc de arriba ya documenta haber evitado con las armas: <em>treinta registros en
     * vez de uno</em>.</p>
     *
     * <p><b>Y las DOS texturas -- el golpe normal y el pesado -- viven en el mismo tipo</b>, porque
     * {@code particles/hurt.json} lista las dos y la opcion elige por indice. Es exactamente como
     * {@code slash.json} lista once armas bajo un tipo: la variante es un dato, no un registro.</p>
     */
    public static final RegistryObject<ParticleType<HurtParticleOptions>> HURT =
            PARTICLE_TYPES.register("hurt", () -> new ParticleType<HurtParticleOptions>(false) {
                @Override
                public com.mojang.serialization.MapCodec<HurtParticleOptions> codec() {
                    return HurtParticleOptions.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<
                        ? super net.minecraft.network.RegistryFriendlyByteBuf, HurtParticleOptions>
                        streamCodec() {
                    return HurtParticleOptions.STREAM_CODEC;
                }
            });

    /**
     * El corazoncito verde de la curacion.
     *
     * <p>Sin parametros, como la sangre: el color esta en el arte y todo el comportamiento -- el
     * ascenso, el fade -- vive en {@link com.cortes.combat.particle.CurarParticle}.</p>
     */
    public static final RegistryObject<SimpleParticleType> CURAR =
            PARTICLE_TYPES.register("curar", () -> new SimpleParticleType(false));

    private ModParticleTypes() {
    }
}