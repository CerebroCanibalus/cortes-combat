package com.cortes.combat.effect;

import com.cortes.combat.enchant.CombatEnchantRules;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The 1.20.1 body of Quebranto. Same armour cut, older modifier API.
 *
 * <p>Same move as {@link DrainedEffect}: constructor registration, per-level amount through
 * {@code getAttributeModifierValue}. The number itself stays pure and shared
 * ({@link #armourReduction}), so a test that checks the math never loads Minecraft --
 * exactly the split the shared twin documents. (⌒‿⌒)</p>
 */
public class QuebrantoEffect extends MobEffect {

    /** Ocho por ciento de armadura menos por stack, como fracción del total. */
    public static final double REDUCTION_PER_STACK = -0.08D;

    /** Tres stacks: veinticuatro por ciento con el efecto al máximo. */
    public static final int MAX_STACKS = 3;

    private static final String MODIFIER_UUID = UUID.nameUUIDFromBytes(
            "cortescombat:quebranto".getBytes(StandardCharsets.UTF_8)).toString();

    public QuebrantoEffect() {
        super(MobEffectCategory.HARMFUL, 0x5A4A2A);
        addAttributeModifier(Attributes.ARMOR, MODIFIER_UUID, REDUCTION_PER_STACK,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /**
     * La reducción de armadura de este nivel, como fracción, y <b>pura</b>.
     */
    public static double armourReduction(int amplifier) {
        return CombatEnchantRules.quebrantoArmour(amplifier);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return armourReduction(amplifier);
    }
}
