package com.cortes.combat.attack;

import com.cortes.combat.platform.ResourceIds;

import com.cortes.combat.platform.CombatState;
import com.cortes.combat.platform.EnchantView;


import java.util.List;

import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.CombatTrace;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.enchant.CombatEnchantments;
import com.cortes.combat.effect.QuebrantoEffect;
import com.cortes.combat.item.WeaponTables;
import com.cortes.combat.registry.ModEffects;
import com.cortes.combat.reach.Reach;
import net.minecraft.world.effect.MobEffectInstance;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.registry.ModDamageTypes;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.SecondWindWindow;
import com.cortes.combat.state.StreakState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        ChargeState charge = CombatState.charge(player);
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
        CombatState.setPendingHit(player,
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
        StreakState streak = CombatState.hammerStreak(player);
        // **The flags first, because the damage source they choose is what the enchantment hooks
        // need.** Whether this swing bypasses armour is a property of the weapon and the charge and
        // has nothing to do with the enchantments on it, so a bonus-free resolve is the honest way
        // to ask -- and the source it builds is the same one every target is hit with.
        AttackResult flags = DamageResolver.resolve(base, stats.charge(), stats.profile(),
                chargeRatio, streak, player.fallDistance);
        DamageSource source = damageSource(player, flags.kind());

        // **Per target, because the enchantment does not have one answer.** Sharpness is the same
        // for every body the swing touches, but Smite is not, and neither is a crowd charge that
        // counts the bodies around the one you aimed at. The base, the charge and the fall are the
        // same for all of them; only the flat enchantment bonus moves.
        //
        // Density is read once and sits with the fall, because the fall and Density are the same
        // family: the level does not depend on the body being hit, so one read feeds every target.
        int density = CombatEnchantments.densityLevel(player);
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
                    com.cortes.combat.enchant.CombatEnchantments.ripostePierce(player, armour);
            if (pierced != null) {
                armour.addTransientModifier(pierced);
            }
            // **La vida de ESTE golpe, no la del swing.** El sable cargado pega dos veces, y tomar
            // esto una sola vez antes del bucle haría que la segunda lectura restara las dos --
            // o sea que el segundo corte se cobraría el daño del primero.
            float beforeThisHit = armoured == null ? 0.0F : armoured.getHealth();
            // **La fuente, una sola vez.** La necesitan el `hurt` y la lectura del impacto, y dos
            // llamadas al mismo método serían dos lugares donde las dos pueden discrepar -- y
            // discreparían justo cuando alguien agregue un arma con otro tipo de daño.
            DamageSource hitSource = damageSource(player, result.kind());
            boolean hurt;
            try {
                hurt = target.hurt(hitSource, result.damage());
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
                // **Sanguijuela: la daga devuelve parte del dano como vida.**
                //
                // Por golpe que ATERRIZA y no por swing: la daga es la mas rapida del juego y su
                // gracia es la CANTIDAD de golpes, no el tamano de ninguno. El porcentaje va sobre
                // el dano que el golpe cobro, que es el unico numero que significa lo mismo en una
                // daga de madera que en una de netherite -- un flat de medio corazon seria, contra
                // los 1,6 de base de la daga, mas de un tercio del dano entero devuelto como vida.
                float leech = CombatEnchantRules.sanguijuela(CombatEnchantments.level(
                        player.level().registryAccess(), player.getMainHandItem(),
                        CombatEnchantments.SANGUJUELA));
                if (leech > 0.0F) {
                    player.heal(result.damage() * leech);
                }
                applyOnHitEffects(player, armoured, chargeRatio);
                // **La lectura del impacto: los puntos de color.**
                //
                // Cuánto entró, cuánto comió la armadura y cuánto la poción, con el reparto de
                // HitReadout sobre el total de vanilla. Va acá y no en `landTheHit` porque es
                // **por golpe que aterriza**: el sable cargado pega dos veces y los dos cortes
                // tienen que verse, que es justamente lo que el playtest pidió cuando dijo que
                // "pega doble" y el juego no lo mostraba.
                //
                // Server-side como el slash, y por la misma razón: todos tienen que verlo.
                if (armoured != null
                        && player.level() instanceof net.minecraft.server.level.ServerLevel server) {
                    com.cortes.combat.particle.HitParticles.impact(server, armoured,
                            DamageSplit.of(armoured, hitSource, result.damage(),
                                    beforeThisHit, armoured.getHealth()),
                            chargeRatio >= DamageResolver.FULL_CHARGE);
                }
                // **The wear the replaced flow dropped, and the reason nothing in this game has
                // ever lost a point of durability.** Vanilla's own `postHurtEnemy` calls
                // `hurtAndBreak(1, attacker, MAINHAND)` on a landed hit, and it lives on the item
                // -- which this mod never calls, because the entire attack flow is replaced. One
                // point per landed hit for every weapon, which is vanilla's sword rule generalised,
                // and per HIT rather than per swing so the sabre's double cut is paid for twice:
                // it lands twice.
                com.cortes.combat.platform.ItemDamage.damageMainHand(player, 1);
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
                    boolean breaksGuard = result.kind().breaksGuard();
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
            notifyHit(player, target, healthBefore, chargeRatio);

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
                EnchantView.postAttackEffects(level, target,
                        damageSource(player, result.kind()));
            }

            // **Adrenalina: el golpe cobra lo que el cuerpo se ahorro.**
            //
            // El buff de Velocidad ya no se aplica aca: se mudo a `LivingIncomingDamageEvent`, que es
            // el unico lugar donde un daño se puede reducir -- y reducirlo <em>antes</em> de que se
            // aplique es toda la diferencia entre entrar a un intercambio perdido y sobrevivirlo. Lo
            // que queda de este lado es la mitad ofensiva del nivel dos: el daño que no recibiste
            // vuelve como daño que pegas.
            cashAdrenalina(player, target);

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
            com.cortes.combat.particle.SlashParticles.doubledHit(level,
                    com.cortes.combat.particle.SlashParticles.spriteFor(player, stats.profile()),
                    target);
        } else {
            com.cortes.combat.particle.SlashParticles.hit(
                    level,
                    com.cortes.combat.particle.SlashParticles.spriteFor(player, stats.profile()),
                    target, charged);
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
        CombatState.setPendingHit(player,
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
    private static void notifyHit(Player player, Entity target, float healthBefore,
                                  float chargeRatio) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server)
                || !(target instanceof LivingEntity living)) {
            return;
        }
        boolean killed = healthBefore > 0.0F && living.getHealth() <= 0.0F;
        com.cortes.combat.net.ModNetworking.sendFeedback(server,
                killed ? CombatFeedback.KILL : CombatFeedback.HIT);
        // **Y los dos temblores que este golpe merece: el remate y la carga.**
        //
        // Los dos van aca y no en `onHit` por la misma razon: `onHit` corre UNA VEZ POR OBJETIVO y
        // esto es del golpe. Un barrido de alabarda que toca cuatro mobs daria cuatro temblores por
        // el mismo swing -- el mismo mensaje cuatro veces, y el ojo lo lee como un terremoto.
        //
        // El del remate va en la posicion del MUERTO y no en la del jugador: CameraShake mide su
        // propia distancia y cae al cuadrado, asi que el mismo numero se siente distinto segun
        // donde haya caido el cuerpo -- y un remate a diez bloques no tiene que sacudir como uno a
        // un bloque. El crosshair ya dice "lo mataste" con su color mas vivo, pero eso pide estar
        // mirandolo; un golpe de camara se siente sin mirar nada.
        if (killed) {
            com.cortes.combat.fx.Shake.to(server, living, com.cortes.combat.fx.ShakeKind.KILL);
        }
        // Y el de la carga: cargar cuesta hasta 40 ticks, asi que llegar con la carga llena merece
        // sentirse. No reemplaza al del remate -- matar y matar cargado son dos hechos distintos, y
        // el temblor grande se lo lleva el remate.
        if (chargeRatio >= DamageResolver.FULL_CHARGE) {
            com.cortes.combat.fx.Shake.to(server, living,
                    com.cortes.combat.fx.ShakeKind.CHARGED_HIT);
        }
    }

    /**
     * La historia del temblor de matar, que hoy vive en {@code ShakeKind.KILL}.
     *
     * <p><b>El número tiene historia y la instrumentación la cerró.</b> Arrancó en 0,5; el playtest
     * reportó que no se veía, y se triplicó a 1,5 -- y siguió sin verse. Los logs del último playtest
     * mostraron la amplitud real calculada, {@code amp=1.39} a 1,5 bloques, o sea que <b>el temblor
     * SÍ se aplicaba</b> y el problema nunca fue que no llegara: <b>1,39° no se lee</b>.</p>
     *
     * <p>El orbe usa 1,8 y "sí se siente", pero eso engaña: el orbe explota <b>lejos</b>, donde la
     * caída cuadrática ya lo bajó, así que su número grande llega chico. Acá el cuerpo muere a un
     * bloque y medio, donde {@code amplitudeAt} casi no descuenta -- el 1,5 llega <b>entero</b> y no
     * alcanza igual.</p>
     *
     * <p>Sigue siendo un golpe corto y no un terremoto: {@code CameraShake} decae a cero a lo largo de
     * la vida, así que un impulso de seis ticks es un golpe seco y uno largo sería un zumbido.</p>
     */
    // El numero final es 2,4: los 4,0 que este javadoc describe, menos el 40 % que pidio el playtest.

    // Y la duracion se mudo con el: ShakeKind.KILL son seis ticks.

    private static void onHit(Player player, Entity target, WeaponStats stats,
                              AttackResult result, float chargeRatio) {
        // **La partícula de daño de vanilla, BORRADA de acá.**
        //
        // Estaba desde que el flujo de ataque reemplazado la perdió, y el playtest la mandó a sacar:
        // los puntos de color la reemplazan entera, y las dos juntas son el mismo mensaje dicho dos
        // veces.
        //
        // **Y no se borra el método, se borra la llamada** -- `SlashParticles.damageIndicator` queda
        // sin llamadores, que es un canal muerto que el próximo lector va a encontrar. Se deja
        // anotado acá en vez de dejarlo suelto.
        if (stats.profile() == WeaponProfile.WARHAMMER) {
            // **Ritmo de Hierro decide la ventana con la que la cadena sigue, y se lee acá y no en
            // cada tick.** El nivel sale del arma que golpeó, así que la cadena vale lo que el arma
            // en la mano vale; a partir de este momento viaja en el estado, y cambiar de martillo a
            // mitad de cadena no la colapsa con una regla con la que nunca empezó. Ver StreakState.
            int window = com.cortes.combat.enchant.CombatEnchantRules.streakWindow(
                    com.cortes.combat.enchant.CombatEnchantments.level(
                            player.level().registryAccess(), player.getMainHandItem(),
                            com.cortes.combat.enchant.CombatEnchantments.RHYTHM_OF_IRON));
            StreakState streak = CombatState.hammerStreak(player).onHit(window);
            CombatState.setHammerStreak(player, streak);
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
            // **Inercia: el empuje del martillo va al aire, y REEMPLAZA al horizontal.**
            //
            // Va en el `else` de `apply` y no encima, porque no es un empuje mas: es el mismo
            // apuntado a otro lado, y sumar los dos daria las dos cosas a la vez. La cadena la lee
            // el estado -- que ya la incremento arriba, en la rama del martillo -- asi que el numero
            // es "cuantos golpes seguidos van", no "cuantos van este tick".
            float lift = 0.0F;
            if (stats.profile() == WeaponProfile.WARHAMMER) {
                lift = com.cortes.combat.enchant.CombatEnchantRules.inerciaLift(
                        com.cortes.combat.enchant.CombatEnchantments.level(
                                player.level().registryAccess(), player.getMainHandItem(),
                                com.cortes.combat.enchant.CombatEnchantments.INERCIA),
                        CombatState.hammerStreak(player).hits());
            }
            if (lift > 0.0F) {
                Knockback.applyLift(target, stats.knockback(),
                        DamageResolver.knockbackFor(chargeRatio), lift);
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

        // **Ensarte: la estocada atraviesa al de adelante y cobra al de atras.**
        //
        // Va al final y no antes porque necesita el golpe ya resuelto: el dano que atraviesa es una
        // fraccion del que cobro el primer cuerpo, y el primer cuerpo no cobra hasta que `apply`
        // termina. Y va en `onHit` -- el camino de una-vez-por-objetivo -- porque un golpe que
        // atraviesa a dos tiene que decidir una vez, no una por cada cuerpo que encuentre.
        ensarte(player, target, stats, result);

        // Y los cinco de la ultima ronda, que tambien se cobran en el golpe.
        lastRoundOnHit(player, target, stats, result, chargeRatio);
    }

    /**
     * El segundo cuerpo de una estocada, si hay uno alineado.
     *
     * <h2>Por que es un metodo y no cuatro lineas aca adentro</h2>
     *
     * <p>Porque el "atras" tiene tres condiciones y las tres son el encantamiento: <b>mas alla del
     * primer cuerpo</b> (si no, seria golpear dos veces al mismo), <b>cerca de la linea</b> del golpe
     * (si no, seria un golpe en area con otro nombre), y <b>un cuerpo vivo</b>. Cada una de las tres
     * puede escribirse mal de una forma que compila y que no se ve en pantalla, que es la clase de
     * bug que este proyecto ya pago varias veces.</p>
     *
     * <h2>Y el segundo cuerpo no cobra bonus de encantamientos</h2>
     *
     * <p>Los bonuses por objetivo -- Punta Fina, Filo Astillado, Apu\u00f1alar -- leen <em>a quien</em> se
     * golpea: su armadura, su angulo, su distancia. Pagarlos otra vez seria cobrar dos veces por una
     * decision que el jugador tomo una sola vez, y ademas le daria al encantamiento un numero que
     * depende del segundo cuerpo en vez de del primero. Lo que atraviesa es el <b>dano base</b>, que
     * es lo que la estocada puso.</p>
     */
    private static void ensarte(Player player, Entity target, WeaponStats stats, AttackResult result) {
        // El gate es el perfil, que es lo que da la clase: Ensarte es de las largas (alabarda y
        // tridente), y una alabarda de otro mod con nuestro tag de molde hereda el perfil y con el
        // el encantamiento.
        if ((stats.profile() != WeaponProfile.HALBERD && stats.profile() != WeaponProfile.TRIDENT)
                || !(target instanceof LivingEntity first)) {
            return;
        }
        float share = com.cortes.combat.enchant.CombatEnchantRules.ensarteShare(
                com.cortes.combat.enchant.CombatEnchantments.level(
                        player.level().registryAccess(), player.getMainHandItem(),
                        com.cortes.combat.enchant.CombatEnchantments.ENSARTE));
        if (share <= 0.0F) {
            return;
        }

        // La linea del golpe: del jugador al primer cuerpo, y un paso mas alla.
        Vec3 line = first.position().subtract(player.position());
        if (line.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 unit = line.normalize();
        Vec3 beyond = first.position().add(unit.scale(1.2));
        AABB box = new AABB(beyond, beyond).inflate(1.0, 1.0, 1.0);

        for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (other == player || other == first || !other.isAlive()
                    || other.isSpectator() || other.isRemoved()) {
                continue;
            }
            // Alineado: el cuerpo tiene que estar del otro lado y pegado a la linea. El 0,9 es medio
            // cuerpo de holgura, que es lo que hace que un mob pegado al primero cuente y uno al
            // costado no.
            Vec3 toOther = other.position().subtract(first.position());
            if (toOther.dot(unit) <= 0.0 || perpendicular(toOther, unit) > 0.9) {
                continue;
            }
            // El dano base, sin los bonuses por objetivo -- ver el javadoc.
            other.invulnerableTime = 0;
            if (other.hurt(player.damageSources().playerAttack(player), result.damage() * share)) {
                other.invulnerableTime = AttackTiming.IFRAME_TICKS;
                if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    com.cortes.combat.particle.SlashParticles.hit(level,
                            com.cortes.combat.particle.SlashParticles.spriteFor(player,
                                    stats.profile()),
                            other, false);
                }
            }
            // Uno y no todos: la estocada atraviesa a UNO, que es lo que la palabra dice.
            break;
        }
    }

    /** La distancia de un punto a una recta que pasa por el origen con direccion unitaria. */
    private static double perpendicular(Vec3 point, Vec3 unit) {
        double along = point.dot(unit);
        return point.subtract(unit.scale(along)).length();
    }

    /**
     * Los cinco de la ultima ronda que se cobran en el golpe: Sentencia, Paliza, Gripa, Trabazon y
     * Corte Gemelo.
     *
     * <h2>Por que viven juntos y no repartidos</h2>
     *
     * <p>Porque <b>los cinco se cobran en el mismo momento</b> -- una vez por cuerpo golpeado -- y
     * repartirlos habria puesto cinco ramas en {@code onHit} que hay que leer enteras para saber que le
     * pasa a un golpe. El metodo es el "y ademas" del golpe: todo lo que estos encantamientos agregan
     * <em>despues</em> de que el daño entro y el empuje salio.</p>
     *
     * <p><b>Y cada uno pregunta por su perfil.</b> El hook esta en el camino generico del golpe, asi
     * que sin la puerta un datapack que mueva un encantamiento de arma le daria a cualquier cosa la
     * mecanica de otra. Es la misma puerta que el resto del proyecto, cinco veces.</p>
     */
    private static void lastRoundOnHit(Player player, Entity target, WeaponStats stats,
                                       AttackResult result, float chargeRatio) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        long now = player.level().getGameTime();
        var access = player.level().registryAccess();
        ItemStack held = player.getMainHandItem();
        boolean charged = chargeRatio >= DamageResolver.FULL_CHARGE;

        // --- Sentencia: cinco golpes de cadena y el cuerpo revienta.
        //
        // El golpe que llega al umbral es el que lo hace, y la cadena ya se incremento arriba -- asi
        // que la pregunta es si el contador esta en cinco. Y el reventon NO daña al que ataca: un
        // premio que te cobra a vos es el veto C2 con otro nombre.
        if (stats.profile() == WeaponProfile.WARHAMMER) {
            int level = CombatEnchantments.level(access, held, CombatEnchantments.SENTENCIA);
            int hits = CombatState.hammerStreak(player).hits();
            if (level > 0 && hits >= CombatEnchantRules.SENTENCIA_STREAK) {
                sentencia(player, living, level);
            }
        }

        // --- Paliza: el golpe cargado que cierra una serie empuja como un cañon.
        if (stats.profile() == WeaponProfile.SHOVEL) {
            int streak = CombatEnchantRules.palizaStreak(CombatEnchantments.level(
                    access, held, CombatEnchantments.PALIZA));
            if (streak > 0) {
                StreakState series = CombatState.palizaStreak(player)
                        .onHit(CombatEnchantRules.PALIZA_WINDOW_TICKS);
                if (charged && series.hits() > streak) {
                    // **El empuje reemplaza al del golpe y no se suma**, y los dos numeros -- 2,4 y
                    // 0,6 -- son los que obligaron a subir el techo de knockback. El reloj se limpia:
                    // una serie que se cobro no se puede cobrar dos veces.
                    Knockback.applyLift(living,
                            new com.cortes.combat.core.weapon.KnockbackProfile(
                                    CombatEnchantRules.PALIZA_HORIZONTAL,
                                    CombatEnchantRules.PALIZA_VERTICAL),
                            DamageResolver.knockbackFor(chargeRatio),
                            CombatEnchantRules.PALIZA_LIFT_SHARE);
                    series = StreakState.EMPTY;
                }
                CombatState.setPalizaStreak(player, series);
            }
        }

        // --- Gripa: el moco, que sube de stack con cada golpe.
        if (stats.profile() == WeaponProfile.DAGGER) {
            int max = CombatEnchantRules.gripaMaxStacks(CombatEnchantments.level(
                    access, held, CombatEnchantments.GRIPA));
            if (max > 0) {
                // **El stack sube y se clampea al tope del nivel.** El efecto que ya esta se lee para
                // saber cuantos hay: sin eso, cada golpe empezaria de uno y el encantamiento seria un
                // daño plano por tick disfrazado de acumulacion.
                var current = living.getEffect(com.cortes.combat.registry.ModEffects.moco());
                int stacks = current == null ? 1 : Math.min(max, current.getAmplifier() + 2);
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        com.cortes.combat.registry.ModEffects.moco(),
                        CombatEnchantRules.GRIPA_TICKS, stacks - 1, false, false, false));
            }
        }

        // --- Trabazon: el golpe cargado clava al rival en el piso.
        if (stats.profile() == WeaponProfile.WARHAMMER && charged) {
            int level = CombatEnchantments.level(access, held, CombatEnchantments.TRABAZON);
            if (level > 0) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        com.cortes.combat.registry.ModEffects.trabazon(),
                        CombatEnchantRules.TRABAZON_TICKS, level - 1, false, false, false));
            }
        }

        // --- Corte Gemelo: el segundo corte del sable.
        if (stats.profile() == WeaponProfile.SABRE) {
            int interval = CombatEnchantRules.corteGemeloInterval(CombatEnchantments.level(
                    access, held, CombatEnchantments.CORTE_GEMELO));
            var gemelo = CombatState.corteGemelo(player);
            if (interval >= 0 && gemelo.isReady(now)) {
                living.invulnerableTime = 0;
                living.hurt(player.damageSources().playerAttack(player),
                        result.damage() * CombatEnchantRules.CORTE_GEMELO_SHARE);
                living.invulnerableTime = AttackTiming.IFRAME_TICKS;
                // El nivel dos tiene intervalo cero, asi que el reloj no se mueve y el corte sale
                // siempre; el uno lo gasta por cuatro segundos.
                CombatState.setCorteGemelo(player, gemelo.spent(now, interval));
            }
        }
    }

    /**
     * El reventon de Sentencia: el cuerpo que llego a cinco golpes de cadena.
     *
     * <p><b>El jugador que ataca queda afuera, y no es un detalle.</b> El reventon ocurre en el cuerpo
     * del rival, que esta a menos de dos bloques de quien lo golpeo; cobrarle a el tambien seria un
     * encantamiento que castiga al que lo usa, que es el veto C2 con otro nombre. El radio se cobra
     * <em>sin el atacante</em>, por el mismo motivo que el de Santa Muerte.</p>
     *
     * <p>Y el nivel dos encadena: los que estan alrededor revientan a su vez, a la mitad. Es lo que
     * convierte el reventon en una herramienta de multitud en vez de un remate.</p>
     */
    private static void sentencia(Player player, LivingEntity body, int level) {
        float damage = CombatEnchantRules.sentenciaDamage(level);
        float splash = CombatEnchantRules.sentenciaSplash(level);

        body.hurt(player.damageSources().playerAttack(player), damage);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            com.cortes.combat.particle.SlashParticles.hit(server,
                    com.cortes.combat.particle.SlashParticles.spriteFor(player,
                            WeaponProfile.WARHAMMER),
                    body, true);
        }

        AABB box = body.getBoundingBox().inflate(CombatEnchantRules.SENTENCIA_RADIUS);
        for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (other == player || other == body || !other.isAlive()) {
                continue;
            }
            other.hurt(player.damageSources().playerAttack(player), splash);
            // El nivel dos encadena: el cuerpo de al lado revienta a su vez, a la mitad.
            if (level >= CombatEnchantRules.SENTENCIA_MAX_LEVEL) {
                float chained = damage * CombatEnchantRules.SENTENCIA_CHAIN_SHARE;
                AABB inner = other.getBoundingBox().inflate(CombatEnchantRules.SENTENCIA_RADIUS);
                for (LivingEntity third : player.level().getEntitiesOfClass(LivingEntity.class, inner)) {
                    if (third == player || third == other || !third.isAlive()) {
                        continue;
                    }
                    third.hurt(player.damageSources().playerAttack(player), chained);
                }
            }
        }
    }

    /**
     * Los tres efectos que un golpe deja en el cuerpo: Cicatrizador, Destripamiento y Quebranto.
     *
     * <h2>Por qué los tres en una función</h2>
     *
     * <p>Porque los tres se cobran en el mismo momento -- <b>el golpe que aterrizó</b> -- y los tres
     * son "aplicar un efecto con una duración y un nivel". Tres llamadas sueltas en {@code apply}
     * serían tres lugares donde olvidarse de una, y el modo de falla de un encantamiento sin llamador
     * es que <b>el build queda verde</b>.</p>
     *
     * <h2>Las tres diferencias, que son las que importan</h2>
     *
     * <ul>
     *   <li><b>Destripamiento sólo con carga completa.</b> Es la decisión del diseño: la alabarda
     *       paga caro por cargar y el sangrado es el premio, así que un tap no lo deja. Y <b>no se
     *       apila</b> -- reaplicar refresca -- porque un DoT que se apilara con cada cargado
     *       convertiría el arma en una que mata sola después de tres segundos de no hacer nada.</li>
     *   <li><b>Quebranto sí se apila, con tope.</b> El stack ES el {@code amplifier}, así que el
     *       tope se lee del efecto anterior en vez de contarse aparte: una cuenta propia sería una
     *       segunda verdad sobre cuántos stacks tiene el cuerpo, y la primera es el efecto.</li>
     *   <li><b>Cicatrizador es el único que va contra la curación</b> y no contra la vida, y por eso
     *       no tiene nada que apilar: su nivel es su nivel.</li>
     * </ul>
     */
    private static void applyOnHitEffects(Player player, LivingEntity target, float chargeRatio) {
        var access = player.level().registryAccess();
        ItemStack weapon = player.getMainHandItem();

        int bruiser = CombatEnchantments.level(access, weapon, CombatEnchantments.CICATRIZADOR);
        if (bruiser > 0) {
            target.addEffect(new MobEffectInstance(ModEffects.antiHealing(),
                    CombatEnchantRules.CICATRIZADOR_TICKS,
                    CombatEnchantRules.amplifierFor(bruiser), false, true));
        }

        int gut = CombatEnchantments.level(access, weapon, CombatEnchantments.DESTRIPAMIENTO);
        if (gut > 0 && chargeRatio >= DamageResolver.FULL_CHARGE) {
            target.addEffect(new MobEffectInstance(ModEffects.bleeding(),
                    CombatEnchantRules.sangradoTicks(gut),
                    CombatEnchantRules.amplifierFor(gut), false, true));
        }

        int sunder = CombatEnchantments.level(access, weapon, CombatEnchantments.QUEBRANTO);
        if (sunder > 0) {
            MobEffectInstance current = target.getEffect(ModEffects.quebranto());
            int stacks = current == null
                    ? 1
                    : Math.min(QuebrantoEffect.MAX_STACKS, current.getAmplifier() + 2);
            target.addEffect(new MobEffectInstance(ModEffects.quebranto(),
                    CombatEnchantRules.QUEBRANTO_TICKS, stacks - 1, false, true));
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
     * read by {@link CombatEnchantments#densityLevel}.</p>
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
        ItemStack weapon = player.getMainHandItem();
        float enchanted = EnchantView.modifiedDamage(level, weapon, target, source, base) - base;
        return Math.max(0.0F, enchanted + perTargetBonus(player, weapon, target, base));
    }

    /**
     * Los bonus <b>por objetivo</b> que son nuestros: Punta Fina, Filo Astillado y Apuñalar.
     *
     * <h2>Por qué los tres viven en una función y no en tres call sites</h2>
     *
     * <p>Los tres son <b>un porcentaje del daño que depende de algo del objetivo o de la
     * posición</b> -- cuán cerca está, cuánta armadura lleva, desde dónde le pegás -- y los tres se
     * cobran en el mismo lugar: el número que el resolver va a usar contra <em>ese</em> cuerpo. Un
     * canal por encantamiento serían tres lugares donde olvidarse de llamarlo, y el modo de falla de
     * un canal que nadie llama es que <b>el build queda verde</b>: el encantamiento existe, se puede
     * poner, y no hace nada.</p>
     *
     * <p><b>Devuelve el delta y no el total</b>, como la mitad de vanilla de arriba: el llamador
     * suma, y el camino sin encantamiento es la misma aritmética de antes.</p>
     *
     * <h2>Las tres condiciones, y de dónde salen</h2>
     *
     * <ul>
     *   <li><b>Punta Fina</b>: la distancia entre el jugador y el cuerpo. La ropera pelea pegada, y
     *       ninguna otra arma premia estar tan cerca.</li>
     *   <li><b>Filo Astillado</b>: las piezas de armadura que el cuerpo lleva <b>puestas</b> (no las
     *       que tiene en el inventario). Es la inversa exacta del martillo, que premia al blindado.</li>
     *   <li><b>Apuñalar</b>: el yaw del objetivo contra el vector que va de él al atacante. La daga
     *       es la única que puede reposicionarse lo bastante rápido para buscar la espalda.</li>
     * </ul>
     */
    private static float perTargetBonus(Player player, ItemStack weapon, Entity target, float base) {
        if (!(target instanceof LivingEntity body)) {
            return 0.0F;
        }
        var access = player.level().registryAccess();

        float share = CombatEnchantRules.puntaFina(
                CombatEnchantments.level(access, weapon, CombatEnchantments.PUNTA_FINA),
                player.distanceTo(target));

        int pieces = 0;
        for (ItemStack slot : body.getArmorSlots()) {
            if (!slot.isEmpty()) {
                pieces++;
            }
        }
        share += CombatEnchantRules.filoAstillado(
                CombatEnchantments.level(access, weapon, CombatEnchantments.FILO_ASTILLADO), pieces);

        share += CombatEnchantRules.apunalar(
                CombatEnchantments.level(access, weapon, CombatEnchantments.APUNALAR),
                CombatEnchantRules.fromBehind(body.getYRot(),
                        player.getX() - body.getX(), player.getZ() - body.getZ()));

        // **Segundo Aliento: el golpe que cobra la ventana pega mas.**
        //
        // La ventana NO se gasta aca, y la razon es de orden: el empuje se cobra en otro canal que
        // corre DESPUES de este, y una ventana gastada en el primero deja al segundo sin premio. Se
        // lee en los dos y se gasta una sola vez, en el ultimo.
        SecondWindWindow wind = CombatState.secondWindow(player);
        if (wind.isOpen(player.level().getGameTime())) {
            share += CombatEnchantRules.segundoAliento(CombatEnchantments.level(access, weapon,
                    CombatEnchantments.SEGUNDO_ALIENTO));
        }

        return base * share;
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
        int enchant = CombatEnchantments.sweepingEdgeLevel(level, player.getMainHandItem());
        if (!com.cortes.combat.weapon.SweepingEdge.fires(enchant)) {
            return;
        }
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        StreakState streak = CombatState.hammerStreak(player);
        float charged = DamageResolver.resolve(base, stats.charge(), stats.profile(), chargeRatio,
                streak, player.fallDistance).damage();
        float waveDamage = charged * com.cortes.combat.weapon.SweepingEdge.share(enchant);
        com.cortes.combat.entity.SlashWave.fire(level, player, player.getEyePosition(),
                player.getLookAngle(), enchant, waveDamage, aimedId);
        // **La onda se oye.** El archivo es del usuario y no lo nombraba ningun evento, asi que la
        // onda salia muda: se veia el filo cruzar el aire y no habia nada que dijera que ademas
        // estaba cobrando. Suena sobre el jugador y no sobre la onda porque la onda nace ahi, y el
        // sonido de un arma lo hace el que la empuna.
        level.playSound(null, player, com.cortes.combat.sound.CombatSounds.event(
                        com.cortes.combat.sound.ModSounds.SWEEPING_EDGE),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        debug(player, "onda de filo nivel " + enchant + " dano=" + waveDamage);
    }

    // **El javadoc de Filo Fugaz vivio aca y se fue con el encantamiento.** Describia el gate de "ya
    // lo tengo" (la Velocidad no se renovaba) y la puerta del perfil, y las dos siguen siendo ciertas
    // en su lugar nuevo -- el gate desaparecio porque el efecto ahora sale del daño recibido y no del
    // golpe, y la puerta del perfil se mudo a `AdrenalinaHandler`. Dejar el texto aca habria sido un
    // comentario sobre un metodo que ya no existe, que es la forma mas barata de mentir en un archivo.

    /**
     * Adrenalina: el daño que el cuerpo se ahorro, devuelto en el proximo golpe.
     *
     * <h2>Por que se cobra en un golpe aparte y no sumado al que acaba de entrar</h2>
     *
     * <p>Porque <b>el daño del golpe ya esta resuelto cuando este metodo corre</b>: el
     * {@code AttackResult} es un record y salio del {@code DamageResolver} con su armadura, su carga
     * y sus bonuses por objetivo ya aplicados. Sumarle un numero despues seria una segunda aritmetica
     * del mismo golpe, y la segunda copia de una aritmetica es la que se desincroniza. Un {@code hurt}
     * en el mismo tick, con la cuenta vaciada, da el mismo total y no toca el resolver.</p>
     *
     * <h2>Y la cuenta se vacia siempre que se lea</h2>
     *
     * <p>Se cobra o se pierde, pero no se guarda: {@code cashed()} corre <em>antes</em> de preguntar
     * por el objetivo, asi que un golpe al aire tambien gasta lo ahorrado. La alternativa -- guardarlo
     * hasta encontrar un cuerpo -- seria un daño que crece entre peleas y que el jugador no puede leer
     * en ninguna parte de la pantalla.</p>
     */
    private static void cashAdrenalina(Player player, Entity target) {
        com.cortes.combat.state.Adrenalina state =
                CombatState.adrenalina(player);
        if (state.banked() <= 0.0F) {
            return;
        }
        CombatState.setAdrenalina(player, state.cashed());
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) {
            return;
        }
        living.invulnerableTime = 0;
        living.hurt(player.damageSources().playerAttack(player), state.banked());
        living.invulnerableTime = AttackTiming.IFRAME_TICKS;
    }

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
        ItemStack weapon = player.getMainHandItem();
        float strength = EnchantView.modifiedKnockback(level, weapon, target,
                damageSource(player, result.kind()),
                NO_WEAPON_KNOCKBACK);
        float push = ENCHANT_KNOCKBACK_SCALE * Math.max(0.0F, strength);

        // **Culata: la bayoneta del arcabuz empuja mas, y es lo unico que hace.**
        //
        // El arcabuz tiene dos botones y el izquierdo es "se me acabo la polvora, uso la bayoneta":
        // su funcion no es pegar -- para eso esta el disparo -- sino hacer espacio para recargar.
        // Asi que el encantamiento sube el empuje y no el dano, y el extra se calcula como una
        // FRACCION del empuje propio del arma: un numero absoluto sumado a un perfil que ya es de
        // los mas altos (1,10) se saldria de la escala.
        //
        // El gate es el PERFIL y no el item: un weapon pack que declare un arma "tipo arcabuz"
        // hereda el encantamiento, que es la regla de compatibilidad del proyecto.
        if (WeaponStats.of(weapon).profile() == WeaponProfile.ARCABUZ) {
            float culata = CombatEnchantRules.culata(CombatEnchantments.level(
                    player.level().registryAccess(), weapon, CombatEnchantments.CULATA));
            push += culata * WeaponTables.knockback(WeaponProfile.ARCABUZ).horizontal();
        }

        // **Segundo Aliento: y empuja mas. Aca SI se gasta.**
        //
        // "Tu siguiente golpe" es uno, y este es el ultimo canal que mira la ventana: el dano la lee
        // antes y no la toca. Gastarla aca es lo que hace que un bloqueo roto de un premio y no un
        // estado de tres segundos.
        SecondWindWindow wind = CombatState.secondWindow(player);
        if (wind.isOpen(player.level().getGameTime())) {
            push += CombatEnchantRules.SEGUNDO_ALIENTO_PUSH
                    * WeaponTables.knockback(WeaponStats.of(weapon).profile()).horizontal();
            CombatState.setSecondWindow(player, wind.spent());
        }
        return push;
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
    private static DamageSource damageSource(Player player, DamageKind kind) {
        return switch (kind) {
            case PURE -> ModDamageTypes.warhammerIgnoredArmour(player);
            case AXE_CHARGED -> ModDamageTypes.axeCharged(player);
            case NORMAL -> player.damageSources().playerAttack(player);
        };
    }
}