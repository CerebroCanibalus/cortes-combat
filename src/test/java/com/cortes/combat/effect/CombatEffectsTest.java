package com.cortes.combat.effect;

import com.cortes.combat.enchant.CombatEnchantRules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los números de los efectos de la tanda 3, y las tres cuentas que pueden mentir.
 *
 * <h2>Qué se prueba acá, y por qué no se prueba el efecto entero</h2>
 *
 * <p><b>{@code MobEffect} no está en el classpath de los tests.</b> Un efecto no se puede instanciar
 * acá -- ni {@code createModifiers}, que recibe un {@code BiConsumer<Holder<Attribute>, ...>} y esos
 * son tipos de Minecraft. Así que cada cuenta vive en una función <b>estática y pura</b> y el
 * envoltorio la llama: {@link AntiHealingEffect#reduction}, {@link DrainedEffect#canDrain} y
 * {@link QuebrantoEffect#armourReduction}.</p>
 *
 * <p><b>Y las tres son justo las que pueden mentir</b>: la reducción de curación tiene un techo que
 * no puede alcanzar, el piso del robo decide si un encantamiento mata o no, y el número de stacks de
 * Quebranto es el único lugar donde el nivel de un efecto entra en una cuenta.</p>
 */
class CombatEffectsTest {

    @Test
    @DisplayName("Anti-curacion: 15 % por nivel, y **nunca llega a bloquear la curacion entera**")
    void antiHealingNeverBlocksEverything() {
        assertEquals(0.15F, CombatEnchantRules.antiHealingReduction(0), 1.0E-6, "nivel I");
        assertEquals(0.30F, CombatEnchantRules.antiHealingReduction(1), 1.0E-6, "nivel II");
        assertEquals(0.45F, CombatEnchantRules.antiHealingReduction(2), 1.0E-6, "nivel III");
        assertEquals(0.60F, CombatEnchantRules.antiHealingReduction(3), 1.0E-6, "nivel IV, el tope del archivo");

        for (int amplifier = 0; amplifier < 20; amplifier++) {
            assertTrue(CombatEnchantRules.antiHealingReduction(amplifier) < 1.0F,
                    "**con el amplifier " + amplifier + " la reduccion llega a uno o mas**: una "
                            + "curacion bloqueada del todo no es este encantamiento, y ademas el "
                            + "jugador no tendria forma de saber por que la comida dejo de servir");
        }
    }

    @Test
    @DisplayName("**Desvio Vampirico: el robo no puede dejar el techo por debajo de uno**")
    void theDrainHasAFloor() {
        assertTrue(CombatEnchantRules.canDrain(20.0F, 0), "un cuerpo sano paga el robo del nivel I");
        assertTrue(CombatEnchantRules.canDrain(20.0F, 3), "y el del nivel IV");

        assertFalse(CombatEnchantRules.canDrain(4.0F, 1),
                "**con cuatro de techo y un robo de cuatro el cuerpo quedaria en cero**, y bajarle el "
                        + "techo a cero es una ejecucion gratuita sobre cualquiera que este tocado -- "
                        + "el veto C7 del proyecto. El robo no se cobra y el desvio queda sin premio, "
                        + "que es mejor que un boton de borrar");
        assertFalse(CombatEnchantRules.canDrain(1.0F, 0), "y con uno de techo tampoco, en ningun nivel");
        assertTrue(CombatEnchantRules.canDrain(5.0F, 1),
                "**con cinco de techo y dos de robo si**: quedan tres, y el piso es uno");
    }

    @Test
    @DisplayName("**Quebranto: el nivel entra en el numero, y el tope son tres stacks**")
    void quebrantoUsesTheAmplifier() {
        assertEquals(-0.08D, CombatEnchantRules.quebrantoArmour(0), 1.0E-9, "un stack: ocho por ciento");
        assertEquals(-0.16D, CombatEnchantRules.quebrantoArmour(1), 1.0E-9, "dos stacks");
        assertEquals(-0.24D, CombatEnchantRules.quebrantoArmour(2), 1.0E-9, "tres stacks");
        assertEquals(-0.24D, CombatEnchantRules.quebrantoArmour(9), 1.0E-9,
                "**y mas de tres no baja mas**: el tope vive en el efecto, porque el numero de stacks "
                        + "ES el amplifier y un amplifier sin tope convertiria tres golpes en un "
                        + "porcentaje arbitrario");
        assertEquals(CombatEnchantRules.QUEBRANTO_MAX_STACKS * -0.08D, CombatEnchantRules.quebrantoArmour(2), 1.0E-9,
                "y el tope declarado es el que la cuenta usa: dos constantes para lo mismo se "
                        + "desincronizan");
    }
}
