package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: same single field, hand-written encode/decode. */
public record SpecialHeldPayload(boolean held) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(held);
    }

    public static SpecialHeldPayload decode(FriendlyByteBuf buf) {
        return new SpecialHeldPayload(buf.readBoolean());
    }
}
