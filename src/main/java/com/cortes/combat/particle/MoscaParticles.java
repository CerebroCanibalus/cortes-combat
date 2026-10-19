package com.cortes.combat.particle;

import com.cortes.combat.registry.ModParticleTypes;
import com.cortes.combat.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;

/**
 * La nube de moscas, lista para el dia que Rey de las Moscas la dispare.
 *
 * <h2>Esta helper existe y nadie la llama todavia</h2>
 *
 * <p>Y <b>esta anotado a proposito</b>, porque un canal cuyo llamador no existe es la forma de
 * fallar que este proyecto mas veces pago: reglas, archivo, tag y textura en su sitio, el build
 * verde, y nada que lo encienda. Rey de las Moscas esta declarado con sus numeros en
 * {@code CombatEnchantRules} -- el radio, el da&ntilde;o por tick, la duracion y el alcance -- y
 * todavia no tiene camino de municion, que es lo que le falta para existir. <b>La part&iacute;cula
 * se hace ahora</b> porque su comportamiento es lo dificil de acertar y se puede probar
 * independientemente; el encantamiento la engancha con una llamada.</p>
 *
 * <h2>Por qué la velocidad de salida es cero</h2>
 *
 * <p>Porque <b>una mosca se propulsa sola</b>. {@link MoscaParticle} elige su propio dardo desde
 * el constructor y tira la velocidad que trajo al nacer, asi que un {@code speed} puesto ac&aacute;
 * se descartaria en el primer frame. Ponerlo seria escribir un numero que no hace nada, que es lo
 * mismo que una constante sin consumidor pero mas caro de encontrar.</p>
 *
 * <p>Lo que si sirve es el <b>spread de posicion</b>: las moscas nacen repartidas por el volumen de
 * la nube en vez de todas en un punto, que es lo que hace que la nube tenga cuerpo desde el
 * primer tick en lugar de formarse.</p>
 */
public final class MoscaParticles {

    private MoscaParticles() {
    }

    /**
     * Suelta una nube de moscas alrededor de un punto.
     *
     * @param count cuantas. Rey de las Moscas usa el radio de {@code CombatEnchantRules} para
     *     decidirlo, no este archivo.
     * @param radius el volumen, en bloques: cada mosca nace dentro de una esfera de ese radio.
     */
    public static void swarm(ServerLevel level, double x, double y, double z, int count, double radius) {
        if (!ServerConfig.particles()) return;
        if (count <= 0) {
            return;
        }
        // speed en cero, y razon arriba. Lo unico que se le da a la mosca es DONDE nace.
        level.sendParticles(ModParticleTypes.MOSCA.get(), x, y, z, count, radius, radius, radius, 0.0D);
    }
}
