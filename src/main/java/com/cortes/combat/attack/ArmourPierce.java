package com.cortes.combat.attack;

import com.cortes.combat.platform.ResourceIds;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * La armadura que un golpe ignora <b>por fraccion</b>, y solo por ese golpe.
 *
 * <h2>Por que un modificador y no una cuenta propia</h2>
 *
 * <p>La cuenta de la armadura la hace vanilla, en
 * {@code CombatRules.getDamageAfterAbsorb}, y una copia de esa formula es una formula que se
 * desincroniza en la proxima version sin que nadie lo note: pegaria un numero que nadie eligio.
 * Lo que si se puede hacer es <b>preguntarle a la armadura del objetivo</b> con menos puntos, y
 * dejar que vanilla haga la cuenta como la hace siempre. Es el mismo mecanismo que usan
 * Contragolpe y Estocada Perforante, y por el mismo motivo.</p>
 *
 * <h2>Y por que es transitorio</h2>
 *
 * <p>El modificador se pone antes del golpe y se saca en un {@code finally}. Una armadura que
 * queda baja para siempre es el bug que este camino no puede dejar atras, y un {@code finally} es
 * lo unico que lo cubre cuando {@code hurt} tira por un mod de terceros. (⌒‿⌒)</p>
 *
 * <h2>El id es fijo y no se inventa por golpe</h2>
 *
 * <p>El id identifica al modificador dentro del atributo, y dos modificadores con el mismo id
 * en el mismo atributo son un error. Con un id fijo, el segundo golpe del mismo objetivo lo
 * encuentra ya puesto si el primero no lo saco -- y por eso el que lo saca esta en un
 * {@code finally}.</p>
 *
 * <h2>Y por que esta clase tiene un gemelo</h2>
 *
 * <p>El identificador de un modificador cambio entre versiones: en 1.21 es una
 * {@code ResourceLocation} y en 1.20.1 es un {@code UUID} con un nombre. La cuenta es la misma en
 * las dos, pero el constructor no, y el nombre de la operacion tampoco
 * ({@code ADD_MULTIPLIED_TOTAL} contra {@code MULTIPLY_TOTAL}: misma cuenta, distinto nombre).
 * El gemelo de 1.20.1 esta en {@code src/v120}.</p>
 */
public final class ArmourPierce {

    /**
     * El id del modificador del martillo.
     *
     * <p>Un id fijo y no uno por golpe: se saca con el mismo objeto que se puso, y una
     * identidad nueva por golpe seria una entrada nueva en el mapa de atributos del objetivo
     * cada vez -- una fuga lenta de memoria por pegar.</p>
     */
    private static final net.minecraft.resources.ResourceLocation WARHAMMER_ID =
            ResourceIds.rl("warhammer_pierce");

    private ArmourPierce() {
    }

    /**
     * El modificador que deja la armadura del objetivo en la fraccion que este golpe ignora.
     *
     * @param tuning los numeros del martillo. Null es "sin penetracion"
     * @param armour el atributo de armadura del objetivo. Null es "no hay armadura que bajar"
     * @return el modificador a poner y sacar, o {@code null} cuando no hay nada que hacer: una
     *         penetracion de cero no baja nada, y una de cien la resuelve la fuente del golpe,
     *         que ignora la armadura entera sin tocar el atributo
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
        return new AttributeModifier(WARHAMMER_ID, -penetration,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
