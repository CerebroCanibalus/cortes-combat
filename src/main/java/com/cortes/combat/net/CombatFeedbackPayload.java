package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

import com.cortes.combat.core.CombatFeedback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: something landed.
 *
 * <p>One byte, and it exists because the crosshair is the only place in the game that reacts
 * to a hit, so the client has to be told the hit happened rather than guess from the swing.
 * Guessing is what a prediction layer would do, and it would be wrong exactly when it matters:
 * a swing blocked by a wall and a swing that connected are indistinguishable from the client.</p>
 *
 * <p>It carries no numbers. Damage, the target's health and the kill credit are all server
 * concerns; all the HUD needs is which of three things happened.</p>
 */
public record CombatFeedbackPayload(CombatFeedback kind) implements CustomPacketPayload {

    public static final Type<CombatFeedbackPayload> TYPE = new Type<>(
            ResourceIds.rl("combat_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(CombatFeedback::byId, CombatFeedback::ordinal),
                    CombatFeedbackPayload::kind,
                    CombatFeedbackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
