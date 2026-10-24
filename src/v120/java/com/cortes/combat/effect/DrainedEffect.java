package com.cortes.combat.effect;

import com.cortes.combat.enchant.CombatEnchantRules;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The 1.20.1 body of Desvio Vampirico (victim half). Same ceiling cut, older modifier API.
 *
 * <p>1.20.1 has no {@code createModifiers}: the modifier registers in the constructor with a
 * stable name-derived UUID, and the per-level amount comes out of
 * {@code getAttributeModifierValue}, which is the hook 1.20.1 calls with the amplifier.
 * Same formula as the shared twin ({@code PER_LEVEL * (max(0, amplifier) + 1)}), same
 * operation ({@code ADDITION}), same attribute. The floor rule ({@link #canDrain}) is pure
 * and shared, untouched. (⌒‿⌒)</p>
 */
public class DrainedEffect extends MobEffect {

    /** Un corazón por nivel, en puntos de vida, y en negativo. */
    public static final double PER_LEVEL = -2.0D;

    /** Diez segundos, los mismos que dura el robo del otro lado. */
    public static final int DURATION_TICKS = 200;

    private static final String MODIFIER_UUID = UUID.nameUUIDFromBytes(
            "cortescombat:drained".getBytes(StandardCharsets.UTF_8)).toString();

    public DrainedEffect() {
        super(MobEffectCategory.HARMFUL, 0x5C1024);
        addAttributeModifier(Attributes.MAX_HEALTH, MODIFIER_UUID, PER_LEVEL,
                AttributeModifier.Operation.ADDITION);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return PER_LEVEL * (Math.max(0, amplifier) + 1);
    }

    /**
     * Si este cuerpo puede pagar el robo sin quedar con el techo por debajo de uno.
     */
    public static boolean canDrain(float maxHealth, int amplifier) {
        return CombatEnchantRules.canDrain(maxHealth, amplifier);
    }
}
