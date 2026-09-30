package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.registry.ModAttachments;
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
        return player.getData(ModAttachments.COOLDOWN.get()).isReady();
    }

    /**
     * Consume one swing: starts the cooldown and spends the accumulated charge.
     * Callers must check {@link #isReady} first; this does not.
     */
    public static void consume(Player player, int attackInterval) {
        player.setData(ModAttachments.COOLDOWN.get(), new CooldownState(attackInterval));
        player.setData(ModAttachments.CHARGE.get(), ChargeState.EMPTY);
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
        ChargeState current = player.getData(ModAttachments.CHARGE.get());
        if (!current.holding()) {
            player.setData(ModAttachments.CHARGE.get(), new ChargeState(0, true));
        }
    }

    /** Stop accumulating without spending anything, for a press that was abandoned. */
    public static void endCharge(Player player) {
        player.setData(ModAttachments.CHARGE.get(), player.getData(ModAttachments.CHARGE.get()).reset());
    }

    /** Drop the cooldown to zero without touching the charge, for a fresh session. */
    public static void resetCooldown(Player player) {
        player.setData(ModAttachments.COOLDOWN.get(), CooldownState.READY);
    }

    /** How charged the held weapon currently is, as a 0..1 ratio. */
    public static float chargeRatio(Player player, ChargeProfile profile) {
        if (!profile.canCharge()) {
            return 0.0F;
        }
        return player.getData(ModAttachments.CHARGE.get()).ratio(profile.fullChargeTicks());
    }

    /**
     * Drop the cooldown to zero. Used when the held item changes, because swapping weapons
     * should not carry a stale cooldown: the new weapon's interval applies from the swap.
     */
    public static void resetOnItemChange(Player player) {
        player.setData(ModAttachments.COOLDOWN.get(), CooldownState.READY);
    }

    public static int intervalFor(LivingEntity holder) {
        return WeaponStats.attackInterval(holder);
    }

    public static ItemStack heldItem(Player player) {
        return player.getMainHandItem();
    }
}
