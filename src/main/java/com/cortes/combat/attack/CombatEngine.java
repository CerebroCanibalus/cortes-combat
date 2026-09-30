package com.cortes.combat.attack;

import com.cortes.combat.core.CombatFeedback;
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

    /**
     * I-frames this mod applies to a target it just hit, in ticks.
     *
     * <p>Vanilla uses 20 (a full second). Four is a fifth of that: still enough to stop one
     * swing landing twice through some other path, short enough that it never decides a
     * fight. Every weapon in the catalogue has a slower interval than this, so a real
     * follow-up is never blocked by it.</p>
     */
    private static final int IFRAME_TICKS = 4;

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
        // Swung on every accepted attack, hit or miss, so the swing animation exists at all:
        // the client plays it locally through gameMode.attack, and this is what makes other
        // players see it in third person. It is deliberately not swung when the cooldown
        // rejects the attack, because that arm movement would claim a hit that never was.
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static void apply(Player player, Entity target, WeaponStats stats,
                              AttackResult result, float chargeRatio) {
        // Read before the hit: afterwards a dead mob reads 0 health and there is no way to tell
        // "the swing killed it" from "the swing finished it off". Only a living thing has health
        // to read, and only a living thing can be in the cone to be hit, so the cast is safe
        // here rather than a branch the caller had to remember.
        float healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0.0F;
        // Vanilla's invulnerability window is one full second, which was tuned for a weapon
        // that swings twice a second. It would swallow the entire follow-up of any fast
        // weapon and make half the swings feel broken. This system already has its own gate,
        // the hard cooldown, so the window is cleared before the hit and re-armed to a short
        // one after: long enough to reject a same-instant double hit, short enough that a
        // legitimate next swing is never eaten.
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(damageSource(player, result.ignoredArmour()), result.damage());
        target.invulnerableTime = IFRAME_TICKS;
        if (hurt) {
            onHit(player, target, stats, result, chargeRatio);
            notifyHit(player, target, healthBefore);
        }
    }

    /**
     * Tells the attacker what happened, so the crosshair can react.
     *
     * <p>A kill is a different event from a hit and gets a different colour, because the player
     * needs to tell "I landed it" from "I finished it" without reading a health bar at a
     * distance. The check is on health dropping to zero rather than on a death event, so a mob
     * that dies to something else in the same tick is not credited to this swing.</p>
     */
    private static void notifyHit(Player player, Entity target, float healthBefore) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server)
                || !(target instanceof LivingEntity living)) {
            return;
        }
        boolean killed = healthBefore > 0.0F && living.getHealth() <= 0.0F;
        com.cortes.combat.net.ModNetworking.sendFeedback(server,
                killed ? CombatFeedback.KILL : CombatFeedback.HIT);
    }

    private static void onHit(Player player, Entity target, WeaponStats stats,
                              AttackResult result, float chargeRatio) {
        if (stats.profile() == WeaponProfile.WARHAMMER) {
            StreakState streak = player.getData(ModAttachments.HAMMER_STREAK.get());
            player.setData(ModAttachments.HAMMER_STREAK.get(), streak.onHit());
        }
        Knockback.apply(player, target, result.knockback());
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
