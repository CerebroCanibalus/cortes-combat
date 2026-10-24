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
 * The 1.20.1 wiring of the sword's point off. Same delta, older event.
 *
 * <p>1.20.1's {@code ItemAttributeModifierEvent} carries a {@code Multimap} instead of a
 * component: entries are matched by modifier UUID (vanilla's fixed
 * {@code BASE_ATTACK_DAMAGE_UUID}) rather than by resource id, and the replacement keeps
 * the same UUID so unequipping still cleans up after itself. The arithmetic is
 * {@link RebalanceMath#swordReduced}, the single source both legs read. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class SwordRebalance {

    /** Vanilla's damage modifier id, read out of `Item.<clinit>` bytecode: the field is protected on 1.20.1. */
    private static final UUID BASE_ATTACK_DAMAGE_UUID =
            UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    /** The point taken off. One, and it is one: the design says one. */
    public static final float REMOVED = RebalanceMath.SWORD_REMOVED;

    private SwordRebalance() {
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) {
            return;
        }
        if (!event.getItemStack().is(ItemTags.SWORDS)) {
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
                            reduced((float) modifier.getAmount()),
                            AttributeModifier.Operation.ADDITION));
            return;
        }
    }

    /**
     * What the reduction does to a given offset, without a stack.
     */
    public static float reduced(float offset) {
        return RebalanceMath.swordReduced(offset);
    }
}
