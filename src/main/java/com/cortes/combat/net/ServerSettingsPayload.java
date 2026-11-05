package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: <b>la config del server</b>, para que el cliente no lea la suya.
 *
 * <p><b>Un solo mensaje, enviado al entrar.</b> La config del server no cambia sin que el server
 * se reinicie (Forge no la recarga en caliente), asi que mandarla una vez al entrar alcanza: no
 * hace falta un keep-alive ni un pedido cada N ticks.</p>
 *
 * <p><b>El registro es opcional a proposito.</b> Un cliente con un jar anterior no conoce este
 * mensaje; si el server se lo mandara como obligatorio, la conexion se caeria. Marcado como
 * opcional, el cliente viejo lo ignora y sigue jugando con su propia copia (que es lo de
 * siempre).</p>
 */
public record ServerSettingsPayload(ServerSettings settings) implements CustomPacketPayload {

    public static final Type<ServerSettingsPayload> TYPE = new Type<>(
            ResourceIds.rl("server_settings"));

    /**
     * El codec, escrito a mano y no compuesto.
     *
     * <p><b>El orden de los campos lo escribe {@link ServerSettings}</b>, que es la clase que
     * los dos know: el payload de 1.20.1 y el de 1.21.1 delegan en los mismos dos metodos, asi
     * que no hay dos opiniones sobre en que campo va primero.</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerSettingsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> ServerSettings.encode(buf, payload.settings()),
                    buf -> new ServerSettingsPayload(ServerSettings.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
