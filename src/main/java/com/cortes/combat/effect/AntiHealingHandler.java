package com.cortes.combat.effect;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Anti-curación: la única cosa que hace Cicatrizador, y por qué vive acá.
 *
 * <h2>Un evento y no un efecto con comportamiento</h2>
 *
 * <p>La reducción tiene que ocurrir <b>cuando la curación entra</b>, no en un tick que pasa. Un
 * {@code applyEffectTick} corre cada veinte ticks y llegaría tarde: la poción ya curó. El evento se
 * dispara <b>dentro</b> de {@code LivingEntity#heal}, con el número todavía en la mano, y por eso es
 * el único lugar donde "cura menos" significa algo.</p>
 *
 * <p><b>Y por eso cubre todos los sources</b>: pociones, manzana dorada, la regeneración natural y
 * la regen de comida de este mod pasan por {@code heal()} y por lo tanto por acá. Un encantamiento
 * que sólo bloqueara las pociones sería uno que el jugador aprende a esquivar comiendo.</p>
 *
 * <h2>Se multiplica y no se resta</h2>
 *
 * <p>{@code amount * (1 - reducción)} y no {@code amount - algo}: la reducción es una fracción del
 * número que llega, así que una curación chica y una grande pierden lo mismo <em>en proporción</em>.
 * Restar un valor fijo convertiría el efecto en un bloqueo total para las curas chicas -- la regen de
 * un punto por tick de este mod se iría a cero y el jugador no entendería por qué la comida dejó de
 * servir.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class AntiHealingHandler {

    private AntiHealingHandler() {
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        MobEffectInstance effect = event.getEntity().getEffect(ModEffects.antiHealing());
        if (effect == null) {
            return;
        }
        float reduction = AntiHealingEffect.reduction(effect.getAmplifier());
        event.setAmount(event.getAmount() * (1.0F - reduction));
    }
}
