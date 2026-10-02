package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.guard.GuardRules;
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
        // The lunge's window. It moves nobody: it measures how far the player got against the
        // material that opened it, and sweeps whatever ground was actually crossed. See Lunge for
        // why the server could never have thrown the player itself.
        com.cortes.combat.special.Lunge.tick(player);
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
            // A lunge in progress is a movement that was never finished. Logging out in the middle
            // of one leaves steps owed to a connection that no longer exists, and the player would
            // be teleported the rest of the way on joining.
            com.cortes.combat.special.Lunge.clear(player);
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
        ParryState.Step step = player.getData(ModAttachments.PARRY.get()).step();
        player.setData(ModAttachments.PARRY.get(), step.state());
        if (step.tellClient()) {
            com.cortes.combat.net.ModNetworking.sendParryState(
                    player, false, step.state().cooldownTicks());
        }
    }
}
