package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: four fields in component order. */
public record ArcabuzFusePayload(boolean lit, int fuseTicks, boolean loaded, int reloadTicks) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(lit);
        buf.writeVarInt(fuseTicks);
        buf.writeBoolean(loaded);
        buf.writeVarInt(reloadTicks);
    }

    public static ArcabuzFusePayload decode(FriendlyByteBuf buf) {
        return new ArcabuzFusePayload(buf.readBoolean(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt());
    }
}
