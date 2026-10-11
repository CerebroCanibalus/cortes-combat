package com.cortes.combat.particle;

import com.cortes.combat.registry.ModParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Donde sale la sangre, en un solo lugar.
 *
 * <h2>Servidor, como los slashes, y por la misma razon</h2>
 *
 * <p>Los que sangran son los cuerpos del servidor, y la sangre tiene que verla todo el que mire
 * hacia ahi -- no solo al que la produjo. Un cliente que predijera su propia gota seria invisible
 * para los demas, que es la mitad del motivo de existir.</p>
 *
 * <h2>Qué controla este helper y qué no, y lo segundo es la mitad importante</h2>
 *
 * <p>{@code sendParticles} <b>no deja elegir la direccion del chorro.</b> Verificado leyendo
 * {@code ClientPacketListener#handleParticleEvent}: cuando el conteo es mayor que cero, el
 * cliente calcula la velocidad como {@code gaussian() * maxSpeed} <em>por cada eje</em>, con el
 * mismo sigma en los tres. O sea que el paquete solo sabe mandar una <b>nube isotropica</b> de
 * velocidades, y un "hacia atras y abajo" no se puede escribir.</p>
 *
 * <p>Y <b>no hace falta</b>, que es lo que hay que comprobar antes de buscarle la vuelta: un
 * salpic&oacute;n de herida <em>es</em> isotropico -- la sangre sale en todas direcciones desde el
 * punto de impacto -- y despues es {@link SangreParticle#GRAVITY}, con sus tres, la que hace el
 * trabajo de direccion: a 0,12 bloques por tick de frenado vertical, un chorro con sigma 0,15
 * sube como mucho un cuarto de bloque y se va al piso. <b>La gravedad es la que le pone el
 * peso;</b> el paquete solo le da la salida.</p>
 *
 * <p>Lo que si se controla bien son las otras dos piezas: <b>donde</b> nace la gota y <b>cuanta</b>
 * hay. Ambas estan ac&aacute;, y los dos numeros de mas abajo son los que hay que tocar si el
 * playtest dice "casi no se ve" o "es un chorret&oacute;n".</p>
 */
public final class SangreParticles {

    /**
     * Cinco gotas por tick de sangrado.
     *
     * <p>Destripamiento sangra un coraz&oacute;n cada dos segundos, o sea diez ticks de intervalo
     * -- cinco gotas diez veces por segundo es un goteo que se lee sin buscarlo, y mas que eso se
     * convierte en una mancha permanente en el piso muy rapido.</p>
     */
    public static final int DROP_COUNT = 5;

    /**
     * Sigma de la posicion, en bloques.
     *
     * <p>Chico, 0,12: las gotas nacen en un cuarto de bloque alrededor de la herida. Mas ancho y
     * la sangre parecer&iacute;a brotar del aire al lado del cuerpo en vez de del cuerpo.</p>
     */
    public static final double DROP_SPREAD = 0.12D;

    /**
     * Sigma de la velocidad por eje, en bloques por tick.
     *
     * <p>0,15, y el numero se midio contra la gravedad y no se adivino: la magnitud tipica de una
     * gaussiana en tres ejes es el sigma por &radic;3, o sea unos 0,26 bloques por tick -- y a
     * 0,12 de frenado vertical eso da un arco de un cuarto de bloque. Que es un salpic&oacute;n,
     * y no un estallido.</p>
     *
     * <p><b>No subirlo pensando que se va a ver mas.</b> Con gravedad en tres, un chorro mas
     * fuerte no se ve mas: se va mas lejos y <em>cae</em> mas lejos, y la sangre deja de leerse
     * como que viene del cuerpo.</p>
     */
    private static final double DROP_SPEED = 0.15D;

    private SangreParticles() {
    }

    /**
     * Un goteo sobre el cuerpo que esta sangrando.
     *
     * <p><b>A la altura de la caja y no a los pies.</b> {@code Entity#getY} es el suelo, y sangre
     * en el suelo es polvo en los tobillos -- el mismo error que el fogonazo de carga ya dejo
     * escrito. El centro de la caja es donde mira el ojo.</p>
     */
    public static void drip(ServerLevel level, LivingEntity target) {
        AABB box = target.getBoundingBox();
        double centreX = (box.minX + box.maxX) * 0.5;
        double centreZ = (box.minZ + box.maxZ) * 0.5;
        double height = Math.max(0.2D, box.maxY - box.minY);

        level.sendParticles(
                ModParticleTypes.SANGRE.get(),
                centreX, box.minY + height * 0.55D, centreZ,
                DROP_COUNT, DROP_SPREAD, height * 0.25D, DROP_SPREAD, DROP_SPEED);
    }
}
