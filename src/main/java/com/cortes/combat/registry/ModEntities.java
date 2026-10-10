package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.entity.SlashWave;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Las entidades del mod, que hasta ahora eran <b>ninguna</b>.
 *
 * <p>El mod tenía cero {@code EntityType} propios y eso era una decisión de forma, no un olvido:
 * todo lo que se movía se resolvía con estado y con partículas. La onda de filo rompe esa regla a
 * propósito, y la razón está escrita en {@link SlashWave}: recorre hasta doce bloques, tiene que
 * poder frenarse contra una pared, y el rival tiene que poder verla venir para cubrirse.</p>
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    CortesCombat.MODID);

    /**
     * La onda de filo.
     *
     * <p>El tamaño (0,6) es el de la caja con la que se la puede golpear y con la que barre; el
     * ancho real del tajo lo aplica la lógica al inflar el segmento barrido, y crece con el nivel.
     * {@code updateInterval(1)} porque una cosa que viaja a un bloque por tick y se dibuja cada dos
     * ticks se vería a saltos, y {@code clientTrackingRange(8)} alcanza para que el que la tiene que
     * esquivar la vea venir.</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SlashWave>> SLASH_WAVE =
            ENTITY_TYPES.register("slash_wave", () -> EntityType.Builder
                    .<SlashWave>of(SlashWave::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("slash_wave"));

    private ModEntities() {
    }
}
