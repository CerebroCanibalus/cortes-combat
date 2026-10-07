package com.cortes.combat.attack;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.weapon.MaceFall;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * The maza's damage, because vanilla's number is not this game's number.
 *
 * <h2>Why an event and not the registration</h2>
 *
 * <p>Because there is nothing to change at registration. {@code Items.MACE} is a single item with
 * no tier &mdash; there is no wooden maza and no netherite one, unlike every other tool &mdash; so
 * its {@code MaceItem.createAttributes()} is baked into the item at startup and the only handle
 * left is the attribute list itself.</p>
 *
 * <p>{@code ItemAttributeModifierEvent} fires <b>every time the attributes of a stack are
 * queried</b>, for any reader. That is the part that makes this the right door rather than a lucky
 * one: the server resolves damage through {@code getAttributeValue} and the tooltip reads the same
 * component, so both sides move together with no second place to update and no way for the number
 * on screen to disagree with the number that lands.</p>
 *
 * <h2>What it does, and the speed it now moves</h2>
 *
 * <p>It also touches {@code ATTACK_SPEED}, which it used to document as untouched: the playtest
 * asked for the maza to swing ten percent slower on top of the damage change, and the maza is the
 * one weapon whose attribute Mojang owns and we cannot edit at registration.</p>
 *
 * <p><b>The slowdown is derived from the speed and not from the offset</b>, and that is not
 * pedantry. {@code ATTACK_SPEED} is an offset from the player's own 4.0, so scaling the
 * <em>offset</em> by 0.9 gives -3.06, which is 0.94 attacks a second -- <b>faster</b> than
 * vanilla's 0.6, from a number that reads as "ten percent slower". Vanilla's is -3.4, verified in
 * 1.21.1 against the arcabuz's 1.4 and the warhammer's 1.05.</p>
 *
 * <h2>The number</h2>
 *
 * <p>{@link MaceFall#BASE_DAMAGE} minus the player's own one, which is now fifteen minus one. The
 * highest in the catalogue used to be the netherite ropera's ten; the maza is the weapon that pays
 * for height, and the playtest moved its base up five and its ceiling to fifty.</p>
 *
 * <p><b>The bus argument is written out for the reason the rest of the project writes them
 * out:</b> a class with no {@code @SubscribeEvent} method throws when it is registered, and taking
 * the whole mod down at construction is a worse report than a wrong number.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MaceRebalance {

    /** Vanilla's offset. Present only so the comment above can be checked against it. */
    public static final float VANILLA_OFFSET = 5.0F;

    /** What this game gives it instead. */
    public static final float OFFSET = MaceFall.BASE_DAMAGE - 1.0F;

    /** The player's own attack speed, which a weapon's attribute is measured from. */
    public static final float PLAYER_BASE_ATTACK_SPEED = 4.0F;

    /** Vanilla's maza: -3.4, which is 0.6 swings a second. The slowest thing in the game. */
    public static final float VANILLA_SPEED_OFFSET = -3.4F;

    /** And ten percent slower than that, which is the playtest's number. */
    public static final float SPEED_SLOWDOWN = 0.9F;

    /** The maza's attack speed here: vanilla's own, ten percent slower. 0.54 a second. */
    public static final float ATTACK_SPEED =
            (PLAYER_BASE_ATTACK_SPEED + VANILLA_SPEED_OFFSET) * SPEED_SLOWDOWN;

    /**
     * The same as an offset, which is what the attribute takes.
     *
     * <p>See the class comment: the subtraction is the difference between "ten percent slower" and
     * ten percent faster, and it is the one line that cannot be read off the constant's name.</p>
     */
    public static final float SPEED_OFFSET = ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED;

    private MaceRebalance() {
    }

    /**
     * Swap the maza's attack damage and speed for this game's.
     *
     * <p>Remove-then-add rather than replace, because the two ids have to stay the same for
     * unequipping to clean up after itself: {@link ItemAttributeModifierEvent} warns about it by
     * name, and a stale modifier left on the player is the kind of thing that looks like a balance
     * change nobody made.</p>
     *
     * <p><b>Both attributes through the same door, on purpose.</b> The tooltip reads
     * {@code Item.BASE_ATTACK_SPEED_ID} off the stack and the interval reads
     * {@code getAttributeValue}, so one event keeps the number on screen and the number that runs
     * in step -- which is the reason this is an event at all and not a registration hook.</p>
     */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (!event.getItemStack().is(Items.MACE)) {
            return;
        }
        event.removeModifier(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
        event.addModifier(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, OFFSET,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
        event.removeModifier(Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID);
        event.addModifier(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, SPEED_OFFSET,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }
}
