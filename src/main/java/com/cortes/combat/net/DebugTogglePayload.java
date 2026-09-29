package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: toggle the debug overlays. Development only.
 *
 * <p>Commands run on the server, so it cannot reach a client-side static directly. The flag
 * travels as a packet like everything else.</p>
 */
public record DebugTogglePayload(boolean cone, boolean hud) implements CustomPacketPayload {

    public static final Type<DebugTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "debug_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugTogglePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DebugTogglePayload::cone,
                    ByteBufCodecs.BOOL, DebugTogglePayload::hud,
                    DebugTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
