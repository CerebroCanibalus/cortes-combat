package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.CooldownState;
import com.cortes.combat.state.StreakState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The whole attack flow, in one method.
 *
 * <p>This replaces vanilla {@code Player#attack} rather than patching it, because vanilla does
 * four things the design does not want: it multiplies damage by click timing, uses a single
 * raycast at the crosshair, resets its cooldown on item swap, and computes damage inside the
 * item. Patching all four would be four coupled mixins; replacing is one entry point.</p>
 *
 * <p>Order matters and is the design: charge is spent only by a swing that is actually allowed,
 * and a swing that whiffs still costs the cooldown, so holding the button is never free.</p>
 */
public final class CombatEngine {

    private CombatEngine() {
    }

    /** @return true if the attack was resolved by us and vanilla must not run */
    public static boolean handleAttack(Player player, Entity target) {
        if (player.level().isClientSide) {
            return false;
        }
        if (!AttackCooldown.isReady(player)) {
            return true;
        }

        ItemStack held = player.getMainHandItem();
        WeaponStats stats = WeaponStats.of(held);
        int interval = AttackCooldown.intervalFor(player);

        float chargeRatio = AttackCooldown.chargeRatio(player, stats.charge());
        boolean inCone = target instanceof LivingEntity living
                && Reach.inCone(player, living, stats.reach());

        AttackResult result = inCone
                ? DamageResolver.resolve(
                        player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                        stats.charge(),
                        stats.profile(),
                        chargeRatio,
                        player.getData(ModAttachments.HAMMER_STREAK.get()))
                : AttackResult.MISS;

        if (inCone) {
            apply(player, target, stats, result, chargeRatio);
        }

        // A whiffed swing still costs the cooldown and still spends the charge. Otherwise
        // holding the button would give free damage by spamming at air.
        AttackCooldown.consume(player, interval);
        return true;
    }

    private static void apply(Player player, Entity target, WeaponStats stats,
                              AttackResult result, float chargeRatio) {
        if (target.hurt(damageSource(player, result.ignoredArmour()), result.damage())) {
            onHit(player, target, stats, result, chargeRatio);
        }
    }

    private static void onHit(Player player, Entity target, WeaponStats stats,
                              AttackResult result, float chargeRatio) {
        if (stats.profile() == WeaponProfile.WARHAMMER) {
            StreakState streak = player.getData(ModAttachments.HAMMER_STREAK.get());
            player.setData(ModAttachments.HAMMER_STREAK.get(), streak.onHit());
        }
        Knockback.apply(player, target, result.knockback());
        player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * The warhammer's charged swing bypasses armour, which needs a damage type declared in
     * the mod's data and a {@code Holder<DamageType>} to reference it. That belongs with the
     * specials, not with the flow replacement, so for now every swing uses the normal player
     * attack source and the flag is carried but not applied.
     */
    private static DamageSource damageSource(Player player, boolean ignoreArmour) {
        return player.damageSources().playerAttack(player);
    }
}
