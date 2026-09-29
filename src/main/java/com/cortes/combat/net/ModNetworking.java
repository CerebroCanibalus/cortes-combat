package com.cortes.combat.net;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.ChargeState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Central registration of every payload, plus the server side of charge input and the
 * outbound state sync.
 */
public final class ModNetworking {

    /** Only send when a value actually moved enough to be visible. */
    private static final float SYNC_EPSILON = 0.01F;

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ChargeInputPayload.TYPE, ChargeInputPayload.STREAM_CODEC,
                ModNetworking::handleChargeInput);
        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                ModNetworking::handleCombatState);
    }

    private static void handleChargeInput(ChargeInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ChargeState current = player.getData(ModAttachments.CHARGE.get());
        if (payload.holding()) {
            // Already holding: ignore. A client spamming "down" must not accumulate charge
            // faster than holding the button honestly would.
            if (!current.holding()) {
                AttackCooldown.beginCharge(player);
            }
        } else {
            AttackCooldown.endCharge(player);
        }
    }

    private static void handleCombatState(CombatStatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer player)) {
            return;
        }
        com.cortes.combat.client.ChargeInputHandler.acceptServerState(
                payload.chargeRatio(), player.getMainHandItem().isEmpty()
                        ? 0
                        : WeaponStats.of(player.getMainHandItem()).charge().fullChargeTicks());
    }

    /**
     * Push the HUD values to one player, but only when they moved enough to see. Sending
     * every tick for every player is 20 packets a second for a bar that rarely changes.
     */
    public static void syncTo(ServerPlayer player, float lastCharge, float lastCooldown) {
        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        float charge = AttackCooldown.chargeRatio(player, stats.charge());
        float cooldown = player.getData(ModAttachments.COOLDOWN.get())
                .ratio(AttackCooldown.intervalFor(player));

        if (Math.abs(charge - lastCharge) < SYNC_EPSILON && Math.abs(cooldown - lastCooldown) < SYNC_EPSILON) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new CombatStatePayload(charge, cooldown));
    }
}
