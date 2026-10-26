package com.cortes.combat.client;

import com.cortes.combat.platform.CombatPackets;

import com.cortes.combat.CombatTrace;

import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.attack.PressBuffer;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.config.ServerConfig;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.net.SwingPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;

import java.util.Optional;

/**
 * The left mouse button, as one press-and-release rather than as a charge and a click.
 *
 * <p>What this replaces: the button used to do two unrelated things at once. The press fired an
 * attack with whatever charge existed, which was always none, and the hold kept accumulating
 * into a buffer that the release threw away. So the charge never applied, ever, and the meter
 * climbed and then snapped back with no payoff. The symptom a player reports is that charged
 * attacks and normal ones "get in each other's way"; the cause is that there was only ever one
 * kind of attack and a second gauge that measured nothing.</p>
 *
 * <p>What it does now: the press opens a swing and the release resolves it, so one press is one
 * attack and the charge is simply however long the button was down. A tap lands as a normal
 * hit; a hold lands charged. The threshold that separates them is
 * {@link com.cortes.combat.attack.AttackTiming#MIN_CHARGE_HOLD_TICKS} and it is global, not per
 * weapon, so the same flick of the wrist means the same thing in every hand.</p>
 *
 * <p>The cost is that a tap resolves on its release rather than its press, so a fast click lands
 * a couple of ticks later than it used to. That is the price of one press being one attack, and
 * it is a couple of ticks: every game with a chargeable button resolves on the release for the
 * same reason.</p>
 *
 * <p>The client reports intent and predicts for the HUD. The charge that counts for damage is
 * the one the server accumulated on its own ticker, so a client cannot claim a hold it did not
 * make.</p>
 */
public final class SwingInputHandler {

    /**
     * What this mod decided to do with the press the client is about to see.
     *
     * <p>Read by {@code MinecraftAttackMixin} so that vanilla never also attacks. The three
     * states are three different answers and collapsing them loses something: {@link #OURS} means
     * a swing is open, {@link #REFUSED} means the click died on a cooldown that was not ready
     * and must stay invisible, and {@link #NOT_OURS} means the press belongs to vanilla, either
     * because the crosshair is on a closer block or because the item is a bow.</p>
     */
    public enum Claim {
        OURS,
        REFUSED,
        NOT_OURS
    }

    /** Local mirror of the charge, for the meter and the viewmodel. Never the source of truth. */
    private static int predictedTicks;

    /** Latched so the cue is one per hold and not one per tick. */
    private static boolean chargeSoundPlayed;

    private static boolean holding;

    /**
     * Whether the open swing already resolved itself on the press.
     *
     * <p><b>Set for a weapon that cannot charge.</b> On those the press <em>is</em> the attack --
     * there is no hold to wait for -- so the swing fires immediately and the release must only
     * close it. Without this flag the release would pay a second hit for the same button, which is
     * the double-hit this class already guards against from the automatic strike.</p>
     *
     * <p>It is cleared wherever the swing is closed, so a stale {@code true} cannot outlive the
     * press that set it -- the family of bug this class has paid for three times.</p>
     */
    private static boolean firedOnPress;

    /**
     * A fire-on-press strike that is owed an announcement to the viewmodel.
     *
     * <p><b>One tick late on purpose.</b> {@code ItemViewModel.tick} clears {@code pendingStrike}
     * on the press edge, so a strike announced from {@code press} -- which runs before the tick in
     * the same frame -- would be erased before it was ever drawn. Setting this flag and announcing
     * from the tail of the tick, after that edge, is what makes the first hit of a held button
     * animate as well as land.</p>
     */
    private static boolean pressStrikePending;

    /** The item the open swing was started with, so a swap cannot carry the charge across. */
    private static Item openItem;

    /**
     * The entity this open swing was thrown at: whatever the press found, kept current by every
     * successful scan since.
     *
     * <p><b>The coyote fallback's memory, and why the release needs one.</b> The crosshair showed
     * this target, the press opened on it, and then the release ran its own fresh scan -- a scan
     * that can come up empty a few pixels of mob movement later, or against a world one frame
     * ahead of the one the indicator drew from. Sending {@link SwingPayload#NO_TARGET} there makes
     * the miss a certainty before the server is asked anything, which is the report "el indicador
     * muestra que deberías dar el golpe y se pierde". With a memory, the release asks the server
     * instead of deciding alone -- and the server still refuses anything out of the strict cone.</p>
     *
     * <p>Reset at the start of every press, so a refused or abandoned swing cannot leak its
     * target into the next one; no other clear is needed, because every path that reads it runs
     * under a press that set it.</p>
     */
    private static int swingTargetId = SwingPayload.NO_TARGET;

    /**
     * The tick the next swing becomes legal, computed locally.
     *
     * <p>This exists because the server's cooldown arrives a round trip late, and a double click
     * inside a single tick would otherwise beat the race: the client would see a ready cooldown
     * both times, open two swings, play two swing animations, and the server would honour the
     * first and drop the second. That is exactly the failure this project rejected when it
     * decided a refused swing has to be invisible: an arm that moves and nothing happens reads
     * as dropped input, and the player's answer to that is to click harder.</p>
     *
     * <p>So the client's gate is deliberately <b>stricter</b> than the value it has. It may
     * refuse a swing the server would have allowed, for at most the length of one round trip,
     * and it must never show one the server will refuse.</p>
     */
    private static int readyAtTick;

    private static int tick;

    /**
     * A press that was refused, parked for the length of {@link PressBuffer#WINDOW_TICKS}.
     *
     * <p>Value in client ticks, {@link PressBuffer#EMPTY} when nothing is parked. Set by the two
     * refusal branches a real press can survive waiting -- cooldown and guard -- and fired from
     * the tick when the same gate it failed would now pass. A click one tick early costs nothing
     * any more; a click two seconds before the cooldown ends never fires later, because the
     * window is over.</p>
     *
     * <p>It deliberately does <b>not</b> cover the other refusals: "not a weapon", "ranged",
     * "screen open" and "entity without life" are answers about what the player is pointing at,
     * and parking one would fire a swing at a thing that never was a swinging target.</p>
     */
    private static long parkedPress = PressBuffer.EMPTY;

    /**
     * The verdict on the press currently being processed.
     *
     * <p>Deliberately not cleared by whoever reads it. Vanilla can run the attack path more than
     * once in a tick when a double click lands inside one, and every one of those has to be
     * refused: a second vanilla attack on a press the player never made is exactly the free
     * swing this system exists to prevent. It is cleared at the end of the client tick instead,
     * once the whole tick has had its say.</p>
     */
    private static Claim claim = Claim.NOT_OURS;

    private SwingInputHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_LEFT) {
            return;
        }
        // The mouse can be clicked while still on the title screen, in a world preview, or
        // while a server is loading, and getConnection() is null in all of those. Sending on
        // a null connection throws and takes the render thread with it.
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            press(Minecraft.getInstance());
        } else {
            release(Minecraft.getInstance());
        }
    }

    /**
     * Open a swing, unless this press belongs to something else.
     *
     * <p>The hard cooldown is refused here rather than on the server, and refused silently. Let
     * the click through and let the server reject it was worse than useless: the arm swung, the
     * bar stayed empty, and nothing happened. A swing that visibly happens and does nothing
     * reads as the game dropping input, and the player's response is to click harder. Refused
     * means the click does not visibly do anything either, and the one thing that <em>does</em>
     * move is the bracket bar filling up.</p>
     */
    private static void press(Minecraft mc) {
        if (!ServerConfig.combatRework()) {
            claim = Claim.NOT_OURS;
            return;
        }
        claim = Claim.NOT_OURS;
        // A real press under the gates supersedes whatever a previous refusal parked.
        parkedPress = PressBuffer.EMPTY;
        firedOnPress = false;
        swingTargetId = SwingPayload.NO_TARGET;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || player.isUsingItem()) {
            debug("press RECHAZADO: sin jugador, spectator o usando item");
            return;
        }
        // A screen open means the click belongs to the screen, not to the world.
        //
        // This is not vanilla's own check being duplicated, it is vanilla's check being
        // DELETED. `Minecraft#startAttack` opens with a guard that refuses while a screen is up,
        // and cancelling at HEAD -- which this mod has to do to replace the whole attack flow --
        // removes that guard along with everything else in the method. The mixin's continueAttack
        // seam compounds it: that is the path `handleKeybinds` walks every tick for as long as the
        // key is held, and the inventory does not pause singleplayer, so holding the button with
        // a chest open swung at whatever was behind the window.
        //
        // Vanilla owns the click while a screen is open, which is exactly what "menus block
        // attacks completely" means and also what vanilla would have done on its own.
        // **Is this thing in my hand a weapon.**
        //
        // **This is the gate that was missing.** Everything below asks whether there is a target
        // in the cone and never what is in the hand, so a block, a potion or a pickaxe claimed
        // the whole click: the swing, the charge, the wind-up and a hit at 3.0 of reach, which is
        // longer than a bare fist's 2.5. The half of "every object I hit with can charge" that
        // got fixed was `deriveProfile` answering NONE for the unknown; the half that stayed is
        // the door in front of it.
        //
        // **It asks about the profile and not about the item**, and that is not a style choice:
        // there is no item-shaped question that answers it. `DiggerItem` groups the axe with the
        // pickaxe and the shovel, and the pickaxe is the one that must not fight; "has attack
        // damage" misses the shovel and the trident. The profile is the capability, and it is
        // also what makes a foreign mod's sword work without anybody listing its id here.
        //
        // **First thing in the press, before the screen check.** Not for speed: a screen swallows
        // the click either way, so the order only decides which line the debug print names, and
        // "it is not a weapon" is the more useful of the two when both are true.
        com.cortes.combat.core.weapon.WeaponProfile held =
                com.cortes.combat.attack.WeaponStats.of(player.getMainHandItem()).profile();
        if (!com.cortes.combat.item.WeaponKinds.fights(held)) {
            debug("press NO NUESTRO: " + held + " no es un arma");
            return;
        }

        if (mc.screen != null) {
            debug("press NO NUESTRO: pantalla abierta ("
                    + mc.screen.getClass().getSimpleName() + ")");
            return;
        }
        // A bow and a crossbow are not ours. Letting vanilla own the whole press is not a
        // fallback: the cone deliberately does not contain projectiles, so there is no target to
        // find and nothing for this system to decide.
        //
        // **And the trident is deliberately absent, because it was here and that was the bug.**
        // It is a projectile weapon in vanilla, so `isRanged` reading `instanceof TridentItem`
        // looked right -- but in this system the trident is a melee weapon at five point three
        // blocks, and this gate runs *after* the profile question, so it took the whole left click
        // away from a profile that had already said it fights. The report was "the trident is
        // still full vanilla": the same shape as the tag that never existed, one layer up.
        if (CombatAim.isRanged(player.getMainHandItem())) {
            debug("press NO NUESTRO: ranged");
            return;
        }
        // **A thing under the crosshair that is not alive is vanilla's click, whole.**
        //
        // This is the gate that broke every item frame, painting, boat, minecart, end crystal,
        // leash knot and ender-dragon part in the game. The cone scans `LivingEntity` and nothing
        // else (`ConeTargeting.findTarget`), so a non-living entity under the crosshair was never a
        // cone target -- which read as "nothing is here" -- while `mc.hitResult` said ENTITY and
        // not BLOCK, so the `isOnBlock` mining escape below did not fire either. The press then fell
        // all the way through to `claim = OURS`, and `MinecraftAttackMixin` cancelled `startAttack`
        // before vanilla's own ENTITY case (`gameMode.attack` -> `player.attack` -> the frame's
        // `hurt`) ever ran. The player held a sword, put the crosshair on a frame full of items,
        // clicked, and nothing happened: the one path that removes the item is the path the
        // replacement ate.
        //
        // **Why the cursor wins over the cone, and not the other way round.** The cone is how this
        // mod asks "is there a body in front of me to hit"; a frame is not a body and never will be
        // -- its whole interaction is a point click on a surface, and vanilla already answers it.
        // Asking the cone first would leave the frame unreachable again for exactly the weapons
        // people carry, and inventing a second, cone-shaped rule for frames would be a rule vanilla
        // already has. So: if the crosshair rests on something with no health, hand the press back
        // and let vanilla be vanilla. `startAttack`'s ENTITY case therefore runs, and the item (if
        // the frame holds one) drops; an empty frame drops itself.
        //
        // It comes before the guard and cooldown gates on purpose, and that is the vanilla
        // behaviour the report asked for: attacking a frame is not a swing, so it is not gated by
        // this mod's hard cooldown and it works with a shield raised, exactly as it does in
        // vanilla. The debug line names the type so a playtest can tell this branch from
        // "not a weapon" and from "ranged".
        if (mc.hitResult instanceof EntityHitResult pointed
                && !(pointed.getEntity() instanceof LivingEntity)) {
            Entity pointedEntity = pointed.getEntity();
            debug("press NO NUESTRO: entidad sin vida bajo la mira ("
                    + pointedEntity.getClass().getSimpleName() + ")");
            return;
        }
        // Covered, so no swing. Read from the local prediction rather than the synced flag,
        // because this gate exists to catch covering and clicking in the same tick and the
        // synced value is a round trip too old to do that.
        if (GuardInputHandler.predictedHolding()) {
            claim = Claim.REFUSED;
            parkedPress = tick;
            debug("press REFUSADO: cubierto");
            return;
        }
        if (!ready()) {
            claim = Claim.REFUSED;
            parkedPress = tick;
            debug("press REFUSADO por cooldown: readyAtTick=" + readyAtTick
                    + " tick=" + tick + " ratio=" + ClientCombatState.cooldownRatio());
            return;
        }
        // An item that cannot charge still gets the swing. It just does not get a charge.
        //
        // This used to `return` here, which set Claim.NOT_OURS and handed the entire click back to
        // vanilla, and that was not what the comment above it said it did. It said the item gets
        // no charge bar and no wind-up, and a return does neither of those things and everything
        // else: the ropera and the dagger (the only two profiles whose ChargeProfile is NONE) ran
        // on vanilla's whole attack path. So they had no cone reach, which for the ropera is the
        // entire identity of the weapon, and vanilla's getAttackStrengthScale applied, which pays
        // more for spamming faster -- the exact rule this mod exists to delete.
        //
        // It was not careless, it was a patch. The client had no equivalent of the
        // `if (!profile.canCharge()) return 0` that AttackCooldown and CombatEngine both open with,
        // and this gate is what kept the client from ever seeing a ratio it would get wrong. The
        // guard now lives in AttackTiming, where all three sites share it, and this returns
        // nothing.
        //
        // What "does not charge" looks like now is not a special case anywhere downstream: the
        // ratio is zero, so there is no bar, no wind-up, no field of view and a flat hit, and the
        // swing still opens, so the strike and recover still play. Which is the thing the return
        // was preventing: the ropera and dagger profiles were written and wired and had never
        // once rendered, because holding never became true for them.

        Optional<LivingEntity> target = ConeTargeting.findTargetForHeldItem(player);
        if (target.isEmpty() && isOnBlock(mc)) {
            // Mining, and only mining when there is nothing to hit.
            //
            // This used to weigh the two: a block slightly nearer than the enemy took the swing
            // away. The playtest killed that, and rightly, on the fists especially: a fist's
            // reach is barely more than its own arm, so a block in front of the target is the
            // normal case rather than an edge case, and "swing at the wall" came up over and over.
            // The playtest asked for it the other way round: be sure to hit first, mine after.
            //
            // The cost of this is that a player aiming at a mob standing against a wall punches
            // the mob, which is the right trade. A swing that does nothing is worse than a swing
            // that hits the wrong thing, because the first one has no explanation.
            debug("press NO NUESTRO: minando (sin objetivo)");
            return;
        }

        holding = true;
        predictedTicks = 0;
        chargeSoundPlayed = false;
        openItem = player.getMainHandItem().getItem();
        // The swing's own target, remembered for the release's fallback. See swingTargetId.
        swingTargetId = target.map(net.minecraft.world.entity.Entity::getId)
                .orElse(SwingPayload.NO_TARGET);
        claim = Claim.OURS;
        debug("press NUESTRO: objetivo=" + target.map(net.minecraft.world.entity.Entity::getId)
                .orElse(SwingPayload.NO_TARGET));
        // The open, sent first so the server has a swing to resolve against. A weapon that fires
        // on the press sends its hit immediately after this, in the same tick and on the same
        // ordered connection, and the server opens on the first and resolves on the second.
        send(new SwingPayload(SwingPayload.NO_TARGET, true));

        // **A weapon that cannot charge fires here, on the press.**
        //
        // <p>The press/release split exists to tell a tap from a hold, and that is a question only
        // a weapon that charges has. On one that does not, waiting for the release is pure
        // latency: the player presses, the swing opens, and nothing lands until they let go --
        // which is the report "I hold it down and nothing happens until I let go". The ropera and
        // the dagger are the two that read as broken because they are the two a player reaches for
        // first.</p>
        //
        // <p>{@code firesOnPress} is the capability question and not a list of names, so the maza
        // and the hoe and the arcabuz get the same behaviour without a line here. The mace simply
        // does not repeat (its damage is the fall, see WeaponTables), which is a separate rule.</p>
        //
        // <p>{@code claim} is already OURS, and it must stay OURS: a return that handed the click
        // to vanilla is exactly the bug that once made the ropera and dagger run on vanilla's whole
        // attack path.</p>
        if (com.cortes.combat.item.WeaponTables.firesOnPress(held)) {
            firedOnPress = true;
            // **Network now, viewmodel from the tick.** The hit has to land on the press, so the
            // resolve packet goes out here; the strike <em>animation</em> cannot be announced here,
            // because the next {@code ItemViewModel.tick} sees the press edge and clears
            // {@code pendingStrike} -- the same edge that exists so a fast second click abandons
            // the first one. Announcing it from the tail of the tick, after that edge has run, is
            // what lets the first hit of a held button also animate.
            readyAtTick = tick + WeaponStats.attackInterval(player);
            send(new SwingPayload(resolveTargetId(player), false));
            pressStrikePending = true;
        }
    }

    /**
     * Resolve a swing locally and tell the server, without touching {@code holding}.
     *
     * <p><b>The one copy of "this swing just happened" for the held-button repeat.</b> The repeat
     * goes through here so the cooldown latch, the packet, the viewmodel strike and the vanilla
     * arm cannot drift. It is the client's prediction; the server still resolves the hit from the
     * packet.</p>
     *
     * <p><b>The press does not use this.</b> It sends the same packet but announces the strike
     * from the tail of the tick instead, because a strike announced before {@code ItemViewModel.tick}
     * is erased by the tick's press edge. See the fires-on-press branch in {@code press}.</p>
     *
     * <p>It does <em>not</em> open or close the swing, and neither does it clear {@code holding} --
     * the ropera and the dagger draw their wind-up off {@code holding}, and closing it to send a
     * strike is what once made them never render.</p>
     */
    private static void strikeNow(LocalPlayer player, float charge) {
        int interval = WeaponStats.attackInterval(player);
        readyAtTick = tick + interval;
        send(new SwingPayload(resolveTargetId(player), false));
        ItemViewModel.onStrike(charge, interval);
        if (!ItemViewModel.ownsSwing(player.getMainHandItem())) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * One line per swing decision, client side.
     *
     * <p>Temporary instrumentation, and it exists because a playtest report of "it does not hit"
     * and a playtest report of "it hits" are the same three lines of ambiguity: the press was
     * refused, the press opened a swing, or the press was never ours. Nothing in the build, the
     * log or a screenshot can tell those apart, and three rounds of guessing the wrong one is
     * what this replaces.</p>
     */
    private static void debug(String message) {
        CombatTrace.client(message);
    }

    /**
     * Whether the crosshair rests on a block that is in front of the target.
     *
     * <p>Measured to the target's centre rather than its nearest point, because this is a
     * question about what the player is looking at rather than a question about the cone: the
     * block is where the crosshair ray actually lands, and the target is a body, and a body has
     * a middle.</p>
     */
    private static boolean blockIsNearer(Minecraft mc, LocalPlayer player, LivingEntity target) {
        if (!isOnBlock(mc)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        return mc.hitResult.getLocation().distanceToSqr(eye)
                <= target.getBoundingBox().getCenter().distanceToSqr(eye);
    }

    private static void release(Minecraft mc) {
        // Opening a menu in the middle of a charge abandons the swing.
        //
        // The press was legitimate and the release would resolve a real, charged, server-paid
        // hit -- against whatever happened to be behind the window when the key came up. A menu
        // is a statement that the player is not fighting any more, and having it also land a
        // swing is the one case where the client's prediction is genuinely correct and the
        // player is genuinely wrong.
        if (mc.screen != null && holding) {
            debug("release ABANDONADO: pantalla abierta");
            predictedTicks = 0;
            holding = false;
            firedOnPress = false;
            ItemViewModel.onCancel();
            return;
        }
        if (!holding) {
            // Nothing was open, so this release is not ours. Vanilla keeps its own handling for
            // bows, for mining, and for a press that died on the cooldown.
            return;
        }
        // **A weapon that cannot charge already fired, on the press.** The release has nothing left
        // to resolve, so it only closes the swing. Without this the same button would pay twice:
        // once on the press and once, a round trip later, when the button came up -- the double hit
        // this class already guards against from the automatic strike, wearing the other edge.
        //
        // It comes before the cooldown guard on purpose: the cooldown is not what is being asked
        // here, and a fire-on-press weapon whose interval had already elapsed would otherwise slip
        // past the guard and pay again.
        if (firedOnPress) {
            debug("release: el press ya pago el golpe, cierro sin cobrar");
            predictedTicks = 0;
            holding = false;
            firedOnPress = false;
            return;
        }
        // **And the cooldown, which this did not used to ask.**
        //
        // <p>Releasing inside a cooldown used to swing anyway: `ready()` was checked on the press
        // and never here, so the button could be tapped faster than the weapon's own interval and
        // the client would keep sending.</p>
        //
        // <p>That was survivable while every swing came from a press. It is not survivable now,
        // because a held button on a weapon that may be held down has already paid several:
        // <b>the automatic strike moved `readyAtTick`, and letting the release through on top of
        // it is a second hit from one press</b> -- the one the player never asked for, arriving
        // when they let go.</p>
        //
        // <p>Closing the swing without paying is the whole of the fix. The charge is discarded,
        // the wind-up stops, and nothing is sent.</p>
        if (!ready()) {
            debug("release ABANDONADO: cooldown, ya cobro un golpe automatico");
            predictedTicks = 0;
            holding = false;
            ItemViewModel.onCancel();
            return;
        }
        // The classification is read HERE, with `holding` still true, and not one line later.
        //
        // `chargedRatio` opens with `if (!holding) return 0`, so clearing `holding` before the
        // read made every release a tap. That one inverted pair of lines is what four rounds of
        // playtesting called "no weight in the cut", "the fist does not animate" and "nothing
        // moves": the charge was 0 on the client, so `onStrike(0)` scaled every viewmodel channel
        // by zero, and `charge <= 0` sent every swing down the vanilla arm path instead of the
        // viewmodel's. The server kept its own tick count and charged the hit correctly, which
        // is why the sword still hurt while the client showed nothing at all.
        //
        // The comment above this used to say "read before anything is reset" and the code did the
        // exact opposite. A comment that describes the intended order is worth nothing next to
        // the order itself, which is why this one now names the bug.
        float charge = chargedRatio(Minecraft.getInstance().player);
        holding = false;
        LocalPlayer player = mc.player;
        if (player == null) {
            predictedTicks = 0;
            return;
        }

        // The client's own gate, tightened before anything is sent. See readyAtTick.
        readyAtTick = tick + WeaponStats.attackInterval(player);

        int targetId = resolveTargetId(player);
        debug("release: objetivo=" + targetId + " carga=" + charge
                + " ticks=" + predictedTicks + " FULLCHARGE="
                + WeaponStats.of(player.getMainHandItem()).charge().fullChargeTicks());
        send(new SwingPayload(targetId, false));

        // Local prediction of the two things the player sees immediately. Neither is the damage:
        // that is the server's, and predicting it would double-apply it, because this mod
        // deliberately clears the target's invulnerability window before hitting.
        //
        // The swing's LENGTH comes from this weapon's own attack interval, which is where most
        // of the weight came from. A sword that may only swing every twelve ticks gets a twelve
        // tick follow-through, and a fist that may swing every four gets four, so a heavy weapon
        // looks heavy and a quick one looks quick for the same reason rather than by a number
        // somebody typed. See ViewModelMotion.strikeTicks.
        ItemViewModel.onStrike(charge, WeaponStats.attackInterval(player));

        // Vanilla's arm plays only when the profile does not own the swing.
        //
        // The charge test used to be here too, `charge <= 0.0F || !ownsSwing`, because a tap used
        // to contribute nothing to the viewmodel. Now it does, so the test is gone: a profile that
        // draws its own strike must not also get vanilla's underneath it, or the two play at
        // different lengths and the item looks like it is being dragged.
        if (!ItemViewModel.ownsSwing(player.getMainHandItem())) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        predictedTicks = 0;
    }

    /**
     * The entity the swing is aimed at, at the moment it is released.
     *
     * <p>Resolved on the release and not on the press, which is the other half of why the attack
     * resolves there: the target is wherever the player was looking when they committed, so a
     * player who starts a swing, adjusts onto a mob and lets go hits the mob.</p>
     *
     * <p><b>And the coyote fallback, when the fresh scan comes up empty.</b> The scan is the
     * strict question and it goes first; only a scan that finds nothing asks the swing's own
     * memory, and only against the slack cone ({@link ConeTargeting#stillInSlackCone}) -- a target
     * the player has since turned away from fails even that. What is sent is an <em>intention</em>:
     * the server re-checks the strict cone on arrival and on every tick of its pending window, so
     * a generous gate here costs at most one packet while a strict one here would cost the hit.</p>
     */
    private static int resolveTargetId(LocalPlayer player) {
        Optional<LivingEntity> fresh = ConeTargeting.findTargetForHeldItem(player);
        if (fresh.isPresent()) {
            swingTargetId = fresh.get().getId();
            return swingTargetId;
        }
        if (swingTargetId != SwingPayload.NO_TARGET
                && player.level().getEntity(swingTargetId) instanceof LivingEntity remembered
                && ConeTargeting.stillInSlackCone(player, remembered,
                        com.cortes.combat.attack.MaceAir.reachFor(
                                WeaponStats.of(player.getMainHandItem()), player))) {
            debug("release: scan vacio, recordado " + swingTargetId + " aun en el slack");
            return swingTargetId;
        }
        return SwingPayload.NO_TARGET;
    }

    private static boolean isOnBlock(Minecraft mc) {
        return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    /**
     * The client's own gate: the local latch, and the server's answer once we have one.
     *
     * <p>The latch is the part that matters and it is deliberately stricter than the server's
     * value, so it can refuse a swing the server would have allowed and never the other way.</p>
     *
     * <p>The replicated ratio is a <b>second</b> opinion and it is only consulted once the server
     * has actually sent one. Treating "never told" as "not ready" is what left a player unable
     * to attack for nineteen seconds with no way to tell why: the press was refused here, and a
     * refused press is swallowed rather than handed to vanilla, so the game looked broken instead
     * of looking gated. A client that guesses wrong loses a packet; the server is the authority
     * either way.</p>
     */
    private static boolean ready() {
        if (tick < readyAtTick) {
            return false;
        }
        return !ClientCombatState.cooldownKnown()
                || ClientCombatState.cooldownRatio() >= 1.0F;
    }

    private static void send(SwingPayload payload) {
        CombatPackets.sendToServer(payload);
    }

    /**
     * The verdict on this press, for the mixin that stops vanilla from also attacking.
     *
     * <p>Anything other than {@link Claim#NOT_OURS} cancels vanilla, including a refused press:
     * that one has to stay invisible, and the only way to keep it invisible is to stop it there
     * too.</p>
     */
    public static Claim claim() {
        return claim;
    }

    /**
     * The charge cue, once per hold, and only once it is actually a charge.
     *
     * <p>Played on the client and not on the server, because this one is feedback about the
     * player's own button and a round trip would put it after the hit it is explaining. Nothing
     * else about a swing is decided here: the sound carries no authority, and the server's charge
     * is the one that pays.</p>
     *
     * <p><b>Past the threshold, not on the press.</b> On the press would mean every ordinary click
     * makes the charging sound, and then the sound stops saying "you are charging" and starts
     * saying "you clicked" -- which is the band the design refuses to put in, because a player only
     * discovers it by having hit for less than they expected. Below the threshold nothing happens
     * and nothing is shown; at the threshold everything starts, and that includes this.</p>
     */
    private static void playChargeSoundOnceItIsACharge(LocalPlayer player) {
        if (chargeSoundPlayed
                || predictedTicks < AttackTiming.MIN_CHARGE_HOLD_TICKS
                || !com.cortes.combat.attack.WeaponStats.canCharge(player.getMainHandItem())) {
            return;
        }
        chargeSoundPlayed = true;
        var event = com.cortes.combat.sound.CombatSounds.event(
                ModSounds.of(com.cortes.combat.attack.WeaponStats.of(
                        player.getMainHandItem()).profile(), ModSounds.Kind.CHARGE));
        if (event != null) {
            player.playSound(event, 0.7F, 1.0F);
        }
    }

    /** Called once per client tick, after the attack path has run. */
    public static void endTick() {
        claim = Claim.NOT_OURS;
    }

    /**
     * Whether the attack button is physically down right now, asked of the window.
     *
     * <p>This is the fix for the report that stopped a playtest: <b>the click died and nothing
     * came back.</b> Not "the click felt wrong" -- the player could not attack, could not break
     * blocks and could not interact, permanently, with no error in the log and no way to get out
     * of it short of quitting.</p>
     *
     * <p>What the friend's log shows is the whole story. Over fifteen minutes every hold was
     * between one and forty-eight ticks -- taps and normal clicks. The last two were
     * <b>142 and 84 ticks</b>: seven seconds and four. Those are the only two long holds in the
     * entire session, and they are the two immediately before the click stopped working.</p>
     *
     * <p>Seven seconds of attack button is not a fight. It is a button that was down when the
     * window lost focus, because <b>GLFW does not deliver a release event when the window is
     * deactivated with a button held</b>. The press opened a swing, the release never came, and
     * {@link #holding} stayed true for the rest of the session. That is the same family as the
     * three bugs this project already fixed in this class -- a state outliving the button that
     * created it -- but the button here vanished for a reason that produces no event at all, so
     * nothing that listens for events ever hears about it.</p>
     *
     * <p>Why it takes all three symptoms with one cause:</p>
     * <ul>
     *   <li>mining dies because {@code MinecraftAttackMixin} cancels {@code continueAttack} --
     *       the per-tick path -- for as long as {@code holding} is true;</li>
     *   <li>attacking dies because a press that arrives while the swing is already open is
     *       refused by the gate that refuses a double click, and nothing tells the player why;</li>
     *   <li>interacting dies because the right button's own {@code holding} flag got stuck the
     *       same way, and that flag is what refuses the item use.</li>
     * </ul>
     *
     * <p>So the swing does not trust the event stream to be complete. Every tick it asks the
     * window whether the button is really down, and if it is not while a swing is open, the
     * release was lost and the swing is closed as if it had arrived. <b>Self-healing on the
     * truth rather than on the notification.</b> That also means the fix does not care which of
     * the several ways a release can go missing was the one that bit.</p>
     *
     * <p>Cheap on purpose: one call per open swing, and only while one is open.</p>
     */
    /**
     * The parked press's gate, re-asked at the moment of the fire.
     *
     * <p>The window does not rewrite a rule a live press obeys. Every branch here is a gate
     * {@code press} ran when the click was first refused; this is the same list, which is what
     * makes the fired press indistinguishable from a fresh one. The button being physically down
     * is the repented-click rule: a tap that was parked on a refusal must never fire once the
     * finger is off, because then the buffer would be longer than the player's click.</p>
     */
    private static boolean canFireTheParkedPress(Minecraft mc, LocalPlayer player) {
        if (player == null
                || mc.screen != null
                || player.isSpectator()
                || player.isUsingItem()
                || holding
                || GuardInputHandler.predictedHolding()
                || !ready()
                || !buttonIsPhysicallyDown()) {
            return false;
        }
        return com.cortes.combat.item.WeaponKinds.fights(
                com.cortes.combat.attack.WeaponStats.of(player.getMainHandItem()).profile());
    }

    private static boolean buttonIsPhysicallyDown() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        // A null handle is the safe direction: it answers "not down", so a window that is gone
        // closes an open swing instead of leaving it latched to nothing.
        return InputLiveness.buttonIsDown(window != null,
                window != null && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                        window.getWindow(),
                        org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                        != org.lwjgl.glfw.GLFW.GLFW_RELEASE);
    }

    /**
     * Closes a swing whose button is no longer down, so a lost release cannot outlive the session.
     *
     * <p>The decision is {@link InputLiveness#shouldCloseSwing}, which is pure and tested; this
     * is only the window read and the effect. Called from the tick, before anything reads
     * {@link #holding}, because that ordering is the mechanism.</p>
     *
     * @return whether a swing was open and had to be resolved as a lost release
     */
    public static boolean reconcileWithTheButton() {
        if (!InputLiveness.shouldCloseSwing(holding, buttonIsPhysicallyDown())) {
            return false;
        }
        debug("release PERDIDO (el boton ya no esta apretado): lo resuelve el tick");
        // Abandoned rather than released: the player let go of the button some time ago and we
        // only just noticed, so paying a swing against a world they have since walked away from
        // would be the wrong answer. Nothing is spent, and the swing closes.
        abandon("release perdido");
        return true;
    }

    // --- For /cc input. These read the fields the gates read and recompute nothing. ---

    /** Whether the client's own latch has expired, which is half of the cooldown gate. */
    public static boolean readyAtTickPassed() {
        return tick >= readyAtTick;
    }

    public static int readyAtTickValue() {
        return readyAtTick;
    }

    /** Whether the server's cooldown answer permits a swing: the other half of that gate. */
    public static boolean syncedCooldownReady() {
        return !ClientCombatState.cooldownKnown()
                || ClientCombatState.cooldownRatio() >= 1.0F;
    }

    public static boolean cooldownKnown() {
        return ClientCombatState.cooldownKnown();
    }

    public static float syncedCooldownRatio() {
        return ClientCombatState.cooldownRatio();
    }

    /** Whether the crosshair rests on a block, which is what makes a swing mean "mining". */
    public static boolean onBlock() {
        return isOnBlock(Minecraft.getInstance());
    }

    public static boolean buttonDownForDebug() {
        return buttonIsPhysicallyDown();
    }

    public static boolean holding() {
        if (!ServerConfig.combatRework()) {
            return false;
        }
        return holding;
    }

    public static int predictedTicks() {
        return predictedTicks;
    }

    /**
     * One client tick of prediction, and the two things that can invalidate an open swing.
     *
     * <p>Advancing the charge is the obvious job. The other two are here because an open swing
     * outlives the button in ways that are each a hole on their own.</p>
     */
    public static void tick() {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // First, before anything reads `holding`. The window losing focus with the button down
        // produces no release event, so the swing can only be found out about by asking whether
        // the button is still down. See reconcileWithTheButton for the whole story.
        reconcileWithTheButton();

        // **El press que se estaciono hace un rato, disparado aqui en el tick.**
        //
        // Un click medio tick antes de que el cooldown termine se tiraba: el gate lo rechazo, el
        // click murio, y re-clickar es el mismo golpe con otro retraso. Si el gate que lo rechazo
        // ya No lo rechazaria y el boton sigue apretado, el press se ejecuta entero por el mismo
        // camino de siempre -- con la misma orden de gates, el mismo objetivo y el mismo paquete.
        //
        // En el tick y NO en el evento de click porque la ventana del buffer es corta: dos ticks
        // despues del press no hay evento que escuche. Y antes de leer `holding`, que es el orden
        // que el press real ya cumple dentro del frame. El `claim` que esto deja puesto lo limpia
        // `endTick` del mismo tick -- ese orden es el que hace seguro el fuego desde aca. (⌒‿⌒)
        if (parkedPress != PressBuffer.EMPTY) {
            if (PressBuffer.shouldFire(parkedPress, tick, canFireTheParkedPress(mc, player))) {
                debug("press estacionado (tick " + parkedPress + ") disparado en tick " + tick);
                parkedPress = PressBuffer.EMPTY;
                press(mc);
            } else if (PressBuffer.expired(parkedPress, tick)) {
                parkedPress = PressBuffer.EMPTY;
            }
        }

        if (player == null) {
            if (holding) {
                holding = false;
                ItemViewModel.onCancel();
            }
            pressStrikePending = false;
            return;
        }
        if (holding && player.getMainHandItem().getItem() != openItem) {
            // The swing was opened with a different weapon in hand, which happens when a player
            // scrolls the hotbar mid hold. Resolving it as a release is the only honest option:
            // the cooldown is paid, because the player did commit to a swing, and the charge is
            // closed. Left open, a long hold on a slow weapon becomes a free fully charged swing
            // on a fast one, because the release reads the charge in ticks and the ticks were
            // counted against the other weapon's much longer scale.
            release(mc);
            return;
        }
        if (holding) {
            predictedTicks++;
            playChargeSoundOnceItIsACharge(player);
            // Holding the button is one strike repeated, for the weapons that can be held down.
            //
            // **It does not touch `holding`, and that is load-bearing.** The ropera and the dagger
            // cannot be charged, and their viewmodel profiles are the ones that read the wind-up --
            // closing the swing here is what made them never render once, because their whole
            // animation hangs off `holding` being true. So the swing stays open and the strike is
            // emitted underneath it.
            //
            // **Which means the release has to be stopped from paying twice.** It is, one guard
            // down in `release`: by the time the button comes up the automatic strike already moved
            // `readyAtTick`, and a release inside the cooldown cannot swing.
            autoStrikeWhileHeld(mc, player);
        } else {
            chargeSoundPlayed = false;
        }
        // The wind-up reads the RAW hold progress, and the charge bar reads the thresholded one.
        //
        // They are different rules and they were welded together. `chargeRatioFor` is zero for
        // the first four ticks and then remaps, which is exactly right for damage and exactly
        // wrong for a wind-up: the playtest said the cocking "starts very late, only in the last
        // part of the charge", and it did, because a sword spends its first fifth of a second
        // doing nothing at all and then has to catch up. A weapon should start moving the instant
        // the button goes down, because that is the feedback that tells the player the press was
        // heard.
        // Two readings of one hold, and the split is the whole point.
        //
        //  + "chargeRatioFor" +  is zero for the first four ticks and then remaps. For the
        // WIND-UP that is exactly wrong: the playtest said the cocking "starts very late, only in
        // the last part of the charge", and it did, because a sword spends its first fifth of a
        // second doing nothing and then has to catch up. A weapon should start moving the instant
        // the button goes down, because that is the feedback that says the press was heard. So the
        // wind-up gets the raw ratio.
        //
        // For the CAMERA the raw ratio is exactly wrong too, in the other direction and for the
        // same reason: a two tick click is one twenty-fourth of a charge and the camera closed on
        // it. That is what "any attack starts the charge" was, on both sides: the camera was
        // reading the wind-up's number. The camera wants to know whether the swing IS a charge,
        // so it gets the thresholded one, and a tap moves nothing at all.
        // **La carga del boton derecho entra por la misma puerta que la del izquierdo.**
        //
        // El playtest pidio que el arma se levante y tiemble mientras carga el orbe, y eso es
        // exactamente lo que el canal WINDUP del perfil ya hace: el mismo gesto que la carga del
        // ataque, con el mismo temblor. No hay perfil nuevo que escribir -- el arma ya sabe
        // levantarse.
        //
        // Se toma el MAYOR de las dos cargas y no la suma: un arma que cargara con los dos botones a
        // la vez levantaria el doble, y el doble de una pose es otra pose. Y se pasa `holding` en
        // `true` mientras haya carga especial para que el canal arranque igual que con un press.
        float special = ClientCombatState.specialCharge();
        boolean winding = holding || special > 0.0F;
        ItemViewModel.tick(winding, Math.max(predictedRatio(player), special),
                chargedRatio(player), ClientCombatState.guardHeld());

        // **The fire-on-press strike, announced after the tick that owns the press edge.** See
        // pressStrikePending: announcing it earlier, while the button was still down, is exactly
        // what the press edge erases. The interval is read fresh so the swing's length is the
        // weapon's own, and the vanilla arm is played only for a profile that does not own the
        // swing -- the same rule the repeat uses.
        if (pressStrikePending) {
            pressStrikePending = false;
            int interval = WeaponStats.attackInterval(player);
            ItemViewModel.onStrike(0.0F, interval);
            if (!ItemViewModel.ownsSwing(player.getMainHandItem())) {
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    /**
     * One strike, emitted underneath a swing that is still open, for a weapon that is held down.
     *
     * <h2>Why this is a separate path and not a {@code release()}</h2>
     *
     * <p>Because {@code release} pays a charge, and a weapon that can be held down cannot be
     * charged: its charge profile is NONE, so the ratio it would pay is zero and the whole call
     * exists to close a swing. Sending the same message with a zero is a second copy of the
     * release, and the two would drift the first time either changed.</p>
     *
     * <h2>And why it leaves {@code holding} alone</h2>
     *
     * <p>The ropera and the dagger are the two weapons this serves most, and both draw their
     * wind-up off {@code holding}. Closing the swing to send a strike is what made them fail to
     * render once. So the swing stays open, the strike goes underneath, and the release is stopped
     * from paying a second one by its own cooldown guard.</p>
     */
    private static void autoStrikeWhileHeld(Minecraft mc, LocalPlayer player) {
        // **`canHoldDown`, not `autoRepeats(.., cocks)`.** The cock gate was here for one round and
        // it took the repeat away from every weapon whose profile reads the wind-up -- which is the
        // ropera and the dagger, the two that read it, and it is precisely those two the playtest
        // reports as "holding does nothing". The rule the rest of the project already asserts is
        // "no charge means it repeats, the maza aside" (`WeaponTablesTest`), and this is the call
        // site that was not asking it.
        com.cortes.combat.core.weapon.WeaponProfile held =
                com.cortes.combat.attack.WeaponStats.of(player.getMainHandItem()).profile();
        if (!buttonIsPhysicallyDown()
                || mc.screen != null
                || player.isUsingItem()
                || !com.cortes.combat.item.WeaponTables.canHoldDown(held)
                || !ready()) {
            return;
        }

        // **Reopen the server's swing, then resolve it, every repeat.**
        //
        // The first repeat after a press finds the swing open (the press sent it) and closes it
        // when it resolves -- `AttackCooldown.consume` clears `holding`. So a second repeat would
        // arrive with nothing open, `CombatEngine.resolveSwing` would return `NO_SWING`, and the
        // held button would land exactly one hit for the whole hold while the client predicted
        // many. The open packet is what makes the loop real rather than drawn.
        send(new SwingPayload(SwingPayload.NO_TARGET, true));
        strikeNow(player, 0.0F);
        // Restarted, not cleared. It is what makes the next wind-up begin at zero instead of
        // arriving at the strike already halfway through a swing the player has held for a second.
        predictedTicks = 0;
        debug("autohit: golpe automatico interval=" + WeaponStats.attackInterval(player));
    }

    /**
     * The charge as the player sees it.
     *
     * <p>Thresholded, and that is the fix for "a normal click shows a charge bar". The bar used
     * to draw the raw ratio, so every click flashed a two hundred millisecond sliver and the
     * player read that as the game charging them when the swing was going to land flat. Below the
     * threshold the bar, the wind-up and the FOV are now all exactly zero: <b>nothing happens and
     * nothing is shown</b>, and at the threshold everything starts.</p>
     */
    public static float predictedRatio() {
        LocalPlayer player = Minecraft.getInstance().player;
        return chargedRatio(player);
    }

    /**
     * The charge this swing will actually be paid with, threshold applied.
     *
     * <p>The same {@link AttackTiming#chargeRatioFor} the server runs, on the client's own tick
     * count. It is a prediction and the server's count wins, but the threshold is a fixed number
     * that both sides evaluate the same way, so the classification agrees rather than the value:
     * a click under four ticks is a tap on both sides no matter whose count is off by one.</p>
     */
    public static float chargedRatio() {
        LocalPlayer player = Minecraft.getInstance().player;
        return chargedRatio(player);
    }

    private static float chargedRatio(LocalPlayer player) {
        if (!holding || player == null) {
            return 0.0F;
        }
        ChargeProfile charge = WeaponStats.of(player.getMainHandItem()).charge();
        // Prediccion con la ventana efectiva de Haste: el mismo nombre que corre el server
        // (AttackTiming#effectiveFullChargeTicks), si no la barra llena mientras el server
        // todavia esta contando. (⌒‿⌒)
        return AttackTiming.chargeRatioFor(
                predictedTicks,
                AttackTiming.effectiveFullChargeTicks(charge.fullChargeTicks(),
                        WeaponStats.hasteLevelOf(player)),
                charge.canCharge());
    }

    private static float predictedRatio(LocalPlayer player) {
        if (!holding || predictedTicks <= 0) {
            return 0.0F;
        }
        ChargeProfile charge = WeaponStats.of(player.getMainHandItem()).charge();
        int full = AttackTiming.effectiveFullChargeTicks(charge.fullChargeTicks(),
                WeaponStats.hasteLevelOf(player));
        if (full <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) predictedTicks / (float) full);
    }

    /** The raw count, for the debug readout. Unthresholded on purpose. */
    public static int heldTicks() {
        return predictedTicks;
    }

    /** Called when the server tells us the real value, so lag does not leave the bar lying. */
    public static void acceptServerState(float chargeRatio, int fullChargeTicks) {
        if (fullChargeTicks <= 0) {
            predictedTicks = 0;
            return;
        }
        // Snap toward the server value rather than overwriting, so a single late packet does
        // not make the indicator jump backwards.
        int serverTicks = Math.round(chargeRatio * fullChargeTicks);
        predictedTicks = Math.max(serverTicks, predictedTicks - 2);
        if (serverTicks < predictedTicks) {
            predictedTicks = serverTicks;
        }
    }

    /** Test hook and logout path: forget the open swing rather than leaving it dangling. */
    public static void reset() {
        holding = false;
        predictedTicks = 0;
        openItem = null;
        firedOnPress = false;
        pressStrikePending = false;
        claim = Claim.NOT_OURS;
        ItemViewModel.onCancel();
    }

    /**
     * Drop an open swing without sending anything, because the player changed their mind about it.
     *
     * <p>Same shape as the menu case and the item-change case: the press was legitimate, the
     * release would land a real charged hit, and what happened in between means it should not.
     * The difference is that nothing is paid, because a swing that was never released was never
     * spent -- which is also why this is not a refusal at the press. The press already happened.
     */
    public static void abandon(String reason) {
        if (!holding) {
            return;
        }
        debug("swing ABANDONADO: " + reason);
        holding = false;
        predictedTicks = 0;
        openItem = null;
        firedOnPress = false;
        claim = Claim.NOT_OURS;
        ItemViewModel.onCancel();
    }
}
