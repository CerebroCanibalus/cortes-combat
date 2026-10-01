package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.registry.ModComponents;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.Offset;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelInput;
import com.cortes.combat.viewmodel.ViewModelMotion;
import com.cortes.combat.viewmodel.ViewModelProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * The one place the viewmodel library touches the game.
 *
 * <p>Everything else in {@code com.cortes.combat.viewmodel} is plain Java that a test can load
 * on its own, and keeping the seam exactly here is what makes that possible: a profile is data,
 * the maths is a function of data, and the only thing left for Minecraft is "read four floats,
 * add them to a pose". The class that does the maths could be lifted into a jar on its own.</p>
 *
 * <h2>Where in the pipeline this lands, and why it is not an event</h2>
 *
 * <p>This used to be a {@code RenderHandEvent} listener, on the strength of a claim that the
 * event "composes around everything vanilla does". The claim survived being written down, being
 * reviewed, and being cited in this file, and it was wrong. Verified against the 1.21.1
 * bytecode:</p>
 *
 * <pre>
 *   renderHandsWithItems:
 *     offset 185  ClientHooks.renderSpecificFirstPersonHand  <- the event fired here
 *     offset 212  renderArmWithItem
 *                    applyItemArmTransform                   <- the hand gets its position
 *                    applyItemArmAttackTransform             <- vanilla's own swing arc
 *                    renderItem
 * </pre>
 *
 * <p>The event fires <b>before the hand has a position</b>. Every offset was therefore applied in
 * a frame where the item was still at the origin, which makes a rotation a rotation about the
 * camera rather than about the player's grip. Composing "around vanilla" turned out to mean
 * composing around the part of vanilla that places the hand, which is the one part that matters.</p>
 *
 * <p>It is now applied at the tail of {@code applyItemArmTransform}, one call before vanilla puts
 * its own swing arc, which is the item's own frame. That needs a mixin, so this feature is no
 * longer mixin free, and the profile's numbers are finally written against the frame they are
 * rendered in. The full account is in {@code ItemInHandRendererMixin}.</p>
 */
public final class ItemViewModel {

    /**
     * One reusable quaternion, mutated and handed to the pose immediately.
     *
     * <p>{@code Axis.rotationDegrees} allocates a new {@code Quaternionf} on every call, and the
     * naive version of this composes three of them per axis per frame, inside the item renderer.
     * {@code mulPose} reads the value before it returns, so a single shared instance is safe on
     * the render thread and costs nothing.</p>
     */
    private static final Quaternionf SCRATCH = new Quaternionf();

    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

    /**
     * The swing state of the item in the player's hand.
     *
     * <p>Static for the same reason {@code ClientCombatState} is: it is display state with the
     * lifetime of a client session, it is read once per frame from one place, and it never feeds
     * back into anything the server resolves.</p>
     */
    private static final ViewModelMotion MOTION = new ViewModelMotion();

    /**
     * How many times the mixin seam has actually run.
     *
     * <p>Exists because "nothing happens" has two completely different causes that look identical
     * from the outside: the seam never being reached, or the profile being empty. Three rounds of
     * tuning numbers cannot tell those apart, and neither can staring at a screenshot. A counter
     * turns the first one into a log line, and {@link #debug()} turns the second into a command.</p>
     */
    public static final java.util.concurrent.atomic.AtomicLong FRAMES = new java.util.concurrent.atomic.AtomicLong();

    /**
     * The same counter for the bare-hand seam.
     *
     * <p>Separate because it is a different method in a different branch, and one counter for
     * both would have hidden exactly the bug it exists to catch: the item seam fires, the arm seam
     * never does, and a single total looks healthy.</p>
     */
    public static final java.util.concurrent.atomic.AtomicLong ARM_FRAMES =
            new java.util.concurrent.atomic.AtomicLong();

    // Resolution is cached on two reference compares rather than read per frame. The item
    // reference catches a swap, and the component reference catches a mod rewriting the profile
    // on the same item, which is rare enough that it should not happen and cheap enough that it
    // does not matter if it does.
    private static Item cachedItem;
    private static ViewModelProfile cachedComponent;
    private static ViewModelProfile cachedProfile;

    /** The held item's cooldown last tick, for the edge that means "the guard just broke". */
    private static float lastStagger = 0.0F;

    /** The guard flag last tick, so a change prints one line instead of twenty per second. */
    private static boolean lastGuarding;

    private ItemViewModel() {
    }

    /**
     * One client tick. Advances the swing state.
     *
     * <p>Both resolutions of the same hold, and the reason there are two is the whole reason the
     * camera used to fire on every click. The wind-up wants the raw one so the weapon starts
     * moving on frame one; anything asking whether the swing is a charge wants the thresholded
     * one. One tick count, two readings, computed by the caller from the same number.</p>
     */
    public static void tick(boolean holding, float rawCharge, float chargedRatio, boolean guarding) {
        // The stagger is a vanilla item cooldown and it is already synced, so the client reads it
        // directly instead of waiting for the server to say the guard dropped. Two reasons that
        // matters: the collapse animation has to start on the same tick the guard breaks or it
        // lags the sound by a round trip, and the cooldown's own progress is the length of the
        // animation, which means the two cannot be re-tuned apart.
        float stagger = staggerPercent();
        // The collapse fires on a transition, and the transition rule lives in the motion.
        //
        // It was here before, and it was `stagger < lastStagger`, which is a descending counter:
        // a hundred ticks of cooldown produced a hundred collapses, each one restarting a seven
        // tick bump, which is a constant partial offset held for five seconds. The motion's own
        // timing was correct and bounded the whole time, and the tests were green, because the
        // bug was in the wiring and a component test cannot see wiring.
        MOTION.observeStagger(stagger);
        // The parry's two channels, gated on the weapon actually reading them.
        //
        // Gated HERE and not at the call site, because the call site is where the bug was: the
        // channels are global and the state that drives them is the player's, not the item's, so
        // a sabre that had been parried kept animating whatever came next. The lockout rides
        // BREAK, and the sword reads BREAK for its guard -- so the sword played the sabre's five
        // seconds of "not right now" and the player read it as a guard stuck down.
        //
        // `reads(PARRY)` rather than `isTheSabre`: the question is what the profile can do with
        // the signal, and it stays true if the sabre ever gets a second item.
        LocalPlayer held = Minecraft.getInstance().player;
        ViewModelProfile profile = held == null
                ? ViewModelDefaults.of(WeaponProfile.UNARMED)
                : resolve(held.getMainHandItem());
        boolean parryWeapon = profile.reads(Channel.PARRY);
        if (parryWeapon) {
            // The server's answer, the edge derived from it, and the number it sent is the
            // length the bump lasts. Called from the render-side tick rather than from the packet
            // handler because the packet arrives once and the bump has to keep running for a
            // hundred ticks after it.
            MOTION.observeParry(ClientCombatState.parryActive(), ClientCombatState.parryCooldown());
        } else {
            MOTION.clearParry();
        }
        // Same gate, same reason: the channels are global and the state driving them is the
        // player's, so a sabre's parry used to keep drawing whatever came next. `reads` is the
        // question "can this profile do anything with the signal", not "is this the right item".
        boolean lungeWeapon = profile.reads(Channel.LUNGE);
        if (!lungeWeapon) {
            MOTION.clearLunge();
        }
        if (guarding != lastGuarding) {
            trace("guardia " + (guarding ? "ARRIBA" : "abajo")
                    + " stamina=" + ClientCombatState.guardStamina()
                    + "/" + ClientCombatState.guardMax()
                    + " cooldown=" + stagger
                    + " item=" + (Minecraft.getInstance().player == null
                    ? "?" : Minecraft.getInstance().player.getMainHandItem().getItem())
                    + " offset=" + describeOffset());
        }
        lastGuarding = guarding;
        lastStagger = stagger;
        MOTION.tick(holding, rawCharge, chargedRatio, guarding, stagger,
                parryWeapon && ClientCombatState.parryActive(),
                lungeWeapon && ClientCombatState.lungeActive());
    }

    /**
     * The composed offset for this frame, in one line.
     *
     * <p>Put next to the guard trace because "the pose is not what the reference shows" is two
     * problems wearing one sentence: the numbers are half what they should be, or the seam is
     * applying them twice. One line with the composed pitch and roll tells them apart, and
     * guessing between them from a screenshot cannot.</p>
     */
    private static String describeOffset() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "sin jugador";
        }
        Offset offset = resolve(player.getMainHandItem())
                .evaluate(input(PARTIAL_TICK.get()));
        return String.format("pitch=%.1f yaw=%.1f roll=%.1f pos=(%.2f,%.2f,%.2f) cero=%s",
                offset.pitch(), offset.yaw(), offset.roll(),
                offset.x(), offset.y(), offset.z(), offset.isZero());
    }

    private static void trace(String message) {
        com.cortes.combat.CombatTrace.client("viewmodel] " + message);
    }

    /**
     * How much of the held item's cooldown is left, 0 when it is ready.
     *
     * <p><b>Zero means ready.</b> {@code getCooldownPercent} returns the fraction remaining and
     * returns zero outright when the item has no cooldown at all. Reading it as "one when ready"
     * is the single mistake that made a guard pose that never appeared: every idle tick counted
     * as fully staggered, so the guard channel was forced to zero sixty times a second and the
     * weapon never left the vanilla pose.</p>
     *
     * <p>Also {@code getCooldownPercent} and not {@code getCooldownPercentage}; verified against
     * the 1.21.1 jar, and the longer name does not exist.</p>
     */
    private static float staggerPercent() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0.0F;
        }
        return player.getCooldowns().getCooldownPercent(player.getMainHandItem().getItem(),
                0.0F);
    }

    /**
     * The strike just resolved on the client.
     *
     * @param charge        how charged the swing was, 0 for a tap. Passed in rather than read
     *                      from the motion so the caller can hand over the same classified
     *                      value the server will use, and the motion the player watches agrees
     *                      with the damage they get
     * @param durationTicks how long this swing lasts, which is the weapon's own attack interval
     *                      clamped. Most of the weight is here and not in the angles
     */
    public static void onStrike(float charge, int durationTicks) {
        MOTION.onStrike(charge, durationTicks);
    }

    /** The swing was abandoned: released, cancelled, or the item changed under us. */
    public static void onCancel() {
        MOTION.onCancel();
    }

    /** The charge the last strike was released with, for the debug readout. */
    public static float releasedCharge() {
        return MOTION.released();
    }

    /** One client tick of the swing clock. */
    public static void tickClock() {
        MOTION.advanceClock();
    }

    /**
     * The frame's interpolation value, read the only way 1.21.1 allows.
     *
     * <p>{@code Minecraft} has no {@code getDeltaTracker()} and no {@code getFrameTime()}. It is
     * {@code getTimer().getGameTimeDeltaPartialTick(false)}, verified against the 1.21.1 jar. The
     * first version of the FOV effect guessed the modern accessor and the compiler said no.</p>
     */
    private static final java.util.function.Supplier<Float> PARTIAL_TICK = () -> {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? 0.0F : mc.getTimer().getGameTimeDeltaPartialTick(false);
    };

    /**
     * Applies the profile to a hand, in the frame the hand is actually drawn in.
     *
     * <p>Called from the tail of {@code ItemInHandRenderer.applyItemArmTransform}, which is where
     * vanilla puts its own swing arc one call later. That is the item's frame: the hand has been
     * moved to {@code ITEM_POS} and a rotation here turns the blade in the player's grip rather
     * than swinging the whole arm around the camera.</p>
     *
     * <p>This replaces a {@code RenderHandEvent} handler, and the replacement is not a
     * preference. That event fires from {@code renderHandsWithItems} at bytecode offset 185;
     * {@code renderArmWithItem}, which is what places the hand, is called at offset 212. So the
     * old seam applied every offset <b>before the hand had a position</b>, where a pitch is a
     * rotation about the camera. Three rounds of playtesting could not make a charged swing feel
     * like a cut and the reason was the frame, not the numbers. Keeping both seams would apply
     * the offset twice, once in each frame, so the old handler is gone rather than disabled.</p>
     *
     * <p>Main hand only, decided by comparing against the player's main arm rather than by
     * hardcoding right: a player who flips their main arm in the settings would otherwise get
     * their off hand animated. The channels describe one weapon's swing, and WINDUP is the main
     * hand's charge, so putting it on the shield would move the shield while the sword charges.</p>
     */
    public static void applyInHand(com.mojang.blaze3d.vertex.PoseStack pose,
                                   net.minecraft.world.entity.HumanoidArm arm) {
        applyProfile(pose, arm, false);
    }

    /**
     * The same thing for a bare hand, from the arm's own seam.
     *
     * <p>The design makes the fists chargeable, and a bare hand is the one case where vanilla
     * draws an <b>arm</b> instead of an item and never touches
     * {@code applyItemArmTransform} at all. So the fist is not a variant of the item path, it is
     * a different path, and it needs its own call.</p>
     */
    public static void applyToArm(com.mojang.blaze3d.vertex.PoseStack pose,
                                  net.minecraft.world.entity.HumanoidArm arm) {
        applyProfile(pose, arm, true);
    }

    private static void applyProfile(com.mojang.blaze3d.vertex.PoseStack pose,
                                     net.minecraft.world.entity.HumanoidArm arm,
                                     boolean bareHand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || arm != player.getMainArm()) {
            return;
        }
        // A bare hand must actually BE a bare hand. Reading the off hand here instead would
        // animate the shield when the player is punching with an empty main hand, which is the
        // same mistake as moving the shield during a sword charge.
        boolean mainHandEmpty = player.getMainHandItem().isEmpty();
        if (bareHand != mainHandEmpty) {
            return;
        }
        ViewModelProfile profile = resolve(player.getMainHandItem());
        if (profile.isEmpty()) {
            return;
        }
        Offset offset = profile.evaluate(input(PARTIAL_TICK.get()));
        if (offset.isZero()) {
            return;
        }
        apply(pose, offset);
    }

    /**
     * The frame to evaluate, interpolated between the last tick and the next one.
     *
     * <p>This is the difference between a viewmodel and a twenty hertz animation played at sixty
     * frames a second. Without the partial tick the item holds each position for three frames,
     * which the eye reads as a stutter rather than as motion, and the tremble turns into a buzz
     * because its clock only moves twenty times a second. Vanilla interpolates its own swing for
     * the same reason.</p>
     */
    private static ViewModelInput input(float partialTick) {
        return MOTION.snapshot(partialTick, cooldownRunning());
    }

    /**
     * The charge a swing is currently at, whether it is being wound up or following through.
     *
     * <p>One value for the FOV and one for the viewmodel, because they have to agree: a FOV that
     * closes for a charge the item did not show, or an item that winds up for a charge the FOV
     * ignored, is worse than having neither. They are not two values kept in step by hand, though.
     * Both read this one, in the same frame, with the partial tick in it, so they cannot drift.
     *
     * <p>Classified, never the swing's amplitude and never the raw hold. Those were what this read
     * at one point or another, and each time the camera moved on every ordinary tap in the game:
     * the amplitude has a floor so a tap still swings, and the raw hold exists so the wind-up
     * starts on frame one. Neither of those is a statement about whether a swing is a charge.</p>
     */
    public static float currentCharge() {
        return MOTION.currentCharge();
    }

    /**
     * {@link #currentCharge()} part way through a frame, for anything that runs per frame.
     *
     * <p>The charge only moves on the tick, so a reader that wants a curve has to interpolate
     * between the last two ticks itself, and a reader that forgets is the reason the FOV used to
     * step twenty times a second.</p>
     */
    public static float chargeAt(float partialTick) {
        return MOTION.chargeAt(partialTick);
    }

    /** The charge the last strike was classified as: 0 for a tap. Not the amplitude. */
    public static float classifiedCharge() {
        return MOTION.classifiedCharge();
    }

    /**
     * How much of the hard cooldown is still running.
     *
     * <p>Counting the cooldown rather than the readiness, because a channel whose resting value
     * is one is a channel whose tracks need a special case, and a profile that shifts the item
     * while the player stands still is a weapon slowly drifting off the vanilla pose.</p>
     */
    private static float cooldownRunning() {
        return 1.0F - ClientCombatState.cooldownRatio();
    }

    /**
     * True when the weapon in hand owns its swing, so the caller knows to skip the local
     * {@code player.swing} that would otherwise play a second, smaller swing underneath.
     *
     * <p>The server still swings, so other players see the vanilla arm animation: first person
     * gets the real viewmodel and third person gets vanilla until the framework grows a
     * third-person story.</p>
     */
    public static boolean ownsSwing(ItemStack stack) {
        return resolve(stack).ownsSwing();
    }

    /**
     * DELETED ON PURPOSE. It was the un-interpolated version of {@link #input(float)}, built from
     * the motion's per-tick accessors, and nothing called it -- but it had the right name and the
     * wrong arithmetic, which is exactly how a twenty-hertz staircase gets reintroduced by
     * somebody who does not remember there was a reason for the other one.
     *
     * <p>Every frame reads {@code snapshot(partialTick, ...)}. There is no second way in.</p>
     */

    /**
     * The profile for an item, in two layers.
     *
     * <p>Our own component, then the default for the weapon profile. Deliberately not the full
     * four-layer resolver that {@code WeaponStats} uses: the outer two layers there are an
     * override file and the derivation from an item's attack attributes, and both of those exist
     * to answer "how hard does this hit". A viewmodel has no such question to answer, and
     * deriving one from damage numbers would be inventing a look nobody asked for.</p>
     */
    private static ViewModelProfile resolve(ItemStack stack) {
        if (stack == null) {
            return ViewModelDefaults.of(WeaponProfile.UNARMED);
        }
        if (stack.isEmpty()) {
            // NOT ViewModelProfile.NONE. That was here from the start and it made the whole
            // UNARMED profile unreachable: an empty hand short circuits to NONE, and NONE is not
            // a weapon, so `chargingFist()` was dead code that three rounds of playtesting
            // reported as "the fist does not animate". A bare hand is a weapon in this design --
            // WeaponStats.of already answers UNARMED for it, and the fists are the fastest thing
            // in the game.
            return ViewModelDefaults.of(WeaponProfile.UNARMED);
        }
        ViewModelProfile own = stack.get(ModComponents.VIEWMODEL_PROFILE.get());
        if (cachedProfile != null
                && cachedItem == stack.getItem()
                && cachedComponent == own) {
            return cachedProfile;
        }
        ViewModelProfile resolved = own != null
                ? own
                : ViewModelDefaults.of(WeaponStats.of(stack).profile());
        cachedItem = stack.getItem();
        cachedComponent = own;
        cachedProfile = resolved;
        return resolved;
    }

    /**
     * One line that answers "is the viewmodel running at all", for {@code /cc viewmodel}.
     *
     * <p>Three rounds of playtesting were spent changing numbers while the real question was
     * whether the pipeline ran at all, and neither of us could see it. Everything here is
     * knowable from a single command, printed while the player is mid charge, so the next bug
     * report about "nothing happens" comes with the answer attached instead of with a guess.</p>
     *
     * <p>The order matters: the profile first, because an empty profile explains every other
     * symptom at once. The composed offset last, because that is the thing that actually moves
     * the item and the only number that proves the seam is live.</p>
     */
    public static String debug() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return "sin jugador";
        }
        ItemStack stack = player.getMainHandItem();
        ViewModelProfile profile = resolve(stack);
        ViewModelInput frame = input(PARTIAL_TICK.get());
        Offset offset = profile.evaluate(frame);

        return String.format(
                "item=%s perfil=%s vacio=%s ownsSwing=%s ESTADO=%s | windup=%.2f strike=%.2f "
                        + "recover=%.2f rotura=%.2f guard=%.2f holding=%s carga=%.2f | "
                        + "offset=(%.3f, %.3f, %.3f | %.1f, %.1f, %.1f) cero=%s",
                stack.getItem().getDescription().getString(),
                profile == ViewModelProfile.NONE ? "NONE" : "ok",
                profile.isEmpty(),
                profile.ownsSwing(),
                com.cortes.combat.viewmodel.ViewModelState.of(frame).label(),
                frame.windup(), frame.strike(), frame.recover(),
                frame.guardBreak(), frame.guard(),
                frame.holding(), currentCharge(),
                offset.x(), offset.y(), offset.z(),
                offset.pitch(), offset.yaw(), offset.roll(),
                offset.isZero());
    }

    /**
     * Translate, then rotate, all in one matrix.
     *
     * <p>The order is the vanilla one, because the numbers in a profile are written against
     * vanilla's frame: item transforms translate first and rotate about the item's own origin
     * afterwards, so a pitch of 40 degrees tips the blade about its grip rather than swinging it
     * around the camera.</p>
     */
    private static void apply(com.mojang.blaze3d.vertex.PoseStack pose, Offset offset) {
        if (offset.x() != 0.0 || offset.y() != 0.0 || offset.z() != 0.0) {
            pose.translate(offset.x(), offset.y(), offset.z());
        }
        if (offset.pitch() == 0.0 && offset.yaw() == 0.0 && offset.roll() == 0.0) {
            return;
        }
        SCRATCH.identity()
                .rotateX((float) offset.pitch() * DEGREES_TO_RADIANS)
                .rotateY((float) offset.yaw() * DEGREES_TO_RADIANS)
                .rotateZ((float) offset.roll() * DEGREES_TO_RADIANS);
        pose.mulPose(SCRATCH);
    }
}
