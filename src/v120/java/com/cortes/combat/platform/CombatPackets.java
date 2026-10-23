package com.cortes.combat.platform;

import com.cortes.combat.net.ModNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/**
 * The 1.20.1 body of the send facade. Same four intentions as the shared twin; the mechanism is
 * a {@code SimpleChannel} send, because the static {@code PacketDistributor.sendTo*} helpers do
 * not exist here. Messages travel as plain objects and the channel resolves the encoder by
 * runtime class, which is why the parameters can stay untyped. (⌒‿⌒)
 */
public final class CombatPackets {

    private CombatPackets() {
    }

    public static void sendToServer(Object msg) {
        ModNetworking.CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, Object msg) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), msg);
    }

    public static void sendToPlayersNear(ServerLevel level, ServerPlayer excluded, double x,
            double y, double z, double radius, Object msg) {
        ResourceKey<Level> dim = level.dimension();
        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        excluded, x, y, z, radius, dim)),
                msg);
    }
}
