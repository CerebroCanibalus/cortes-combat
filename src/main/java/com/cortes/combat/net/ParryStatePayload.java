package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server telling the client that the sabre's parry window is open, or that it is not.
 *
 * <p>Its own packet rather than a field on {@code CombatStatePayload}, and the reason is
 * arithmetic rather than taste: that record is already at five components and
 * {@code StreamCodec.composite} stops at six, so the parry's two numbers do not fit in it. One
 * short packet that only the sabre ever reads is also the honest shape -- the guard's three
 * numbers ride along in the shared one because the HUD draws orbs on every weapon, and the parry
 * is drawn on exactly one.</p>
 *
 * <p><b>Sent on the transitions, not every tick.</b> The window opening and closing are the two
 * things the client needs, and a tickly stream of a number that is only read for its sign is a
 * packet per player per tick for the length of a press.</p>
 *
 * @param active whether the window is open right now
 * @param cooldown ticks until the next parry is allowed, 0 when ready
 */
public record ParryStatePayload(boolean active, int cooldown) implements CustomPacketPayload {

    public static final Type<ParryStatePayload> TYPE = new Type<>(
            ResourceIds.rl("parry_state"));

    public static final StreamCodec<ByteBuf, ParryStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ParryStatePayload::active,
                    ByteBufCodecs.VAR_INT, ParryStatePayload::cooldown,
                    ParryStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
