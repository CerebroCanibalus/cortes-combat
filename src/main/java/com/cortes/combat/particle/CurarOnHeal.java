package com.cortes.combat.particle;

import com.cortes.combat.CortesCombat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Dispara los corazones verdes cuando un jugador se cura.
 *
 * <h2>Un evento y no cuatro call sites</h2>
 *
 * <p>El mod cura en cuatro lugares -- la regeneración de comida, Sanguijuela, Desvío Vampírico y el
 * encantamiento de oro -- y hay una quinta vía que no es nuestra: <b>las pociones, la manzana
 * dorada y la regeneración de vanilla</b>, más lo que agregue cualquier mod. Poner el efecto en los
 * cuatro call sites habría dejado las otras vías afuera, y el jugador habría aprendido que curarse
 * con una poción no se ve y curarse comiendo sí.</p>
 *
 * <p><b>Y el seam ya estaba elegido por el proyecto</b>: {@code AntiHealingHandler} escucha este
 * mismo evento y su javadoc dice por qué -- se dispara <em>dentro</em> de {@code LivingEntity#heal},
 * con el número todavía en la mano. Es el único lugar donde "cuánto se curó" existe como dato.</p>
 *
 * <h2>Por qué no lleva throttle</h2>
 *
 * <p>Porque <b>no hace falta y un throttle tendría estado</b>. Ninguna vía de curación de vanilla
 * corre por tick: la regeneración del efecto es cada cincuenta ticks dividido por el amplificador, y
 * la de este mod es un punto por segundo. Un contador por jugador sería un {@code static mutable}
 * -- que el proyecto veta por nombre -- o un attachment nuevo, para proteger un caso que no existe.
 *
 * <p><b>Y si algún día aparece una cura por tick, el techo de {@link CurarReadout#MAX_HEARTS} no
 * es la defensa</b>: lo que habría que agregar es el throttle, y este javadoc es donde está escrito
 * que hoy no está.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class CurarOnHeal {

    private CurarOnHeal() {
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        // **Sólo jugadores, por decisión de diseño.** Un mob que se regenera con la dificultad
        // pacífica curaría todo el tiempo, y una nube de corazones sobre cada zombi le quitaría al
        // efecto lo único que tiene: que significa algo cuando aparece.
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel server)) {
            return;
        }
        CurarParticles.puff(server, player, event.getAmount());
    }
}
