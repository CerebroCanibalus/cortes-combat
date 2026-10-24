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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The 1.20.1 wiring of the per-tick combat state. Same order, older tick.
 *
 * <p>One handler instead of five, and the ordering between the five is the same rule as the
 * shared twin: a cooldown that runs out this tick lets you attack this tick. The ride is
 * {@code TickEvent.PlayerTickEvent} gated to {@code END} -- 1.20.1 has no {@code Post}
 * subclass -- and everything below the gate is identical, including the load-bearing
 * position of {@code PlayerStateSync} after every other tick. (⌒‿⌒)</p>
 */
public final class CombatTicker {

    private CombatTicker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        tickCooldown(player);
        tickCharge(player);
        tickGuard(player);
        tickStreak(player);
        tickParry(player);
        com.cortes.combat.special.Lunge.tick(player);
        com.cortes.combat.special.Arcabuz.tick(player);
        com.cortes.combat.special.SpecialCharges.tick(player);
        ModNetworking.syncTo(player);
        PlayerStateSync.sync(player);
    }

    /**
     * A player stopped being watched, so this client should stop remembering them.
     */
    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof net.minecraft.client.player.LocalPlayer) {
            RemotePoseStates.forget(event.getTarget().getId());
        }
    }

    /**
     * A session starts with nothing open: charge, cooldown, guard, lunge and arcabuz all
     * cleared. See the shared twin for why each line is there rather than implied.
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttackCooldown.endCharge(player);
            AttackCooldown.resetCooldown(player);
            GuardHandler.setGuard(player, false);
            com.cortes.combat.special.Lunge.clear(player);
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
        GuardStats.Resolved resolved = GuardHandler.bestFor(player);
        GuardProfile profile = resolved.profile();
        boolean staggered = player.getCooldowns().isOnCooldown(resolved.item().getItem());

        GuardState resized = guard.withMax(profile.safePoints());
        GuardState settled = staggered ? resized.lower() : resized;

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
