package com.cortes.combat.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sangrado: lo que deja un ataque cargado de alabarda.
 *
 * <h2>DoT alto y lento, y las dos mitades importan</h2>
 *
 * <p><b>Alto</b> porque la alabarda paga caro por cargarlo -- 0,6 de bonus en treinta y seis ticks,
 * que contra un solo enemigo es la peor relación del juego -- y el premio tiene que sentirse.
 * <b>Lento</b> porque un DoT rápido se lee como "el arma pega dos veces" y uno lento se lee como una
 * herida: el jugador ve la vida bajar después de haberse ido, que es lo que hace que un sangrado sea
 * un sangrado y no un segundo golpe.</p>
 *
 * <p>Un punto de corazón cada dos segundos: seis segundos al nivel uno son tres corazones, diez
 * segundos al dos son cinco. <b>El daño es mágico</b> -- {@code DamageSources#magic}, el mismo que
 * usa la purificación del cobre -- porque es vanilla y ya es exactamente "daño que ignora la
 * armadura", que es lo que una herida abierta tiene que ser.</p>
 *
 * <p><b>Y no se apila: reaplicar refresca.</b> El diseño lo dice, y es la decisión correcta: un DoT
 * que se apilara con cada golpe cargado convertiría la alabarda en un arma que mata sola después de
 * tres segundos de no hacer nada.</p>
 */
public class BleedingEffect extends MobEffect {

    /** Un corazón por tick de daño. En Minecraft un corazón son dos puntos. */
    public static final float DAMAGE = 2.0F;

    /** Cada dos segundos. Ver la nota de la clase: lento es la mitad del encantamiento. */
    public static final int INTERVAL_TICKS = 40;

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A1F1F);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().magic(), DAMAGE);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // El primero cae al final del intervalo y no en el tick cero: un golpe cargado que ya hizo
        // su daño y encima cobra el sangrado en el mismo tick se lee como un golpe mas grande, que
        // es exactamente lo que este encantamiento no es.
        return duration % INTERVAL_TICKS == 0;
    }
}
