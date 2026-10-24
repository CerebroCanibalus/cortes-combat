package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Trabazon: el golpe cargado del martillo que clava al rival en el piso.
 *
 * <h2>Por que son dos eventos y no un tick que resta algo</h2>
 *
 * <p>Porque <b>"puedo saltar" y "puedo correr" se contestan en el momento en que el jugador aprieta la
 * tecla</b>, no cada veinte ticks. Un efecto que restara velocidad no seria lo mismo: el jugador
 * seguiria pudiendo saltar el hueco, solo mas lento, y la mitad que el encantamiento vende es
 * exactamente esa -- <em>no poder cruzar</em>.</p>
 *
 * <h2>Y el salto no se cancela: se devuelve</h2>
 *
 * <p>{@code LivingJumpEvent} no es cancelable -- el impulso ya esta puesto cuando el evento corre. Lo
 * que si se puede es <b>deshacerlo</b>: se le baja el eje Y a cero o menos, y el cuerpo se queda donde
 * estaba. Es la diferencia entre un salto que no sale y un salto que sale y se cae, y la primera es la
 * que el jugador lee como "estoy clavado".</p>
 *
 * <h2>El sprint es del nivel dos</h2>
 *
 * <p>El L1 saca el salto y el L2 saca tambien la carrera, y la pregunta se le hace al
 * <b>amplificador del efecto</b> -- que es el nivel del encantamiento menos uno, como todo efecto de
 * Minecraft. Ver {@code CombatEnchantRules#trabazonBlocksSprint}.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class TrabazonHandler {

    private TrabazonHandler() {
    }

    /** El salto: la mitad que impide cruzar un hueco. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasEffect(ModEffects.trabazon())) {
            return;
        }
        var motion = player.getDeltaMovement();
        if (motion.y > 0.0) {
            player.setDeltaMovement(motion.x, 0.0, motion.z);
            player.hurtMarked = true;
        }
    }

    /** Y la carrera, que es del nivel dos. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.isSprinting()) {
            return;
        }
        var effect = player.getEffect(ModEffects.trabazon());
        if (effect == null) {
            return;
        }
        if (CombatEnchantRules.trabazonBlocksSprint(effect.getAmplifier() + 1)) {
            player.setSprinting(false);
        }
    }
}
