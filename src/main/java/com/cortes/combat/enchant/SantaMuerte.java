package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Santa Muerte: la azada que deja un cuerpo muerto pudriendose.
 *
 * <h2>Por que es un handler propio, con el mismo criterio que Aventurero</h2>
 *
 * <p>Porque <b>el evento no es del golpe</b>. Lo que el encantamiento compra no es el dano que mata:
 * es lo que pasa <em>despues</em>, y pasa en un camino que este mod no reemplaza. El golpe ya cobro
 * su dano y el motor ya decidio que el cuerpo cae; lo unico que queda por decidir es que el hueco
 * que ese cuerpo deja quede envenenado.</p>
 *
 * <p><b>Y es la razon por la que el hook es la muerte y no el `hurt`.</b> Un mob puede morir de tres
 * formas que no son este golpe -- se ahoga, se cae, lo mata otro -- y ninguna de las tres pasa por
 * `CombatEngine`. La pregunta correcta es "este cuerpo murio con mi azada en la mano", y esa pregunta
 * solo tiene respuesta al morir.</p>
 *
 * <h2>Por que el Wither va al radio y no al que murio</h2>
 *
 * <p>El cuerpo que cae ya no importa: un efecto sobre un cadaver no lo ve nadie. Lo que el jugador
 * compra es que <b>el siguiente cuerpo que entre al hueco lo reciba</b>, que es exactamente lo que una
 * nube de putrefaccion es.</p>
 *
 * <h2>Y el asesino no se envenena a si mismo</h2>
 *
 * <p>La nube lo rodea -- esta en el centro. Aplicarle el efecto seria un encantamiento que castiga al
 * que lo usa, y hay un veto escrito para eso en el documento (C2, "el bloqueo devuelve dano": castigar
 * al atacante por atacar). El radio se cobra <b>sin el asesino</b> por el mismo motivo.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class SantaMuerte {

    private SantaMuerte() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }
        // **El nivel se lee del arma del ataque, y el gate no hace falta escribirlo.** El
        // encantamiento solo se puede poner en la azada (`supported_items` del JSON), asi que si el
        // arma en la mano no es una azada el nivel es cero y la pregunta se contesta sola. Escribir
        // ademas un `profile() == HOE` seria una segunda respuesta a la misma pregunta -- la clase de
        // copia que en este proyecto ya costo un bug.
        int level = CombatEnchantments.level(killer.level().registryAccess(),
                killer.getMainHandItem(), CombatEnchantments.SANTA_MUERTE);
        if (level < 1) {
            return;
        }

        LivingEntity body = event.getEntity();
        var world = body.level();
        AABB box = body.getBoundingBox().inflate(CombatEnchantRules.SANTA_MUERTE_RADIUS);

        // El humo putrefacto: tinta y humo, que es lo mas negro que el juego dibuja sin arte propio.
        // Sin textura nueva, por la misma razon que Gripa usa la particula del panda.
        if (world instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                    body.getX(), body.getY() + 0.5, body.getZ(),
                    24, 1.2, 0.6, 1.2, 0.02);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    body.getX(), body.getY() + 0.4, body.getZ(),
                    30, 1.0, 0.5, 1.0, 0.01);
        }

        List<LivingEntity> caught = world.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity other : caught) {
            if (other == killer || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            other.addEffect(new MobEffectInstance(MobEffects.WITHER,
                    CombatEnchantRules.SANTA_MUERTE_WITHER_TICKS, 0));
        }
    }
}
