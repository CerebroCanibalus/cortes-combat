package com.cortes.combat.particle;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import com.cortes.combat.config.ServerConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Lo que sale de un cuerpo <b>cuando se termina</b>: un reventón de sangre, o un alma si era un
 * jugador.
 *
 * <h2>Por qué es un evento y no parte del motor de combate</h2>
 *
 * <p>La sangre de un golpe vive en {@code CombatEngine} porque ahí está el split y la dirección del
 * golpe. Una muerte <b>no es un golpe</b>: puede venir de un ahogo, de un fuego, de una caída o de
 * otro mod, y el motor de combate no la ve. {@code LivingDeathEvent} sí ve todas, y es el mismo
 * criterio que ya usa {@code OnAnyDamage} para el daño que no es un golpe.</p>
 *
 * <h2>El jugador es la excepción, y es una decisión</h2>
 *
 * <p>Un jugador muerto suelta un <b>alma</b> y no sangre, porque un reventón de sangre sobre el
 * propio cuerpo es un mensaje que el jugador no necesita leer dos veces. El alma <b>sube</b>, que es
 * lo contrario de la gota -- ver <code>docs/fx.md</code> §5.1.</p>
 *
 * <p>Y se emite <b>en el momento de la muerte</b> y no desde el renderer, porque el jugador que muere
 * deja de dibujarse: una partícula que nace de un cuerpo que ya no está tiene que nacer del lado del
 * servidor o no nace.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class DeathBurst {

    /**
     * Cuántas almas suelta un jugador.
     *
     * <p>Pocas, ocho, y a propósito: el alma es un acento, no un efecto. Y la velocidad es casi cero
     * (0,02) porque {@code ParticleTypes.SOUL} <b>ya sabe subir</b> por su cuenta -- darle un empuje
     * fuerte con el paquete, que es isotrópico, la mandaría a los costados y perdería justo lo que la
     * hace un alma.</p>
     */
    private static final int SOUL_COUNT = 8;

    private DeathBurst() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!ServerConfig.particles()) return;
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel server)) {
            return;
        }
        if (dead instanceof Player) {
            server.sendParticles(ParticleTypes.SOUL,
                    dead.getX(), dead.getY() + dead.getBbHeight() * 0.5D, dead.getZ(),
                    SOUL_COUNT, 0.3D, 0.4D, 0.3D, 0.02D);
            return;
        }
        SangreParticles.burst(server, dead);
    }
}
