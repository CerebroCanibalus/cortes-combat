package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * El puente de los números que no son componentes: {@code damage} y {@code attack_speed} del
 * YAML, que en 1.21.1 son atributos y sólo se pueden tocar por este evento.
 *
 * <p><b>LOWEST a propósito.</b> El orden respecto a {@code SwordRebalance} y {@code HoeRebalance}
 * no está garantizado por registro -- está garantizado por prioridad, y la última en correr es
 * la que pisa. El pack declara el número entero del ítem, así que corre la última y reemplaza lo
 * que los rebalances hubieran hecho; lo que el pack <em>no</em> declara no se toca y los
 * rebalances siguen aplicando con normalidad. Esa es la regla de §14.7 entera en dos líneas.
 *
 * <p>La aritmética vive en {@link #damageAmount} y {@link #speedAmount}: puras, sin stack, y por
 * eso testeadas -- el evento en sí necesita un juego corriendo.
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WeaponPackAttributes {

    /** El atributo de daño arranca en 1: modifier = total - 1. */
    private static final float BASE_DAMAGE = 1.0F;
    /** El atributo de velocidad arranca en 4.0: vanilla's sword es -2.4 por esto. */
    private static final float BASE_SPEED = 4.0F;

    private WeaponPackAttributes() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ResolvedWeapon pack = WeaponPackStore.resolve(event.getItemStack());
        if (pack == null) {
            return;
        }
        if (pack.damage() != null) {
            replace(event, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID,
                    damageAmount(pack.damage()));
        }
        if (pack.attackSpeed() != null) {
            replace(event, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID,
                    speedAmount(pack.attackSpeed()));
        }
    }

    /**
     * Del YAML al modifier: el pack escribe <b>corazones</b> (lo que el jugador lee en un
     * corazón y medio), el atributo vive en puntos -- dos por corazón -- y arranca en
     * {@value #BASE_DAMAGE}.
     */
    static float damageAmount(float hearts) {
        return hearts * 2.0F - BASE_DAMAGE;
    }

    /** Velocidad en toques por segundo, contra el default del atributo (4.0). */
    static float speedAmount(float perSecond) {
        return perSecond - BASE_SPEED;
    }

    /**
     * Reemplaza el modifier base si el ítem lo trae por defecto, y si no lo trae, lo pone en la
     * mano principal. Sobre los <em>defaults</em> del evento y no sobre su lista propia: la lista
     * que se itera es la que llena el mismo caller, y mutarla en el acto es una
     * ConcurrentModification sin mensaje que valga.
     */
    private static void replace(ItemAttributeModifierEvent event,
                                net.minecraft.core.Holder<Attribute> attribute,
                                net.minecraft.resources.ResourceLocation id, float amount) {
        for (ItemAttributeModifiers.Entry entry
                : new ArrayList<>(event.getDefaultModifiers().modifiers())) {
            if (entry.attribute() == attribute && entry.modifier().id() == id) {
                event.removeModifier(attribute, id);
                event.addModifier(attribute,
                        new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE),
                        entry.slot());
                return;
            }
        }
        event.addModifier(attribute,
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }
}
