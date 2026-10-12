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
 * <h2>El unico dano es el estornudo, y los stacks lo hacen mas seguido</h2>
 *
 * <p>Ese es el rediseno que pidio el playtest, y reemplaza a un dano por tick que <b>no se veia</b>:
 * el rival perdia un punto por stack cada veinte ticks y nada lo decia. Ahora el moco cobra de una
 * sola forma -- el estornudo -- y lo que sube con los stacks es <b>cada cuanto estornuda</b>: de uno
 * cada tres segundos con un moco solo a <b>uno por segundo</b> con los seis del tope. Ver
 * {@code CombatEnchantRules#gripaSneezeInterval}.</p>
 *
 * <h2>El nivel del encantamiento NO viaja en el efecto</h2>
 *
 * <p>Lo que viaja es el {@code amplifier}, que son los stacks. Preguntar "que nivel lo puso" no tiene
 * respuesta desde aca, y por eso el intervalo se ata al unico numero que si esta. El tope de stacks es
 * lo que le pone un techo a la escalada, en vez de un campo nuevo en el efecto que llevara un dato que
 * ya esta en la pila.</p>
 *
 * <h2>Un reloj solo, y el reloj es el del cuerpo</h2>
 *
 * <p>Antes habia dos y no coincidian: la puerta del efecto dejaba entrar al metodo cada tanto y aca
 * adentro habia un modulo aparte, asi que el estornudo necesitaba que los dos cayeran juntos y casi
 * nunca pasaba. El reloj del efecto tampoco sirve porque <b>se reinicia en cada golpe</b>: su resto
 * vuelve siempre al mismo valor y un moco recien puesto estornudaria al instante. El cuerpo trae su
 * propio contador y sigue corriendo pase lo que pase. <b>Sin arte nuevo</b>: la particula y el sonido
 * son los del panda, que es el animal que estornuda en este juego.</p>
 */
public class MocoEffect extends MobEffect {

    public MocoEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B8E23);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        int stacks = Math.min(amplifier + 1,
                CombatEnchantRules.gripaMaxStacks(CombatEnchantRules.GRIPA_MAX_LEVEL));

        // **Un solo reloj, el del cuerpo, y ese es el arreglo.**
        //
        // Habia dos y no coincidian: `shouldApplyEffectTickThisTick` dejaba entrar al metodo cada
        // `interval` ticks y aca adentro habia un `% 40` aparte. Para que el estornudo saliera, los
        // dos modulos tenian que caer juntos, y casi nunca caian -- el playtest lo reporto como "no
        // hace nada" y tenia razon: casi nunca pasaba.
        //
        // El reloj del efecto no sirve para esto porque **se reinicia en cada golpe** (la duracion
        // se renueva), asi que su resto vuelve siempre al mismo valor. `entity.tickCount` sigue
        // corriendo pase lo que pase.
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
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // **Siempre, y ese "siempre" es la otra mitad del arreglo.** La frecuencia del estornudo
        // depende de los stacks y la decide `applyEffectTick` sobre el reloj del cuerpo;
        // preguntarlo tambien aca seria el segundo reloj otra vez, que es exactamente lo que estaba
        // roto. Un tick por cuerpo con moco es lo que cuesta, y un moco lo tienen pocos.
        return true;
    }
}
