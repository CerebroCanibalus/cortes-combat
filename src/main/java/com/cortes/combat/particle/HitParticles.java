package com.cortes.combat.particle;

import com.cortes.combat.attack.DamageSplit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Suelta los puntos de color de un golpe que aterrizó.
 *
 * <h2>Server-side, como el slash y por la misma razón</h2>
 *
 * <p>Una predicción del cliente mostraría el impacto <b>solo al que pegó</b>, y el diseño pide que
 * todos vean lo mismo -- es la mitad del punto de un sistema de combate compartido. El efecto que
 * decide el color corre en el servidor, así que no hay nada que predecir: los puntos y el daño son
 * el mismo evento.</p>
 *
 * <h2>Nace en el centro del cuerpo, no en los pies</h2>
 *
 * <p>{@code Entity#getY()} es los pies, y una nube a la altura del suelo lee como polvo en las botas
 * -- es el error que {@code PurifiedParticles} ya documenta haber pagado. El centro de la caja es
 * donde mira el ojo.</p>
 *
 * <h2>La cantidad es la de vanilla, y el color es el reparto</h2>
 *
 * <p>{@link DamageParticles#countFor} sigue siendo la regla del juego -- la mitad del daño, redondeada
 * para abajo -- así que <b>el jugador que quiere saber cuánto pegó, cuenta</b>. Lo que cambia es que
 * los puntos no son todos iguales: {@link HitReadout} reparte ese total entre los colores de
 * {@link DamageSplit}, y el total cierra exacto.</p>
 *
 * <p>Los tres colores van en <b>tres paquetes</b> y no en uno, porque {@code sendParticles} lleva un
 * solo {@code ParticleOptions} por llamada y el color viaja ahí. Tres llamadas con un puñado de
 * puntos cada una cuestan menos que una con todos, y además dejan que cada balde tenga su propia
 * dispersión.</p>
 */
public final class HitParticles {

    /** Qué tan lejos del centro pueden nacer. Un tercio de bloque: sobre el cuerpo, no sobre la escena. */
    public static final double SPREAD = 0.35D;

    /**
     * La velocidad que se les da.
     *
     * <p>Chica: el punto tiene que quedarse donde el ojo lo lee, y {@code HurtParticle} ya le pone su
     * propia gravedad y su propio freno. Una velocidad alta los convierte en un salpicón y el color
     * deja de distinguirse, que es exactamente lo que el diseño quiere evitar.</p>
     */
    public static final double SPEED = 0.02D;

    private HitParticles() {
    }

    /**
     * Un impacto que <b>no es un golpe</b>: todo el daño entró, de un solo color.
     *
     * <p>Es la puerta del daño de efecto -- veneno, fuego, caída -- donde el split es trivial porque
     * no hay armadura que lo pare. Ver {@code OnAnyDamage} para por qué el split y el tipo son dos
     * preguntas distintas y cada una tiene su puerta.</p>
     */
    public static void impact(ServerLevel level, LivingEntity target, float amount, int rgb) {
        int total = DamageParticles.countFor(amount);
        if (total <= 0) {
            return;
        }
        spawn(level, target, new int[]{total}, new int[]{rgb}, false);
    }

    /**
     * Un impacto, con su reparto de colores.
     *
     * @param heavy el golpe cargado: cambia la textura, no el color ni la cantidad
     */
    public static void impact(ServerLevel level, LivingEntity target, DamageSplit split, boolean heavy) {
        int total = DamageParticles.countFor(split.raw());
        if (total <= 0) {
            return;
        }
        spawn(level, target, HitReadout.distribute(split.amounts(), total), split.colours(), heavy);
    }

    private static void spawn(ServerLevel level, LivingEntity target, int[] counts, int[] colours,
                              boolean heavy) {
        AABB box = target.getBoundingBox();
        double centreX = (box.minX + box.maxX) * 0.5D;
        double centreY = box.minY + Math.max(0.2D, box.maxY - box.minY) * 0.5D;
        double centreZ = (box.minZ + box.maxZ) * 0.5D;
        double spreadX = Math.max(0.15D, (box.maxX - box.minX) * 0.5D);
        double spreadZ = Math.max(0.15D, (box.maxZ - box.minZ) * 0.5D);

        for (int bucket = 0; bucket < counts.length; bucket++) {
            if (counts[bucket] <= 0) {
                continue;
            }
            level.sendParticles(
                    heavy ? HurtParticleOptions.heavy(colours[bucket])
                            : HurtParticleOptions.of(colours[bucket]),
                    centreX, centreY, centreZ,
                    counts[bucket], spreadX, SPREAD, spreadZ, SPEED);
        }
    }
}
