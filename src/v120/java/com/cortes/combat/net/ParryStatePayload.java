package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: active flag and cooldown, in component order. */
public record ParryStatePayload(boolean active, int cooldown) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(cooldown);
    }

    public static ParryStatePayload decode(FriendlyByteBuf buf) {
        return new ParryStatePayload(buf.readBoolean(), buf.readVarInt());
    }
}
