package com.cortes.combat.special;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.AttackResult;
import com.cortes.combat.attack.DamageResolver;
import com.cortes.combat.attack.Knockback;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.sound.CombatSounds;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.particle.SlashParticles;
import com.cortes.combat.reach.Reach;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The right button's other meaning: the halberd's wide sweep.
 *
 * <p><b>It hits everything in the arc, not the best thing in it.</b> That is the entire reason it
 * exists. The left button already picks one target with the cone and takes it to full health, so a
 * special that dealt more damage would be a strictly better left button. What a crowd weapon needs
 * is the one thing a single-target cone cannot do.</p>
 *
 * <p>So the damage per enemy is the weapon's ordinary flat damage, uncharged, and the value is
 * entirely in the count. Three mobs is three times the output of a normal swing; one mob is not
 * worth using it on, and the player learns that from the cooldown rather than from a tooltip.</p>
 *
 * <p>It spends the same hard cooldown as an attack, which means a sweep replaces a swing rather
 * than adding one. A special with its own timer would stack with the attack cooldown and the
 * answer to "what does this button cost" would be two numbers.</p>
 */
public final class SpecialHandler {

    /** Matches the anti-double-hit window the normal attack uses. */
    private static final int IFRAME_TICKS = 10;

    private SpecialHandler() {
    }

    /**
     * Resolves a special press.
     *
     * <p>Refuses on a cooldown that is not ready, on an item the server did not expect, and on
     * anything that is not a live entity. Each refusal is silent in the same way the attack's are:
     * the player's own cooldown bracket is the feedback.</p>
     */
    public static void resolve(ServerPlayer player, ItemStack claimed) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !ItemStack.isSameItemSameComponents(held, claimed)) {
            return;
        }
        WeaponStats stats = WeaponStats.of(held);
        if (stats.special() != SpecialType.MULTI_HIT) {
            return;
        }
        if (!AttackCooldown.isReady(player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        sweep(player, level, stats);
    }

    private static void sweep(ServerPlayer player, ServerLevel level, WeaponStats stats) {
        List<LivingEntity> targets = targetsInCone(player, stats);

        // The sound and the sweep particle happen whether or not anything was hit. The swing is an
        // event: a polearm swung wide is a thing the player did, and the orbs come out of the arc
        // itself. Making the effect conditional on a hit would make a miss into no feedback at all.
        CombatSounds.play(level, player, WeaponProfile.HALBERD, ModSounds.Kind.SPECIAL);
        SlashParticles.sweep(level, stats.profile(),
                player.getX() + player.getLookAngle().x * 1.2,
                player.getEyeY(),
                player.getZ() + player.getLookAngle().z * 1.2);

        // Spent whether or not it connected. A special that only cost anything on a hit would be
        // free to spam at air, and air is the thing you want to be able to practise against.
        AttackCooldown.consume(player, WeaponStats.attackInterval(player));

        for (LivingEntity target : targets) {
            AttackResult result = DamageResolver.resolve(
                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                    stats.charge(),
                    stats.profile(),
                    0.0F,
                    player.getData(com.cortes.combat.registry.ModAttachments.HAMMER_STREAK.get()));
            Knockback.apply(player, target, stats.knockback(), 0.0F);
            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), result.damage())) {
                target.invulnerableTime = IFRAME_TICKS;
                SlashParticles.hit(level, stats.profile(), target, false);
                ModNetworking.sendFeedback(player,
                        com.cortes.combat.core.CombatFeedback.HIT);
            }
        }
    }

    /**
     * Everything inside the arc, nearest first.
     *
     * <p>Nearest first so that when the invulnerability window eats the second hit of a pair of
     * overlapping mobs, the one that dies is the one the player was looking at.</p>
     */
    private static List<LivingEntity> targetsInCone(ServerPlayer player, WeaponStats stats) {
        List<LivingEntity> found = new java.util.ArrayList<>();
        var reach = stats.reach();
        var box = player.getBoundingBox().inflate(reach.distance() + 1.0);
        for (Entity candidate : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == player || !candidate.isAlive()
                    || candidate.isSpectator() || candidate.isRemoved()) {
                continue;
            }
            if (Reach.inCone(player, candidate, reach)) {
                found.add((LivingEntity) candidate);
            }
        }
        found.sort((a, b) -> Double.compare(
                a.distanceToSqr(player), b.distanceToSqr(player)));
        return found;
    }

}