package com.cortes.combat.attack;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.CombatTrace;
import com.cortes.combat.CortesCombat;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.state.PendingHit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The 1.20.1 wiring of the swing's confirmation window. Same five ticks, older tick.
 *
 * <p>The only change is the ride: {@code TickEvent.PlayerTickEvent} gated to {@code END}
 * instead of the 1.21.1 {@code Post} subclass. The window still forgives timing and never
 * aim -- every re-check reads the live look direction -- and what waits is still only the
 * damage and the miss sound. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class HitCoyote {

    private HitCoyote() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        PendingHit pending = CombatState.pendingHit(player);
        if (pending.none()) {
            return;
        }
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
                // Cleared before the hit and not after: landing runs arbitrary code, and a
                // window that outlives its own hit by a re-entrant tick would be a double hit.
                clear(player);
                debug(player, "coyote CONECTO target=" + pending.targetId()
                        + " cargo=" + pending.chargeRatio());
                CombatEngine.landTheHit(player, target, stats, pending.chargeRatio(), false);
                return;
            }
            if (!pending.expiredAt(player.level().getGameTime())) {
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
        CombatState.setPendingHit(player, PendingHit.NONE);
    }

    /** One line per window that moves, server side, paired with the client's own in the log. */
    private static void debug(Player player, String message) {
        CombatTrace.server(player.getName().getString(), message);
    }
}
