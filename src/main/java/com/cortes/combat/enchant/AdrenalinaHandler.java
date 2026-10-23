package com.cortes.combat.enchant;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.state.Adrenalina;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Adrenalina: la daga que entra a los intercambios que no puede ganar.
 *
 * <h2>Por que la reduccion va en {@code LivingIncomingDamageEvent} y no despues del golpe</h2>
 *
 * <p>Porque <b>el momento es el encantamiento</b>. Una curacion despues del golpe llega tarde: el
 * daño ya se cobro, la muerte ya pudo pasar, y el jugador que entra a un intercambio perdido sigue
 * perdiendolo. {@code LivingIncomingDamageEvent} corre <em>antes</em> de que el daño se aplique, asi
 * que el golpe que te iba a matar no te mata -- y esa diferencia no se ve en un numero, se ve en
 * sobrevivir.</p>
 *
 * <h2>Por que la puerta es la daga</h2>
 *
 * <p>Porque este hook vive en el camino <em>generico</em> del daño: cualquier arma pasa por aca. Un
 * datapack que cambie los items del encantamiento, o un pack que se equivoque de perfil, le daria
 * Adrenalina a una espada -- y una espada con un tercio del daño entrante de menos pelea distinto.
 * La puerta cuesta una linea y es la que el diseño pide.</p>
 *
 * <h2>Y el L2 no sube el numero: duplica la frecuencia</h2>
 *
 * <p>Los dos niveles reducen mas (25 % → 35 %) y ademas el cooldown se parte al medio (30 s → 15 s).
 * En una pelea larga la frecuencia vale mas que los diez puntos, y el daño ahorrado que se acumula
 * para el proximo golpe es la mitad <em>ofensiva</em> del nivel dos: la daga se guarda lo que no
 * recibio y lo devuelve.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class AdrenalinaHandler {

    private AdrenalinaHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (WeaponStats.of(player.getMainHandItem()).profile() != WeaponProfile.DAGGER) {
            return;
        }
        int level = CombatEnchantments.level(player.level().registryAccess(),
                player.getMainHandItem(), CombatEnchantments.ADRENALINA);
        if (level < 1) {
            return;
        }
        long now = player.level().getGameTime();
        Adrenalina state = CombatState.adrenalina(player);
        if (!state.isReady(now)) {
            return;
        }

        float reduction = CombatEnchantRules.adrenalinaReduction(level);
        float saved = event.getAmount() * reduction;
        event.setAmount(event.getAmount() - saved);
        // **La cuenta se arma aca y la cobra el golpe.** Solo el nivel dos la guarda: el L1 es
        // defensivo puro, y darle la mitad ofensiva al nivel uno dejaria al segundo sin nada que
        // agregar salvo diez puntos de reduccion.
        CombatState.setAdrenalina(player,
                state.spent(now, CombatEnchantRules.adrenalinaCooldown(level))
                        .banking(level >= CombatEnchantRules.ADRENALINA_MAX_LEVEL ? saved : 0.0F));
        // Sin particulas propias: el brillo de Velocidad que el jugador ya conoce es el de la pocion,
        // y agregar particulas encima taparia justo lo que tiene que leer -- de donde le vino.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                CombatEnchantRules.ADRENALINA_SPEED_TICKS,
                CombatEnchantRules.adrenalinaSpeedAmplifier(level), false, false, false));
    }
}
