package com.cortes.combat.attack;

import java.util.List;

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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

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

        // **A new swing supersedes any confirmation the previous one still awaited.** The pending
        // window is five ticks and the fastest weapon in the game can legally fire again inside
        // them; without this line a stale pending hit would land a swing nobody is throwing any
        // more, at a charge an earlier release decided. Placed after the open-swing gate above,
        // so a stray or replayed release cannot cancel a legitimate window either.
        player.setData(com.cortes.combat.registry.ModAttachments.PENDING_HIT.get(),
                com.cortes.combat.state.PendingHit.NONE);

        float ratio = chargedRatio(charge, stats.charge());
        resolveTarget(player, targetId, stats, ratio);

        // **La onda de filo sale con el tajo, acierte o no.** Ver fireSweepingWave: vive acá porque
        // `resolveSwing` es el único lugar donde un tajo existe exactamente una vez -- `landTheHit`
        // corre dos veces cuando la ventana de coyote confirma. Un tajo cargado al aire también la
        // dispara, que es lo que el diseño dice ("al dar un ataque cargado", no "al acertar"), y
        // `targetId` viaja como el objetivo a saltear para que el melé y la onda no cobren dos veces
        // al mismo cuerpo.
        fireSweepingWave(player, targetId, stats, ratio);
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
        if (!Reach.inCone(player, target, com.cortes.combat.attack.MaceAir.reachFor(stats, player))) {
            // **The coyote window, and what this branch used to be: an instant miss.**
            //
            // <p>The entity exists, is alive and is the one the client aimed at -- only the
            // geometry disagrees, and geometry across the wire is one tick behind (entity
            // positions and the player's own rotation both arrive over the network). Rather than
            // spend the swing on that disagreement, the aim is registered and re-checked every
            // tick for {@link com.cortes.combat.state.PendingHit#WINDOW_TICKS} ticks by
            // {@link HitCoyote}, against this same strict cone. The window forgives timing, never
            // aim: a player who has turned away fails the first re-check, and a target that left
            // for good expires with the miss sound it would have played right now.</p>
            //
            // <p>The arm swings here, on the release, because the swing happened then -- delaying
            // the animation to the confirmation would show every watcher a player who waits half a
            // second before attacking. Only the damage and the miss sound wait.</p>
            debug(player, "objetivo " + targetId + " (" + target.getName().getString()
                    + ") FUERA del cono: coyote "
                    + com.cortes.combat.state.PendingHit.WINDOW_TICKS + " ticks, alcance="
                    + stats.reach().distance());
            swingArm(player);
            openPending(player, targetId, chargeRatio);
            return;
        }
        landTheHit(player, target, stats, chargeRatio, true);
    }

    /**
     * The aimed hit, fully applied: the event hook, the arm, the damage, the extras.
     *
     * <p><b>Extracted so the coyote window can land the same swing a few ticks later without a
     * second copy of any of it.</b> {@code resolveTarget} runs the validation that decides
     * <em>whether</em> this is owed; this runs everything after that verdict, from the same two
     * call sites, with one difference between them.</p>
     *
     * @param swingNow whether the arm still has its swing to play. {@code true} from the release,
     *         where the swing is happening; {@code false} from {@link HitCoyote}'s confirmation,
     *         where the arm already swung when the window opened and a second one would read as a
     *         second attack.
     */
    static void landTheHit(Player player, LivingEntity target, WeaponStats stats,
                           float chargeRatio, boolean swingNow) {
        debug(player, "objetivo " + target.getId() + " (" + target.getName().getString()
                + ") VALIDO, aplicando dano");

        // **NeoForge's own attack hook, which `Player#attack` fires and a replaced flow drops.**
        // Fired for the aimed target only, the way vanilla fires it once per attack and not once
        // per sweep victim. If a mod cancels it the swing is still spent: the cooldown was paid
        // before the target was looked at, which is the same rule a whiff obeys -- an attempt costs
        // whether or not it lands.
        if (!net.neoforged.neoforge.common.CommonHooks.onPlayerAttackTarget(player, target)) {
            debug(player, "objetivo " + target.getId() + " cancelado por AttackEntityEvent");
            return;
        }

        if (swingNow) {
            swingArm(player);
        }

        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        StreakState streak = player.getData(ModAttachments.HAMMER_STREAK.get());
        // **The flags first, because the damage source they choose is what the enchantment hooks
        // need.** Whether this swing bypasses armour is a property of the weapon and the charge and
        // has nothing to do with the enchantments on it, so a bonus-free resolve is the honest way
        // to ask -- and the source it builds is the same one every target is hit with.
        AttackResult flags = DamageResolver.resolve(base, stats.charge(), stats.profile(),
                chargeRatio, streak, player.fallDistance);
        DamageSource source = damageSource(player, flags.ignoredArmour(), flags.axeCharged());

        // **Per target, because the enchantment does not have one answer.** Sharpness is the same
        // for every body the swing touches, but Smite is not, and neither is a crowd charge that
        // counts the bodies around the one you aimed at. The base, the charge and the fall are the
        // same for all of them; only the flat enchantment bonus moves.
        //
        // Density is read once and sits with the fall, because the fall and Density are the same
        // family: the level does not depend on the body being hit, so one read feeds every target.
        int density = densityLevel(player);
        AttackResult result = DamageResolver.resolve(base, stats.charge(), stats.profile(),
                chargeRatio, streak, player.fallDistance, density,
                enchantBonus(player, target, source, base));

        // **The one the player was aiming at pays first, and the area weapons add the rest.**
        //
        // <p>Order is the whole decision here and it is not "nearest first". The client sent one
        // id: that is the player's intent, and it is the one thing the server cannot reconstruct on
        // its own. So the aimed target is resolved and applied exactly as before, and the
        // additional targets are appended <em>after</em> it &mdash; nearest first among themselves,
        // because between two things nobody chose, the closer one is the better guess.</p>
        //
        // <p><b>The reverse would be wrong in a way that shows.</b> If the sweep ran first, a player
        // aiming at the mob behind the crowd would watch the mob in front take the hit, and the
        // damage would look like it went to the wrong target -- which is the exact symptom of the
        // cone and reach bugs this project has already chased down twice.</p>
        //
        // **One landing for the whole swing.** The maza hits up to three bodies and each body's
        // Wind Burst clears the fall immunity, so the descent stop and the re-asserts do not divide
        // the same way -- see `MaceSmash.Landing`. Created here, before the bodies are applied.
        MaceSmash.Landing smash = smashLanding(player, stats);

        apply(player, target, stats, result, chargeRatio, smash);

        // **La onda de filo NO se dispara acá, a propósito.** Este método corre dos veces para un
        // solo tajo cuando la ventana de coyote confirma el golpe diferido, así que dispararla acá
        // sería un segundo cobro del mismo espadazo. Sale de `resolveSwing`, una vez por tajo,
        // acierte o no -- ver fireSweepingWave.

        int extra = com.cortes.combat.item.WeaponTables.maxTargets(stats.profile()) - 1;
        if (extra > 0) {
            // **The weapon's own cone, not a derived one.** The halberd's sweep deliberately reads a
            // wider and longer reach because it costs four swings; this costs one, and a weapon that
            // is also the strongest in the game does not need reach on top of six targets.
            List<LivingEntity> others = com.cortes.combat.reach.ConeTargets.nearestInCone(
                    player.level(), player, com.cortes.combat.attack.MaceAir.reachFor(stats, player), extra);
            int landed = 0;
            for (LivingEntity other : others) {
                // Skipped, never re-resolved: the aimed mob has already paid and paying it twice
                // would be a hit the player cannot see the second half of.
                if (other == target) {
                    continue;
                }
                // Its own enchantment bonus, because Smite and the crowd charge both read the body
                // being hit. The source does not change: it is still the player's swing.
                AttackResult otherResult = DamageResolver.resolve(base, stats.charge(),
                        stats.profile(), chargeRatio, streak, player.fallDistance, density,
                        enchantBonus(player, other, source, base));
                apply(player, other, stats, otherResult, chargeRatio, smash);
                landed++;
            }
            debug(player, "area: " + landed + "/" + extra + " objetivos extra (tope "
                    + com.cortes.combat.item.WeaponTables.maxTargets(stats.profile()) + ")");
        }
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
                              AttackResult result, float chargeRatio, MaceSmash.Landing smash) {
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
            // **Contragolpe: el golpe que cobra la ventana entra por donde la armadura ya no
            // cubre.** La armadura se baja para ESTE golpe y se devuelve enseguida -- ver
            // ripostePierce, que es donde vive la decisión y por qué no se suma daño.
            LivingEntity armoured = target instanceof LivingEntity wearer ? wearer : null;
            net.minecraft.world.entity.ai.attributes.AttributeInstance armour = armoured == null
                    ? null
                    : armoured.getAttribute(
                            net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            net.minecraft.world.entity.ai.attributes.AttributeModifier pierced =
                    ripostePierce(player, armour);
            if (pierced != null) {
                armour.addTransientModifier(pierced);
            }
            boolean hurt;
            try {
                hurt = target.hurt(
                        damageSource(player, result.ignoredArmour(), result.axeCharged()),
                        result.damage());
            } finally {
                // En un `finally` y no después: `hurt` puede tirar por un mod de terceros, y una
                // armadura que queda baja para siempre es exactamente el bug que este encantamiento
                // no puede dejar atrás.
                if (pierced != null) {
                    armour.removeModifier(pierced);
                }
            }
            target.invulnerableTime = AttackTiming.IFRAME_TICKS;
            if (hurt) {
                landed++;
                anyLanded = true;
                // **The wear the replaced flow dropped, and the reason nothing in this game has
                // ever lost a point of durability.** Vanilla's own `postHurtEnemy` calls
                // `hurtAndBreak(1, attacker, MAINHAND)` on a landed hit, and it lives on the item
                // -- which this mod never calls, because the entire attack flow is replaced. One
                // point per landed hit for every weapon, which is vanilla's sword rule generalised,
                // and per HIT rather than per swing so the sabre's double cut is paid for twice:
                // it lands twice.
                player.getMainHandItem().hurtAndBreak(1, player,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
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

            // **The maza's landing, and it runs BEFORE the post-attack effects on purpose.**
            // Vanilla runs it in that order too: `MaceItem#hurtEnemy` is called inside `hurt`
            // (`Player#attack`:1377), the post-attack effects fire right after it (:1380), and only
            // then `postHurtEnemy` resets the fall (:1385) at the bottom of this block.
            //
            // The descent stop belongs in this half, because Wind Burst's own explosion pushes the
            // player upward and that launch is a feature: stopping the fall after it would cancel
            // the height the enchantment just paid for. And it runs only on the FIRST body that
            // lands -- see `MaceSmash.Landing` for why a second one would erase the launch. What
            // Wind Burst does wipe is the immunity flag, re-set after each body's effects below.
            if (smash != null) {
                smash.onLanding(target);
            }

            // **Vanilla's post-attack enchantment hooks, once per body that took the hit.** These
            // are what fire Aspect of Fire and Wind Burst, and `Player#attack` calls them right
            // after a landed hit -- which the replaced flow never does. Once per target and not
            // once per cut: the sable's second cut is the same victim, and setting it on fire
            // twice is not twice the fire, it is the same fire set a tick later.
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                EnchantmentHelper.doPostAttackEffects(level, target,
                        damageSource(player, result.ignoredArmour(), result.axeCharged()));
            }

            // **Filo Fugaz: la daga compra tempo con el golpe.** Una vez por TAJO y no por corte: el
            // buff es del jugador, y aplicarlo dos veces en el mismo tick es el mismo buff. El gate
            // de "ya lo tengo" vive en el helper, y sin él la daga a seis ticks renovaría la
            // velocidad en cada golpe -- el premio dejaría de ser un premio y pasaría a ser
            // permanente mientras haya algo que apuñalar.
            applyFleetingEdge(player, stats);

            // **The immunity is set AGAIN after the effects, and this is the whole of the fix.**
            // Wind Burst is a POST_ATTACK effect on the attacker: its `ExplodeEffect` explodes at
            // the player, and that explosion calls `ServerPlayer#onExplosionHit`, which overwrites
            // `currentImpulseImpactPos` and clears `ignoreFallDamageFromCurrentImpulse` (the
            // explosion's source entity is the player, not a wind charge). So the guard armed above
            // survives exactly until here, and without this line a maza swing with Wind Burst lands
            // you from the height the enchantment threw you -- the opposite of "a landed smash
            // forgives the fall". One use only: `tryResetCurrentImpulseContext` clears it on the
            // first landing. Also per body, because the maza hits up to three and each one's Wind
            // Burst clears the flag again.
            if (smash != null) {
                smash.afterEffects();
            }

            // **The fall is cancelled here and not in `onHit`, because the smash's sound reads it.**
            // `spawnSlash` above chose the anvil's pitch from `player.fallDistance`; zeroing it any
            // earlier would make the biggest hit in the game sound like the smallest. And only for
            // the maza: vanilla resets the fall on its own smash and on nothing else.
            if (com.cortes.combat.weapon.MaceFall.reads(stats.profile())) {
                FallCoyote.landed(player);
            }
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
     * The landing to replay a maza smash into, or {@code null} when this swing is not one.
     *
     * <p>Two questions, and both have to be yes: is the weapon the maza, and is the wielder
     * actually falling. The second is vanilla's own {@code canSmashAttack} gate -- a grounded maza
     * hit is an ordinary club hit and arms none of the impulse states -- asked of
     * {@link com.cortes.combat.weapon.MaceFall#smashes} so the threshold lives in one place and a
     * bare-JVM test can walk it.</p>
     *
     * <p>Asked <b>once per swing</b>, here in {@code resolveTarget} before any target is applied,
     * because the maza hits up to three bodies and the fall is reset after the first one lands; a
     * per-body question would answer "no smash" for the other two. The {@link MaceSmash.Landing}
     * it returns is what keeps the descent stop to one body and the immunity re-assert per body.</p>
     */
    private static MaceSmash.Landing smashLanding(Player player, WeaponStats stats) {
        if (!com.cortes.combat.weapon.MaceFall.reads(stats.profile())) {
            return null;
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server)) {
            return null;
        }
        return com.cortes.combat.weapon.MaceFall.smashes(server.fallDistance, server.isFallFlying())
                ? new MaceSmash.Landing(server) : null;
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

        // **The fall's own voice, and only when there was one.** The hit above is the same
        // recording at every height, so a two block drop and an eight block one sounded identical
        // while doing twice the damage. The anvil is what tells them apart, and the pitch falls as
        // the damage climbs: more damage, lower note. Zero below the threshold, so an ordinary
        // grounded swing is unchanged.
        float fall = com.cortes.combat.weapon.MaceFall.progressFor(
                stats.profile(), player.fallDistance, com.cortes.combat.attack.MaceAir.airborne(player));
        if (fall > 0.0F) {
            com.cortes.combat.sound.CombatSounds.playMaceSmash(level, target, fall);
        }

        // **The maza's core, on top of its own recording, on every hit.** The hammer's recordings
        // are borrowed and the anvil only speaks when there was a fall, so a grounded mace hit had
        // one voice. This is a second, deeper thud under it, and on the smash it drops with the
        // fall the way every other weight cue in the sound file does.
        if (stats.profile() == WeaponProfile.MACE) {
            com.cortes.combat.sound.CombatSounds.playHeavyCore(level, target,
                    fall > 0.0F ? ModSounds.MACE_CORE_SMASH_PITCH : ModSounds.MACE_CORE_PITCH);
        }

        // **And the pala's own impact, on top of the borrowed swing.** A vanilla item borrowing
        // vanilla's weak hit had no voice; the lantern breaking is the tool landing, and a charged
        // pala adds the wind burst under it.
        if (stats.profile() == WeaponProfile.SHOVEL) {
            com.cortes.combat.sound.CombatSounds.playShovelHit(level, target, charged);
        }
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
    static void playMiss(Player player, WeaponStats stats) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            com.cortes.combat.sound.CombatSounds.play(
                    level, player, stats.profile(), ModSounds.Kind.MISS);
        }
    }

    /**
     * Registers the swing so {@link HitCoyote} can confirm it while the cone comes back into
     * agreement, with the charge frozen at the value the release closed at.
     *
     * <p>The weapon travels as its registry id string rather than as an {@code Item}, for the
     * same reason every field of {@link com.cortes.combat.state.PendingHit} is a plain type: the
     * state stays loadable outside the game, and the swap check at connection compares ids
     * rather than references.</p>
     */
    private static void openPending(Player player, int targetId, float chargeRatio) {
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(player.getMainHandItem().getItem()).toString();
        player.setData(com.cortes.combat.registry.ModAttachments.PENDING_HIT.get(),
                new com.cortes.combat.state.PendingHit(targetId, chargeRatio, itemId,
                        player.level().getGameTime()
                                + com.cortes.combat.state.PendingHit.WINDOW_TICKS));
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
        // **Vanilla's damage indicator, which the replaced attack flow dropped.** It lives here
        // and not in `spawnSlash` because this is the once-per-target call -- the slash is per
        // swing and the number is per body that took the hit.
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            com.cortes.combat.particle.SlashParticles.damageIndicator(level, target, result.damage());
        }
        if (stats.profile() == WeaponProfile.WARHAMMER) {
            // **Ritmo de Hierro decide la ventana con la que la cadena sigue, y se lee acá y no en
            // cada tick.** El nivel sale del arma que golpeó, así que la cadena vale lo que el arma
            // en la mano vale; a partir de este momento viaja en el estado, y cambiar de martillo a
            // mitad de cadena no la colapsa con una regla con la que nunca empezó. Ver StreakState.
            int window = com.cortes.combat.enchant.CombatEnchantRules.streakWindow(
                    com.cortes.combat.enchant.CombatEnchantments.level(
                            player.level().registryAccess(), player.getMainHandItem(),
                            com.cortes.combat.enchant.CombatEnchantments.RHYTHM_OF_IRON));
            StreakState streak = player.getData(ModAttachments.HAMMER_STREAK.get()).onHit(window);
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
        //
        // **And the trident does the opposite instead, when the charge is real.**
        //
        // <p>The gate is the same {@code FULL_CHARGE} the damage reads, and not "any charge at
        // all": a tap on the trident has to shove like everything else, or the weapon has two
        // behaviours and no way to ask for one of them. It is the charged thrust that brings the
        // target in, and that is the half of the weapon the range is built around.</p>
        float pull = com.cortes.combat.item.WeaponTables.pullFor(stats.profile());
        if (pull > 0.0F && chargeRatio >= DamageResolver.FULL_CHARGE) {
            Knockback.pull(player, target, pull);
        } else {
            // The enchantment's own push, on top of the weapon's. Vanilla's Knockback always
            // shoves and has no charge to gate it with, so it is not run through the tap floor --
            // see `Knockback.apply`'s five-argument note. The trident's charged pull is left
            // alone: an inward drag and an outward enchantment push are opposite instructions and
            // the weapon's special wins.
            Knockback.apply(player, target, stats.knockback(),
                    DamageResolver.knockbackFor(chargeRatio),
                    enchantPush(player, target, result));
        }
    }

    /**
     * The flat enchantment bonus for one target, which is two enchantments in one number.
     *
     * <h2>Why both halves arrive through the same channel</h2>
     *
     * <p>{@code modifyDamage} is vanilla's own hook for Sharpness, Smite, Bane of Arthropods,
     * Impaling and Density, and it is the one {@code ServerPlayer#getEnchantedDamage} calls. It is
     * asked with the swing's base as its input and the difference taken -- the same "bonus, not
     * total" shape the fall uses -- so that the caller adds a flat amount and the bonus-free path
     * is the same arithmetic as before.</p>
     *
     * <p>The crowd charge rides along because it is also a flat, per-target damage bonus, and one
     * argument is one place for the resolver to add it. Keeping it separate would be a second
     * channel that could be forgotten by a future caller of {@code resolve}.</p>
     *
     * <h2>The two maza enchantments that are deliberately not here</h2>
     *
     * <p><b>Density and Breach do not use {@code modifyDamage}.</b> They are
     * {@code SMASH_DAMAGE_PER_FALLEN_BLOCK} and {@code ARMOR_EFFECTIVENESS}, each with its own hook
     * and its own {@code EnchantmentHelper} method. Density is ours: it is applied beside the fall,
     * read here by {@link #densityLevel}.</p>
     *
     * <p><b>Breach needs no code and never did.</b> Its only consumer is
     * {@code CombatRules.getDamageAfterAbsorb}, and that runs on the <em>defender</em>, inside
     * {@code LivingEntity#hurt}, well after this file has decided the number. It reads the
     * attacker's weapon off the damage source ({@code getWeaponItem}), and every source this mod
     * builds -- {@code playerAttack}, {@code warhammerIgnoredArmour}, {@code axeCharged} -- puts
     * the player on as the direct entity, which is exactly what that read needs. So it survives the
     * replaced flow untouched. <b>A future auditor should not look for it again.</b></p>
     */
    private static float enchantBonus(Player player, Entity target, DamageSource source, float base) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return 0.0F;
        }
        ItemStack weapon = player.getWeaponItem();
        float enchanted = EnchantmentHelper.modifyDamage(level, weapon, target, source, base) - base;
        return Math.max(0.0F, enchanted);
    }

    /**
     * La onda de filo: el golpe a carga completa con Sweeping Edge dispara un espadazo que viaja.
     *
     * <p><b>Lo que reemplaza.</b> Sweeping Edge era un crowd charge: daño plano que crecía con los
     * cuerpos alrededor del objetivo. El problema era de raíz -- el jugador no controlaba la
     * variable -- y el dueño lo cambió por esto: la onda se dispara con <b>un golpe a carga
     * completa</b>, así que el encantamiento pasa a ser una decisión de distancia y le da a la carga
     * de la espada una razón de existir. Ver {@code weapon/SweepingEdge}.</p>
     *
     * <p><b>Sale con el tajo, acierte o no, y por eso vive acá y no en {@code landTheHit}.</b> El
     * diseño dice "al dar un ataque cargado", no "al acertar": un tajo cargado al aire también tiene
     * que soltar la onda. Y hay una segunda razón, más dura: {@code landTheHit} corre <b>dos veces</b>
     * cuando la ventana de coyote confirma el golpe diferido, así que dispararla allá sería un segundo
     * cobro del mismo tajo -- una onda por tajo es la invariante, y el único lugar donde el tajo
     * existe exactamente una vez es acá.</p>
     *
     * <p><b>El daño es el del arma, no el del objetivo.</b> Se resuelve con la misma
     * {@code DamageResolver.resolve} que el melé usa para elegir la fuente, pero <b>sin</b> el
     * {@code enchantBonus} por objetivo: la onda atraviesa a varios y Sharpness es igual para todos
     * pero Smite no, así que el único número honesto es el cargado del arma. Es la misma regla que el
     * tooltip sigue -- un número, una definición.</p>
     *
     * <p><b>Y saltea al objetivo apuntado.</b> El id viaja con la onda para que no lo cobre dos
     * veces: la onda es el seguimiento del tajo más allá del cuerpo. Un whiff manda
     * {@code NO_TARGET}, que no es ninguna entidad y no saltea a nadie.</p>
     */
    private static void fireSweepingWave(Player player, int aimedId, WeaponStats stats,
                                         float chargeRatio) {
        if (stats.profile() != WeaponProfile.SWORD
                || chargeRatio < DamageResolver.FULL_CHARGE
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        int enchant = sweepingEdgeLevel(level, player.getWeaponItem());
        if (!com.cortes.combat.weapon.SweepingEdge.fires(enchant)) {
            return;
        }
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        StreakState streak = player.getData(ModAttachments.HAMMER_STREAK.get());
        float charged = DamageResolver.resolve(base, stats.charge(), stats.profile(), chargeRatio,
                streak, player.fallDistance).damage();
        float waveDamage = charged * com.cortes.combat.weapon.SweepingEdge.share(enchant);
        com.cortes.combat.entity.SlashWave.fire(level, player, player.getEyePosition(),
                player.getLookAngle(), enchant, waveDamage, aimedId);
        debug(player, "onda de filo nivel " + enchant + " dano=" + waveDamage);
    }

    /**
     * The weapon's Sweeping Edge level, through the registry.
     *
     * <p>A {@code ResourceKey} and not a {@code Holder}, so it has to be resolved against the
     * level's registry before the item can be asked. {@code getHolderOrThrow} rather than a nullable
     * get is deliberate: the enchantment is built into the game, and a missing one is a broken
     * install that should say so rather than read as "no enchantment".</p>
     */
    private static int sweepingEdgeLevel(net.minecraft.server.level.ServerLevel level, ItemStack weapon) {
        Holder<Enchantment> sweeping = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SWEEPING_EDGE);
        return weapon.getEnchantmentLevel(sweeping);
    }

    /**
     * The weapon's Density level, through the registry.
     *
     * <p><b>The enchantment that keeps the maza's fall growing.</b> Vanilla applies Density inside
     * {@code MaceItem#getAttackDamageBonus} as {@code modifyFallBasedDamage(...) * fallDistance},
     * and that class appears nowhere in this project because the entire attack flow is replaced --
     * so without this read a Density maza carried a dead enchantment. The registry plumbing is the
     * same as {@link #sweepingEdgeLevel}'s, and for the same reason: a {@code ResourceKey} has to be
     * resolved against the level's registry before the item can be asked.</p>
     *
     * <p>Zero outside a server level, where there is no registry to ask; the resolver then sums a
     * zero and the maza's fall is unchanged, which is the right answer for a client-side call and
     * the only one available.</p>
     */
    private static int densityLevel(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return 0;
        }
        Holder<Enchantment> density = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.DENSITY);
        return player.getWeaponItem().getEnchantmentLevel(density);
    }

    /**
     * E5 · Filo Fugaz: un golpe de daga que aterriza te da Velocidad, una vez.
     *
     * <h2>Las dos decisiones que están acá y no en la tabla</h2>
     *
     * <p><b>El gate de "ya lo tengo".</b> Si el jugador ya tiene Velocidad -- de este encantamiento,
     * de una poción, o de lo que sea -- no se aplica nada. Sin ese gate la daga, que pega cada seis
     * ticks, renovaría el efecto en cada golpe y el buff sería permanente mientras hubiera algo
     * enfrente: un premio que se puede sostener indefinidamente no es un premio, es un estado. La
     * regla del diseño es "no se refresca si ya está activo", y vive acá porque es una pregunta
     * sobre el jugador y no sobre el arma.</p>
     *
     * <p><b>La puerta del perfil.</b> El archivo de datos ya limita el encantamiento a
     * {@code #cortescombat:profile/dagger}, pero este hook vive en el camino <em>genérico</em> del
     * golpe: cualquier arma pasa por acá. Un datapack que cambie los ítems del encantamiento, o un
     * pack que se equivoque de perfil, le daría Velocidad a una espada -- y una espada con velocidad
     * gratis pelea distinto. La puerta cuesta una línea y es la que el diseño pide.</p>
     *
     * <p>El efecto va sin partículas ni icono: el brillo de Velocidad que el jugador ya conoce es el
     * mismo de la poción, y agregar partículas propias taparía justo lo que el jugador tiene que
     * leer -- de dónde le vino la velocidad.</p>
     */
    private static void applyFleetingEdge(Player player, WeaponStats stats) {
        if (stats.profile() != WeaponProfile.DAGGER) {
            return;
        }
        int ticks = com.cortes.combat.enchant.CombatEnchantRules.fleetingEdgeTicks(
                com.cortes.combat.enchant.CombatEnchantments.level(player.level().registryAccess(),
                        player.getMainHandItem(),
                        com.cortes.combat.enchant.CombatEnchantments.FLEETING_EDGE));
        if (ticks <= 0 || player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, ticks, 0, false, false, false));
    }

    /**
     * E7 · Contragolpe: la armadura que el golpe que sigue a un bloqueo deja de contar.
     *
     * <h2>Por qué se baja la armadura y no se suma daño</h2>
     *
     * <p><b>La cuenta la hace vanilla, y sólo puede hacerla ella.</b> {@code CombatRules.getDamageAfterAbsorb}
     * corre en el <em>defensor</em>, dentro de {@code LivingEntity#hurt}, y lee
     * {@code getArmorValue()} y {@code ARMOR_TOUGHNESS} en ese momento. Sumar daño por nuestra
     * cuenta para simular un cuarto menos de armadura sería una <b>segunda copia de la fórmula de
     * armadura de Minecraft</b> -- con su clamp, su rama dependiente del daño y su dureza -- y una
     * copia que se desincroniza en la próxima versión no falla: pega un número que nadie eligió.</p>
     *
     * <p>Bajando la armadura, el número que vanilla usa <em>es</em> el que le dejamos, y el resto
     * del cálculo queda intacto por construcción. Se devuelve en el mismo tick, así que nada más lo
     * ve -- ni el HUD del defensor, que se sincroniza al final del tick.</p>
     *
     * <h2>Y por qué no el tipo de daño del martillo</h2>
     *
     * <p>Porque {@code bypasses_armor} es un booleano y esto es un cuarto. Pero sobre todo porque
     * vanilla anida {@code bypasses_armor} dentro de {@code bypasses_shield}: un golpe que ignora la
     * armadura también pasa por encima de un escudo levantado. <b>Contragolpe es un encantamiento de
     * guardia</b>, y un premio que atravesara la guardia del rival sería el chiste contado al
     * revés.</p>
     *
     * @return el modificador a poner y sacar, o {@code null} si no hay ventana abierta, no hay
     *         encantamiento o el objetivo no tiene atributos que bajar
     */
    private static net.minecraft.world.entity.ai.attributes.AttributeModifier ripostePierce(
            Player attacker, net.minecraft.world.entity.ai.attributes.AttributeInstance armour) {
        if (armour == null) {
            return null;
        }
        long now = attacker.level().getGameTime();
        com.cortes.combat.state.ContragolpeWindow window =
                attacker.getData(ModAttachments.CONTRAGOLPE.get());
        if (!window.isOpen(now)) {
            return null;
        }
        float ignored = com.cortes.combat.enchant.CombatEnchantRules.riposteArmourIgnored(
                com.cortes.combat.enchant.CombatEnchantments.level(
                        attacker.level().registryAccess(), attacker.getMainHandItem(),
                        com.cortes.combat.enchant.CombatEnchantments.RIPOSTE));
        if (ignored <= 0.0F) {
            // Ventana abierta sin el encantamiento: no se gasta. El bloqueo fue real, pero el arma
            // en la mano no es la que promete el premio, y gastarla seria cobrarle al jugador algo
            // que no recibio.
            return null;
        }
        // **"Tu siguiente ataque" es uno.** La ventana se gasta en el golpe que la usa: si
        // sobreviviera, un bloqueo daria cuarenta ticks de penetracion en vez de un golpe.
        attacker.setData(ModAttachments.CONTRAGOLPE.get(), window.spent());
        return new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                RIPOSTE_MODIFIER_ID, -ignored,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
                        .ADD_MULTIPLIED_TOTAL);
    }

    /**
     * El id del modificador de Contragolpe.
     *
     * <p>Un id fijo y no uno por golpe: se saca con el mismo objeto que se puso, y una identidad
     * nueva por golpe seria una entrada nueva en el mapa de atributos del objetivo cada vez --
     * una fuga lenta de memoria por pegar.</p>
     */
    private static final net.minecraft.resources.ResourceLocation RIPOSTE_MODIFIER_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.cortes.combat.CortesCombat.MODID, "contragolpe");

    /**
     * The Knockback enchantment's push for one target.
     *
     * <p>{@code modifyKnockback} is called with a base of zero because the weapon's own push is
     * the profile's, not the {@code ATTACK_KNOCKBACK} attribute -- this project replaced that
     * number. Asking from zero leaves exactly the enchantment's contribution: vanilla's Knockback
     * adds one point per level to the attribute, and vanilla then converts the whole strength to a
     * velocity with {@code f4 * 0.5F}. Mirroring that factor here is what keeps Knockback I worth
     * about the same shove it is worth in vanilla rather than twice it, which is the difference
     * between a shove and a launch on a number the player did not ask to be enormous.</p>
     */
    private static float enchantPush(Player player, Entity target, AttackResult result) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return 0.0F;
        }
        float strength = EnchantmentHelper.modifyKnockback(level, player.getWeaponItem(), target,
                damageSource(player, result.ignoredArmour(), result.axeCharged()),
                NO_WEAPON_KNOCKBACK);
        return ENCHANT_KNOCKBACK_SCALE * Math.max(0.0F, strength);
    }

    /**
     * Vanilla's own strength-to-velocity factor, {@code f4 * 0.5F} in {@code Player#attack}.
     */
    private static final float ENCHANT_KNOCKBACK_SCALE = 0.5F;

    /**
     * The weapon's contribution to {@code modifyKnockback}, which here is nothing.
     *
     * <p>The per-weapon push is this project's own {@code KnockbackProfile}, not vanilla's
     * {@code ATTACK_KNOCKBACK} attribute, so asking the enchantment from zero is what leaves only
     * the enchantment inside the answer. Named rather than written as a literal because the
     * literal is the one shape the fall's wiring test forbids in this file (it flags a zero
     * argument to the resolver as the bug it is elsewhere).</p>
     */
    private static final float NO_WEAPON_KNOCKBACK = 0.0F;

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
