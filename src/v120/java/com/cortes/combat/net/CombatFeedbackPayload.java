package com.cortes.combat.net;

import com.cortes.combat.core.CombatFeedback;
import net.minecraft.network.FriendlyByteBuf;

/** The 1.20.1 twin: the kind travels as its ordinal, read back through {@code byId}. */
public record CombatFeedbackPayload(CombatFeedback kind) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind.ordinal());
    }

    public static CombatFeedbackPayload decode(FriendlyByteBuf buf) {
        return new CombatFeedbackPayload(CombatFeedback.byId(buf.readByte()));
    }
}
