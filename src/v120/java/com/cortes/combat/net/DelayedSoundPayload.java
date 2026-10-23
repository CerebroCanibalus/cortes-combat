package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: sound name, pitch and delay, in component order. */
public record DelayedSoundPayload(String sound, float pitch, int delayTicks) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sound);
        buf.writeFloat(pitch);
        buf.writeVarInt(delayTicks);
    }

    public static DelayedSoundPayload decode(FriendlyByteBuf buf) {
        return new DelayedSoundPayload(buf.readUtf(), buf.readFloat(), buf.readVarInt());
    }
}
