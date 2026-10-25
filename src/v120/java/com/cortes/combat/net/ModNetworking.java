package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.CombatEngine;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.platform.CombatPackets;
import com.cortes.combat.platform.CombatState;
import com.cortes.combat.platform.ResourceIds;
import com.cortes.combat.state.SyncState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The 1.20.1 twin of the network registry: one {@code SimpleChannel} instead of payload
 * registrations, same 14 messages, same handlers, same send helpers.
 *
 * <p><b>Registration rides the mod bus through the shared constructor call.</b> The shared
 * {@code CortesCombat} constructor calls {@code modEventBus.addListener(ModNetworking::register)}
 * on both legs; here that method takes the 1.20.1 setup event instead of the 1.21.1 payload
 * event, and each leg resolves the same call site to its own loader. No shared line knows
 * which version it compiled against, and there is exactly one registration path (no
 * self-registration here) so a message can never be registered twice. (⌒‿⌒)</p>
 *
 * <p>Every handler ends with {@code setPacketHandled(true)} and runs game logic inside
 * {@code enqueueWork}: a 1.20.1 channel handler runs on the network thread, and the camera-shake
 * lesson (touching client state from the wrong thread makes the effect silently vanish) applies
 * to the server thread the same way.</p>
 */
public final class ModNetworking {

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceIds.rl("main"), () -> "1", s -> true, s -> true);

    /** Only send when a value actually moved enough to be visible. */
    private static final float SYNC_EPSILON = 0.01F;

    private ModNetworking() {
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        registerMessages();
    }

    static void registerMessages() {
        int id = 0;
        CHANNEL.messageBuilder(SwingPayload.class, id++).encoder(SwingPayload::encode)
                .decoder(SwingPayload::decode).consumerMainThread(ModNetworking::handleSwing).add();
        CHANNEL.messageBuilder(GuardInputPayload.class, id++).encoder(GuardInputPayload::encode)
                .decoder(GuardInputPayload::decode).consumerMainThread(ModNetworking::handleGuardInput).add();
        CHANNEL.messageBuilder(SpecialPayload.class, id++).encoder(SpecialPayload::encode)
                .decoder(SpecialPayload::decode).consumerMainThread(ModNetworking::handleSpecial).add();
        CHANNEL.messageBuilder(SpecialHeldPayload.class, id++).encoder(SpecialHeldPayload::encode)
                .decoder(SpecialHeldPayload::decode).consumerMainThread(ModNetworking::handleSpecialHeld).add();
        CHANNEL.messageBuilder(CombatStatePayload.class, id++).encoder(CombatStatePayload::encode)
                .decoder(CombatStatePayload::decode).consumerMainThread(ModNetworking::handleCombatState).add();
        CHANNEL.messageBuilder(DebugTogglePayload.class, id++).encoder(DebugTogglePayload::encode)
                .decoder(DebugTogglePayload::decode).consumerMainThread(ModNetworking::handleDebugToggle).add();
        CHANNEL.messageBuilder(CombatFeedbackPayload.class, id++).encoder(CombatFeedbackPayload::encode)
                .decoder(CombatFeedbackPayload::decode).consumerMainThread(ModNetworking::handleCombatFeedback).add();
        CHANNEL.messageBuilder(ParryStatePayload.class, id++).encoder(ParryStatePayload::encode)
                .decoder(ParryStatePayload::decode).consumerMainThread(ModNetworking::handleParryState).add();
        CHANNEL.messageBuilder(ArcabuzFusePayload.class, id++).encoder(ArcabuzFusePayload::encode)
                .decoder(ArcabuzFusePayload::decode).consumerMainThread(ModNetworking::handleArcabuzFuse).add();
        CHANNEL.messageBuilder(CameraShakePayload.class, id++).encoder(CameraShakePayload::encode)
                .decoder(CameraShakePayload::decode).consumerMainThread(ModNetworking::handleCameraShake).add();
        CHANNEL.messageBuilder(PlayerCombatStatePayload.class, id++).encoder(PlayerCombatStatePayload::encode)
                .decoder(PlayerCombatStatePayload::decode).consumerMainThread(ModNetworking::handlePlayerCombatState).add();
        CHANNEL.messageBuilder(CombatEventPayload.class, id++).encoder(CombatEventPayload::encode)
                .decoder(CombatEventPayload::decode).consumerMainThread(ModNetworking::handleCombatEvent).add();
        CHANNEL.messageBuilder(ArcabuzShotPayload.class, id++).encoder(ArcabuzShotPayload::encode)
                .decoder(ArcabuzShotPayload::decode).consumerMainThread(ModNetworking::handleArcabuzShot).add();
        CHANNEL.messageBuilder(DelayedSoundPayload.class, id++).encoder(DelayedSoundPayload::encode)
                .decoder(DelayedSoundPayload::decode).consumerMainThread(ModNetworking::handleDelayedSound).add();
    }

    private static void handleSwing(SwingPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraftforge.network.NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (payload.pressed()) {
                boolean opened = AttackCooldown.beginCharge(player);
                if (!opened) {
                    // Press estacionado: mismo arreglo que la pata 1.21.1 (ver el comentario
                    // alla). El cooldown de aca esta un par de ticks por detras del del cliente;
                    // si el gate pasa dentro de la ventana, el tick de abajo abre la carga.
                    CombatState.setSwingBuffer(player, player.level().getGameTime());
                }
                return;
            }
            float charged = CombatEngine.resolveSwing(player, payload.entityId());
            if (charged != CombatEngine.NO_SWING) {
                CombatPackets.sendToPlayersTrackingEntity(player,
                        CombatEventPayload.strike(player.getId(), charged,
                                WeaponStats.attackInterval(player)));
            } else {
                CombatPackets.sendToPlayersTrackingEntity(player,
                        CombatEventPayload.cancel(player.getId()));
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleGuardInput(GuardInputPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraftforge.network.NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                GuardHandler.setGuard(player, payload.holding());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSpecial(SpecialPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraftforge.network.NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.cortes.combat.special.SpecialHandler.resolve(player, payload.stack());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSpecialHeld(SpecialHeldPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraftforge.network.NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.cortes.combat.special.Arcabuz.setHeld(player, payload.held());
                com.cortes.combat.special.SpecialCharges.setHeld(player, payload.held());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleCombatState(CombatStatePayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        // Client-bound and main-thread already; the LocalPlayer gate keeps dedicated servers out.
        net.minecraft.client.player.LocalPlayer player =
                net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        com.cortes.combat.client.ClientCombatState.set(payload.chargeRatio(), payload.cooldownRatio());
        com.cortes.combat.client.ClientCombatState.setSpecialCharge(payload.specialCharge());
        com.cortes.combat.client.ClientCombatState.setGuard(
                payload.guardStamina(), payload.guardMax(), payload.guardHeld());
        com.cortes.combat.client.GuardInputHandler.acceptServerState(payload.guardHeld());
        com.cortes.combat.client.SwingInputHandler.acceptServerState(
                payload.chargeRatio(), player.getMainHandItem().isEmpty()
                        ? 0
                        : WeaponStats.of(player.getMainHandItem()).charge().fullChargeTicks());
    }

    private static void handleParryState(ParryStatePayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        com.cortes.combat.client.ClientCombatState.setParry(payload.active(), payload.cooldown());
    }

    private static void handlePlayerCombatState(PlayerCombatStatePayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraft.client.player.LocalPlayer local =
                net.minecraft.client.Minecraft.getInstance().player;
        if (local == null) {
            return;
        }
        PlayerCombatState state = payload.state();
        net.minecraft.world.entity.Entity found = local.level().getEntity(state.entityId());
        if (!(found instanceof net.minecraft.client.player.AbstractClientPlayer player)) {
            com.cortes.combat.client.RemotePoseStates.forget(state.entityId());
            return;
        }
        com.cortes.combat.client.RemotePoseStates.accept(state, player);
    }

    private static void handleCombatEvent(CombatEventPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        if (net.minecraft.client.Minecraft.getInstance().player == null) {
            return;
        }
        int id = payload.entityId();
        switch (payload.kind()) {
            case STRIKE:
                com.cortes.combat.client.RemotePoseStates.strike(
                        id, payload.charge(), payload.durationTicks());
                break;
            case SWEEP:
                com.cortes.combat.client.RemotePoseStates.sweep(id);
                break;
            case CANCEL:
                com.cortes.combat.client.RemotePoseStates.cancel(id);
                break;
        }
    }

    private static void handleArcabuzFuse(ArcabuzFusePayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraftforge.network.NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> com.cortes.combat.client.ArcabuzFuseState.accept(
                payload.lit(), payload.fuseTicks(), payload.loaded(), payload.reloadTicks()));
        context.setPacketHandled(true);
    }

    private static void handleCameraShake(CameraShakePayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        // enqueueWork, same as the 1.21.1 twin: this handler runs on the network thread and
        // CameraShake touches client state the render thread owns.
        ctx.get().enqueueWork(() -> com.cortes.combat.client.CameraShake.accept(payload));
        ctx.get().setPacketHandled(true);
    }

    private static void handleArcabuzShot(ArcabuzShotPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraftforge.network.NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                com.cortes.combat.client.ClientArcabuzShot.arm(payload.targets());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleDelayedSound(DelayedSoundPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        net.minecraft.client.player.LocalPlayer local =
                net.minecraft.client.Minecraft.getInstance().player;
        if (local != null) {
            com.cortes.combat.client.DelayedSounds.queue(
                    payload.sound(),
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0F, payload.pitch(), payload.delayTicks());
        }
    }

    private static void handleCombatFeedback(CombatFeedbackPayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        com.cortes.combat.client.CrosshairFeedback.onEvent(payload.kind());
    }

    private static void handleDebugToggle(DebugTogglePayload payload,
            java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        if (net.minecraft.client.Minecraft.getInstance().player == null) {
            return;
        }
        com.cortes.combat.client.ClientDebugFlags.setConeVisible(payload.cone());
        com.cortes.combat.client.ClientDebugFlags.setHudVisible(payload.hud());
        com.cortes.combat.CombatTrace.set(payload.trace());
        if (payload.dump()) {
            com.cortes.combat.client.HudGeometryDump.print();
        }
        if (payload.input()) {
            String report = com.cortes.combat.client.InputDiagnostic.report();
            com.cortes.combat.CombatTrace.client(report);
            net.minecraft.client.player.LocalPlayer local =
                    net.minecraft.client.Minecraft.getInstance().player;
            if (local != null) {
                local.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(report), false);
            }
        }
    }

    public static void sendArcabuzFuse(ServerPlayer player, boolean lit, int fuseTicks,
            boolean loaded, int reloadTicks) {
        CombatPackets.sendToPlayer(player,
                new ArcabuzFusePayload(lit, fuseTicks, loaded, reloadTicks));
    }

    public static void sendDelayedSound(ServerPlayer to, String sound, float pitch,
            int delayTicks) {
        CombatPackets.sendToPlayer(to, new DelayedSoundPayload(sound, pitch, delayTicks));
    }

    public static void sendFeedback(ServerPlayer player, CombatFeedback kind) {
        CombatPackets.sendToPlayer(player, new CombatFeedbackPayload(kind));
    }

    public static void sendParryState(ServerPlayer player, boolean active, int cooldown) {
        CombatPackets.sendToPlayer(player, new ParryStatePayload(active, cooldown));
    }

    public static void sendDebugToggle(ServerPlayer player, boolean cone, boolean hud,
            boolean trace, boolean dump, boolean input) {
        CombatPackets.sendToPlayer(player, new DebugTogglePayload(cone, hud, trace, dump, input));
    }

    public static void syncTo(ServerPlayer player) {
        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        float charge = AttackCooldown.chargeRatio(player, stats.charge());
        float cooldown = CombatState.cooldown(player)
                .ratio(AttackCooldown.intervalFor(player));
        com.cortes.combat.state.GuardState guard = CombatState.guard(player);
        int stamina = guard.stamina();
        int max = guard.max();
        boolean held = guard.isActive();

        SyncState last = CombatState.lastSynced(player);
        float specialCharge =
                com.cortes.combat.net.PlayerStateSync.specialChargeRatio(player);
        // **La condicion invertida del port, y por eso el cooldown "no se mueve".** En 1.21.1 el
        // patron es: si NO dirty -> no mandar nada; si dirty -> setLastSynced + mandar. Aqui quedo
        // al reves: si dirty -> return (nunca manda cuando el estado cambia) y si NO dirty -> manda
        // SIEMPRE el mismo valor congelado. El cliente recibe la barra igual al estado del ultimo
        // world save: cooldown a 100%, carga que nunca aparece y special_charge en cero (sin
        // cooldown) -- los tres sintomas del playtest, un signo de diferencia. (^o^)
        if (!last.isDirty(charge, cooldown, stamina, max, held, specialCharge, SYNC_EPSILON)) {
            return;
        }
        CombatState.setLastSynced(player,
                new SyncState(charge, cooldown, stamina, max, held, specialCharge));
        CombatPackets.sendToPlayer(player,
                new CombatStatePayload(charge, cooldown, stamina, max, held, specialCharge));
    }
}
