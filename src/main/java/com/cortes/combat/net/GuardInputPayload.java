package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the guard button went down or came up.
 *
 * <p>One bool, two packets per raise, the same shape the swing payload has. The vanilla
 * shield raise is not reused for the sword, because a sword has no vanilla raise to reuse: the
 * same input path for both is what makes them one mechanic instead of two.</p>
 */
public record GuardInputPayload(boolean holding) implements CustomPacketPayload {

    public static final Type<GuardInputPayload> TYPE = new Type<>(
            ResourceIds.rl("guard_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuardInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, GuardInputPayload::holding,
                    GuardInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
