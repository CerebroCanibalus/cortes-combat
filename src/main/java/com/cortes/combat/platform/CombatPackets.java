package com.cortes.combat.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * How a packet leaves, in one place per leg.
 *
 * <p>The 1.20.1 loader has no static {@code PacketDistributor.sendTo*} helpers: sending goes
 * through a channel and a target object. Every call site would have to spell the difference, so
 * instead they spell the <em>intention</em> and the leg spells the mechanism. Same method names
 * on both legs; the parameter types differ (a payload <em>is</em> a {@code CustomPacketPayload}
 * here, it is just an object there), which is fine because each leg compiles on its own.</p>
 *
 * <p>The send sites keep their shape: {@code CombatPackets.sendToServer(new SwingPayload(...))}
 * reads the same on both legs, and the leg decides what a send is. (⌒‿⌒)</p>
 */
public final class CombatPackets {

    private CombatPackets() {
    }

    public static void sendToServer(CustomPacketPayload msg) {
        PacketDistributor.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload msg) {
        PacketDistributor.sendToPlayer(player, msg);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload msg) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, msg);
    }

    public static void sendToPlayersNear(ServerLevel level, ServerPlayer excluded, double x,
            double y, double z, double radius, CustomPacketPayload msg) {
        PacketDistributor.sendToPlayersNear(level, excluded, x, y, z, radius, msg);
    }
}
