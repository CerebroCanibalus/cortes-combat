package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.registry.ModAttachments;
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
        tickCharge(player);
        tickGuard(player);
        tickStreak(player);
        tickParry(player);
        ModNetworking.syncTo(player);
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
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttackCooldown.endCharge(player);
            AttackCooldown.resetCooldown(player);
        }
    }

    private static void tickCooldown(ServerPlayer player) {
        CooldownState cooldown = player.getData(ModAttachments.COOLDOWN.get());
        if (cooldown.isReady()) {
            return;
        }
        player.setData(ModAttachments.COOLDOWN.get(), cooldown.tick());
    }

    /**
     * Charge only accumulates while the attack button is held. Releasing resets it to zero:
     * the next swing is an uncharged one.
     */
    private static void tickCharge(ServerPlayer player) {
        ChargeState charge = player.getData(ModAttachments.CHARGE.get());
        if (!charge.holding()) {
            return;
        }
        player.setData(ModAttachments.CHARGE.get(), charge.advance());
    }

    /** Guard recovers on its own and never touches food. */
    private static void tickGuard(ServerPlayer player) {
        GuardState guard = player.getData(ModAttachments.GUARD_STAMINA.get());
        // The item decides the timings, read fresh every tick so a hotbar swap cannot leave a
        // stale regen rate running.
        GuardStats.Resolved resolved = GuardHandler.bestFor(player);
        GuardProfile profile = resolved.profile();
        // Same cooldown the handler gates blocking on, so the refill and the stagger are one
        // clock instead of two unrelated timers.
        boolean staggered = player.getCooldowns().isOnCooldown(resolved.item().getItem());
        GuardState resized = guard.withMax(profile.safePoints());
        GuardState ticked = resized.tick(profile.regenTicks(), staggered, profile.stunTicks());
        if (ticked != resized) {
            player.setData(ModAttachments.GUARD_STAMINA.get(), ticked);
        }
    }

    /** The warhammer streak only breaks when its 4s window runs out, never on taking damage. */
    private static void tickStreak(ServerPlayer player) {
        StreakState streak = player.getData(ModAttachments.HAMMER_STREAK.get());
        StreakState ticked = streak.tick();
        if (ticked != streak) {
            player.setData(ModAttachments.HAMMER_STREAK.get(), ticked);
        }
    }

    private static void tickParry(ServerPlayer player) {
        ParryState parry = player.getData(ModAttachments.PARRY.get());
        player.setData(ModAttachments.PARRY.get(), parry.tick());
    }
}
