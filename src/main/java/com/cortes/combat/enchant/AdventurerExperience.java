package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * Aventurero: la espada que hace que matar rinda más experiencia.
 *
 * <h2>Por qué es un handler propio y no una línea en el motor</h2>
 *
 * <p>Porque <b>el evento no es del golpe</b>. La experiencia de un mob no la decide el ataque: la
 * decide el mob al morir, en {@code LivingEntity#dropExperience}, que corre en un camino que este
 * mod no reemplaza y que no sabe nada de conos, cargas ni cooldowns. Engancharlo en
 * {@code CombatEngine} habría sido cobrar el bonus en el golpe y esperar que el mob muera después
 * &mdash; dos momentos distintos, y el segundo puede no llegar nunca (el mob se ahoga, lo mata otro,
 * se cae).</p>
 *
 * <p>Es el mismo criterio que el resto de los encantamientos de este mod: <b>el hook vive donde el
 * número se cobra</b>, y éste se cobra al morir.</p>
 *
 * <h2>Por qué suma sobre el original y no sobre el actual</h2>
 *
 * <p>{@code getOriginalExperience()} es lo que el mob valía antes de que nadie tocara el número, y
 * {@code getDroppedExperience()} es lo que vale ahora &mdash; que puede haber cambiado si otro mod
 * escuchó el mismo evento antes. Sumar el porcentaje sobre el original hace que este encantamiento
 * valga siempre lo mismo, sin importar el orden en que los mods se registraron.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class AdventurerExperience {

    private AdventurerExperience() {
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null) {
            // Sin jugador no hay arma, y sin arma no hay encantamiento. Un mob que muere por el
            // entorno no le paga a nadie.
            return;
        }
        float share = CombatEnchantRules.aventurero(CombatEnchantments.level(
                killer.level().registryAccess(), killer.getMainHandItem(),
                CombatEnchantments.AVENTURERO));
        if (share <= 0.0F) {
            return;
        }
        event.setDroppedExperience(event.getDroppedExperience()
                + Math.round(event.getOriginalExperience() * share));
    }
}
