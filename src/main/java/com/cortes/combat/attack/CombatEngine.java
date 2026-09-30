package com.cortes.combat.attack;

import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.StreakState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The whole attack flow, in one method, resolved on the release of one button.
 *
 * <p>This replaces vanilla {@code Player#attack} rather than patching it, because vanilla does
 * four things the design does not want: it multiplies damage by click timing, uses a single
 * raycast at the crosshair, resets its cooldown on item swap, and computes damage inside the
 * item. Patching all four would be four coupled mixins; replacing is one entry point.</p>
 *
 * <h2>Why the release, and not the press</h2>
 *
 * <p>One button has to mean two things: tap to hit, hold to charge. The only way to tell them
 * apart at the moment the press happens is to not know yet, so the decision cannot be made then.
 * Resolving on the press and charging on top of it is what produced the old behaviour, and it
 * is broken in a way that is not subtle: the press fired a hit with no charge, the hold kept
 * accumulating, and the release threw all of it away. The charge was built and discarded on
 * every single swing, so the multiplier never applied and the meter was a promise the game did
 * not keep.</p>
 *
 * <p>On the release, one press is one attack, always, and the charge is whatever the server
 * measured while the button was down.</p>
 *
 * <h2>Order</h2>
 *
 * <p>The cooldown is paid and the charge is spent <em>before</em> the target is even looked at.
 * That ordering is the design: a whiff still costs, so holding the button against air is never
 * a way to bank attacks, and the cost of an attempt does not depend on whether it connected.</p>
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

    /**
     * Resolve one released swing.
     *
     * <p>Called by the payload handler on the server. Does nothing at all unless a press is
     * actually open, which is both the anti-replay gate and the reason a stray release cannot
     * hand a player free damage.</p>
     *
     * @param targetId the entity the client resolved against the cone, or zero for a whiff
     */
    public static void resolveSwing(Player player, int targetId) {
        ChargeState charge = player.getData(ModAttachments.CHARGE.get());
        if (!charge.holding()) {
            // No open swing. Either the release arrived twice, or it arrived without a press,
            // or the press was refused client side because the cooldown was not ready. All three
            // mean the same thing: this is not an attack and must not cost anything.
            return;
        }

        ItemStack held = player.getMainHandItem();
        WeaponStats stats = WeaponStats.of(held);
        int interval = AttackCooldown.intervalFor(player);

        // Paid first, hit or miss, and it closes the swing. A second release for the same press
        // finds nothing open and stops here.
        AttackCooldown.consume(player, interval);

        float ratio = chargedRatio(charge, stats.charge());
        resolveTarget(player, targetId, stats, ratio);
    }

    /**
     * The charge this swing gets, or zero if it was a tap.
     *
     * <p>The threshold is a hard cut, not a scale. A swing under it is exactly a normal hit,
     * because a click that quietly did a bit more is a click the player cannot learn from
     * watching, and the only way they would find out is by having hit for less than expected.</p>
     */
    private static float chargedRatio(ChargeState charge, ChargeProfile profile) {
        if (!profile.canCharge()) {
            return 0.0F;
        }
        return AttackTiming.chargeRatioFor(charge.ticks(), profile.fullChargeTicks());
    }

    private static void resolveTarget(Player player, int targetId, WeaponStats stats,
                                      float chargeRatio) {
        if (targetId == com.cortes.combat.net.SwingPayload.NO_TARGET) {
            swingArm(player);
            return;
        }
        Entity found = player.level().getEntity(targetId);
        if (!(found instanceof LivingEntity target)
                || target == player
                || !target.isAlive()
                || target.isSpectator()
                || target.isRemoved()) {
            swingArm(player);
            return;
        }
        // The client picked the target with the same cone the server checks with, and it is
        // checked again here rather than trusted: a client that sends an id from outside its own
        // reach gets a swing that visibly happens and does nothing, which is worse than a wrong
        // number, because it looks like lag.
        if (!Reach.inCone(player, target, stats.reach())) {
            swingArm(player);
            return;
        }
        swingArm(player);

        AttackResult result = DamageResolver.resolve(
                player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                stats.charge(),
                stats.profile(),
                chargeRatio,
                player.getData(ModAttachments.HAMMER_STREAK.get()));
        apply(player, target, stats, result, chargeRatio);
    }

    /**
     * Swings the arm on every accepted swing, hit or miss.
     *
     * <p>Deliberately after the target is settled, so a rejected swing and a landed one look the
     * same to everyone else. The local player does not see this one: when the weapon's viewmodel
     * owns the strike, the client plays its own motion instead of the vanilla one, and
     * {@code ItemViewModel.ownsSwing} is what the input code asks before calling
     * {@code player.swing} locally.</p>
     */
    private static void swingArm(Player player) {
        player.swing(InteractionHand.MAIN_HAND);
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
        // The weapon's own push, not a number from the damage result. Knockback is what the
        // weapon does, not a by-product of how hard it hit, and folding it into the damage
        // result is what made every item in the game shove a mob equally far.
        Knockback.apply(player, target, stats.knockback(),
                DamageResolver.knockbackFor(chargeRatio));
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
