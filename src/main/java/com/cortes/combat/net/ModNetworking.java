package com.cortes.combat.net;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.CombatEngine;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.registry.ModAttachments;
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
        registrar.playToServer(SwingPayload.TYPE, SwingPayload.STREAM_CODEC,
                ModNetworking::handleSwing);
        registrar.playToServer(GuardInputPayload.TYPE, GuardInputPayload.STREAM_CODEC,
                ModNetworking::handleGuardInput);
        registrar.playToServer(SpecialPayload.TYPE, SpecialPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        com.cortes.combat.special.SpecialHandler.resolve(sp, payload.stack());
                    }
                });        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                ModNetworking::handleCombatState);
        registrar.playToClient(DebugTogglePayload.TYPE, DebugTogglePayload.STREAM_CODEC,
                ModNetworking::handleDebugToggle);
        registrar.playToClient(CombatFeedbackPayload.TYPE, CombatFeedbackPayload.STREAM_CODEC,
                ModNetworking::handleCombatFeedback);
        registrar.playToClient(ParryStatePayload.TYPE, ParryStatePayload.STREAM_CODEC,
                ModNetworking::handleParryState);
        registrar.playToClient(ArcabuzFusePayload.TYPE, ArcabuzFusePayload.STREAM_CODEC,
                ModNetworking::handleArcabuzFuse);
        registrar.playToClient(DelayedSoundPayload.TYPE, DelayedSoundPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof net.minecraft.client.player.LocalPlayer p) {
                        com.cortes.combat.client.DelayedSounds.queue(
                                payload.sound(),
                                net.minecraft.sounds.SoundSource.PLAYERS,
                                1.0F, payload.pitch(), payload.delayTicks());
                    }
                });
    }

    private static void handleParryState(ParryStatePayload payload, IPayloadContext context) {
        com.cortes.combat.client.ClientCombatState.setParry(payload.active(), payload.cooldown());
    }

    /**
     * The fuse started or stopped, and the client keeps the countdown from there.
     *
     * <p>On the transitions only, for the reason {@link ArcabuzFusePayload} gives. The client's
     * own clock runs against the server's and the jitter between them is normal, so a value that
     * arrives higher than the local one wins -- the same correction
     * {@code ClientCombatState#tickParry} makes, and for the same reason: without it a late
     * correction reads as "the number went up" and a client that mistakes that for a new press
     * restarts something it should have let finish.</p>
     */
    private static void handleArcabuzFuse(ArcabuzFusePayload payload, IPayloadContext context) {
        com.cortes.combat.client.ArcabuzFuseState.accept(
                payload.lit(), payload.fuseTicks(), payload.loaded());
    }

    /**
     * Tell one player what the arcabuz is doing, so the model can burn through the fuse.
     *
     * @param fuseTicks the whole fuse length, or zero when it has gone off
     */
    public static void sendArcabuzFuse(ServerPlayer player, boolean lit, int fuseTicks,
                                       boolean loaded) {
        PacketDistributor.sendToPlayer(player, new ArcabuzFusePayload(lit, fuseTicks, loaded));
    }

    /**
     * Asks one client's game to play a sound a number of ticks from now.
     *
     * <p>To the attacker only. It is their own swing being reported back to them, and a sound every
     * player in the radius heard would be the same hit heard twice from two directions.</p>
     *
     * @param pitch the final pitch, already scaled by whatever the weapon's own pitch is
     */
    public static void sendDelayedSound(
            net.minecraft.server.level.ServerPlayer to, String sound, float pitch, int delayTicks) {
        PacketDistributor.sendToPlayer(to,
                new DelayedSoundPayload(sound, pitch, delayTicks));
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

    /**
     * Tell one player that the parry window opened or closed.
     *
     * <p>Only on the transitions. The client reads this for the sign, not for a progress bar, and
     * a stream of a number nobody plots is a packet per player per tick for the length of a
     * press.</p>
     */
    public static void sendParryState(ServerPlayer player, boolean active, int cooldown) {
        PacketDistributor.sendToPlayer(player, new ParryStatePayload(active, cooldown));
    }

    private static void handleGuardInput(GuardInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        GuardHandler.setGuard(player, payload.holding());
    }

    /**
     * One press, one release, one decision point.
     *
     * <p>The press only opens a swing. Nothing is paid and nothing is hit yet, and the charge
     * starts counting on the server's own ticker. The release closes it, pays the cooldown hit or
     * miss, and applies the damage if the id names a target the cone agrees with.</p>
     *
     * <p>Both edges are gated on the same condition the client used, and that gate is the point
     * rather than an afterthought: a client that spams presses against a cooldown it knows is not
     * ready gets nothing, and a release with no press open behind it is dropped rather than
     * turned into a free attack.</p>
     */
    private static void handleSwing(SwingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.pressed()) {
            AttackCooldown.beginCharge(player);
            org.slf4j.LoggerFactory.getLogger("cortescombat").info(
                    "[server] press de " + player.getName().getString()
                            + " item=" + player.getMainHandItem().getItem());
            return;
        }
        org.slf4j.LoggerFactory.getLogger("cortescombat").info(
                "[server] release de " + player.getName().getString()
                        + " objetivo=" + payload.entityId()
                        + " abierto=" + player.getData(ModAttachments.CHARGE.get()).holding()
                        + " ticks=" + player.getData(ModAttachments.CHARGE.get()).ticks());
        CombatEngine.resolveSwing(player, payload.entityId());
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
        com.cortes.combat.client.SwingInputHandler.acceptServerState(
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
        com.cortes.combat.CombatTrace.set(payload.trace());

        if (payload.dump()) {
            com.cortes.combat.client.HudGeometryDump.print();
        }

        if (payload.input()) {
            // Both the chat line and the log, on purpose: the log is what gets pasted into a bug
            // report and the chat line is what gets read first. The report this answers had
            // neither.
            String report = com.cortes.combat.client.InputDiagnostic.report();
            com.cortes.combat.CombatTrace.client(report);
            if (context.player() instanceof net.minecraft.client.player.LocalPlayer local) {
                local.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(report), false);
            }
        }
    }

    /**
     * Send the debug flags to one player. Development helper.
     *
     * <p>All three go in one packet on purpose. The trace is here for the same reason the cone
     * is: a command cannot reach a client-side static, and the flag that is missing is the one
     * nobody reads.</p>
     */
    public static void sendDebugToggle(ServerPlayer player, boolean cone, boolean hud,
                                        boolean trace, boolean dump, boolean input) {
        PacketDistributor.sendToPlayer(player,
                new DebugTogglePayload(cone, hud, trace, dump, input));
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
