package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One button, one packet, one decision point.
 *
 * <p>This replaces two payloads and the whole reason it can is that the client sends the
 * <em>target</em> rather than asking the server to find one. That is what collapses the
 * "swing, whiff, swing" ordering hazard: previously the release told the server to stop
 * charging while a separate packet told it a swing had missed, and which of the two arrived
 * first decided whether the cooldown was charged. One packet cannot be in the wrong order.</p>
 *
 * <p>The two edges of one press, and nothing else:</p>
 * <ul>
 *   <li><b>Press</b>: {@code pressed} is true and the entity id is meaningless. The server opens
 *       a swing, which means a swing is now pending and the charge is counting. No damage has
 *       happened and no cooldown has been paid.</li>
 *   <li><b>Release</b>: {@code pressed} is false and the id is the entity the client resolved
 *       against the cone, or {@link #NO_TARGET} for a swing at nothing. The server closes the
 *       swing, pays the cooldown, and applies the damage if there is a target it agrees with.</li>
 * </ul>
 *
 * <p>What makes that safe is that the charge is measured on the server. The client can say "I
 * am holding" and "I have let go", and the number of ticks in between is counted by the server's
 * own ticker: a client that claims a longer hold than it took has to actually hold for it, and
 * one that lies about a short hold only ever hurts itself.</p>
 */
public record SwingPayload(int entityId, boolean pressed) implements CustomPacketPayload {

    /** The id sent when the swing found nothing. Zero is never a valid entity id. */
    public static final int NO_TARGET = 0;

    public static final Type<SwingPayload> TYPE = new Type<>(
            ResourceIds.rl("swing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SwingPayload::entityId,
                    ByteBufCodecs.BOOL, SwingPayload::pressed,
                    SwingPayload::new);

    public boolean hasTarget() {
        return entityId != NO_TARGET;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
