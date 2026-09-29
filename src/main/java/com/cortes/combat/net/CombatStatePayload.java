package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the two values the HUD needs to draw honestly.
 *
 * <p>Both are server-owned. The client predicts locally to keep the bars smooth under lag,
 * but what it draws is corrected by these, and what counts for damage is only ever what the
 * server computed.</p>
 *
 * @param chargeRatio 0..1 accumulated charge
 * @param cooldownRatio 0..1 attack readiness, 1 means the next swing is allowed
 */
public record CombatStatePayload(float chargeRatio, float cooldownRatio) implements CustomPacketPayload {

    public static final Type<CombatStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "combat_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, CombatStatePayload::chargeRatio,
                    ByteBufCodecs.FLOAT, CombatStatePayload::cooldownRatio,
                    CombatStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
