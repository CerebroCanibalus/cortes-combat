package com.cortes.combat.platform;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraft.world.item.ItemStack;

import com.google.common.collect.Multimap;
import java.util.Map;

/**
 * <b>Los dos n&uacute;meros que el tooltip muestra, le&iacute;dos donde el juego los
 * calcula.</b> Da&ntilde;o = 1 + la suma de ADDITION sobre <code>ATTACK_DAMAGE</code> en MAINHAND;
 * velocidad = 4 + la suma sobre <code>ATTACK_SPEED</code>. La escala del tooltip no es
 * decoraci&oacute;n: son los n&uacute;meros contra los que el due&ntilde;o calibra a ojo, y una
 * capa autom&aacute;tica que clasifique en otra escala clasifica contra un arma que no existe.
 *
 * <p>Cuerpo 1.20.1: ac&aacute; el <code>ItemStack</code> s&iacute; devuelve un Multimap propio
 * (<code>getAttributeModifiers(EquipmentSlot)</code>, verificado con javap sobre el jar mapeado
 * 47.4.28) y las operaciones todav&iacute;a se llaman ADDITION.</p>
 */
public final class WeaponAttr {

    private WeaponAttr() {
    }

    /** The two numbers, already on the tooltip scale. */
    public record Hand(float damage, float speed) {
    }

    public static Hand of(ItemStack stack) {
        float damage = 1.0F;
        float speed = 4.0F;
        Multimap<Attribute, AttributeModifier> mods = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (Map.Entry<Attribute, AttributeModifier> entry : mods.entries()) {
            if (entry.getValue().getOperation() != AttributeModifier.Operation.ADDITION) {
                continue;
            }
            Attribute attribute = entry.getKey();
            if (attribute == Attributes.ATTACK_DAMAGE) {
                damage += (float) entry.getValue().getAmount();
            } else if (attribute == Attributes.ATTACK_SPEED) {
                speed += (float) entry.getValue().getAmount();
            }
        }
        return new Hand(damage, speed);
    }
}
