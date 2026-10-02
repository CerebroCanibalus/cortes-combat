package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server telling the client what the arcabuz is doing.
 *
 * <p>Its own packet for the same reason {@link ParryStatePayload} has one: that record is already
 * at five components and {@code StreamCodec.composite} stops at six, and both of these are read
 * by exactly one weapon out of fourteen. The guard's three numbers ride along in the shared
 * packet because the HUD draws orbs on every weapon; a fuse is drawn on exactly one.</p>
 *
 * <p><b>Sent on the transitions and not every tick.</b> Lighting the fuse, loading the gun and
 * the shot going off are the three things the client needs, and twenty packets a shot to carry a
 * number nobody plots is twenty packets a player for the length of a second. The countdown itself
 * is the client's to keep from the length it was handed, which is the arrangement
 * {@code ClientCombatState#tickParry} already uses for the sabre's five second lockout.</p>
 *
 * @param lit whether a fuse is burning right now
 * @param fuseTicks the whole fuse length on the light, so the client counts it down from the
 *                  server's number rather than keeping its own copy of the constant
 * @param loaded whether a firestone went into the chamber
 */
public record ArcabuzFusePayload(boolean lit, int fuseTicks, boolean loaded)
        implements CustomPacketPayload {

    public static final Type<ArcabuzFusePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "arcabuz_fuse"));

    public static final StreamCodec<ByteBuf, ArcabuzFusePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ArcabuzFusePayload::lit,
                    ByteBufCodecs.VAR_INT, ArcabuzFusePayload::fuseTicks,
                    ByteBufCodecs.BOOL, ArcabuzFusePayload::loaded,
                    ArcabuzFusePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
