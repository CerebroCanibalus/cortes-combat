package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: five booleans in component order. */
public record DebugTogglePayload(boolean cone, boolean hud, boolean trace, boolean dump,
        boolean input) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(cone);
        buf.writeBoolean(hud);
        buf.writeBoolean(trace);
        buf.writeBoolean(dump);
        buf.writeBoolean(input);
    }

    public static DebugTogglePayload decode(FriendlyByteBuf buf) {
        return new DebugTogglePayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean());
    }
}
