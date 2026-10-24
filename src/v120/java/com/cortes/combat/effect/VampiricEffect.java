package com.cortes.combat.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The 1.20.1 body of Desvio Vampirico (thief half). Same stolen ceiling, older modifier API.
 *
 * <p>Same move as {@link DrainedEffect}: constructor registration with a stable UUID,
 * per-level amount through {@code getAttributeModifierValue}. Un corazon por nivel, diez
 * segundos iguales en los cuatro niveles. (⌒‿⌒)</p>
 */
public class VampiricEffect extends MobEffect {

    /** Un corazón por nivel, en puntos de vida. En Minecraft un corazón son dos. */
    public static final double PER_LEVEL = 2.0D;

    /** Diez segundos, iguales en los cuatro niveles. */
    public static final int DURATION_TICKS = 200;

    private static final String MODIFIER_UUID = UUID.nameUUIDFromBytes(
            "cortescombat:vampiric".getBytes(StandardCharsets.UTF_8)).toString();

    public VampiricEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9B1B3A);
        addAttributeModifier(Attributes.MAX_HEALTH, MODIFIER_UUID, PER_LEVEL,
                AttributeModifier.Operation.ADDITION);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return PER_LEVEL * (Math.max(0, amplifier) + 1);
    }
}
