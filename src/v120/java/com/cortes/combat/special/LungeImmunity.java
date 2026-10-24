package com.cortes.combat.special;

import com.cortes.combat.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The 1.20.1 wiring of the lunge's immunity. Same effect, older event.
 *
 * <p>{@code LivingAttackEvent}, cancelled: the top of {@code hurt()}, before the i-frame
 * gate -- the same pre-gate moment the spike assigns to parry-consume and lunge-immunity
 * alike. The field and the effect stay two independent paths to the one rule. (⌒‿⌒)</p>
 */
public final class LungeImmunity {

    private LungeImmunity() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(ModEffects.lunge())) {
            return;
        }
        event.setCanceled(true);
    }
}
