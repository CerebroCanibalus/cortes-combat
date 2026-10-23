package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: position, size, length and reach, in component order. */
public record CameraShakePayload(double x, double y, double z, float degrees, int ticks,
        float reach) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(degrees);
        buf.writeVarInt(ticks);
        buf.writeFloat(reach);
    }

    public static CameraShakePayload decode(FriendlyByteBuf buf) {
        return new CameraShakePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }
}
