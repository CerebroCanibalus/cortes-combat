package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the values the HUD needs to draw honestly.
 *
 * <p>All server-owned. The client predicts locally to keep the bars smooth under lag, but
 * what it draws is corrected by these, and what counts for damage is only ever what the
 * server computed.</p>
 *
 * <p>Guard stamina rides along in the same packet. It is a single byte and saving a second
 * packet for three orbs is not worth the complexity, and an orb layer showing a stale
 * default of 0/3 would be lying to the player.</p>
 *
 * @param chargeRatio 0..1 accumulated charge
 * @param cooldownRatio 0..1 attack readiness, 1 means the next swing is allowed
 * @param guardStamina remaining blockable hits, 0..3
 * @param guardHeld whether the server currently considers the guard up
 */
public record CombatStatePayload(float chargeRatio, float cooldownRatio, int guardStamina,
                                boolean guardHeld) implements CustomPacketPayload {

    public static final Type<CombatStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "combat_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, CombatStatePayload::chargeRatio,
                    ByteBufCodecs.FLOAT, CombatStatePayload::cooldownRatio,
                    ByteBufCodecs.VAR_INT, CombatStatePayload::guardStamina,
                    ByteBufCodecs.BOOL, CombatStatePayload::guardHeld,
                    CombatStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
