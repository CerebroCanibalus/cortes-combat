package com.cortes.combat.effect;

import com.cortes.combat.enchant.CombatEnchantRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Moco: lo que deja un golpe de la daga con Gripa.
 *
 * <h2>Los stacks son el nivel del efecto</h2>
 *
 * <p>{@code amplifier} es la cuenta de mocos menos uno, asi que el daño por tick se multiplica por
 * cuantos haya. Es lo que hace que el encantamiento se sienta como una cuenta que sube en vez de un
 * temporizador: el rival no pierde vida mas <em>tiempo</em>, pierde mas vida <em>por golpe que le
 * pegaste</em>, y eso es una recompensa por insistir.</p>
 *
 * <h2>El intervalo depende de los stacks, y hay que decirlo</h2>
 *
 * <p>El documento pide <b>20 ticks por tick de daño en el L1 y 15 en el L2</b>, y <b>el nivel del
 * encantamiento no viaja en el efecto</b> -- lo que viaja es el {@code amplifier}, que son los stacks.
 * Preguntar "que nivel lo puso" no tiene respuesta desde aca, asi que el intervalo se ata al unico
 * numero que si esta: <b>un moco que llego al tope pega mas seguido</b>. Es la misma historia que pega
 * mas fuerte contada en el otro eje, y es preferible a inventar un campo en el efecto para llevar un
 * dato que ya esta en la pila.</p>
 *
 * <h2>El estornudo, que es lo que se ve</h2>
 *
 * <p>Un daño por tick no tiene feedback: el rival pierde vida y nada lo dice. Cada cuarenta ticks del
 * cuerpo el moco lo hace estornudar -- un punto de daño, un salto de 0,4 y la particula del panda --
 * y esa es la mitad visible del encantamiento. <b>Sin arte nuevo</b>: la particula y el sonido son los
 * del panda, que es exactamente el animal que estornuda en este juego.</p>
 */
public class MocoEffect extends MobEffect {

    public MocoEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B8E23);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        int stacks = amplifier + 1;
        entity.hurt(entity.damageSources().magic(),
                CombatEnchantRules.GRIPA_DAMAGE_PER_STACK * stacks);

        // El estornudo, sobre el reloj de la entidad y no sobre el del efecto: el efecto se reaplica
        // con cada golpe y su duracion se renueva, asi que un contador adentro nunca llegaria a
        // cuarenta. El tick del cuerpo si sigue corriendo.
        if (entity.tickCount % CombatEnchantRules.GRIPA_SNEEZE_TICKS == 0) {
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
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // El tope del nivel dos son seis stacks; ver la nota de la clase sobre por que el intervalo se
        // ata a los stacks y no al nivel del encantamiento.
        int interval = amplifier + 1 >= 6
                ? CombatEnchantRules.gripaInterval(CombatEnchantRules.GRIPA_MAX_LEVEL)
                : CombatEnchantRules.gripaInterval(1);
        return duration % interval == 0;
    }
}
