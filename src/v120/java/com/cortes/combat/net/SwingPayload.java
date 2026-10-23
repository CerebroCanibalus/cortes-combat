package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The 1.20.1 twin: same fields, same wire order as the shared 1.21.1 record it mirrors.
 * Hand-written encode/decode on {@code FriendlyByteBuf} instead of a {@code StreamCodec},
 * because 1.20.1 has no payload system. See the shared twin for why press and release are one
 * packet. (⌒‿⌒)
 */
public record SwingPayload(int entityId, boolean pressed) {

    public static final int NO_TARGET = 0;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(pressed);
    }

    public static SwingPayload decode(FriendlyByteBuf buf) {
        return new SwingPayload(buf.readVarInt(), buf.readBoolean());
    }

    public boolean hasTarget() {
        return entityId != NO_TARGET;
    }
}
