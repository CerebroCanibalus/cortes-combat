package com.cortes.combat.fx;

import com.cortes.combat.net.CameraShakePayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Manda un temblor de cámara. Tres formas, y cada una contesta una pregunta distinta.
 *
 * <h2>Por qué hay una clase y no una llamada suelta por sitio</h2>
 *
 * <p>Porque el paquete tiene que construirse <b>siempre igual</b>: el tamaño y la duración salen de
 * {@link ShakeKind} y nunca de un número escrito en el llamador. Con seis fuentes, un
 * {@code new CameraShakePayload(...)} a mano por sitio es seis lugares donde alguien puede escribir
 * un 3,0 porque "este momento es importante", y la tabla deja de ser la respuesta a
 * <em>"¿cuánto sacude esto?"</em>.</p>
 *
 * <h2>La posición viaja, y por eso hay dos destinos</h2>
 *
 * <p>{@code CameraShake} mide su propia distancia al origen y cae al cuadrado, así que el mismo
 * número se siente distinto según dónde haya sido. Eso da las dos formas:</p>
 *
 * <ul>
 *   <li>{@link #to} -- el que lo va a sentir es uno, y el origen es una cosa con posición (el
 *       cuerpo que cayó, el jugador al que le rompieron la guardia).</li>
 *   <li>{@link #around} -- <b>el aviso no tiene dueño</b>: un reventón lo sienten todos los que
 *       estén cerca, y el radio es del cliente aunque el que lo manda sea el servidor. Es la misma
 *       división que el resto del mod: el servidor decide <em>qué pasó</em>, el cliente decide
 *       <em>cómo se ve</em>.</li>
 * </ul>
 */
public final class Shake {

    private Shake() {
    }

    /** Un impulso en un punto, para un jugador. */
    public static void to(ServerPlayer player, double x, double y, double z, ShakeKind kind) {
        PacketDistributor.sendToPlayer(player, payload(x, y, z, kind));
    }

    /** Un impulso en la posición de una entidad, para el jugador que lo va a sentir. */
    public static void to(ServerPlayer player, Entity origin, ShakeKind kind) {
        to(player, origin.getX(), origin.getY(), origin.getZ(), kind);
    }

    /**
     * Un impulso con el tamaño puesto a mano, para lo que <b>no</b> es una fila de la tabla.
     *
     * <p>Existe por dos casos que ya tenían su número antes de que hubiera tabla: el culatazo del
     * arcabuz, cuya amplitud <b>varía</b> según lo que tenga cargado, y el reventón del orbe. Los dos
     * llevan el alcance común, porque los dos pasan a quemarropa del que los siente.</p>
     */
    public static void to(ServerPlayer player, double x, double y, double z, float degrees,
                          int ticks) {
        PacketDistributor.sendToPlayer(player,
                new CameraShakePayload(x, y, z, degrees, ticks, ShakeKind.COMMON_REACH));
    }

    /** Un impulso que lo sienten todos los que estén dentro del alcance del impulso. Sin dueño. */
    public static void around(ServerLevel level, double x, double y, double z, ShakeKind kind) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, kind.reach(),
                payload(x, y, z, kind));
    }

    /**
     * Un impulso sin dueño con el tamaño y el alcance puestos a mano.
     *
     * <p>Existe por el orbe, que trae sus propios números desde antes de que hubiera tabla.
     * <b>Y su alcance ahora funciona</b>: antes el cliente lo tenía escrito en cuarenta bloques, así
     * que su {@code SHAKE_REACH} no podía llegar más lejos por grande que fuera -- pasado el tope el
     * temblor era cero. Ver {@code ShakeKind#reach}.</p>
     */
    public static void around(ServerLevel level, double x, double y, double z, double reach,
                              float degrees, int ticks) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, reach,
                new CameraShakePayload(x, y, z, degrees, ticks, (float) reach));
    }

    private static CameraShakePayload payload(double x, double y, double z, ShakeKind kind) {
        return new CameraShakePayload(x, y, z, kind.degrees(), kind.ticks(), kind.reach());
    }
}
