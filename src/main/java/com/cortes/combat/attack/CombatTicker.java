package com.cortes.combat.attack;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.guard.GuardRules;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.client.RemotePoseStates;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.net.PlayerStateSync;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.CooldownState;
import com.cortes.combat.state.GuardState;
import com.cortes.combat.state.ParryState;
import com.cortes.combat.state.StreakState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;


/**
 * Advances every piece of per-tick combat state, in one place, server side only.
 *
 * <p>One handler instead of five: the ordering between them is a rule (a cooldown that runs
 * out this tick lets you attack this tick), and rules that are not written down in one place
 * stop being true as soon as someone adds a sixth listener.</p>
 *
 * <p>Client side is handled separately and differently: the client predicts to draw and never
 * decides. See ARCHITECTURE.md section 1.</p>
 */
public final class CombatTicker {

    private CombatTicker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tickCooldown(player);
        tickSwingBuffer(player);
        tickCharge(player);
        tickGuard(player);
        tickStreak(player);
        tickParry(player);
        // The lunge's window. It moves nobody: it measures how far the player got against the
        // material that opened it, and sweeps whatever ground was actually crossed. See Lunge for
        // why the server could never have thrown the player itself.
        com.cortes.combat.special.Lunge.tick(player);
        // The arcabuz's fuse AND its reload. Twenty ticks of countdown and then the blast; fifty ticks
        // of filling that only advance while the right button is up. Both live here because both
        // are state the player started and the server has to finish, and a press arriving in the
        // middle of either is refused by the state rather than by a guard on this line.
        com.cortes.combat.special.Arcabuz.tick(player);
        // **Y la carga del boton derecho, que es el mismo tipo de cosa y por eso vive al lado.** El
        // orbe de la pala se carga sosteniendo y sale al soltar; sin este tick el attachment se
        // quedaria en cero y el disparo nunca llegaria al tope.
        com.cortes.combat.special.SpecialCharges.tick(player);
        ModNetworking.syncTo(player);
        // **After every other tick above, and the order is load-bearing.**
        //
        // This describes the player to the clients watching them, so it has to read the states
        // those ticks just advanced. Sent before `tickCharge`, a charge would travel one tick
        // behind the arm it is describing -- which on a three second axe commit is a charge bar
        // and an arm that disagree for three seconds, the exact thing `ClientTasks` documents for
        // the local player's own ordering.
        //
        // It goes out every tick while something is happening and once a second when it is not.
        // See `PlayerStateSync` for why a counter that climbs by one is not an edge.
        PlayerStateSync.sync(player);
    }

    /**
     * A player stopped being watched, so this client should stop remembering them.
     *
     * <p><b>Without this, every player who walks out of range leaves a clock behind.</b> Not a
     * crash and not a visible bug: an entity id is never reused within a session, so the stale
     * entry is only memory. A server with players coming and going accumulates one
     * {@code ViewModelMotion}, one previous pose and one current pose per player who has ever been
     * in range, for as long as the process runs.</p>
     *
     * <p>It is registered on the game bus and reads a client-side class, and that is the same
     * asymmetry every other handler here has: the class is loaded on a dedicated server and this
     * method is never reached there, because the event that reaches it only fires where there are
     * players watching.</p>
     */
    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof net.minecraft.client.player.LocalPlayer) {
            RemotePoseStates.forget(event.getTarget().getId());
        }
    }

    /**
     * A session starts with nothing open.
     *
     * <p>Without this the charge is a real bug rather than a cosmetic one. It lives in a
     * persistent attachment, so a player who logs out mid-hold has {@code holding} still set
     * when they come back and the ticker goes on counting a hold that no button is making: the
     * release that would have closed it went to a connection that no longer existed. The result
     * is a player who rejoins silently charged to full, and a free fully loaded swing out of
     * it.</p>
     *
     * <p>The cooldown is dropped too, because a session that opens with a partly spent bar is
     * just confusing: nothing about reconnecting should cost a swing.</p>
     *
     * <p>The guard is lowered for the same reason the charge is cleared: {@code held} is a button,
     * and a button that is still set from a connection that no longer exists is a state nothing in
     * the game is producing. The playtest hit this on joining -- a session that opened with the
     * guard already up, orbs lit, and no way to block. Attachments are not written to disk so it
     * should already start clean, and this is the line that makes that true by intent rather than
     * by a property of the storage nobody chose on purpose.</p>
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttackCooldown.endCharge(player);
            AttackCooldown.resetCooldown(player);
            GuardHandler.setGuard(player, false);
            // Y el press estacionado: una sesion que abre con un buffer es un press de una
            // conexion que ya no existe. Misma regla que el resto del arranque de aca.
            CombatState.setSwingBuffer(player, PressBuffer.EMPTY);
            // A lunge in progress is a movement that was never finished. Logging out in the middle
            // of one leaves steps owed to a connection that no longer exists, and the player would
            // be teleported the rest of the way on joining.
            com.cortes.combat.special.Lunge.clear(player);
            // The arcabuz for the same reason and a sharper one. `loaded` is a button the player
            // pressed on a connection that no longer exists: a session that opens with a loaded
            // gun is a free shot nobody paid a firestone for, and it is the guard's `held` bug in
            // a different item.
            com.cortes.combat.special.Arcabuz.clear(player);
        }
    }

    private static void tickCooldown(ServerPlayer player) {
        CooldownState cooldown = CombatState.cooldown(player);
        if (cooldown.isReady()) {
            return;
        }
        CombatState.setCooldown(player, cooldown.tick());
    }

    /**
     * El press que el server rechazo y quedo estacionado, disparado aqui donde el cooldown
     * expira.
     *
     * <p>Va justo despues de {@code tickCooldown} y el orden es la parte de la feature: un
     * cooldown que muere este tick abre la carga este mismo tick, y "apenas puede" significa
     * eso. El gate se vuelve a preguntar entero -- el press estacionado paga las mismas cuatro
     * puertas del press real (cooldown, guardia, item en uso, espectador), no menos y no mas.</p>
     *
     * <p>Limpia el buffer ahi mismo, en los dos caminos, porque un press que se tiene que
     * "recordar para despues de la vida y la muerte de este tick" no es un estado que quiera
     * sobrevivir a nada.</p>
     */
    private static void tickSwingBuffer(ServerPlayer player) {
        long parked = CombatState.swingBuffer(player);
        if (parked == PressBuffer.EMPTY) {
            return;
        }
        long now = player.level().getGameTime();
        if (PressBuffer.expired(parked, now)) {
            CombatState.setSwingBuffer(player, PressBuffer.EMPTY);
            return;
        }
        boolean gateOpen = AttackCooldown.isReady(player)
                && !GuardHandler.isGuarding(player)
                && !player.isUsingItem()
                && !player.isSpectator();
        if (PressBuffer.shouldFire(parked, now, gateOpen)) {
            CombatState.setSwingBuffer(player, PressBuffer.EMPTY);
            AttackCooldown.beginCharge(player);
        }
    }

    /**
     * Charge only accumulates while the attack button is held. Releasing resets it to zero:
     * the next swing is an uncharged one.
     */
    private static void tickCharge(ServerPlayer player) {
        ChargeState charge = CombatState.charge(player);
        if (!charge.holding()) {
            return;
        }
        CombatState.setCharge(player, charge.advance());
    }

    /** Guard recovers on its own and never touches food. */
    private static void tickGuard(ServerPlayer player) {
        GuardState guard = CombatState.guard(player);
        // The item decides the timings, read fresh every tick so a hotbar swap cannot leave a
        // stale regen rate running.
        GuardStats.Resolved resolved = GuardHandler.bestFor(player);
        GuardProfile profile = resolved.profile();
        boolean staggered = player.getCooldowns().isOnCooldown(resolved.item().getItem());

        // A staggered guard is DOWN, not "raised but broken". Lowering it here rather than in the
        // handler is what makes the orbs dim, what stops the guard coming back up on its own
        // when the cooldown ends, and what clears a `held` flag that no button is holding.
        //
        // That last one is the weird case the playtest hit on joining: `held` is input state,
        // and input state that survives something the player did not do is the definition of a
        // bug that only shows up on a fresh session.
        GuardState resized = guard.withMax(profile.safePoints());
        GuardState settled = staggered ? resized.lower() : resized;

        // One flag, and the two halves are an OR because they are two answers to the same
        // question: can this guard be used right now?
        //
        // <b>Raised</b> costs the regen, because holding is the price of holding.
        //
        // <b>Staggered</b> also costs it, which the playtest added: a broken guard regenerates
        // nothing until its cooldown is over. Points arriving during the stun is the one window
        // where a player who is helpless is also recovering.
        //
        // It lives in GuardRules now, with a test for all four cells, because the version that
        // was here read `isActive() && !staggered` into a parameter that means <em>this stops
        // the regen</em>. Both of those halves look like a negation, so both of them were
        // backwards, and the guard refilled while raised while refusing to while broken. The
        // state class was right the whole time; the caller was where the bug lived, which is
        // exactly the kind of bug a component test cannot see.
        boolean regenPaused = GuardRules.regenPaused(settled.held(), staggered);

        GuardState ticked = settled.tick(profile.regenTicks(), regenPaused);
        if (ticked != settled) {
            CombatState.setGuard(player, ticked);
        }
    }

    /** The warhammer streak only breaks when its 4s window runs out, never on taking damage. */
    private static void tickStreak(ServerPlayer player) {
        StreakState streak = CombatState.hammerStreak(player);
        StreakState ticked = streak.tick();
        if (ticked != streak) {
            CombatState.setHammerStreak(player, ticked);
        }
    }

    /**
     * The parry runs down, and the client is told when the window closes.
     *
     * <p>All of it is in {@link ParryState#step()}, including whether to send. This method writes
     * the answer and sends the packet; it does not decide anything. It used to decide, and the
     * decision it made was "if the window is closed, there is nothing to do" -- which is true of
     * the <em>packet</em> and was silently also taken as true of the <em>countdown</em>, so the
     * five second lockout never ran down and the sabre could be parried once per session.</p>
     */
    private static void tickParry(ServerPlayer player) {
        ParryState.Step step = CombatState.parry(player).step();
        CombatState.setParry(player, step.state());
        if (step.tellClient()) {
            com.cortes.combat.net.ModNetworking.sendParryState(
                    player, false, step.state().cooldownTicks());
        }
    }
}
