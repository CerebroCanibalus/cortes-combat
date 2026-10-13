package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Un temblor de camara puntual: <b>donde, cuanto, y por cuanto tiempo</b>.
 *
 * <h2>Por que un paquete y no el camino del reactor</h2>
 *
 * <p>El temblor de la estrella del nether viaja por el block entity, porque un reactor <em>esta</em>
 * en el mundo: tiene posicion fija, persiste, y su estado ya viaja en el update tag. Un orbe existe
 * dos segundos y se va. Colgar un temblor de algo que no existe es un estado sin dueño, asi que lo
 * que viaja aca es <b>el aviso</b> y el cliente lo guarda el tiempo que dura.</p>
 *
 * <h2>Viaja la posicion, y no una amplitud ya calculada</h2>
 *
 * <p>Porque la distancia que importa es la del <b>jugador que lo ve</b> y el servidor no la conoce:
 * mandar un numero fijo seria sacudir igual al que esta encima y al que esta a treinta bloques. Se
 * manda el origen y cada cliente mide lo suyo, que es la misma division que usa el resto del mod --
 * el servidor decide que paso, el cliente decide como se ve.</p>
 *
 * <h2>Y viaja el alcance, porque el alcance es del hecho y no del efecto</h2>
 *
 * <p>El cliente tenia el alcance en una constante de cuarenta bloques -- el de un reventon comun --
 * y eso hacia que <b>ningun numero pudiera llegar mas lejos</b>: pasando los cuarenta el temblor no
 * era chico, era <b>cero</b>. Un reactor tiene que sentirse a cien bloques y una flecha que pasa al
 * lado a dos, y esa no es una diferencia de amplitud. Ver {@code ShakeKind#reach}.</p>
 *
 * @param x       el origen del reventon
 * @param y       el origen del reventon
 * @param z       el origen del reventon
 * @param degrees la amplitud en grados <b>a quemarropa</b>; cae con el cuadrado de la distancia
 * @param ticks   cuanto dura. Terminado el plazo el cliente lo olvida solo.
 * @param reach   hasta donde se siente, en bloques. Mas alla el cliente lo descarta solo.
 */
public record CameraShakePayload(double x, double y, double z, float degrees, int ticks, float reach)
        implements CustomPacketPayload {

    public static final Type<CameraShakePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "camera_shake"));

    public static final StreamCodec<ByteBuf, CameraShakePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, CameraShakePayload::x,
                    ByteBufCodecs.DOUBLE, CameraShakePayload::y,
                    ByteBufCodecs.DOUBLE, CameraShakePayload::z,
                    ByteBufCodecs.FLOAT, CameraShakePayload::degrees,
                    ByteBufCodecs.VAR_INT, CameraShakePayload::ticks,
                    ByteBufCodecs.FLOAT, CameraShakePayload::reach,
                    CameraShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
