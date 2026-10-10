package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

/**
 * Los sonidos que un weapon pack declara, registrados como {@code SoundEvent} -- la misma
 * ventana que {@link WeaponPackItems} usa para los ítems, y la misma razón.
 *
 * <p><b>Por qué registrar y no jugarlos crudos:</b> {@code SimpleSoundInstance} acepta un
 * {@code ResourceLocation} suelto y en cliente local eso bastaría, pero los sonidos de combate
 * salen del <em>servidor</em> ({@code level.playSound} -> {@code ClientboundSoundPacket}, que
 * serializa el <em>holder</em> por id en el registro). Un id que no está en
 * {@code Registries.SOUND_EVENT} no puede viajar. Registrado una vez acá, el packet funciona y
 * el resource pack del autor sólo tiene que aportar el {@code sounds.json} y el .ogg.
 *
 * <p><b>Sin duplicados:</b> un id que ya existe (otro mod lo registró) se salta con un warning
 * -- dos registradores para el mismo id es la forma más rápida de un crash de registry.
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WeaponPackSounds {

    private WeaponPackSounds() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!Registries.SOUND_EVENT.equals(event.getRegistryKey())) {
            return;
        }
        for (WeaponPackSpec pack : WeaponPackStore.packs()) {
            for (WeaponPackSpec.Entry entry : pack.weapons()) {
                WeaponPackSpec.Sounds sounds = entry.sounds();
                if (sounds == null) {
                    continue;
                }
                for (String raw : List.of(sounds.swing(), sounds.hit(), sounds.charged())) {
                    registerOne(event, pack.origin(), entry.identity(), raw);
                }
            }
        }
    }

    private static void registerOne(RegisterEvent event, String origin, String identity,
                                    String raw) {
        if (raw == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            CortesCombat.LOGGER.warn("[weaponpack] {} :: {} -- sonido '{}' no es un id válido",
                    origin, identity, raw);
            return;
        }
        if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            CortesCombat.LOGGER.info("[weaponpack] {} :: {} -- el sonido '{}' ya existe en el "
                    + "registro (¿de otro mod?): se usa tal cual y no se registra de nuevo",
                    origin, identity, raw);
            return;
        }
        event.register(Registries.SOUND_EVENT, id,
                () -> SoundEvent.createVariableRangeEvent(id));
    }
}
