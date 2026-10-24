package com.cortes.combat.effect;

import com.cortes.combat.enchant.CombatEnchantRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The 1.20.1 body of Moco. Same sneeze, older tick contract.
 *
 * <p>1.21.1 asks {@code boolean applyEffectTick} + {@code shouldApplyEffectTickThisTick};
 * 1.20.1 asks {@code void applyEffectTick} + {@code isDurationEffectTick}. The logic is
 * untouched: one clock (the body's {@code tickCount}), the interval from the stacks, panda
 * sneeze and all. (⌒‿⌒)</p>
 */
public class MocoEffect extends MobEffect {

    public MocoEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B8E23);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        int stacks = Math.min(amplifier + 1,
                CombatEnchantRules.gripaMaxStacks(CombatEnchantRules.GRIPA_MAX_LEVEL));

        // **Un solo reloj, el del cuerpo, y ese es el arreglo.** Ver el gemelo compartido
        // para la historia completa: dos relojes que nunca caian juntos.
        int interval = CombatEnchantRules.gripaSneezeInterval(stacks);

        if (entity.tickCount % interval == 0) {
            entity.hurt(entity.damageSources().magic(), CombatEnchantRules.GRIPA_SNEEZE_DAMAGE);
            entity.push(0.0, CombatEnchantRules.GRIPA_SNEEZE_JUMP, 0.0);
            entity.hurtMarked = true;
            if (entity.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SNEEZE,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.8, entity.getZ(),
                        8, 0.2, 0.1, 0.2, 0.01);
                server.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        net.minecraft.sounds.SoundEvents.PANDA_SNEEZE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // **Siempre.** La frecuencia la decide applyEffectTick sobre el reloj del cuerpo;
        // preguntarlo tambien aca seria el segundo reloj otra vez.
        return true;
    }
}
