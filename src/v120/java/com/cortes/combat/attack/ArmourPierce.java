package com.cortes.combat.attack;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * El gemelo de 1.20.1 de {@code ArmourPierce}. Misma cuenta, distinta escritura.
 *
 * <p><b>Lo que cambia y por que.</b> En 1.20.1 el identificador de un modificador es un
 * {@code UUID} con un nombre, y no una {@code ResourceLocation}; y la operacion que multiplica
 * el total se llama {@code MULTIPLY_TOTAL}, no {@code ADD_MULTIPLIED_TOTAL}. <b>Las dos cuentas
 * son la misma</b>: las dos multiplican el total por (1 + cantidad), asi que el martillo que
 * ignora la mitad de la armadura ignora la mitad en las dos patas.</p>
 *
 * <p>El UUID sale del nombre con {@code nameUUIDFromBytes}, igual que los de Contragolpe y
 * Estocada: estable entre reinicios y unico del mod.</p>
 */
public final class ArmourPierce {

    /** El id del modificador del martillo, estable entre reinicios. */
    private static final java.util.UUID WARHAMMER_ID = java.util.UUID.nameUUIDFromBytes(
            "cortescombat:warhammer_pierce".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private ArmourPierce() {
    }

    /**
     * El modificador que deja la armadura del objetivo en la fraccion que este golpe ignora.
     *
     * @param tuning los numeros del martillo. Null es "sin penetracion"
     * @param armour el atributo de armadura del objetivo. Null es "no hay armadura que bajar"
     * @return el modificador a poner y sacar, o {@code null} cuando no hay nada que hacer
     */
    public static AttributeModifier warhammer(WarhammerTuning tuning, AttributeInstance armour) {
        if (tuning == null || armour == null) {
            return null;
        }
        float penetration = tuning.armourPenetration();
        if (penetration <= 0.0F || penetration >= 1.0F) {
            // Cero: la armadura cuenta entera. Uno: la fuente del golpe ya la ignora entera, y
            // un modificador de -1 seria una segunda cuenta de lo mismo.
            return null;
        }
        return new AttributeModifier(WARHAMMER_ID, "warhammer_pierce", -penetration,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
