package com.cortes.combat.attack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import com.cortes.combat.CortesCombat;

/**
 * One point off every sword.
 *
 * <h2>Why the gate is a tag and not a class</h2>
 *
 * <p>Because {@code SwordItem} is not the question. A mod's sword is usually a {@code TieredItem}
 * or its own class entirely, and {@code deriveProfile} has already answered "is this a sword" by
 * asking {@link ItemTags#SWORDS} &mdash; so asking the same tag here is <b>one definition of the
 * word, asked twice</b>, rather than a second way of recognising it that could disagree.</p>
 *
 * <h2>Why it reads the existing modifier instead of writing a table</h2>
 *
 * <p>Six materials, six numbers that all happen to be one lower than six other numbers. Writing
 * them out would be a second copy of a table vanilla already has, and the copy would be wrong the
 * day somebody adds a seventh material or another mod ships a sword that reads its damage
 * differently. <b>Subtracting one from whatever the item already says</b> cannot drift: it is a
 * delta, and a delta has no opinion about what it is a delta of.</p>
 *
 * <h2>Why it cannot go below zero</h2>
 *
 * <p>Because a mod's sword may declare less than one point of damage, and a negative attack damage
 * turns a weapon into a healer with a swing animation. Zero is the floor and it is reached by
 * clamping rather than by hoping.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SwordRebalance {

    /** The point taken off. One, and it is one: the design says one. */
    public static final float REMOVED =
            com.cortes.combat.weapon.RebalanceMath.SWORD_REMOVED;

    private SwordRebalance() {
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (!event.getItemStack().is(ItemTags.SWORDS)) {
            return;
        }
        // **Over the default list, not over the event's own.** The event hands out a builder and
        // removing from a list that is being iterated by the very caller that fills it is how you
        // get a concurrent modification with no error message worth reading.
        for (ItemAttributeModifiers.Entry entry : new ArrayList<>(event.getDefaultModifiers().modifiers())) {
            if (entry.attribute() != Attributes.ATTACK_DAMAGE
                    || entry.modifier().id() != Item.BASE_ATTACK_DAMAGE_ID) {
                continue;
            }
            // **Through `reducedOf`, and not through the arithmetic inline.** The test for the sword's
            // numbers walks `reduced(...)`, and for a long round that was a tested method with no
            // caller: the event computed the same thing separately and agreed by coincidence.
            // Verified by sabotage -- removing the subtraction from the event left the whole suite
            // green, because the tested function and the live one were two copies of a delta.
            event.removeModifier(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, reducedOf(entry),
                            AttributeModifier.Operation.ADD_VALUE),
                    entry.slot());
            return;
        }
    }

    /**
     * What the reduction does to a given offset, without a stack.
     *
     * <p>Pure and public so the test can walk vanilla's six materials through it and say what each
     * one ends up at. The event path is untestable here &mdash; it needs a stack and a game
     * &mdash; so this is where the arithmetic is asserted, and the event calls it rather than
     * repeating it.</p>
     */
    public static float reduced(float offset) {
        return com.cortes.combat.weapon.RebalanceMath.swordReduced(offset);
    }

    /** The same, off a modifier. The one the event uses, so there is only one of them. */
    private static float reducedOf(ItemAttributeModifiers.Entry entry) {
        return reduced((float) entry.modifier().amount());
    }

    /** Kept out of the class body so the import list above is not a suggestion. */
    static List<ItemAttributeModifiers.Entry> defaultsOf(ItemAttributeModifiers modifiers) {
        return modifiers.modifiers();
    }
}
