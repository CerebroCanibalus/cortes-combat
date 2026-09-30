package com.cortes.combat.net;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.CombatFeedback;
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
        registrar.playToServer(WhiffPayload.TYPE, WhiffPayload.STREAM_CODEC,
                ModNetworking::handleWhiff);
        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                ModNetworking::handleCombatState);
        registrar.playToClient(DebugTogglePayload.TYPE, DebugTogglePayload.STREAM_CODEC,
                ModNetworking::handleDebugToggle);
        registrar.playToClient(CombatFeedbackPayload.TYPE, CombatFeedbackPayload.STREAM_CODEC,
                ModNetworking::handleCombatFeedback);
    }

    private static void handleCombatFeedback(CombatFeedbackPayload payload,
                                             IPayloadContext context) {
        com.cortes.combat.client.CrosshairFeedback.onEvent(payload.kind());
    }

    /**
     * Tell one player that a hit landed, so their crosshair can react.
     *
     * <p>Sent from the server rather than predicted on the client. A client that flashed on its
     * own swing would also flash on a swing into a wall or into a shield, and a crosshair that
     * cries wolf is worse than one that stays quiet: the player learns to wait for the colour
     * instead of watching for the hit.</p>
     */
    public static void sendFeedback(ServerPlayer player, CombatFeedback kind) {
        PacketDistributor.sendToPlayer(player, new CombatFeedbackPayload(kind));
    }

    private static void handleGuardInput(GuardInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        GuardHandler.setGuard(player, payload.holding());
    }

    /**
     * A swing at nothing still costs the cooldown.
     *
     * <p>Guarded by {@code isReady} exactly like a real attack, and for the same reason: a
     * client that spams this must not be able to extend its own cooldown, which would turn a
     * missed swing into a way to lock yourself out.</p>
     */
    private static void handleWhiff(WhiffPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!AttackCooldown.isReady(player)) {
            return;
        }
        AttackCooldown.consume(player, AttackCooldown.intervalFor(player));
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
                payload.guardMax(),
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
        int max = guard.max();
        boolean held = guard.isActive();

        SyncState last = player.getData(ModAttachments.LAST_SYNCED.get());
        if (!last.isDirty(charge, cooldown, stamina, max, held, SYNC_EPSILON)) {
            return;
        }
        player.setData(ModAttachments.LAST_SYNCED.get(),
                new SyncState(charge, cooldown, stamina, max, held));
        PacketDistributor.sendToPlayer(player,
                new CombatStatePayload(charge, cooldown, stamina, max, held));
    }

}
