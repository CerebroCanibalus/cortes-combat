package com.cortes.combat.platform;

import net.minecraft.world.item.ItemStack;

/**
 * <b>The shield answer, as a capability and not as a class.</b>
 *
 * <p>Compat-autom&aacute;tica se pregunta "puede este item bloquear", y &eacute;se es un
 * <code>ToolAction</code>: vanilla lo declara en <code>DEFAULT_SHIELD_ACTIONS</code>, y cada
 * mod bien portado lo contesta el suyo. Epic Knights no lo consiguen con Java &mdash; sus
 * escudos extienden <code>Item</code> pelado (verificado en el jar del mod: una clase aparte
 * contesta <code>canPerformAction</code> con el set completo) &mdash; sino con el contrato, y
 * el contrato es el seam.</p>
 *
 * <p>Chequear <code>instanceof ShieldItem</code> se lo saltaba a medio mundo: Spartan, Simply
 * Shields-adjacent y cualquier escudo custom se comportaban como bloqueo de mojang sin unseren
 * aguante ni nuestra postura. Lo quir&uacute;rgico: proteger el contrato.</p>
 */
public final class ShieldSeams {

    private ShieldSeams() {
    }

    /** Whether the item answers what vanilla asks before it lets a shield absorb a hit. */
    public static boolean canShieldBlock(ItemStack stack) {
        return stack.canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK);
    }
}
