package com.cortes.combat.fx;

import com.cortes.combat.CortesCombat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.ExplosionEvent;

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
 * <h2>El radio es el del impulso, no el de la explosión</h2>
 *
 * <p>{@code CameraShake} cae al cuadrado hasta el alcance que trae el impulso, así que a 20 bloques
 * el 5,0 de la tabla llega a 1,25 y a 38 es un susurro. El paquete va a los que están dentro de ese
 * mismo alcance -- mandarlo de más cuesta unos bytes y el cliente lo descarta solo.</p>
 *
 * <p><b>Y por eso el reactor no pasa por acá.</b> Su reventón no llama a {@code explode} (vaporiza
 * sin encadenar, ver {@code ReactorBlast}) y además tiene que llegar <b>mucho más lejos</b> que una
 * TNT: su temblor lo dispara {@code NetherReactorBlockEntity} con {@code ShakeKind.NUCLEAR}.</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class ExplosionShake {

    private ExplosionShake() {
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel server)) {
            return;
        }
        Vec3 center = event.getExplosion().getPosition();
        Shake.around(server, center.x, center.y, center.z, ShakeKind.EXPLOSION);
    }
}
