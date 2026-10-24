package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.entity.AntimatterOrb;
import com.cortes.combat.entity.MobCloud;
import com.cortes.combat.entity.MungusShell;
import com.cortes.combat.entity.SlashWave;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

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
    public static final RegistryObject<EntityType<SlashWave>> SLASH_WAVE =
            ENTITY_TYPES.register("slash_wave", () -> EntityType.Builder
                    .<SlashWave>of(SlashWave::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("slash_wave"));

    /**
     * La nube de moscas de Rey de las Moscas.
     *
     * <p><b>Tamaño chico y a proposito.</b> El volumen que lastima lo infla la logica contra
     * {@code MOSCAS_RADIUS}, no la caja de la entidad -- una caja grande haria que la nube chocara
     * contra paredes que no deberia tocar, y es {@code noPhysics} justo para que no choque con
     * nada.</p>
     *
     * <p>{@code clientTrackingRange(10)} y no 8: el alcance del disparo es de ocho bloques, y el
     * jugador que lo ve venir esta mas lejos que el que lo recibe.</p>
     */
    public static final RegistryObject<EntityType<MobCloud>> MOB_CLOUD =
            ENTITY_TYPES.register("mob_cloud", () -> EntityType.Builder
                    .<MobCloud>of(MobCloud::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("mob_cloud"));

    /**
     * El orbe de antimateria de la pala.
     *
     * <p>Caja chica ({@code 0.4}) porque el orbe tiene que <b>chocar</b> con las paredes -- es un
     * proyectil y su viaje termina contra algo -- pero no tan grande como para engancharse en el
     * borde de un bloque al pasar por un hueco de un bloque de ancho.</p>
     */
    public static final RegistryObject<EntityType<AntimatterOrb>> ANTIMATTER_ORB =
            ENTITY_TYPES.register("antimatter_orb", () -> EntityType.Builder
                    .<AntimatterOrb>of(AntimatterOrb::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("antimatter_orb"));

    /**
     * El Firestone enorme de Mungus.
     *
     * <p><b>Caja de uno, como la bola de fuego del ghast</b>, y el tipo existe para que el cliente
     * simule la misma clase y no una bola recta: ver {@link MungusShell}, que tiene el porque
     * escrito entero. Un proyectil lo tickean las dos mitades.</p>
     */
    public static final RegistryObject<EntityType<MungusShell>> MUNGUS_SHELL =
            ENTITY_TYPES.register("mungus_shell", () -> EntityType.Builder
                    .<MungusShell>of(MungusShell::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("mungus_shell"));

    private ModEntities() {
    }
}
