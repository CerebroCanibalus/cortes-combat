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
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.ParryState;
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
        } else if (stats.special() == SpecialType.PARRY) {
            parry(player, level, stats);
        } else if (stats.special() == SpecialType.LUNGE) {
            if (!AttackCooldown.isReady(player)) {
                return;
            }
            lunge(player, level, stats);
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
        int tier = tierOf(player);

        // The direction is taken ONCE, at the press, and stored on the player. Deriving it
        // from the player's motion each tick meant any steering during the lunge bent the dash into
        // a curve, which is not a lunge -- it is a flight the player is flying.
        var look = player.getLookAngle();
        var direction = new net.minecraft.world.phys.Vec3(look.x, 0.0, look.z);
        if (direction.lengthSqr() < 1.0E-6) {
            return;
        }
        direction = direction.normalize();

        // Six small steps across six ticks, not one jump. See Lunge for why a server-side velocity
        // does not move a player at all, and why the fix is to move them over TIME rather than to
        // move them less.
        Lunge.begin(player, tier);

        // Four swings, on the same hard cooldown as an attack so that the answer to "what does
        // this button cost" stays one number per button.
        AttackCooldown.consume(player, Math.round(WeaponStats.attackInterval(player)
                * LungeDamage.COOLDOWN_FACTOR));

        CombatSounds.play(level, player, WeaponProfile.ROPERA, ModSounds.Kind.LUNGE);

        // Flat and uncharged, like the sweep: the value of a lunge is the ground it covered, not
        // the number on any single hit. A special that also scaled would be competing with the
        // swing on the axis the swing is already best at.
        float baseDamage = (float) player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

        // **A box from the torso, not a cone from the camera.** A swing is a cone because the
        // camera is what the player aims with. A lunge is not aimed -- it is thrown, and what it
        // sweeps is the space the body passed through. Testing the camera cone made the hit land
        // where the player was looking, which during a movement they did not choose is the one
        // thing that feels wrong.
        // The corridor is as long as the player will travel and as wide as the tier's reach, so a
        // better material sweeps more ground rather than a longer corridor through the same crowd.
        double length = LungeDamage.distanceFor(tier);
        double width = LungeDamage.reachFor(tier);
        var sweep = Lunge.sweepBox(player.position(), direction, length, width);
        var query = player.getBoundingBox().inflate(length + 1.5);

        var targets = new java.util.ArrayList<LivingEntity>();
        for (Entity candidate : level.getEntitiesOfClass(LivingEntity.class, query)) {
            if (candidate == player || !candidate.isAlive()
                    || candidate.isSpectator() || candidate.isRemoved()) {
                continue;
            }
            if (Lunge.sweeps(sweep, candidate.position(), candidate.getBbHeight())) {
                targets.add((LivingEntity) candidate);
            }
        }
        // Nearest first, and it matters twice over: the enemy the player was aiming at is the one
        // that takes the first and therefore the smallest multiplier, and it is the one that
        // survives if an invulnerability window eats a later hit.
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));

        // **Everything in the box, and each one hits harder than the last.** Nearest is the
        // weakest, so the player is rewarded for aiming into the far end of a group rather than
        // for turning around after it starts.
        int index = 0;
        for (LivingEntity target : targets) {
            float damage = LungeDamage.damageFor(baseDamage, index);
            Knockback.apply(player, target, stats.knockback(), 0.0F);
            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                target.invulnerableTime = IFRAME_TICKS;
                SlashParticles.hit(level, stats.profile(), target, false);
                // The per-target hit, a fifth of a tone down from the weapon's own: a rising
                // series across a group rather than one sound repeated.
                CombatSounds.play(level, target, WeaponProfile.ROPERA, ModSounds.Kind.HIT,
                        1.0F - index * 0.06F);
            }
            index++;
        }

        // Vanilla's crit on top, once, for the lunge itself. Not one per target: the crit is the
        // sound of the movement landing, and a movement makes one.
        if (!targets.isEmpty()) {
            ModNetworking.sendFeedback(player, com.cortes.combat.core.CombatFeedback.HIT);
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.9, player.getZ(),
                    12, 0.3, 0.3, 0.3, 0.05);
        }
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
