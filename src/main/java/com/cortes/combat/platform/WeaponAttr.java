package com.cortes.combat.platform;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraft.world.item.ItemStack;

/**
 * <b>Los dos n&uacute;meros que el tooltip muestra, le&iacute;dos donde el juego los
 * calcula.</b> Da&ntilde;o = 1 + la suma de ADD_VALUE sobre <code>ATTACK_DAMAGE</code> en MAINHAND;
 * velocidad = 4 + la suma sobre <code>ATTACK_SPEED</code>. La escala del tooltip no es
 * decoraci&oacute;n: son los n&uacute;meros contra los que el due&ntilde;o calibra a ojo, y una
 * capa autom&aacute;tica que clasifique en otra escala clasifica contra un arma que no existe.
 *
 * <p>Cuerpo 1.21.1: en esta pata el <code>ItemStack</code> no devuelve un mapa propio &mdash;
 * el componente <code>ItemAttributeModifiers</code> vive dentro &mdash; y la lectura que Neo
 * ya refleja (componente + evento) es <code>forEachModifier(EquipmentSlot, ...)</code>,
 * verificada en las fuentes 21.1.250.</p>
 */
public final class WeaponAttr {

    private WeaponAttr() {
    }

    /** The two numbers, already on the tooltip scale. */
    public record Hand(float damage, float speed) {
    }

    public static Hand of(ItemStack stack) {
        float[] damage = {1.0F};
        float[] speed = {4.0F};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (mod.operation() != AttributeModifier.Operation.ADD_VALUE) {
                return;
            }
            if (attr.is(Attributes.ATTACK_DAMAGE)) {
                damage[0] += (float) mod.amount();
            } else if (attr.is(Attributes.ATTACK_SPEED)) {
                speed[0] += (float) mod.amount();
            }
        });
        return new Hand(damage[0], speed[0]);
    }
}
