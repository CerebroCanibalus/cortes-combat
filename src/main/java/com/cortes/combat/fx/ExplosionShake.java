package com.cortes.combat.fx;

import com.cortes.combat.CortesCombat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * El temblor de <b>una explosión</b>: el más grande del juego, y el único que lo sienten varios.
 *
 * <h2>Por qué {@code Detonate} y no el daño</h2>
 *
 * <p>Porque un reventón se <b>siente antes de que te toque</b>. Colgar esto de {@code LivingDamageEvent}
 * lo ataría a que alguien haya perdido vida: el jugador a doce bloques que ve volar el cráter no
 * recibiría nada, y el que está a un bloque recibiría el temblor <b>además</b> del de recibir daño.
 * {@code Detonate} es el hecho -- hubo una explosión, acá -- y el cliente decide si le llega según
 * su distancia.</p>
 *
 * <h2>El radio es del aviso, no del efecto</h2>
 *
 * <p>{@code CameraShake} ya cae al cuadrado hasta su propio alcance, así que a 20 bloques el 5,0 de
 * la tabla llega a 1,25 y a 38 bloques es un susurro. Este radio sólo decide <b>a quién le llega el
 * paquete</b>, y por eso es generoso: mandarlo de más cuesta unos bytes y el cliente lo descarta solo
 * cuando la distancia da cero.</p>
 *
 * <h2>Y va sin dueño</h2>
 *
 * <p>No hay "el jugador de la explosión": la sintieron todos los que estaban cerca, y cada uno mide
 * lo suyo. Es la misma forma que el temblor del orbe, y la razón por la que {@link Shake#around}
 * existe.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class ExplosionShake {

    /** Hasta dónde viaja el aviso, en bloques. Generoso a propósito: ver el javadoc. */
    public static final double RADIUS = 48.0;

    private ExplosionShake() {
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel server)) {
            return;
        }
        Vec3 center = event.getExplosion().center();
        Shake.around(server, center.x, center.y, center.z, RADIUS, ShakeKind.EXPLOSION);
    }
}
