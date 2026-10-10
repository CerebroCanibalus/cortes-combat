package com.cortes.combat.special;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.Knockback;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.net.ArcabuzShotPayload;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

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
        // **The stack is the truth about the chamber, and this is the one place the two disagree.**
        //
        // The attachment still carries a `loaded` flag because the gates on the record are pure and
        // cannot see an inventory -- and that used to make the player's *person* hold the
        // firestone: load a gun, swap to the other, and the second could be fired without ever
        // being loaded. So before either gate is asked, the flag is read off the stack in hand.
        //
        // One place, at the press, is enough: the press is the only moment the answer changes what
        // happens, and asking it once means the two answers cannot disagree about a gun that was
        // just loaded.
        boolean chamber = chamber(player);
        state = state.withChamber(chamber);

        if (state.canLight()) {
            // The firestone is gone on this tick, before the fuse has moved one step. That is the
            // rule the whole state exists for and putting it here makes it true by construction
            // rather than by the order of two statements further down.
            player.setData(ModAttachments.ARCABUZ.get(), state.light());
            // **And off the stack, in the same breath as off the record.**
            //
            // Spent in the chamber and not at the shot, so the two cannot drift: a gun that fired
            // but still reports itself charged would let a player reload-cancel their way into a
            // second shot off one firestone.
            player.getMainHandItem().remove(
                    com.cortes.combat.registry.ModComponents.ARCABUZ_CHAMBER);
            // **loaded = true, and that one word is the whole of "apunta hacia abajo".** The
            // firestone is spent from the CHAMBER's point of view at the shot, not at the light:
            // the ball is still in the barrel while the fuse burns, so the loaded pose stays for
            // the whole second. Sending false here made the gun point at the floor until the shot
            // went off, and two channels moving opposite ways over that second is what read as a bob.
            ModNetworking.sendArcabuzFuse(player, true, ArcabuzState.FUSE_TICKS, true, 0);
            CombatSounds.play(level, player, WeaponProfile.ARCABUZ, ModSounds.Kind.FUSE_LIT);
            trace(player, state, "mecha encendida, Firestone gastada");
            return;
        }

        if (state.canReload() && firestoneSlot(player) < 0) {
            // **The fifth refusal, and the one that had no line of its own.** The reload used to
            // open on any press of an empty gun, so holding the button for two and a half seconds
            // drew the entire reload motion -- the lean that reads as the loaded pose -- and then
            // spent itself on nothing. The player was shown a reload that could not happen, which
            // is the same class of lie as the model drawing a charged musket over an empty one, and
            // it is fixed the same way: refuse it on the press instead of discovering it fifty
            // ticks later, which costs the player half a second of their time to learn.
            LOG.info("[arcabuz] press RECHAZADO: sin Firestone, la recarga no arranca");
        } else if (state.canReload()) {
            // **The firestone is NOT spent on this press, and that is a change.**
            //
            // It used to be: load() took one here, so a player who pressed and then let go had
            // been charged for a gun they never loaded. The press now only opens the reload and
            // the firestone is spent in tick(), on the tick the gun actually becomes loaded.
            // Letting go costs nothing, which is the only thing "I let go" can mean.
            //
            // The price the reload used to pay -- the attack cooldown, and the reload sound --
            // moved with it, to the tick that finishes.
            // **`withHeld(true)` and not the flag we happen to be holding.**
            //
            // A press IS evidence that the button is down -- it is the server being told the
            // button went down, or came up a second time. Relying on the held packet having
            // arrived first is what broke this: the reload began in a state whose only clock was
            // a flag the press had just cleared, so the gun took the reload and never filled.
            int reloadTotal = reloadTicks(player.getMainHandItem(), player);
            player.setData(ModAttachments.ARCABUZ.get(), state.startReload(reloadTotal).withHeld(true));
            // **The press sound is NOT played here, and its absence is the fix.**
            //
            // It used to be, and this is the line that has to go for the reload to be able to
            // stop sounding. `ServerLevel#playSound` hands the sound to `PlayerList#broadcast` and
            // then forgets about it -- the server keeps no list of live sounds -- so from here the
            // only way to end it would be a stop with nothing to name. A reload the player abandoned
            // would keep grinding to the end of the file.
            //
            // **The client starts it instead**, in `ArcabuzFuseState.tick`, off the same count that
            // drives the motion, and cuts it on the tick the count is no longer running. It follows
            // the player too, which this path could not: the packet carries coordinates, so the
            // sound stayed where it was played from.
            //
            // The press is still the moment it sounds at -- the count arrives here, on the press --
            // so what is lost is only the server's inability to stop it.
            trace(player, state, "recarga empezada (" + reloadTotal + " ticks)");
            // **And this packet, which is the whole of "no hay forma de saber que estoy
            // recargando".** The server said nothing at all on the press: the RELOAD channel had a
            // track on the arcabuz's profile and no signal from anywhere to move it, so the gun sat
            // in its unloaded pose for two and a half seconds with nothing on screen. The count
            // travels rather than a flag so the motion is exactly as long as the reload.
            ModNetworking.sendArcabuzFuse(player, false, 0, false, reloadTotal);
        }

        // A press mid-fuse, or a press that restarted nothing, is what a cooldown looks like.
        // **This else is the fourth of the five silent refusals and it is here to stop being one.**
        // It is the only branch that runs on every press the player makes at a gun that cannot
        // do what they asked, and it says which of the three conditions stopped it.
        else {
            LOG.info("[arcabuz] press RECHAZADO: cargado={} quemando={} recargando={}",
                    state.loaded(), state.isBurning(), state.isReloading());
        }
    }
    /**
     * The server's copy of the right mouse button.
     *
     * <p>Written from the client's own reconciliation rather than from a release event, because the
     * release is not reliable: GLFW drops it when the window loses focus with a button down, and a
     * flag that only moves on events leaves the reload waiting for a release that is never coming.
     * It is cleared with everything else on login and logout for the same reason the charge and the
     * guard are: it is a button, and a button still set from a connection that no longer exists is
     * state nothing in the game is producing.</p>
     */
    public static void setHeld(ServerPlayer player, boolean held) {
        ArcabuzState state = player.getData(ModAttachments.ARCABUZ.get());
        player.setData(ModAttachments.ARCABUZ.get(), state.withHeld(held));
        LOG.info("[arcabuz] held={} (antes {}) -> {}", held, state.reloadHeld(),
                player.getData(ModAttachments.ARCABUZ.get()).reloadHeld());
    }

    /**
     * Where the player has a firestone, and spends one if they do.
     *
     * <p><b>The ungated line in here is not debug output -- it is the reason this method can be
     * diagnosed at all.</b> The reload path has five places that can refuse: the press never
     * arrives, the state refuses it, the button flag never turns up, the timer never finishes, and
     * the firestone is not there. Four of the five were silent, and a silent refusal reads as
     * "the weapon is broken" rather than as "the player had nothing to load it with".
     * {@code /cc input} exists for the same reason on the attack button.</p>
     *
     * <p>Out of the whole inventory and not out of the hand. The hand has to hold the gun or the
     * gun would not be held, and the recipe for a firestone is two of them out of a gunpowder and
     * a glowstone, so it is a stack a player carries rather than something they click.</p>
     *
     * <p><b>Called when the reload completes, not when it starts</b>, and that difference is a
     * design decision: a reload the player abandoned must not cost anything, or letting go of the
     * button reads as being charged for nothing.</p>
     *
     * <h3>The index, which is the bug this method used to have</h3>
     *
     * <p>It used to be {@code contains(probe)} followed by {@code removeItem(probe)}. The first
     * works -- it compares with {@code isSameItemSameComponents} -- and the second <b>removes
     * nothing at all</b>, because {@code Inventory#removeItem(ItemStack)} is written for "forget
     * this exact stack I just took out" and tests {@code slot == stack}, which for an
     * {@code ItemStack} is <em>reference identity</em>. A freshly built probe is never the instance
     * sitting in a slot, so the comparison never matched, the method returned {@code void} having
     * removed nothing, and the gun loaded for free.</p>
     *
     * <p>The index is the flattened one: {@code Inventory#getItem(int)} and
     * {@code Inventory#removeItem(int, int)} both walk {@code compartments} subtracting sizes, so
     * the read and the write use one loop and cannot disagree about what a number means. Read out
     * of the sources rather than assumed, because the two walking differently is exactly the bug
     * the paragraph above is about.</p>
     */
    private static boolean load(ServerPlayer player) {
        int found = firestoneSlot(player);
        if (found < 0) {
            trace(player, player.getData(ModAttachments.ARCABUZ.get()), "sin Firestone");
            return false;
        }
        player.getInventory().removeItem(found, 1);
        // **The charge lands on the stack, in the same statement that pays for it.**
        //
        // The firestone comes out of the inventory here and goes into *this gun*, so the write is
        // here too and not one call later: a gun that was paid for and not charged is the one
        // state a player cannot read, and it would be read as the model lying.
        player.getMainHandItem().set(com.cortes.combat.registry.ModComponents.ARCABUZ_CHAMBER,
                Boolean.TRUE);
        return true;
    }

    /**
     * Whether the musket in the player's hand has a firestone in it.
     *
     * <p><b>Absent means empty.</b> Not "false": the component is written when the reload finishes
     * and removed when the gun fires, so a gun that has never been loaded and one that has just
     * been fired are the same stack of components -- which is what makes them the same gun.</p>
     */
    private static boolean chamber(ServerPlayer player) {
        return player.getMainHandItem()
                .get(com.cortes.combat.registry.ModComponents.ARCABUZ_CHAMBER) != null;
    }

    /**
     * The first slot holding a firestone, or -1. <b>Reads and never writes.</b>
     *
     * <p><b>Extracted rather than copied, and the reason is that this question gets asked twice.</b>
     * It used to be asked once, fifty ticks after the player pressed, by {@link #load}. So a player
     * with an empty inventory held the button for two and a half seconds, watched the whole reload
     * motion run -- which leans the weapon down, which is the pose that reads as *loaded* -- and
     * then nothing happened. The gun spent half a second of the player's time drawing a reload it
     * was never going to perform, and told them it was loaded at no point in the process, only
     * animated like it.</p>
     *
     * <p>So the same question is now asked on the press, and the answer is what decides whether the
     * reload starts. This is the same rule the file already states about paying: <b>cheaper to say
     * nothing happened</b>, and the cheapest nothing is the one that costs the player no time.</p>
     *
     * <p>One search, two callers, and the two cannot disagree about where the ammunition is --
     * which is the bug shape the paragraph above on {@link #load} is entirely about.</p>
     */
    private static int firestoneSlot(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        int found = -1;
        int total = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.FIRESTONE.get())) {
                if (found < 0) {
                    found = slot;
                }
                total += stack.getCount();
            }
        }
        LOG.info("[arcabuz] buscar Firestone: {} en {} slots, {} disponibles",
                found < 0 ? "NO" : "slot " + found, size, total);
        return found;
    }

    /**
     * One tick: the fuse counts down, the reload fills, and whichever of them finished pays for
     * itself.
     *
     * <p>Nothing is decided here. {@link ArcabuzState#step()} answers every question -- does the
     * state move, does the shot go off, did the reload finish -- and this writes what it is handed.
     * The parry's ticker used to decide, and the decision it made ("the window is closed, nothing
     */
    public static void tick(ServerPlayer player) {
        // **Read before the step, not after.** These ungated lines exist because four of the
        // five places this path can refuse were silent, and a silent refusal reads as the weapon
        // being broken rather than as the player having nothing to load it with.
        ArcabuzState previous = player.getData(ModAttachments.ARCABUZ.get());

        // **The gun has to still be in the hand, and this is the only place that can know.**
        //
        // The press is refused when the held stack is not the one that was pressed -- and
        // `SpecialHandler#resolve` already does that -- but `ItemStack#isSameItemSameComponents`
        // compares the *item and its components*, not the identity of the stack. Two arcabuz are
        // the same item with the same components, so that guard has never been able to tell them
        // apart and never will. It is the wrong tool for this question, not a tool misused.
        //
        // Which is why the shot went off from the wrong gun: **the fuse is lit at the press and
        // goes off in this tick**, and nothing between the two ever asked what was in the hand. Lit
        // with one musket, swapped to the other, and the second one fired on the first one's
        // firestone. The recharge was correct and the shot was stolen.
        //
        // Here, and not at the press, because a press cannot see the future. The whole window
        // between lighting and firing is twenty ticks of the player being free to walk away.
        //
        // **A snuff, not a transfer.** The firestone is spent at the light, so a gun that goes out
        // this way is empty and its ball is gone. That is the price of swapping, it is the same
        // price as missing, and it is legible: the gun goes down and stays down until reloaded.
        if (previous.shouldSnuff(player.getMainHandItem().is(ModItems.ARCABUZ.get()))) {
            LOG.info("[arcabuz] mecha apagada: cambio de objeto en la mano");
            player.setData(ModAttachments.ARCABUZ.get(), ArcabuzState.EMPTY);
            ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
            return;
        }

        // **The gun came back to the hand, so the client needs telling what it is holding.**
        //
        // This is the other half of the snuff above, and it was the half nobody wrote. Swapping to a
        // sword makes the client's `ItemViewModel` run `clearArcabuz()`, which wipes
        // `ArcabuzFuseState` -- the countdown, and `loaded`. The gun is drawn empty. Picking the
        // musket back up changes nothing on the client, because the client has nothing left to
        // change: `sendArcabuzFuse` only fires when the state *moves* (`Step#tellClient`), and this
        // gun has not moved. So the player gets a charged weapon drawn as an empty one, and the next
        // shot asks for a firestone they already spent.
        //
        // **The server was right the whole time.** The chamber lives on the stack, so
        // `chamber(player)` here reads true and the attachment's `loaded` is the stale half. The
        // same reconciliation `resolve` does on the press, done on the tick instead -- because the
        // press is not the only event that can leave the client holding a stale answer, and the tick
        // is the one that always runs.
        //
        // **Why this is cheap, which is the only reason to do it here.** It is one boolean compare
        // per tick against a value already on the stack, and it sends only on the tick where they
        // disagree. A player holding a musket they just loaded pays this once and never again.
        ArcabuzState current = player.getData(ModAttachments.ARCABUZ.get());
        boolean inHand = player.getMainHandItem().is(ModItems.ARCABUZ.get());
        if (inHand) {
            ArcabuzState reconciled = current.withChamber(chamber(player));
            if (reconciled != current) {
                LOG.info("[arcabuz] resincronizado al retomar el arma: cargada={}",
                        reconciled.loaded());
                player.setData(ModAttachments.ARCABUZ.get(), reconciled);
                // **The countdown goes with it, not just the flag.** A gun picked back up mid-burn
                // has `fuseTicks > 0` on the server and zero on the client, and the frame comes off
                // the countdown -- so sending only the flag would draw a gun that is neither lit nor
                // empty, with a fuse a third spent rendering as its first frame.
                ModNetworking.sendArcabuzFuse(player, false, reconciled.fuseTicks(),
                        reconciled.loaded(), reconciled.reloadTicks());
                return;
            }
        }

        ArcabuzState.Step step = previous.step();
        player.setData(ModAttachments.ARCABUZ.get(), step.state());

        // **The fuse frame is NOT written here any more, and that is the fix for the weapon
        // bobbing in the hand.** It used to be a data component on the held stack, set every
        // tick of the burn -- and setting a component on the held stack syncs the stack, the
        // client ends up with a different ItemStack, and vanilla plays its item-swap animation,
        // which moves the weapon sixty centimetres while it settles. Four frames in a one second
        // burn is four item swaps a second. The client now computes the frame from the countdown
        // it already keeps; see ArcabuzModelProperties#frameForTicks.

        if (step.state().isReloading() && !previous.isReloading()) {
            LOG.info("[arcabuz] recarga EN CURSO {} ticks", step.state().reloadTicks());
        }
        // **And the cancellation, which is the half the client cannot work out on its own.**
        //
        // The client's countdown runs on the client's clock, so a player who lets go halfway
        // through has the server cancel while the client still has twenty ticks left to draw. The
        // gun would carry on tipping its muzzle down and come back up on its own, for a reload
        // that no longer exists.
        if (previous.isReloading() && !step.state().isReloading() && !step.reloadDone()) {
            int cancellingTotal = reloadTicks(player.getMainHandItem(), player);
            LOG.info("[arcabuz] recarga CANCELADA (boton soltado en {} de {})",
                    cancellingTotal - previous.reloadTicks(), cancellingTotal);
            ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
        }
        if (step.reloadDone() && player.level() instanceof ServerLevel level) {
            LOG.info("[arcabuz] recarga TERMINADA, cobrando la Firestone");
            // **The price, paid the tick the gun is actually loaded.**
            //
            // If the player cannot pay, the gun goes back to empty rather than becoming loaded:
            // a gun that reports itself loaded and then cannot light is the one state of this
            // weapon a player cannot read, because the model would draw a charged musket over a
            // gun with nothing in it. Cheaper to say nothing happened.
            //
            // The cooldown is here rather than on the press because this is where the reload is
            // really paid for. Half a second of holding a button is not a swing, and the bracket
            // around the crosshair is the only number the player already knows how to read.
            if (load(player)) {
                AttackCooldown.consume(player, AttackCooldown.intervalFor(player));
                CombatSounds.play(level, player, WeaponProfile.ARCABUZ, ModSounds.Kind.LOADED);
                ModNetworking.sendArcabuzFuse(player, false, 0, true, 0);
                trace(player, step.state(), "cargada");
            } else {
                player.setData(ModAttachments.ARCABUZ.get(), ArcabuzState.EMPTY);
                ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
            }
        }

        if (step.tellClient()) {
            // The state's own `loaded`, not a literal. It was written `true` here first and that
            // told the client a gun with nothing in it was loaded, which is a viewmodel drawing a
            // charged musket over an empty one -- the same shape as a signal that does not carry
            // the state it is supposed to carry, only its own optimism about it.
            ModNetworking.sendArcabuzFuse(player, false, 0, step.state().loaded(), 0);
        }
        if (step.fireNow() && player.level() instanceof ServerLevel level) {
            fire(player, level);
        }
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
        List<LivingEntity> found = targetsInCone(player, level, stats.shotReach());
        List<LivingEntity> targets = ArcabuzShot.capped(found);
        float damage = ArcabuzShot.damageFor(player.getAttributeValue(Attributes.ATTACK_DAMAGE));

        // **Where the fire leaves from, on the server, for everybody.**
        //
        // **Not the eye any more.** The old call put it at `eye + look * MUZZLE_OFFSET`, which is a
        // point on the view axis -- dead centre, at the crosshair -- and that is the whole reason it
        // read as the screen flickering rather than as a gun going off. Here it only has to be
        // *near* the barrel, because the streaks are a line between two entities and a point a
        // quarter block off the muzzle changes nothing about where the lava appears to come from.
        //
        // The flash itself is gone from this method and is drawn by the client from the camera
        // basis, which is the only place the gun's real position exists.
        //
        // getEyePosition rather than getEyeY with getX and getZ: 1.21.1 has no getEyeX or
        // getEyeZ on Entity, only the whole vector.
        Vec3 muzzle = player.getEyePosition()
                .add(player.getLookAngle().normalize().scale(ArcabuzMuzzle.FORWARD));

        boolean anyLanded = false;
        boolean anyKilled = false;
        int landedCount = 0;
        // **The things that were actually damaged**, as a list, because the fire is thrown at this
        // one and not at the ten the cone found. A target that dodged the blast -- an armoured one
        // that survived it, or one that was already dead when the loop reached it -- must not have
        // a streak drawn at it, because a streak is a claim about what the shot did.
        List<LivingEntity> landed = new ArrayList<>(targets.size());
        for (LivingEntity target : targets) {
            Knockback.apply(player, target, stats.knockback(), 1.0F);
            // Re-armed before every hit, for the reason IFRAME_TICKS' comment gives.
            target.invulnerableTime = 0;
            float before = target.getHealth();
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                target.invulnerableTime = IFRAME_TICKS;
                anyLanded = true;
                landedCount++;
                landed.add(target);
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

        // **The fire is thrown, and the flash is announced, after the loop and not before it.**
        //
        // After, because the streaks have to point at the things that were actually damaged and the
        // only way to know that is to have finished the loop. Before, they would point at the ten
        // the cone found, which is a different list.
        blast(player, level, muzzle, landed);

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
     * Everything inside the cone, **aimed at first** and then nearest.
     *
     * <p><b>The order is the whole of "los de al frente tienen prioridad", and it is the opposite
     * of what this method used to do.</b> It sorted by distance, which is right for a sword and
     * wrong for a gun: a player aiming at a mob six blocks away with a husk four blocks behind it
     * was asking to shoot the far one, and {@link ArcabuzShot#capped} handed them the near one
     * because it was closer. The weapon was doing the wrong thing with a correct cone.</p>
     *
     * <p>So the sort key is the squared deviation from the look axis, which orders exactly the way
     * an angle would and needs no trigonometry. Distance is the tiebreak and not the primary: two
     * things under the crosshair are both aimed at, and the nearer one is the more urgent.</p>
     *
     * @param reach the <em>shot's</em> cone, never the bayonet's
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
        var look = player.getLookAngle();
        found.sort((a, b) -> ArcabuzShot.compareByAimThenDistance(
                aimDeviation(a, player, look), a.distanceToSqr(player),
                aimDeviation(b, player, look), b.distanceToSqr(player)));
        return found;
    }

    /**
     * How far off the look axis a target sits, so it can be ordered without an angle.
     *
     * <p><b>One minus the dot product, measured from the shooter's eye to the target's eye.</b> The
     * dot product of the look direction with a unit vector towards the target is the cosine of the
     * angle between them, so one minus it is zero for dead ahead and grows towards one at the
     * edge. Squaring is left to the caller.</p>
     *
     * <p><b>Both eyes, and not the target's feet.</b> {@code getLookAngle} leaves the player's eye
     * and {@code getPosition()} is at the target's feet, so measuring between them puts every
     * target a little below where the player is pointing. At six blocks that error is small; at the
     * half block the bayonet is for, it is the whole question. The eye is what the player is
     * looking along.</p>
     *
     * <p>A target exactly on the eye returns zero rather than dividing: it is perfectly aimed, and
     * a {@code NaN} in a sort comparator on the server thread is not a thing worth having.</p>
     */
    private static double aimDeviation(LivingEntity target, ServerPlayer player,
                                       net.minecraft.world.phys.Vec3 look) {
        var to = target.getEyePosition().subtract(player.getEyePosition());
        double length = to.length();
        if (length <= 1.0E-6) {
            return 0.0;
        }
        return 1.0 - (to.x * look.x + to.y * look.y + to.z * look.z) / length;
    }

    /**
 * The fire the shot throws, and who gets to see it.
 *
 * <h2>Why the lava is here and the flash is not</h2>
 *
 * <p>Because a line between two entities is in the world and both of its ends are things the server
 * knows. Fire that flies from the barrel to a husk is a thing every player in the fight should see,
 * and a server that draws it draws it once for everybody.</p>
 *
 * <p>The muzzle flash cannot be drawn here at all: the weapon's transform is applied by the client
 * while it renders, so the only point the server can produce is the player's eye -- which is on the
 * view axis, at the crosshair, which is why the flash used to read as the screen flickering. So the
 * flash goes out as {@link ArcabuzShotPayload} and the client draws it from the camera basis. The
 * numbers both halves use are {@link ArcabuzMuzzle}, so a retune of the smoke does not have to be
 * made twice.</p>
 *
 * <p><b>Streaks towards each thing it hit, and scatter when it hit nothing.</b> The streaks lie
 * between the muzzle and the target so the player reads "that fire went <em>there</em>", and the
 * scatter exists because a gun that fires into an empty field and shows nothing leaves the player
 * unable to tell a wasted shot from a laggy one.</p>
 */
private static void blast(ServerPlayer player, ServerLevel level, Vec3 muzzle,
                                 List<LivingEntity> hit) {
        boolean drewAtSomething = ArcabuzMuzzle.drewAtSomething(hit.size());
        int total = ArcabuzMuzzle.lavaCount(hit.size());

        if (drewAtSomething) {
            // **One call per particle along the line, not one call with a big spread.** A
            // `sendParticles` with a distance spreads inside a box around one point, which draws a
            // cloud; what reads as a line is several points walked between the two ends.
            for (LivingEntity target : hit) {
                Vec3 to = target.getBoundingBox().getCenter();
                for (int step = 1; step <= ArcabuzMuzzle.LAVA_PER_TARGET; step++) {
                    double t = step / (double) (ArcabuzMuzzle.LAVA_PER_TARGET + 1);
                    level.sendParticles(ParticleTypes.LAVA,
                            muzzle.x + (to.x - muzzle.x) * t,
                            muzzle.y + (to.y - muzzle.y) * t,
                            muzzle.z + (to.z - muzzle.z) * t,
                            1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        } else {
            // **Uniform over the sphere and not over a box**, for the reason ArcabuzMuzzle says:
            // a cube's bias makes a whiff read as a spray that was blown.
            for (int i = 0; i < total; i++) {
                double[] direction = ArcabuzMuzzle.randomDirection();
                level.sendParticles(ParticleTypes.LAVA,
                        muzzle.x + direction[0] * 0.3,
                        muzzle.y + direction[1] * 0.3,
                        muzzle.z + direction[2] * 0.3,
                        1, direction[0] * 0.15, direction[1] * 0.15, direction[2] * 0.15, 0.0);
            }
        }

        // **And the flash is told to the shooter, who is the only one who can place it.**
        //
        // The client draws it at the real muzzle and the server cannot, so this payload carries no
        // position -- only the ids the blast reached, so the client throws fire at the same things
        // the server damaged rather than at whatever is under the crosshair when it arrives.
        List<Integer> ids = new ArrayList<>(hit.size());
        for (LivingEntity target : hit) {
            ids.add(target.getId());
        }
        PacketDistributor.sendToPlayer(player, new ArcabuzShotPayload(List.copyOf(ids)));
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
        ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
    }

    /**
     * Behind {@code /cc trace}, like every other trace line except the lunge's.
     *
     * <p>A shot is about two lines a minute even in a fight, so it is not worth the exemption,
     * and the fuse is twenty ticks long enough that "it lit but never fired" is the kind of thing
     * only a log can answer.</p>
     */
    /**
     * Los ticks que esta recarga va a tardar, <b>Quick Charge incluido</b>.
     *
     * <p>El encantamiento es <b>el de vanilla</b> y lo lee <b>el helper de vanilla</b>: se le pasa la
     * base en segundos (nuestros cincuenta ticks) y devuelve los segundos ya modificados, que
     * {@link ArcabuzState#reloadTicksFor} pasa a ticks con piso. Así el servidor, el cliente y el
     * tooltip sacan el número de <em>una</em> aritmética, que es la única forma de que no se
     * desincronicen -- y el piso vive en la parte pura, donde un test lo camina.</p>
     */
    public static int reloadTicks(net.minecraft.world.item.ItemStack gun,
                                  net.minecraft.world.entity.LivingEntity user) {
        float seconds = ArcabuzState.RELOAD_TICKS / 20.0F;
        if (user != null) {
            seconds = net.minecraft.world.item.enchantment.EnchantmentHelper
                    .modifyCrossbowChargingTime(gun, user, seconds);
        }
        return ArcabuzState.reloadTicksFor(seconds);
    }

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
