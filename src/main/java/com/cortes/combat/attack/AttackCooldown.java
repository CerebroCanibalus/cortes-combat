package com.cortes.combat.attack;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.CooldownState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The hard attack speed: the gate that decides whether a swing is allowed at all.
 *
 * <p>This is the permission, not the damage. The old vanilla behaviour multiplied damage by
 * how well timed the click was, which meant spamming paid more. Here a swing is either
 * allowed or it is not, and the damage is flat.</p>
 */
public final class AttackCooldown {

    private AttackCooldown() {
    }

    public static boolean isReady(Player player) {
        return CombatState.cooldown(player).isReady();
    }

    /**
     * Consume one swing: starts the cooldown and spends the accumulated charge.
     * Callers must check {@link #isReady} first; this does not.
     */
    public static void consume(Player player, int attackInterval) {
        CombatState.setCooldown(player, new CooldownState(attackInterval));
        CombatState.setCharge(player, ChargeState.EMPTY);
    }

    /**
     * Open a swing, which is also what starts the charge counting.
     *
     * <p>Refused on a cooldown that is not ready, and refused silently for the same reason the
     * client refuses it: a press that visibly starts something and then does nothing reads as
     * dropped input.</p>
     *
     * <p>Ignored while a swing is already open, which is not a formality. A client that spams
     * presses must not accumulate charge faster than holding the button honestly would, and the
     * charge is the only thing a long hold is actually buying.</p>
     *
     * <p>The client checks the same condition before it ever sends this, from a value that is up
     * to one round trip old. That is why the check is repeated here rather than trusted: the
     * repeat is the rule and the client's copy is only the prediction.</p>
     */
    public static void beginCharge(Player player) {
        if (!isReady(player)) {
            return;
        }
        // Covering and swinging are mutually exclusive, and the server is where that is decided.
        //
        // The client's copy of this gate reads its own prediction, which is at most one round trip
        // behind, so a player who covers and clicks in the same tick gets through it. Repeating
        // the check here is the same reason the readiness check is repeated: the repeat is the
        // rule and the client's copy is only the prediction.
        if (GuardHandler.isGuarding(player)) {
            return;
        }
        ChargeState current = CombatState.charge(player);
        if (!current.holding()) {
            CombatState.setCharge(player, new ChargeState(0, true));
        }
    }

    /** Stop accumulating without spending anything, for a press that was abandoned. */
    public static void endCharge(Player player) {
        CombatState.setCharge(player, CombatState.charge(player).reset());
    }

    /** Drop the cooldown to zero without touching the charge, for a fresh session. */
    public static void resetCooldown(Player player) {
        CombatState.setCooldown(player, CooldownState.READY);
    }

    /**
     * How charged the held weapon is, as a 0..1 ratio, classified.
     *
     * <p>The same {@link AttackTiming#chargeRatioFor} the damage is paid with, and deliberately
     * the only function that turns a tick count into a charge anywhere in the mod. This used to
     * hand out a raw {@code ticks / window} instead, which is the whole reason a two-tick click
     * used to draw a sliver of charge bar and twitch the camera: the damage was thresholded and
     * the readout was not, so the two disagreed about whether a click was a charge, and the
     * readout is the one a player looks at.</p>
     */
    public static float chargeRatio(Player player, ChargeProfile profile) {
        return AttackTiming.chargeRatioFor(
                CombatState.charge(player).ticks(),
                profile.fullChargeTicks(),
                profile.canCharge());
    }

    /**
     * Drop the cooldown to zero. Used when the held item changes, because swapping weapons
     * should not carry a stale cooldown: the new weapon's interval applies from the swap.
     */
    public static void resetOnItemChange(Player player) {
        CombatState.setCooldown(player, CooldownState.READY);
    }

    public static int intervalFor(LivingEntity holder) {
        return WeaponStats.attackInterval(holder);
    }

    public static ItemStack heldItem(Player player) {
        return player.getMainHandItem();
    }
}
