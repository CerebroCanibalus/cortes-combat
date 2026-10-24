package com.cortes.combat.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The 1.20.1 body of Sangrado. Same wound, older tick contract.
 *
 * <p>{@code void applyEffectTick} + {@code isDurationEffectTick} instead of the 1.21.1
 * boolean pair. The blood still comes out of the {@code hurt} result, not the effect's
 * clock: a body that took nothing drips nothing. (⌒‿⌒)</p>
 */
public class BleedingEffect extends MobEffect {

    /** Un corazón por tick de daño. En Minecraft un corazón son dos puntos. */
    public static final float DAMAGE = 2.0F;

    /** Cada dos segundos. Lento es la mitad del encantamiento. */
    public static final int INTERVAL_TICKS = 40;

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A1F1F);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        boolean landed = entity.hurt(entity.damageSources().magic(), DAMAGE);
        if (landed && entity.level() instanceof net.minecraft.server.level.ServerLevel server) {
            com.cortes.combat.particle.SangreParticles.drip(server, entity);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // El primero cae al final del intervalo y no en el tick cero: un golpe cargado que
        // ya hizo su daño y encima cobra el sangrado en el mismo tick se leeria como un
        // golpe mas grande, que es exactamente lo que este encantamiento no es.
        return duration % INTERVAL_TICKS == 0;
    }
}
