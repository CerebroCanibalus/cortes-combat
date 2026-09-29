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

    /** Begin accumulating charge. Called when the attack button goes down. */
    public static void beginCharge(Player player) {
        ChargeState current = player.getData(ModAttachments.CHARGE.get());
        if (!current.holding()) {
            player.setData(ModAttachments.CHARGE.get(), new ChargeState(0, true));
        }
    }

    /** Stop accumulating. The charge is spent; the next swing starts from zero. */
    public static void endCharge(Player player) {
        player.setData(ModAttachments.CHARGE.get(), player.getData(ModAttachments.CHARGE.get()).reset());
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
