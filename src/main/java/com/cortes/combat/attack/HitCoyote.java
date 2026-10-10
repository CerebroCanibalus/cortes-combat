package com.cortes.combat.attack;

import com.cortes.combat.CombatTrace;
import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.state.PendingHit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Confirms a swing whose target had slipped out of the cone, while there is still a cone to
 * agree with.
 *
 * <h2>The report, and the two ways it happens</h2>
 *
 * <p>"El golpe a veces se pierde pese a que el indicador de UI muestra que deberías dar el
 * golpe." The crosshair promised the hit and the swing whiffed. Two shapes behind it: the target
 * stepped out between the press and the release (so the client's fresh scan, or the server's
 * strict check, said no a few pixels late), or the server's copy of the world is a tick behind
 * the client's -- entity positions and the player's own rotation both arrive over the wire, so
 * "in the cone" can be true on one side and false on the other <b>without either being wrong
 * about where anything is</b>.</p>
 *
 * <h2>What the window is, and is not</h2>
 *
 * <p>It is the same idea as {@link FallCoyote}, worn by the other side of combat: the fall holds
 * damage owed so a hit can cancel it; this holds a hit owed so the cone can accept it. Five
 * ticks of re-checking the <b>same strict {@link Reach#inCone} the instant path uses</b> -- never
 * a widened one. The window forgives <em>timing</em>: stale packets, a target oscillating on the
 * edge, one frame of interpolation. It never forgives <em>aim</em>, because every re-check reads
 * the player's look direction as it is now: turn away and the first tick after fails.</p>
 *
 * <p>What waits is only the damage and the miss sound. The arm swung on the release (see
 * {@code CombatEngine.resolveTarget}), the cooldown was paid there, and the charge is frozen in
 * the {@link PendingHit} -- the hold is over; this is its verdict arriving late, not a second
 * attack. Connecting recomputes everything else (streak, fall, enchantments) from live state
 * through the exact {@code CombatEngine.landTheHit} the instant path runs, so there is one
 * definition of a landed hit in this project and this is not a second one.</p>
 *
 * <h2>How a window closes</h2>
 *
 * <ul>
 *   <li><b>Connects</b> -- the cone agrees on any tick of the window: the hit lands.</li>
 *   <li><b>Expires</b> -- five ticks of disagreement: the miss sound plays now, where it would
 *       have played on the release before this existed.</li>
 *   <li><b>Target gone</b> -- died, unloaded, wrong entity: nothing to wait for, miss now.</li>
 *   <li><b>Weapon swapped</b> -- the swing belongs to the weapon it was thrown with; a
 *       scroll-wheel inside the window cancels rather than landing with the wrong stats.</li>
 *   <li><b>Player dead</b> -- ends silently; the swing died with them.</li>
 *   <li><b>Another release</b> -- {@code CombatEngine.resolveSwing} clears the window before
 *       resolving the new swing, so at most one pending hit exists and a stale one can never
 *       land for a swing that is no longer happening.</li>
 * </ul>
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class HitCoyote {

    private HitCoyote() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        PendingHit pending = player.getData(ModAttachments.PENDING_HIT.get());
        if (pending.none()) {
            return;
        }
        // Ends the window the moment it would stop being an attack anybody threw. Respawn is
        // already covered by the attachment having no copyOnDeath; this is the dying-in-place
        // half, where the entity survives but the swing should not.
        if (!player.isAlive()) {
            clear(player);
            return;
        }
        String heldId = BuiltInRegistries.ITEM
                .getKey(player.getMainHandItem().getItem()).toString();
        if (!heldId.equals(pending.itemId())) {
            expire(player, "arma cambiada a " + heldId);
            return;
        }
        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        Entity found = player.level().getEntity(pending.targetId());
        if (found instanceof LivingEntity target && target != player && target.isAlive()
                && !target.isSpectator() && !target.isRemoved()) {
            if (Reach.inCone(player, target, MaceAir.reachFor(stats, player))) {
                // Cleared before the hit and not after: landing runs arbitrary code (other mods'
                // attack hooks, death drops, explosions), and a window that outlives its own hit
                // by a re-entrant tick would be a double hit -- the one bug this must not have.
                clear(player);
                debug(player, "coyote CONECTO target=" + pending.targetId()
                        + " cargo=" + pending.chargeRatio());
                CombatEngine.landTheHit(player, target, stats, pending.chargeRatio(), false);
                return;
            }
            if (!pending.expiredAt(player.level().getGameTime())) {
                // Still inside the window and the cone still disagrees: this is the case the
                // window exists for, and it costs one entity lookup and one cone test per tick.
                return;
            }
            expire(player, "ventana vencida con target=" + pending.targetId());
            return;
        }
        expire(player, "objetivo " + pending.targetId() + " ya no es valido");
    }

    /**
     * The window closes without a hit: say so now, where the instant path would have spoken on
     * the release.
     */
    private static void expire(Player player, String reason) {
        clear(player);
        debug(player, "coyote VENCIDO (" + reason + ")");
        CombatEngine.playMiss(player, WeaponStats.of(player.getMainHandItem()));
    }

    private static void clear(Player player) {
        player.setData(ModAttachments.PENDING_HIT.get(), PendingHit.NONE);
    }

    /** One line per window that moves, server side, paired with the client's own in the log. */
    private static void debug(Player player, String message) {
        CombatTrace.server(player.getName().getString(), message);
    }
}
