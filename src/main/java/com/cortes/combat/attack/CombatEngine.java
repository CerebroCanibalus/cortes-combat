package com.cortes.combat.attack;

import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.CombatTrace;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.registry.ModDamageTypes;
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
     * @return the charge this swing was resolved at, or {@code -1} when there was no open swing.
     *         <p><b>Returned rather than re-read by the caller.</b> The value is decided here,
     *         against this player's own {@code ChargeState} and this weapon's own charge profile,
     *         and the caller that needs it -- the network layer, which tells the players watching
     *         what kind of strike this was -- would otherwise have to compute it a second time
     *         from the same two inputs. Two copies of an arithmetic that decides whether a hit was
     *         charged is the shape that drifts.</p>
     */
    public static float resolveSwing(Player player, int targetId) {
        ChargeState charge = player.getData(ModAttachments.CHARGE.get());
        if (!charge.holding()) {
            // No open swing. Either the release arrived twice, or it arrived without a press,
            // or the press was refused client side because the cooldown was not ready. All three
            // mean the same thing: this is not an attack and must not cost anything.
            debug(player, "release SIN press abierto: no es un ataque");
            return NO_SWING;
        }

        ItemStack held = player.getMainHandItem();
        WeaponStats stats = WeaponStats.of(held);
        int interval = AttackCooldown.intervalFor(player);

        // Paid first, hit or miss, and it closes the swing. A second release for the same press
        // finds nothing open and stops here.
        AttackCooldown.consume(player, interval);

        float ratio = chargedRatio(charge, stats.charge());
        resolveTarget(player, targetId, stats, ratio);
        return ratio;
    }

    /**
     * What {@link #resolveSwing} returns when there was nothing open.
     *
     * <p><b>Minus one and not zero, and the difference is the whole point.</b> A whiff is a real
     * swing resolved at no charge, and it is drawn: a weapon swings, it just hits nothing. Zero
     * would read as "a tap", which is a different animation at a different length. A caller that
     * treated this as a charge of zero would tell every client watching that the player struck at
     * a quarter of a second's hold when in fact they swung at nothing.</p>
     */
    public static final float NO_SWING = -1.0F;

    /**
     * The charge this swing gets, or zero if it was a tap.
     *
     * <p>The threshold is a hard cut, not a scale. A swing under it is exactly a normal hit,
     * because a click that quietly did a bit more is a click the player cannot learn from
     * watching, and the only way they would find out is by having hit for less than expected.</p>
     */
    private static float chargedRatio(ChargeState charge, ChargeProfile profile) {
        return AttackTiming.chargeRatioFor(
                charge.ticks(), profile.fullChargeTicks(), profile.canCharge());
    }

    private static void resolveTarget(Player player, int targetId, WeaponStats stats,
                                      float chargeRatio) {
        if (targetId == com.cortes.combat.net.SwingPayload.NO_TARGET) {
            debug(player, "sin objetivo, whiff");
            swingArm(player);
            playMiss(player, stats);
            return;
        }
        Entity found = player.level().getEntity(targetId);
        if (!(found instanceof LivingEntity target)
                || target == player
                || !target.isAlive()
                || target.isSpectator()
                || target.isRemoved()) {
            debug(player, "objetivo " + targetId + " no valido: "
                    + (found == null ? "no existe" : found.getClass().getSimpleName()
                    + " vivo=" + (found instanceof LivingEntity le ? le.isAlive() : "n/a")
                    + " spectator=" + (found instanceof LivingEntity le2 ? le2.isSpectator() : "n/a")));
            swingArm(player);
            playMiss(player, stats);
            return;
        }
        // The client picked the target with the same cone the server checks with, and it is
        // checked again here rather than trusted: a client that sends an id from outside its own
        // reach gets a swing that visibly happens and does nothing, which is worse than a wrong
        // number, because it looks like lag.
        if (!Reach.inCone(player, target, stats.reach())) {
            debug(player, "objetivo " + targetId + " (" + target.getName().getString()
                    + ") FUERA del cono, alcance=" + stats.reach().distance());
            swingArm(player);
            playMiss(player, stats);
            return;
        }
        debug(player, "objetivo " + targetId + " (" + target.getName().getString()
                + ") VALIDO, aplicando dano");
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

    /**
     * One line per swing decision, server side.
     *
     * <p>The pair of these and the ones in {@code SwingInputHandler} exists because "it does not
     * hit" is a sentence with at least six causes behind it, and only two of them are visible from
     * the outside: the press was refused by the cooldown, the client sent no target, or the server
     * rejected the target the client sent. Reading the log afterwards turns all six into one.</p>
     */
    private static void debug(Player player, String message) {
        CombatTrace.server(player.getName().getString(), message);
    }

    private static void apply(Player player, Entity target, WeaponStats stats,
                              AttackResult result, float chargeRatio) {
        // Read before the hit: afterwards a dead mob reads 0 health and there is no way to tell
        // "the swing killed it" from "the swing finished it off". Only a living thing has health
        // to read, and only a living thing can be in the cone to be hit, so the cast is safe
        // here rather than a branch the caller had to remember.
        float healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0.0F;

        // **How many hits this swing lands, asked once and used for both the damage and the
        // picture.** One for every weapon in the game except the sabre at full charge, which lands
        // two. It used to be one heavy hit instead, which is the same total damage and a different
        // event: the playtest read the sabre as "pega doble", counted two charged swings to kill a
        // zombie, and correctly asked why it was not giving both. Two hits are two chances to
        // connect, two knockbacks, and two impacts that can be watched.
        int hits = com.cortes.combat.particle.SlashTiming.hitsAt(stats.profile(), stats.charge(), chargeRatio);

        boolean anyLanded = false;
        int landed = 0;
        for (int i = 0; i < hits; i++) {
            // Vanilla's invulnerability window is one full second, which was tuned for a weapon
            // that swings twice a second. It would swallow the entire follow-up of any fast
            // weapon and make half the swings feel broken. This system already has its own gate,
            // the hard cooldown, so the window is cleared before EACH hit rather than once before
            // both: re-armed between them it would eat the second cut of the sabre's own charge,
            // which is the one double hit in this game that is meant to land.
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(
                    damageSource(player, result.ignoredArmour(), result.axeCharged()),
                    result.damage());
            target.invulnerableTime = AttackTiming.IFRAME_TICKS;
            if (hurt) {
                landed++;
                anyLanded = true;
                // The knockback goes on the first cut only. Twice the push would throw a mob
                // across the room, and a second cut that shoves is not a second cut.
                if (i == 0) {
                    onHit(player, target, stats, result, chargeRatio);
                    // **Inside `hurt`, and after it returned true.**
                    //
                    // The guard is on the target, not the attacker. Two weapons come through
                    // here and for two different reasons: the warhammer's source bypasses the
                    // shield outright, and the axe's charged one does not but empties the bar by
                    // hand. Either way the defender pays, which is the whole point -- without
                    // this the warhammer would go through a raised shield for free and the guard
                    // would be the one thing in the game with no price.
                    //
                    // After `hurt` and inside it: a swing that missed breaks nothing, and a
                    // second hit of a double cut does not break it twice. `target` may not be a
                    // player at all, and a mob has no guard to spend.
                    boolean breaksGuard = result.ignoredArmour() || result.axeCharged();
                    if (breaksGuard && target instanceof Player defender) {
                        com.cortes.combat.guard.GuardHandler.breakGuard(defender);
                    }
                }
                spawnSlash(player, target, stats, chargeRatio, i == 0);
                // **The second cut's sound is DELAYED, and that is the fix.**
                //
                // It used to be sent from inside this loop, so both sounds played on the same tick
                // about a millisecond apart and the ear heard one. The playtest reported the sound
                // was missing; it was being played twice at once. A different pitch does not help
                // with two sounds in the same millisecond, which is the whole reason the particle
                // needed the same treatment.
                //
                // 1.21.1 has no way to schedule a sound on the server: no delayed task queue, and
                // the sound packet carries no delay. So the delay travels to the client, which is
                // the same trick the slash particle uses.
                //
                // A third of a tone up as well, because a sound late AND different is unmistakable
                // where a sound late and identical is just the first one echoing.
                if (i > 0 && anyLanded && player instanceof net.minecraft.server.level.ServerPlayer me) {
                    com.cortes.combat.net.ModNetworking.sendDelayedSound(me,
                            com.cortes.combat.sound.ModSounds.of(stats.profile(),
                                    chargedKind(stats, chargeRatio)),
                            ModSounds.pitch(stats.profile(), chargedKind(stats, chargeRatio)) * 1.35F,
                            com.cortes.combat.particle.SlashTiming.DOUBLE_GAP_TICKS);
                }
            }
        }
        if (anyLanded) {
            notifyHit(player, target, healthBefore);
        }
        // One line per landed swing, and only when the count is not the ordinary one. The sabre's
        // doubling has been reported broken twice while this file said it worked, and both times the
        // disagreement was about a number that only exists here: how many hits ran, how charged the
        // swing was, and how much each one did. The playtest's arithmetic is as good as mine and it
        // can only be checked against the game's own.
        if (hits > 1 || chargeRatio >= 1.0F) {
            org.slf4j.LoggerFactory.getLogger("cortescombat").info(
                    "[hit] " + stats.profile() + " cargo=" + chargeRatio
                            + " golpes=" + hits + " aterrizaron=" + landed
                            + " dano=" + result.damage()
                            + " vida=" + healthBefore + " -> "
                            + (target instanceof LivingEntity l ? l.getHealth() : 0.0F));
        }
    }

    /**
     * The slash, where the swing actually connected.
     *
     * <p>Server side and only on a confirmed hit, so everyone watching sees the same slash at the
     * same moment and nobody sees one for a swing that did nothing. A charged hit gets vanilla's
     * crit instead, which is already a big animated slash: a charged hit is then told apart by
     * its shape rather than by the same one drawn louder.</p>
     */
    /**
     * Which sound a hit at this charge makes.
     *
     * <p>Asked once and used by both cuts of a doubled swing, so that the second cut can never be
     * a different event from the first. A charged hit and an ordinary one are different sounds on
     * purpose, and a doubled swing is both of those things at once.</p>
     */
    private static ModSounds.Kind chargedKind(WeaponStats stats, float chargeRatio) {
        return chargeRatio > 0.0F ? ModSounds.Kind.CHARGED_HIT : ModSounds.Kind.HIT;
    }

    private static void spawnSlash(Player player, Entity target, WeaponStats stats,
                                   float chargeRatio, boolean firstCut) {
        if (!firstCut) {
            // The sabre's second cut is drawn by the first call, which sends both at once -- one
            // now and one a beat later. Drawing it here instead would put three cuts on screen
            // per swing, because the first call already draws two.
            return;
        }
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        boolean charged = chargeRatio > 0.0F;
        // A charge that lands TWICE is drawn as two cuts a beat apart, and a charge that is merely
        // bigger keeps the crit burst. Asked of the charge profile rather than decided here, so the
        // number that says "twice" about the damage and the one that says it about the picture are
        // the same number.
        if (charged && com.cortes.combat.particle.SlashParticles.chargesToDouble(stats.profile(), stats.charge())) {
            com.cortes.combat.particle.SlashParticles.doubledHit(level, stats.profile(), target);
        } else {
            com.cortes.combat.particle.SlashParticles.hit(
                    level, stats.profile(), target, charged);
        }
        // Same place, same branch: a charged hit is a different slash AND a different sound, and
        // the two must never disagree about which happened.
        com.cortes.combat.sound.CombatSounds.play(level, player, stats.profile(),
                charged ? ModSounds.Kind.CHARGED_HIT : ModSounds.Kind.HIT);
    }

    /**
     * The swing found nothing, and says so.
     *
     * <p>A miss is worth a sound because "it did not hit" and "the game ignored my click" look
     * identical from the outside. The viewmodel already answers it with an arm that moved; this
     * is the answer for the half-second when the player is looking at the enemy and not at their
     * own hand.</p>
     *
     * <p>Played for a rejected target too, and for a whiff. Both are a miss from where the player
     * stands, and treating the server's refusal as silence would leave the one case where the
     * player is right about aiming and wrong about the outcome with no way to tell it apart.</p>
     */
    private static void playMiss(Player player, WeaponStats stats) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            com.cortes.combat.sound.CombatSounds.play(
                    level, player, stats.profile(), ModSounds.Kind.MISS);
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
            StreakState streak = player.getData(ModAttachments.HAMMER_STREAK.get()).onHit();
            player.setData(ModAttachments.HAMMER_STREAK.get(), streak);
            // After the increment, so the first landed hit is one and sounds at the floor. The pitch
            // reads the new streak rather than taking a count as an argument, so a caller cannot
            // play the note for the streak the player was in before this swing.
            if (player instanceof net.minecraft.server.level.ServerPlayer server) {
                com.cortes.combat.sound.CombatSounds.playStackStep(server, target, streak.stackPitch());
            }
        }
        // The weapon's own push, not a number from the damage result. Knockback is what the
        // weapon does, not a by-product of how hard it hit, and folding it into the damage
        // result is what made every item in the game shove a mob equally far.
        Knockback.apply(player, target, stats.knockback(),
                DamageResolver.knockbackFor(chargeRatio));
    }

    /**
 * The warhammer's charged swing bypasses armour, and the axe's charged swing wears it twice as
 * fast. Two different consequences, two different damage types.
 *
 * <p>The warhammer's flag was computed for two rounds with nowhere to land, because
 * {@code CombatRules.getDamageAfterAbsorb} reads nothing but the source's tags: no amount of
 * arithmetic before the hit changes what armour takes.</p>
 *
 * <p><b>And the warhammer carries a consequence that is not ours to choose.</b> Vanilla nests
 * {@code bypasses_armor} inside {@code bypasses_shield}
 * ({@code DamageTypeTagsProvider}: {@code BYPASSES_SHIELD.addTag(BYPASSES_ARMOR)}), so anything
 * that ignores armour also goes straight through a raised shield, and {@code GuardHandler} leaves
 * the defender's stamina untouched when the source bypasses. There is no tag that opts out of
 * that.</p>
 *
 * <p><b>The axe is deliberately the other way round.</b> It is <em>not</em> tagged
 * {@code bypasses_armor}: a charged axe hit still gets reduced by a diamond plate, and the axe
 * still breaks a guard because {@link #breakGuard} spends the defender's points by hand rather
 * than by asking a tag to do it. One weapon reaches past armour and one weapon takes it apart, and
 * they are two entries in this method rather than one rule with a flag.</p>
 */
    private static DamageSource damageSource(Player player, boolean ignoreArmour,
                                            boolean axeCharged) {
        if (ignoreArmour) {
            return ModDamageTypes.warhammerIgnoredArmour(player);
        }
        return axeCharged ? ModDamageTypes.axeCharged(player)
                : player.damageSources().playerAttack(player);
    }
}
