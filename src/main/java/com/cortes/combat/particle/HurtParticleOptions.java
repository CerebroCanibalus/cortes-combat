package com.cortes.combat.particle;

import com.cortes.combat.registry.ModParticleTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * La lectura de un golpe, en un punto: <b>de qué color</b> y <b>de qué tamaño</b>.
 *
 * <h2>Por qué el color viaja en la partícula</h2>
 *
 * <p>El arte es <b>una textura de 4x4 en escala de grises</b> y el color se aplica al dibujar. La
 * alternativa era un tipo por color -- y con los colores de {@link HurtPalette} eso son una docena
 * de tipos, una docena de registros y una docena de proveedores, que es exactamente el error que
 * {@code ModParticleTypes} ya documenta haber evitado con las armas: <em>treinta registros en vez de
 * uno</em>.</p>
 *
 * <p><b>Es el patrón de {@link SlashParticleOptions}, con el color en el lugar del sprite.</b> Allá
 * el dato que viaja es "qué arma", acá es "qué pasó con el golpe" -- y en los dos casos el dato es
 * chico, tiene codecs y el tipo es uno.</p>
 *
 * <h2>Los dos campos, y por qué son dos</h2>
 *
 * <p>{@code rgb} es el color del split ({@link HurtPalette}), y {@code heavy} elige la textura: el
 * golpe normal o el pesado. <b>Son ejes distintos y por eso son campos distintos</b>: un golpe
 * cargado puede entrar entero (rojo, pesado) o ser parado por la armadura (azul, pesado), así que
 * meterlos en un solo enum sería una tabla de casos en vez de una multiplicación.</p>
 *
 * <p><b>Ninguna aritmética vive acá.</b> Implementa {@code ParticleOptions}, o sea que una JVM
 * pelada no puede cargarla y todo número suyo sería intestable. Los colores son constantes de
 * {@link HurtPalette}, que es pura.</p>
 */
public record HurtParticleOptions(int rgb, boolean heavy) implements ParticleOptions {

    public static final MapCodec<HurtParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.INT.fieldOf("rgb").forGetter(HurtParticleOptions::rgb),
                    Codec.BOOL.optionalFieldOf("heavy", false).forGetter(HurtParticleOptions::heavy)
            ).apply(instance, HurtParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, HurtParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, HurtParticleOptions::rgb,
                    ByteBufCodecs.BOOL, HurtParticleOptions::heavy,
                    HurtParticleOptions::new);

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
}
