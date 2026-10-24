package com.cortes.combat.attack;

import java.util.ArrayList;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.UUID;

import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.weapon.RebalanceMath;

/**
 * The 1.20.1 wiring of the hoe's raise. Same delta, older event.
 *
 * <p>Same move as the sword's twin: match vanilla's damage modifier by UUID, replace it
 * with the raised amount from {@link RebalanceMath#hoeRaised}. No clamp, like the shared
 * twin -- adding to a negative baseline is the point. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class HoeRebalance {

    /** Vanilla's damage modifier id, read out of `Item.<clinit>` bytecode: the field is protected on 1.20.1. */
    private static final UUID BASE_ATTACK_DAMAGE_UUID =
            UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    /** The points added, and it is one and a half: the design's number. */
    public static final float ADDED = RebalanceMath.HOE_ADDED;

    private HoeRebalance() {
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) {
            return;
        }
        if (!event.getItemStack().is(ItemTags.HOES)) {
            return;
        }
        for (AttributeModifier modifier
                : new ArrayList<>(event.getModifiers().get(Attributes.ATTACK_DAMAGE))) {
            if (!modifier.getId().equals(BASE_ATTACK_DAMAGE_UUID)) {
                continue;
            }
            event.removeModifier(Attributes.ATTACK_DAMAGE, modifier);
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                            raised((float) modifier.getAmount()),
                            AttributeModifier.Operation.ADDITION));
            return;
        }
    }

    /**
     * What the raise does to a given offset, without a stack.
     */
    public static float raised(float offset) {
        return RebalanceMath.hoeRaised(offset);
    }
}
