package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The 1.20.1 wiring of Trabazon. Same pinned rival, older tick.
 *
 * <p>{@code LivingJumpEvent} is unchanged across the versions (verified: same class, same
 * package). The sprint watch rides {@code TickEvent.PlayerTickEvent} gated to {@code END}
 * instead of the 1.21.1 {@code Post} subclass. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
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
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
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
