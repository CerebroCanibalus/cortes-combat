package com.cortes.combat.effect;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * What a mob wearing Revelacion leaves behind: one diamond, and only one.
 *
 * <p>The request makes the field a source of more than damage -- a hostile mob that dies marked
 * drops a diamond, so clearing an area with the reactor pays for itself. <b>The mark is the
 * criterion, not the killer:</b> a mob that was branded and then finished off by anything still
 * drops it, because what the player did was leave it marked. Reading the effect at death is the
 * whole test -- {@code LivingDropsEvent} runs while the effect is still on the body, and
 * {@code magic} as the killing blow would have been an indistinguishable source anyway.</p>
 *
 * <p>One event, one list, no new loot table: the diamond is appended to the drops vanilla has
 * already decided on, so a mob's own loot still falls normally.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RevelationDrops {

    private RevelationDrops() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDropsEvent event) {
        if (!event.getEntity().hasEffect(ModEffects.revelation())) {
            return;
        }
        event.getDrops().add(new ItemEntity(event.getEntity().level(),
                event.getEntity().getX(), event.getEntity().getY() + 0.5,
                event.getEntity().getZ(), new ItemStack(Items.DIAMOND)));
    }
}
