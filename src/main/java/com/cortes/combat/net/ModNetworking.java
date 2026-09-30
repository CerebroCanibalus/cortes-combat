package com.cortes.combat.net;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.SyncState;
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
        registrar.playToServer(GuardInputPayload.TYPE, GuardInputPayload.STREAM_CODEC,
                ModNetworking::handleGuardInput);
        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                ModNetworking::handleCombatState);
        registrar.playToClient(DebugTogglePayload.TYPE, DebugTogglePayload.STREAM_CODEC,
                ModNetworking::handleDebugToggle);
    }

    private static void handleGuardInput(GuardInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        GuardHandler.setGuard(player, payload.holding());
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
        com.cortes.combat.client.ClientCombatState.set(payload.chargeRatio(), payload.cooldownRatio());
        com.cortes.combat.client.ClientCombatState.setGuard(
                payload.guardStamina(),
                com.cortes.combat.state.GuardState.MAX_STAMINA,
                payload.guardHeld());
        com.cortes.combat.client.GuardInputHandler.acceptServerState(payload.guardHeld());
        com.cortes.combat.client.ChargeInputHandler.acceptServerState(
                payload.chargeRatio(), player.getMainHandItem().isEmpty()
                        ? 0
                        : WeaponStats.of(player.getMainHandItem()).charge().fullChargeTicks());
    }

    private static void handleDebugToggle(DebugTogglePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) {
            return;
        }
        com.cortes.combat.client.ClientDebugFlags.setConeVisible(payload.cone());
        com.cortes.combat.client.ClientDebugFlags.setHudVisible(payload.hud());
    }

    /** Send a debug flag to one player. Development helper. */
    public static void sendDebugToggle(ServerPlayer player, boolean cone, boolean hud) {
        PacketDistributor.sendToPlayer(player, new DebugTogglePayload(cone, hud));
    }

    /**
     * Push the HUD values to one player, but only when they moved enough to see. Sending
     * every tick for every player is 20 packets a second for a bar that rarely changes.
     */
    public static void syncTo(ServerPlayer player) {
        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        float charge = AttackCooldown.chargeRatio(player, stats.charge());
        float cooldown = player.getData(ModAttachments.COOLDOWN.get())
                .ratio(AttackCooldown.intervalFor(player));
        com.cortes.combat.state.GuardState guard =
                player.getData(ModAttachments.GUARD_STAMINA.get());
        int stamina = guard.stamina();
        boolean held = guard.isActive();

        SyncState last = player.getData(ModAttachments.LAST_SYNCED.get());
        if (!last.isDirty(charge, cooldown, stamina, held, SYNC_EPSILON)) {
            return;
        }
        player.setData(ModAttachments.LAST_SYNCED.get(),
                new SyncState(charge, cooldown, stamina, held));
        PacketDistributor.sendToPlayer(player,
                new CombatStatePayload(charge, cooldown, stamina, held));
    }

}
