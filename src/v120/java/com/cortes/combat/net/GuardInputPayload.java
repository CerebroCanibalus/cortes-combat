package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: same single field, hand-written encode/decode. */
public record GuardInputPayload(boolean holding) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(holding);
    }

    public static GuardInputPayload decode(FriendlyByteBuf buf) {
        return new GuardInputPayload(buf.readBoolean());
    }
}
