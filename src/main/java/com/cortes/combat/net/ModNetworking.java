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
                });        registrar.playToServer(SpecialHeldPayload.TYPE,
                SpecialHeldPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        com.cortes.combat.special.Arcabuz.setHeld(sp, payload.held());
                        // **Y el segundo que escucha el boton derecho.** El flag es del BOTON y no
                        // del arcabuz, asi que cualquiera que tenga algo que hacer mientras este
                        // abajo tiene que enterarse: la pala carga el orbe asi. Que esto viva en el
                        // handler del arcabuz es historia, no diseño.
                        com.cortes.combat.special.SpecialCharges.setHeld(sp, payload.held());
                    }
                });
        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                ModNetworking::handleCombatState);
        registrar.playToClient(DebugTogglePayload.TYPE, DebugTogglePayload.STREAM_CODEC,
                ModNetworking::handleDebugToggle);
        registrar.playToClient(CombatFeedbackPayload.TYPE, CombatFeedbackPayload.STREAM_CODEC,
                ModNetworking::handleCombatFeedback);
        registrar.playToClient(ParryStatePayload.TYPE, ParryStatePayload.STREAM_CODEC,
                ModNetworking::handleParryState);
        registrar.playToClient(ArcabuzFusePayload.TYPE, ArcabuzFusePayload.STREAM_CODEC,
                ModNetworking::handleArcabuzFuse);

        // Another player's combat state, so this client can work out their pose.
        //
        // **It is the same shape of question as `CombatStatePayload` and not the same question.**
        // That one goes to a player about their own HUD; this one goes to everyone who can see a
        // given player, about that player. The destinations differ, so the packets differ.
        registrar.playToClient(PlayerCombatStatePayload.TYPE,
                PlayerCombatStatePayload.STREAM_CODEC,
                ModNetworking::handlePlayerCombatState);

        // The three moments a clock cannot work out from state: the strike, the sweep, the cancel.
        registrar.playToClient(CombatEventPayload.TYPE, CombatEventPayload.STREAM_CODEC,
                ModNetworking::handleCombatEvent);

        // "You fired", and the things it reached. The server cannot place the muzzle flash --
        // the weapon's transform is the client's, and the only point the server can produce is
        // the player's eye, which is on the view axis and reads as the screen flickering. So it
        // says WHAT happened and the client works out WHERE the gun is.
        registrar.playToClient(ArcabuzShotPayload.TYPE, ArcabuzShotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof net.minecraft.client.player.LocalPlayer) {
                        com.cortes.combat.client.ClientArcabuzShot.arm(payload.targets());
                    }
                });
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
     * One other player's state arrived, and this client now has a clock for them.
     *
     * <p><b>The id in the payload is resolved against the level rather than trusted.</b> The state
     * carries a network entity id so the receiving side does not have to match names or UUIDs, but
     * an id can name something that is not a player: an entity that has just despawned, one that
     * left tracking, or an armor stand whose id this client has not heard about yet. So the lookup
     * is a question with an answer that can be "no", and a "no" is a state that is simply not
     * recorded.</p>
     *
     * <p><b>And the state's own id is what is kept, not the entity's.</b> They are the same number
     * today, but the key is the one the next packet will name: if the entity is replaced by one
     * with a different id, the old clock goes with the old id rather than the new player inheriting
     * somebody's half-finished swing.</p>
     *
     * <p>Gated on {@code LocalPlayer} like every other client-side handler here, because this class
     * is loaded on a dedicated server too and {@code context.player()} is a {@code ServerPlayer}
     * there. The pattern is not defensive: the branch that would fail is never taken.</p>
     */
    private static void handlePlayerCombatState(PlayerCombatStatePayload payload,
                                               IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer local)) {
            return;
        }
        PlayerCombatState state = payload.state();
        net.minecraft.world.entity.Entity found = local.level().getEntity(state.entityId());
        if (!(found instanceof net.minecraft.client.player.AbstractClientPlayer player)) {
            // **Forget rather than record a partial state.**
            //
            // A clock with no profiles on it still produces a pose -- just one with no charge and
            // no guard, silently. That is the shape of bug this project has already paid for twice,
            // and a player rendered half-animated is worse than one rendered standing still,
            // because the first looks intentional.
            //
            // Forgetting is also what stops a leak: without this, an id that keeps resolving to
            // nothing keeps an entry alive forever.
            com.cortes.combat.client.RemotePoseStates.forget(state.entityId());
            return;
        }
        com.cortes.combat.client.RemotePoseStates.accept(state, player);
    }

    /**
     * A moment arrived, and one clock has to hear it.
     *
     * <p><b>Resolved by id, and a clock that does not exist is not created here.</b> An event for a
     * player nobody has a clock for cannot be played: the clock is what turns the charge into a
     * strike length and a swing age, and a clock made from this event alone would be a strike with
     * no state behind it. The state packet is what creates clocks; this one only moves them.</p>
     *
     * <p>That is also what makes a lost event harmless rather than wrong. The clock keeps running
     * from the last state it had, and the next event arrives on the next action &mdash; a weapon
     * that misses one strike is a weapon one beat out of date, not a weapon animating a strike
     * that never happened.</p>
     */
    private static void handleCombatEvent(CombatEventPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) {
            return;
        }
        CombatEventPayload.Kind kind = payload.kind();
        int id = payload.entityId();
        switch (kind) {
            case STRIKE -> com.cortes.combat.client.RemotePoseStates.strike(
                    id, payload.charge(), payload.durationTicks());
            case SWEEP -> com.cortes.combat.client.RemotePoseStates.sweep(id);
            case CANCEL -> com.cortes.combat.client.RemotePoseStates.cancel(id);
        }
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
                payload.lit(), payload.fuseTicks(), payload.loaded(), payload.reloadTicks());
    }

    /**
     * Tell one player what the arcabuz is doing, so the model can burn through the fuse.
     *
     * <p><b>Four numbers and they are four states, not one state with decoration.</b> Every call
     * site below says which of them it is sending, because the two that were wrong before were
     * wrong in exactly this way: the lit packet used to say {@code loaded = false} and the press
     * used to say nothing at all, so the client spent a second showing an unloaded gun and a
     * reload with no motion on it.</p>
     *
     * @param fuseTicks   the whole fuse length on the light, or zero otherwise
     * @param loaded      whether the chamber holds its charge; <b>true while the fuse burns</b>
     * @param reloadTicks ticks of loading left, zero when no reload is running
     */
    public static void sendArcabuzFuse(ServerPlayer player, boolean lit, int fuseTicks,
                                       boolean loaded, int reloadTicks) {
        PacketDistributor.sendToPlayer(player,
                new ArcabuzFusePayload(lit, fuseTicks, loaded, reloadTicks));
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

        float charged = CombatEngine.resolveSwing(player, payload.entityId());

        // **And then the strike is told to everybody watching.**
        //
        // This is the moment the wire state cannot carry. The chop is born on the release with the
        // charge it was released at, and a state packet saying "the button came up" does not say
        // what it was worth on the way -- so a remote client given only state plays a swing that
        // is a tap, always.
        //
        // **`NO_SWING` is a distinct answer from zero**, and this is the line that reads it: a
        // release with nothing open is not a strike and must not announce one, while a release
        // that hit nothing is a strike at no charge and does.
        if (charged != CombatEngine.NO_SWING) {
            PacketDistributor.sendToPlayersTrackingEntity(player,
                    CombatEventPayload.strike(player.getId(), charged,
                            WeaponStats.attackInterval(player)));
        } else {
            PacketDistributor.sendToPlayersTrackingEntity(player,
                    CombatEventPayload.cancel(player.getId()));
        }
    }

    private static void handleCombatState(CombatStatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer player)) {
            return;
        }
        com.cortes.combat.client.ClientCombatState.set(payload.chargeRatio(), payload.cooldownRatio());
        com.cortes.combat.client.ClientCombatState.setSpecialCharge(payload.specialCharge());
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
        // **La carga del boton derecho sale de la MISMA funcion que la de la pose**, y no de una
        // cuenta escrita aca: dos copias de "ticks sobre el tope" es lo que este proyecto ya pago
        // con una barra que iba por una funcion distinta que el dano.
        float specialCharge =
                com.cortes.combat.net.PlayerStateSync.specialChargeRatio(player);
        if (!last.isDirty(charge, cooldown, stamina, max, held, specialCharge, SYNC_EPSILON)) {
            return;
        }
        player.setData(ModAttachments.LAST_SYNCED.get(),
                new SyncState(charge, cooldown, stamina, max, held, specialCharge));
        PacketDistributor.sendToPlayer(player,
                new CombatStatePayload(charge, cooldown, stamina, max, held, specialCharge));
    }

}
