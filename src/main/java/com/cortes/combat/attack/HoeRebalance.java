package com.cortes.combat.attack;

import java.util.ArrayList;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import com.cortes.combat.CortesCombat;

/**
 * One and a half points onto every hoe.
 *
 * <h2>Why the hoe needs adding to and not rewriting</h2>
 *
 * <p>Vanilla's hoe is the one tool whose attack damage is a <b>penalty</b>: its baseline runs
 * {@code 0, -1, -2, -3, -4} by material, so a netherite hoe takes three points off the player's
 * own one and a wooden one leaves it alone. That is Mojang saying "this is a farm tool", and in
 * this game the hoe is a weapon -- six targets, no charge, a clearing motion -- so the number it
 * starts from is the wrong number and the fix is a delta on top of whatever it already says.</p>
 *
 * <p><b>A delta and not a table, for the reason {@link SwordRebalance} gives:</b> six materials,
 * six numbers, and a copy of them here would be wrong the day somebody adds a seventh or another
 * mod ships a hoe with its own damage. Adding to the modifier the item already carries cannot
 * drift, because it has no opinion about what it is adding to.</p>
 *
 * <h2>Why the gate is the tag</h2>
 *
 * <p>{@code #minecraft:hoes} is what {@code WeaponStats.deriveProfile} already reads to decide the
 * item is a hoe, so this is <b>one definition of the word, asked twice</b> rather than a second
 * way of recognising it that could disagree. A mod's hoe is covered the moment it is tagged.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class HoeRebalance {

    /** The points added, and it is one and a half: the design's number. */
    public static final float ADDED =
            com.cortes.combat.weapon.RebalanceMath.HOE_ADDED;

    private HoeRebalance() {
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (!event.getItemStack().is(ItemTags.HOES)) {
            return;
        }
        // Over a copy of the default list, not the event's own: removing from a list the caller is
        // iterating is a concurrent modification with no useful message.
        for (ItemAttributeModifiers.Entry entry
                : new ArrayList<>(event.getDefaultModifiers().modifiers())) {
            if (entry.attribute() != Attributes.ATTACK_DAMAGE
                    || entry.modifier().id() != Item.BASE_ATTACK_DAMAGE_ID) {
                continue;
            }
            // **Through `raisedOf`, and not the arithmetic inline.** The test walks `raised(...)`
            // and a live copy that computes the same thing separately agrees by coincidence until
            // one of them moves -- which is exactly the shape SwordRebalance's own comment records
            // paying for.
            event.removeModifier(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, raisedOf(entry),
                            AttributeModifier.Operation.ADD_VALUE),
                    entry.slot());
            return;
        }
    }

    /**
     * What the raise does to a given offset, without a stack.
     *
     * <p>Pure and public so a test can walk vanilla's six materials through it and say what each
     * ends up at. No clamp: unlike the sword's subtraction, adding to a negative baseline is the
     * point -- a netherite hoe at {@code -4} becomes {@code -2.5}, and there is no floor to reach
     * because the player's own one is still underneath it.</p>
     */
    public static float raised(float offset) {
        return com.cortes.combat.weapon.RebalanceMath.hoeRaised(offset);
    }

    /** The same, off a modifier. The one the event uses, so there is only one of them. */
    private static float raisedOf(ItemAttributeModifiers.Entry entry) {
        return raised((float) entry.modifier().amount());
    }
}
