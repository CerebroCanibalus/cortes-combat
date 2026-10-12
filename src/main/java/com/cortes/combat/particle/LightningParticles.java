package com.cortes.combat.particle;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Un rayo dibujado como una linea en zigzag, de un punto a otro.
 *
 * <h2>Por qué existe, y es una corrección de un playtest</h2>
 *
 * <p>Capacitor devolvia el proyectil con un penacho de {@code END_ROD} <b>en el objetivo</b>, y se
 * leia como "aparecieron particulas ahi" y no como un rayo que vino de algun lado. La diferencia
 * no es cosmética: <b>un rayo sin origen no dice quién lo tiro</b>, y en un duelo eso es la mitad
 * de la información. Esto dibuja la linea entera, del jugador al cuerpo, y por eso se lee como una
 * descarga y no como un fogonazo.</p>
 *
 * <h2>Cómo se dibuja sin poder dibujar lineas</h2>
 *
 * <p>{@code sendParticles} sabe mandar <b>una caja</b> -- una posición con un spread por eje -- y
 * no una trayectoria. Asi que la linea se camina a mano: N puntos entre A y B, y un paquete por
 * punto. El zigzag no viene de mover cada punto a azar sino de moverlo dentro de una
 * <b>envolvente senoidal</b> que es cero en los extremos: los dos puntos de la linea son el
 * jugador y el cuerpo, y todo lo que se mueve esta en el medio. Sin esa envolvente el rayo
 * empezaria flotando en el aire al lado del que lo tiro.</p>
 *
 * <p>Y los dos ejes perpendiculares se construyen con un producto cruz contra un eje que no
 * colineal, asi que el zigzag tiene dos dimensiones y no un aplastamiento en una sola -- un rayo
 * que zigzaguea en un plano se lee como un dibujo, no como una descarga.</p>
 */
public final class LightningParticles {

    /** Puntos de la linea. Diez: bastante para leer el zigzag, barato para un solo evento. */
    private static final int SEGMENTS = 10;

    /** Que tan lejos del eje se va cada punto, en bloques. */
    private static final double SPREAD = 0.35D;

    private LightningParticles() {
    }

    /** Un rayo de {@code from} a {@code to}. Los extremos no se mueven nunca. */
    public static void zigzag(ServerLevel level, Vec3 from, Vec3 to) {
        zigzag(level, from, to, ParticleTypes.END_ROD);
    }

    /** Lo mismo, con la partícula que se quiera. */
    public static void zigzag(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions type) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1.0E-6) {
            return;
        }
        RandomSource random = level.getRandom();

        // Un marco perpendicular: el eje de la linea cruzado contra uno que no sea paralelo a el.
        // Se elige contra Y salvo que la linea vaya casi recta hacia arriba, donde Y seria
        // colineal y el producto cruz daria cero.
        Vec3 up = Math.abs(axis.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 unit = axis.normalize();
        Vec3 perpA = unit.cross(up).normalize();
        Vec3 perpB = unit.cross(perpA).normalize();

        for (int i = 0; i <= SEGMENTS; i++) {
            // La envolvente: cero en los extremos y maximo en el medio, con la forma de una
            // semionda. Es lo que hace que la descarga parezca ir de A a B en vez de flotar.
            double envelope = Math.sin(Math.PI * i / (double) SEGMENTS);
            double a = (random.nextDouble() - 0.5) * 2.0 * SPREAD * envelope;
            double b = (random.nextDouble() - 0.5) * 2.0 * SPREAD * envelope;

            Vec3 point = from.add(axis.scale((double) i / SEGMENTS))
                    .add(perpA.scale(a)).add(perpB.scale(b));
            level.sendParticles(type, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
