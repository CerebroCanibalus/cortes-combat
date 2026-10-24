package com.cortes.combat.enchant;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.state.Adrenalina;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The 1.20.1 wiring of Adrenalina. Same dagger, older event.
 *
 * <p>1.20.1 has no incoming event, so the reduction lands on {@code LivingHurtEvent} --
 * the spike's own assignment for damage mods. It is the same moment that matters: before
 * armour, before the number is applied, so the hit that would have killed does not.
 * A hit the i-frame gate would have dropped never reaches this event, and there is no
 * reduction to apply to it. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class AdrenalinaHandler {

    private AdrenalinaHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
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
