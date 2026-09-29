package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the attack button went down or came up.
 *
 * <p>Two packets only, not a stream of them. The server accumulates charge on its own tick
 * while this flag is set, so the network cost of charging is two booleans per swing.</p>
 *
 * <p>The server refuses a "down" while already holding, so a client that spams the packet
 * cannot accumulate charge faster than holding it would.</p>
 */
public record ChargeInputPayload(boolean holding) implements CustomPacketPayload {

    public static final Type<ChargeInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "charge_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChargeInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ChargeInputPayload::holding,
                    ChargeInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
