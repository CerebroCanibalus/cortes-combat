package com.cortes.combat.special;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.Knockback;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.particle.SlashParticles;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.registry.ModComponents;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.sound.CombatSounds;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.state.ArcabuzState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The arcabuz's right button: light the fuse, or load the gun.
 *
 * <h2>One button, two meanings, decided by what is already in it</h2>
 *
 * <p>This is the same idiom the whole combat system is built on -- one press meaning two things
 * -- applied to a different axis. The attack button means "hit" or "hit harder" depending on how
 * long it was held; this one means "load" or "fire" depending on whether a firestone is in the
 * chamber. Both readings come out of {@link ArcabuzState}, and the state is the only thing that
 * decides, which is why the decision is a test rather than a hope.</p>
 *
 * <h2>Why the press does not fire</h2>
 *
 * <p>The press lights a fuse and spends the firestone. The shot is twenty ticks later, resolved
 * by {@link #tick} from the server's own clock. That split is the whole design -- a musket is not
 * a weapon whose damage happens when you click -- and it is also the third time this project has
 * had to write the "a state that outlives the button that made it" rule down. The two earlier
 * ones were the ropera's discarded charge and the parry's swallowed countdown, and both were a
 * caller asking a question the state could have answered itself.</p>
 *
 * <p>So a second press while the fuse burns is refused, and refused <em>without</em> resetting
 * the countdown. See {@link ArcabuzState#canLight()}.</p>
 *
 * <h2>What the shot costs, and what it does not</h2>
 *
 * <ul>
 *   <li><b>One firestone</b>, spent at the light rather than at the shot, so a gun with a live
 *       fuse cannot be topped up behind the player's back.</li>
 *   <li><b>One second of standing still</b>, which is the delay and is not a cooldown.</li>
 *   <li><b>The attack cooldown, on the reload only.</b> That is the decision requirement 6 asks
 *       to be justified, and it is the halberd's argument verbatim: a special with its own timer
 *       stacks with the swing cooldown, and then "what does this button cost" is two numbers.
 *       Loading is not free and it is not instant, and the one number the player already knows
 *       how to read is the bracket around the crosshair.</li>
 * </ul>
 *
 * <p>Lighting the fuse deliberately does <b>not</b> touch the cooldown. It already costs a
 * firestone and a second of doing nothing, and paying a third price would make the most
 * expensive button in the game also the one with the most arithmetic on it. The result is a
 * rhythm of about a second and a half per shot -- half walking, half loading -- which is what a
 * black powder weapon is.</p>
 */
public final class Arcabuz {

    /**
     * Matches the anti-double-hit window the normal attack uses.
     *
     * <p>Cleared before <em>every</em> hit in the loop rather than once before it, for the same
     * reason {@code CombatEngine#apply} does: the window is one full second in vanilla, ten of
     * our targets would collapse into one hit if it were not re-armed between them, and "a blast
     * that hit four things because three of them were immune" reads as the weapon being broken
     * rather than as vanilla working.</p>
     */
    private static final int IFRAME_TICKS = 10;

    private Arcabuz() {
    }

    /**
     * Resolves one right-button press on the arcabuz.
     *
     * <p>Exactly one of two things happens, and the state decides which. Nothing else: no third
     * branch, no "if it is somehow both", because the state cannot be both
     * ({@code loaded && fuseTicks > 0} is refused by both gates at once).</p>
     */
    public static void resolve(ServerPlayer player, ServerLevel level, WeaponStats stats) {
        ArcabuzState state = player.getData(ModAttachments.ARCABUZ.get());

        if (state.canLight()) {
            // The firestone is gone on this tick, before the fuse has moved one step. That is the
            // rule the whole state exists for and putting it here makes it true by construction
            // rather than by the order of two statements further down.
            player.setData(ModAttachments.ARCABUZ.get(), state.light());
            ModNetworking.sendArcabuzFuse(player, true, ArcabuzState.FUSE_TICKS, false);
            trace(player, state, "mecha encendida, LASTIMA gastada");
            return;
        }

        // Refused mid-fuse on purpose: no branch here, no log, no sound. A press that did nothing
        // is what a cooldown looks like, and the fuse burning is the feedback that says why.
        if (state.canReload() && load(player)) {
            player.setData(ModAttachments.ARCABUZ.get(), state.reload());
            AttackCooldown.consume(player, AttackCooldown.intervalFor(player));
            CombatSounds.play(level, player, WeaponProfile.ARCABUZ, ModSounds.Kind.CHARGE);
            ModNetworking.sendArcabuzFuse(player, false, 0, true);
            trace(player, state, "cargada");
        }
    }

    /**
     * Whether the player has a firestone, and spends one if they do.
     *
     * <p>Out of the whole inventory and not out of the hand. The hand has to hold the gun or the
     * gun would not be held, and the recipe for a firestone is two of them out of a gunpowder
     * and a glowstone, so it is a stack a player carries rather than something they click.</p>
     *
     * <p>Checked before the cooldown is paid, so a player with no firestone and a ready
     * cooldown is not left with a swing they did not ask for.</p>
     */
    private static boolean load(ServerPlayer player) {
        ItemStack probe = new ItemStack(ModItems.FIRESTONE.get());
        if (!player.getInventory().contains(probe)) {
            trace(player, player.getData(ModAttachments.ARCABUZ.get()), "sin LASTIMA");
            return false;
        }
        player.getInventory().removeItem(probe);
        return true;
    }

    /**
     * One tick of a burning fuse: count down, and fire on the tick it runs out.
     *
     * <p>Nothing is decided here. {@link ArcabuzState#step()} answers both questions -- does the
     * state move, and does the shot go off -- and this writes what it is handed. The parry's
     * ticker used to decide, and the decision it made ("the window is closed, nothing to report")
     * was true of the packet and silently also taken as true of the countdown, so the lockout
     * never ran and the sabre could be parried once per session.</p>
     */
    public static void tick(ServerPlayer player) {
        ArcabuzState.Step step = player.getData(ModAttachments.ARCABUZ.get()).step();
        player.setData(ModAttachments.ARCABUZ.get(), step.state());

        // **The visible fuse frame, written onto the gun in the player's hand.**
        //
        // The drawn art is one strip of five frames and a .mcmeta runs it freely, so the gun's
        // fuse burned down in the inventory whether or not a shot had been fired -- and 1.21.1
        // gives a mod no way to choose a frame of an animated texture. So the strip is five files
        // and the model picks one with a predicate on this component.
        //
        // **It is a component and not a client field because every client that can see the gun has
        // to see the same frame.** A client-side number would be right on the one screen that
        // computed it and wrong on the other player's, which for a weapon whose whole mechanic is
        // "how long until it goes off" is not a cosmetic bug.
        writeFuseFrame(player, step.state());

        if (step.tellClient()) {
            // The state's own `loaded`, not a literal. It was written `true` here first and that
            // told the client a gun with nothing in it was loaded, which is a viewmodel drawing a
            // charged musket over an empty one -- the same shape as a signal that does not carry
            // the state it is supposed to carry, only its own optimism about it.
            ModNetworking.sendArcabuzFuse(player, false, 0, step.state().loaded());
        }
        if (step.fireNow() && player.level() instanceof ServerLevel level) {
            fire(player, level);
        }
    }

    /**
     * Which of the five drawn frames the gun is showing, written onto the stack.
     *
     * <p><b>Zero when nothing is lit.</b> Frame zero is the unlit gun, and it is also the default,
     * so an arcabuz that has never been fired and one whose fuse has just finished look the same and
     * neither of them looks like a gun mid-shot.</p>
     *
     * <p><b>Frames one to four across the burn, and the arithmetic is deliberately not
     * {@code progress * 5}.</b> That would put a frame on the far side of the burn the moment the
     * fuse was lit, which is a gun with a fuse already burning. The frame is
     * {@code ceil(fractionBurned * 4)} instead: frame one arrives when the fuse has burnt a
     * quarter, and frame four -- almost out -- arrives only in the last quarter tick.</p>
     *
     * <p>The set is guarded against rewriting the stack when the frame has not moved, because this
     * runs every tick and a component write is a network update to every client that can see the
     * gun.</p>
     */
    private static void writeFuseFrame(ServerPlayer player, ArcabuzState state) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.ARCABUZ.get())) {
            return;
        }
        int frame = 0;
        if (state.fuseTicks() > 0) {
            int total = Math.max(1, ArcabuzState.FUSE_TICKS);
            float burnt = 1.0F - (state.fuseTicks() / (float) total);
            frame = Math.max(1, Math.min(4, (int) Math.ceil(burnt * 4.0F)));
        }
        Integer current = stack.get(ModComponents.ARCABUZ_FUSE.get());
        if (current != null && current == frame) {
            return;
        }
        stack.set(ModComponents.ARCABUZ_FUSE.get(), frame);
    }

    /**
     * The shot: everything in the cone, up to ten of them, each one set on fire.
     *
     * <p><b>The cone is the weapon's own.</b> {@link Reach#inCone} with {@code stats.reach()},
     * no widened version and no separate profile, because the design says the cone is reused
     * <em>for</em> this weapon: the same eye origin, the same angles, the same leash a target's
     * size has earned. A shotgun with its own wider cone would be a second set of numbers to keep
     * in step with this one, and the client would have to resolve both.</p>
     */
    private static void fire(ServerPlayer player, ServerLevel level) {
        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        // The item could have changed in the twenty ticks the fuse burned -- and the left click
        // still works during those ticks, so it really can. Refusing is the honest answer for
        // something that is no longer a gun: the shot is what the player did, and paying it with
        // a different weapon's numbers would be a damage number nobody chose.
        if (stats.special() != SpecialType.FUSEE) {
            trace(player, player.getData(ModAttachments.ARCABUZ.get()),
                    "el item ya no es un arcabuz, disparo perdido");
            return;
        }

        // Found and hit are two numbers, and both are on the log line: `encontrados` is what the
        // cone saw and `golpeados` is what it capped to and actually damaged, so the cap biting
        // is legible in the log rather than a number somebody has to reconstruct.
        List<LivingEntity> found = targetsInCone(player, level, stats.reach());
        List<LivingEntity> targets = ArcabuzShot.capped(found);
        float damage = ArcabuzShot.damageFor(player.getAttributeValue(Attributes.ATTACK_DAMAGE));

        // The flash goes at the muzzle, in front of the eye, and before the damage so the frame the
        // player sees is the frame the shot left on. A flash at the camera reads as the screen
        // flickering, which is the reason ArcabuzShot.MUZZLE_OFFSET exists at all.
        //
        // getEyePosition rather than getEyeY with getX and getZ: 1.21.1 has no getEyeX or
        // getEyeZ on Entity, only the whole vector.
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        SlashParticles.muzzle(level,
                eye.x + look.x * ArcabuzShot.MUZZLE_OFFSET,
                eye.y,
                eye.z + look.z * ArcabuzShot.MUZZLE_OFFSET);

        boolean anyLanded = false;
        boolean anyKilled = false;
        int landedCount = 0;
        for (LivingEntity target : targets) {
            Knockback.apply(player, target, stats.knockback(), 1.0F);
            // Re-armed before every hit, for the reason IFRAME_TICKS' comment gives.
            target.invulnerableTime = 0;
            float before = target.getHealth();
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                target.invulnerableTime = IFRAME_TICKS;
                anyLanded = true;
                landedCount++;
                anyKilled |= before > 0.0F && target.getHealth() <= 0.0F;
                // Vanilla's fire, applied after the hit landed so a target that dies of the blast
                // is not left smouldering. Entity#igniteForSeconds is the whole API: 1.21.1 has
                // no setSecondsOnFire and the effect is vanilla's own onFire source doing
                // vanilla's one damage every twenty ticks.
                target.igniteForSeconds(ArcabuzShot.FIRE_SECONDS);
                // Vanilla's flame particles, at the centre of the box and not at the feet, for the
                // reason SlashParticles' burst comment gives.
                var box = target.getBoundingBox();
                level.sendParticles(ParticleTypes.FLAME,
                        (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5,
                        (box.minZ + box.maxZ) * 0.5,
                        12, 0.25, 0.35, 0.25, 0.02);
            }
        }

        CombatSounds.play(level, player, WeaponProfile.ARCABUZ, ModSounds.Kind.FUSEE);

        // **One feedback packet for the whole blast, and this is the decision.**
        //
        // The halberd's sweep sends one per target, which is two or three identical crosshair
        // flashes in the same tick and reads as one. At ten targets the same loop would be ten
        // packets to say one thing, and a KILL reported by a later target would be overwritten
        // by a HIT from an earlier one. So the shot reports once, and a kill wins over a hit --
        // the same precedence CombatEngine#notifyHit uses for a single swing.
        if (anyLanded) {
            ModNetworking.sendFeedback(player,
                    anyKilled ? CombatFeedback.KILL : CombatFeedback.HIT);
        }

        LOG.info("[arcabuz] {} encontrados={} golpeados={} dano={} quemados={}s",
                player.getName().getString(), found.size(), landedCount, damage,
                ArcabuzShot.FIRE_SECONDS);
    }

    /**
     * Everything inside the cone, nearest first.
     *
     * <p><b>Nearest first, and it is not a detail.</b> {@link ArcabuzShot#capped} takes the first
     * ten of whatever it is given, so when the cap bites the survivors are whatever was behind
     * the blast -- which means the player has to step to the side to hit the rest rather than
     * stand still and watch a wall of husks. Sorting here is what makes that happen.</p>
     */
    private static List<LivingEntity> targetsInCone(ServerPlayer player, ServerLevel level,
                                                    ReachProfile reach) {
        List<LivingEntity> found = new ArrayList<>();
        var box = player.getBoundingBox().inflate(reach.distance() + 1.0);
        for (Entity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
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

    /**
     * Drops any fuse and any chambered firestone, for a session that starts with nothing.
     *
     * <p>Called from the ticker on login for the same reason the charge and the guard are reset
     * there: {@code holding} is a button, and a button still set from a connection that no
     * longer exists is state nothing in the game is producing.</p>
     */
    public static void clear(ServerPlayer player) {
        player.setData(ModAttachments.ARCABUZ.get(), ArcabuzState.EMPTY);
        // Not loaded, because that is what EMPTY says. Sending `true` here was the same mistake
        // as the one in tick(): a constant in the place the state belonged.
        ModNetworking.sendArcabuzFuse(player, false, 0, false);
    }

    /**
     * Behind {@code /cc trace}, like every other trace line except the lunge's.
     *
     * <p>A shot is about two lines a minute even in a fight, so it is not worth the exemption,
     * and the fuse is twenty ticks long enough that "it lit but never fired" is the kind of thing
     * only a log can answer.</p>
     */
    private static void trace(ServerPlayer player, ArcabuzState state, String message) {
        if (com.cortes.combat.CombatTrace.isEnabled()) {
            com.cortes.combat.CombatTrace.server(player.getName().getString(),
                    "[arcabuz] " + message + " (mecha=" + state.fuseTicks()
                            + " cargada=" + state.loaded() + ")");
        }
    }

    /**
     * One line per blast, ungated.
     *
     * <p>Unlike the trace: this one carries the only number the shot has (how many it found, how
     * many it capped, what it did for each), it is one line per <em>shot</em> rather than per
     * tick, and a shot costs a firestone and a second, so it cannot drown anything.</p>
     */
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("cortescombat");
}
