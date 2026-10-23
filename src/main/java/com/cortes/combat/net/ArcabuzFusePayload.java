package com.cortes.combat.net;

import com.cortes.combat.platform.ResourceIds;

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
 * @param loaded whether the chamber still holds its charge.
 *
 * <p><b>True while the fuse burns</b>, because the ball does not leave the barrel until the gun
 * goes off. It was {@code false} for the whole burn, which dropped the loaded pose the instant the
 * fuse was lit: the gun pointed at the floor for a full second and then sprang back, and the two
 * channels moving in opposite directions at once is what the playtest saw as the model bobbing up
 * and down.</p>
 *
 * @param reloadTicks ticks of loading left, zero when no reload is running.
 *
 * <p><b>The server's own number and not a local age.</b> The reload is fifty ticks long and the
 * player is holding a button for all of them, so the motion has to be exactly as long as the
 * reload: a client that counted from the moment the packet landed would draw a motion of a
 * different length than the thing it is drawing.</p>
 */
public record ArcabuzFusePayload(boolean lit, int fuseTicks, boolean loaded, int reloadTicks)
        implements CustomPacketPayload {

    public static final Type<ArcabuzFusePayload> TYPE = new Type<>(
            ResourceIds.rl("arcabuz_fuse"));

    public static final StreamCodec<ByteBuf, ArcabuzFusePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ArcabuzFusePayload::lit,
                    ByteBufCodecs.VAR_INT, ArcabuzFusePayload::fuseTicks,
                    ByteBufCodecs.BOOL, ArcabuzFusePayload::loaded,
                    ByteBufCodecs.VAR_INT, ArcabuzFusePayload::reloadTicks,
                    ArcabuzFusePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
