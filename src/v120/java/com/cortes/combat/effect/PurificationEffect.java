package com.cortes.combat.effect;

import com.cortes.combat.particle.PurifiedParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The 1.20.1 body of Purification. Same burn, three older APIs.
 *
 * <ol>
 *   <li>Tick: {@code void applyEffectTick} + {@code isDurationEffectTick}.</li>
 *   <li>Modifier: 1.20.1 has no {@code ResourceLocation}-keyed modifier and no
 *       {@code createModifiers}. The modifier registers in the constructor with a stable
 *       name-derived UUID ({@code cortescombat:purification_damage}), and the amount stays
 *       exact because this one never scaled with the level anyway.</li>
 *   <li>Netherite check: 1.20.1 materials are singletons, so {@code ==} instead of
 *       {@code Holder.is}.</li>
 * </ol>
 * (⌒‿⌒)
 */
public class PurificationEffect extends MobEffect {

    /** How hard each tick lands. Small on purpose: this is a debuff, not a hit. */
    public static final float DAMAGE = 0.5F;

    /** Twice a second, so a five second effect is ten small hits. */
    public static final int INTERVAL_TICKS = 10;

    /** How long the weapon leaves it on, in ticks. */
    public static final int DURATION_TICKS = 100;

    /** The cut to the victim's attack damage, as a fraction of its total. */
    public static final double DAMAGE_CUT = -0.30D;

    private static final String MODIFIER_UUID = UUID.nameUUIDFromBytes(
            "cortescombat:purification_damage".getBytes(StandardCharsets.UTF_8)).toString();

    public PurificationEffect() {
        super(MobEffectCategory.HARMFUL, 0x4AA0A0);
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                MODIFIER_UUID,
                DAMAGE_CUT,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (isPurifiable(entity)) {
            entity.hurt(entity.damageSources().magic(), DAMAGE);
            if (entity.level() instanceof ServerLevel server) {
                PurifiedParticles.puff(server, entity);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }

    /**
     * Whether this thing is what copper is for: the undead, or anyone hiding behind netherite.
     *
     * <p>1.20.1 has no {@code UNDEAD} entity-type tag (verified against the mapped jar), so
     * the check is {@code getMobType()}, which is the field vanilla itself reads for
     * inverted healing. Same set of mobs, no tag to publish.</p>
     */
    public static boolean isPurifiable(LivingEntity entity) {
        if (entity.getMobType() == net.minecraft.world.entity.MobType.UNDEAD) {
            return true;
        }
        for (ItemStack stack : entity.getArmorSlots()) {
            if (stack.getItem() instanceof ArmorItem armour
                    && armour.getMaterial() == ArmorMaterials.NETHERITE) {
                return true;
            }
        }
        return false;
    }
}
