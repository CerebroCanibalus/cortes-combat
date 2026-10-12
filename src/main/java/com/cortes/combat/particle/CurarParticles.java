package com.cortes.combat.particle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Los corazoncitos verdes que suben: <b>alguien se curó</b>.
 *
 * <h2>Cuántos, y por qué el número sale de la curación</h2>
 *
 * <p>Uno por defecto, y hasta cuatro para las curas grandes. <b>El número es la lectura</b>, igual
 * que en la partícula de daño: el jugador ve de un vistazo si se curó un punto -- la regeneración de
 * comida, que es constante y chica -- o si se comió una manzana dorada. Sin eso, los dos momentos
 * más distintos del juego se verían igual.</p>
 *
 * <p>Y el techo de cuatro no es decoración: {@code SurvivalTicker} cura <b>todo el tiempo</b>, un
 * punto por segundo. Una partícula por punto de vida con un techo alto convertiría la pantalla en un
 * acuario y -- peor -- <b>dejaría de significar</b>. El valor de este efecto está en que aparece
 * cuando pasa algo. El número vive en {@link CurarReadout}, que es pura.</p>
 *
 * <h2>Server-side, como la sangre y el slash</h2>
 *
 * <p>El que se cura no es siempre el que mira, y una predicción del cliente mostraría el corazón
 * sólo al que lo ganó. El evento que lo dispara corre en el servidor, así que no hay nada que
 * predecir.</p>
 */
public final class CurarParticles {

    /** Qué tan lejos del centro nacen. Sobre el cuerpo, no sobre la escena. */
    public static final double SPREAD = 0.3D;

    /**
     * La velocidad que se les da.
     *
     * <p><b>Casi se descarta:</b> {@code CurarParticle} pisa el eje Y con su propio ascenso, porque
     * el paquete de {@code sendParticles} no puede elegir una dirección -- reparte
     * {@code gaussian() * speed} por eje y con el mismo sigma en los tres. Lo único que decide este
     * número es la dispersión lateral, y por eso es chico: un corazón que sale disparado de costado
     * se lee como una chispa, no como algo que flota.</p>
     */
    public static final double SPEED = 0.01D;

    private CurarParticles() {
    }

    /** Una curación, con sus corazones. */
    public static void puff(ServerLevel level, Entity target, float amount) {
        int count = CurarReadout.heartsFor(amount);
        if (count <= 0) {
            return;
        }
        AABB box = target.getBoundingBox();
        double centreX = (box.minX + box.maxX) * 0.5D;
        // **Desde el pecho y no desde los pies.** El mismo error que PurifiedParticles ya documenta:
        // `getY()` es los pies, y un corazón que sale del suelo lee como polvo, no como algo que
        // sube del cuerpo.
        double centreY = box.minY + Math.max(0.2D, box.maxY - box.minY) * 0.55D;
        double centreZ = (box.minZ + box.maxZ) * 0.5D;
        double spreadX = Math.max(0.15D, (box.maxX - box.minX) * 0.5D);
        double spreadZ = Math.max(0.15D, (box.maxZ - box.minZ) * 0.5D);

        level.sendParticles(
                com.cortes.combat.registry.ModParticleTypes.CURAR.get(),
                centreX, centreY, centreZ,
                count, spreadX, SPREAD, spreadZ, SPEED);
    }
}
