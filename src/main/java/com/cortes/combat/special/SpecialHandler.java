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
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.particle.SlashParticles;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.ParryState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // Two specials, two different prices, and the price is the whole of the difference.
        //
        // The halberd's is paid in swing tempo, so it goes through the same hard cooldown as an
        // attack: a sweep replaces a hit rather than adding one, and the answer to "what does this
        // button cost" stays a single number for the left button and one for the right.
        //
        // The sabre's is paid in a window, a crawl, and a five second lockout, none of which is the
        // attack cooldown -- so it must NOT go through it. Routing the parry through the swing
        // cooldown would make the sabre unable to parry because it swung two ticks ago, which is a
        // rule nobody would design on purpose and which reads as the button being broken.
        if (stats.special() == SpecialType.MULTI_HIT) {
            if (!AttackCooldown.isReady(player)) {
                return;
            }
            sweep(player, level, stats);
            // **And the sweep is announced, because a sweep is a moment and not a state.**
            //
            // The client predicts it on the press for the player doing it, which is why nobody ever
            // noticed: `SpecialInputHandler` calls `onSweep` locally and the animation is right for
            // you and absent for everybody watching. The sweep has twenty ticks of life that the
            // clock runs down from this one call, and nothing in a rate-sampled state packet could
            // say when it started.
            //
            // **After the call and not before**, so a refused sweep announces nothing. And this is
            // the server's decision, not the client's: the client already predicted it, so a client
            // that was wrong about the cooldown will animate a sweep that nobody else ever sees,
            // which is the correct outcome -- their prediction was wrong and the truth is the truth.
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(player,
                    com.cortes.combat.net.CombatEventPayload.sweep(player.getId()));
        } else if (stats.special() == SpecialType.PARRY) {
            parry(player, level, stats);
        } else if (stats.special() == SpecialType.LUNGE) {
            if (!AttackCooldown.isReady(player)) {
                return;
            }
            lunge(player, level, stats);
        } else if (stats.special() == SpecialType.FUSEE) {
            // **No cooldown gate here, and that is the decision.**
            //
            // The first three specials are refused on a cooldown before they are even looked at.
            // The arcabuz cannot be: its press may be a RELOAD, which does pay the attack
            // cooldown, or it may be lighting a fuse, which pays a firestone and twenty ticks
            // instead. Gating the branch on readiness would have made the fuse impossible to
            // light while a swing was recovering, which is exactly when a player holding a gun
            // most wants to start the one second.
            //
            // So the gate moved into the state, where it can tell the two apart: ArcabuzState
            // refuses a second press while the fuse burns, and Arcabuz#load refuses a reload the
            // player cannot pay for.
            Arcabuz.resolve(player, level, stats);
        }
    }

    /**
     * The ropera's lunge: a step forward with the blade extended, at a longer reach than the
     * ropera can otherwise manage.
     *
     * <p><b>The step is the price, and it is paid in position.</b> The ropera's problem is its
     * three and a half blocks, and this buys two more of them by moving the player somewhere they
     * did not choose to be -- into the range of whatever was waiting there. It is the one special
     * in the set that can put you inside a swing you did not see coming, and that is the reason it
     * is not simply a longer thrust.</p>
     *
     * <p><b>No vertical component, ever.</b> A step that can gain height is a mobility tool, and a
     * mobility tool is a way to leave a fight. The horizontal delta replaces the player's own,
     * so it cannot stack with a sprint and it reads as a lunge rather than as a shove.</p>
     *
     * <p>It spends the same hard cooldown as an attack, for the same reason the halberd's sweep
     * does: a special with its own timer would stack with the swing and the answer to "what does
     * this button cost" would be two numbers.</p>
     */
    private static void lunge(ServerPlayer player, ServerLevel level, WeaponStats stats) {
        // **Three things, and none of them is a hit.**
        //
        // This used to build a box of the promised length in front of the player and damage
        // everything in it, right here, before the player had moved a centimetre. That is the whole
        // reason the lunge cost nothing: cover was free, because the volume did not care where the
        // body went. The step is supposed to BE the price, and a hit resolved at the press is not
        // paid in position.
        //
        // So the press now only charges and opens the window. The ground gets swept as it is
        // covered, out of the player's own movement report, and Lunge.tick decides whether any of
        // it was legal. See Lunge for the authority model and for why the server could never have
        // moved the player in the first place.

        // Four swings, on the same hard cooldown as an attack so that the answer to "what does
        // this button cost" stays one number per button.
        AttackCooldown.consume(player, Math.round(WeaponStats.attackInterval(player)
                * LungeDamage.COOLDOWN_FACTOR));

        CombatSounds.play(level, player, WeaponProfile.ROPERA, ModSounds.Kind.LUNGE);

        Lunge.begin(player, tierOf(player));
    }

    /** The material rank of whatever the player is holding, or zero if it is not ours. */
    public static int tierOf(ServerPlayer player) {
        if (player.getMainHandItem().getItem()
                instanceof com.cortes.combat.item.CortesWeaponItem weapon) {
            return weapon.material().rank();
        }
        return 0;
    }

    /**
     * The sabre's parry: half a second of blade out, at a crawl, then five seconds of nothing.
     *
     * <p>Refused on a cooldown that is still running and refused while the window is already
     * open, and the second refusal is the one that matters. A window that a second press could
     * refresh is not a parry, it is a block, and a block that costs nothing to keep up is the
     * thing the sabre's terrible reach was supposed to be paying for.</p>
     *
     * <p>The slowdown is the other half of the price and it is applied here rather than inside the
     * window reader, so it lands on the same tick the blade starts coming out. It is a vanilla
     * effect with the particles off: an icon would be a promise about a buff the player cannot
     * see the cause of, and particles during a parry would sit exactly where the incoming hit is
     * supposed to be readable.</p>
     */
    private static void parry(ServerPlayer player, ServerLevel level, WeaponStats stats) {
        ParryState state = player.getData(ModAttachments.PARRY.get());
        if (!state.canActivate()) {
            return;
        }
        player.setData(ModAttachments.PARRY.get(), state.activate());

        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                ParryState.WINDOW_TICKS + 2,
                SLOWNESS_AMPLIFIER,
                false, false, false));

        ModNetworking.sendParryState(player, true, ParryState.COOLDOWN_TICKS);
    }

    /**
     * How much the parry costs in speed, as slowness levels.
     *
     * <p>Four, and four is a lot: vanilla's slowness is {@code -0.15} per level on
     * {@code ADD_MULTIPLIED_TOTAL}, so this is 75% off. Three would be 60% and would read as "you
     * are walking carefully", which is a cost you stop noticing by the second fight. The point of
     * the parry is that it is the riskiest thing this weapon does -- the shortest reach in the
     * game is what pays for being able to do it at all.</p>
     */
    private static final int SLOWNESS_AMPLIFIER = 3;

    private static void sweep(ServerPlayer player, ServerLevel level, WeaponStats stats) {
        var reach = HalberdSweep.reachFor(stats.reach());
        List<LivingEntity> targets = targetsInCone(player, reach);

        // The sound and the sweep particle happen whether or not anything was hit. The swing is an
        // event: a polearm swung wide is a thing the player did, and the orbs come out of the arc
        // itself. Making the effect conditional on a hit would make a miss into no feedback at all.
        CombatSounds.play(level, player, WeaponProfile.HALBERD, ModSounds.Kind.SPECIAL);
        double arc = HalberdSweep.arcOffset(reach);
        SlashParticles.sweep(level, stats.profile(),
                player.getX() + player.getLookAngle().x * arc,
                player.getEyeY(),
                player.getZ() + player.getLookAngle().z * arc);

        // Spent whether or not it connected. A special that only cost anything on a hit would be
        // free to spam at air, and air is the thing you want to be able to practise against.
        //
        // **Four swings, not one.** The sweep used to cost exactly what the click it competes with
        // costs, which made it that click with a wider hitbox, more targets and more shove -- and a
        // button that is strictly better than the attack is not a button, it is a mistake. At four
        // swings the choice is real and it is the count that decides it.
        AttackCooldown.consume(player,
                HalberdSweep.cooldownFor(WeaponStats.attackInterval(player)));

        for (LivingEntity target : targets) {
            AttackResult result = DamageResolver.resolve(
                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                    stats.charge(),
                    stats.profile(),
                    0.0F,
                    player.getData(com.cortes.combat.registry.ModAttachments.HAMMER_STREAK.get()));
            Knockback.apply(player, target, stats.knockback(), HalberdSweep.PUSH);
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
     *
     * <p>The cone is a parameter and not read off the weapon, because the sweep's is deliberately
     * not the weapon's: it reaches further and opens wider. Reading {@code stats.reach()} here is
     * what had the sweep hitting exactly what the thrust hits, only more of it.</p>
     */
    private static List<LivingEntity> targetsInCone(ServerPlayer player, ReachProfile reach) {
        List<LivingEntity> found = new java.util.ArrayList<>();
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
