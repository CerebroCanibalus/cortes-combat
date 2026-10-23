package com.cortes.combat.net;

import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: six fields in component order, hand-written encode/decode. */
public record CombatStatePayload(float chargeRatio, float cooldownRatio, int guardStamina,
        int guardMax, boolean guardHeld, float specialCharge) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(chargeRatio);
        buf.writeFloat(cooldownRatio);
        buf.writeVarInt(guardStamina);
        buf.writeVarInt(guardMax);
        buf.writeBoolean(guardHeld);
        buf.writeFloat(specialCharge);
    }

    public static CombatStatePayload decode(FriendlyByteBuf buf) {
        return new CombatStatePayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readFloat());
    }
}
