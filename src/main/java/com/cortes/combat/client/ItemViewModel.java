package com.cortes.combat.client;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.registry.ModComponents;
import com.cortes.combat.viewmodel.Offset;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelInput;
import com.cortes.combat.viewmodel.ViewModelMotion;
import com.cortes.combat.viewmodel.ViewModelProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Quaternionf;

/**
 * The one place the viewmodel library touches the game.
 *
 * <p>Everything else in {@code com.cortes.combat.viewmodel} is plain Java that a test can load
 * on its own, and keeping the seam exactly here is what makes that possible: a profile is data,
 * the maths is a function of data, and the only thing left for Minecraft is "read four floats,
 * add them to a pose". The class that does the maths could be lifted into a jar on its own.</p>
 *
 * <h2>Where in the pipeline this lands</h2>
 *
 * <p>NeoForge fires {@link RenderHandEvent} from
 * {@code ClientHooks.renderSpecificFirstPersonHand}, which {@code ItemInHandRenderer} calls from
 * {@code renderHandsWithItems} <em>before</em> {@code renderArmWithItem}. Verified against the
 * 1.21.1 bytecode, and the order is the useful one:</p>
 *
 * <pre>
 *   view bobbing  ->  [here]  ->  arm transforms  ->  item model transform  ->  draw
 * </pre>
 *
 * <p>So a profile composes <b>around</b> everything vanilla does, rather than being overwritten
 * by it. Nothing in the vanilla chain is replaced and a profile that evaluates to zero leaves the
 * item exactly where vanilla put it, which is what makes it safe to attach one to an item and
 * have it be wrong only while the weapon is in use.</p>
 *
 * <p>It is a public NeoForge event rather than a mixin, which means the core of this feature
 * needs no bytecode manipulation at all. The only mixin the project still needs for the viewmodel
 * is none.</p>
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

    // Resolution is cached on two reference compares rather than read per frame. The item
    // reference catches a swap, and the component reference catches a mod rewriting the profile
    // on the same item, which is rare enough that it should not happen and cheap enough that it
    // does not matter if it does.
    private static Item cachedItem;
    private static ViewModelProfile cachedComponent;
    private static ViewModelProfile cachedProfile;

    private ItemViewModel() {
    }

    /** One client tick. Advances the swing state. */
    public static void tick(boolean holding, float chargeRatio) {
        MOTION.tick(holding, chargeRatio);
    }

    /**
     * The strike just resolved on the client.
     *
     * @param charge how charged the swing was, 0 for a tap. Passed in rather than read from the
     *               motion so the caller can hand over the same classified value the server will
     *               use, and the motion the player watches agrees with the damage they get
     */
    public static void onStrike(float charge) {
        MOTION.onStrike(charge);
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

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        // Main hand only, and not because the event cannot fire for the off hand. The channels
        // describe one weapon's swing: WINDUP is the main hand's charge, and applying that to
        // the shield in the off hand would move the shield when the sword is charged. An off
        // hand profile needs its own inputs, and that is a later step, not a guess now.
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (Minecraft.getInstance().player == null) {
            return;
        }
        ViewModelProfile profile = resolve(event.getItemStack());
        if (profile.isEmpty()) {
            return;
        }
        Offset offset = profile.evaluate(input(event.getPartialTick()));
        if (offset.isZero()) {
            return;
        }
        apply(event.getPoseStack(), offset);
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
        return MOTION.snapshot(partialTick, cooldownRunning(), ClientCombatState.guardHeld());
    }

    /**
     * The charge a swing is currently at, whether it is being wound up or following through.
     *
     * <p>One value for the FOV and one for the viewmodel, because they have to agree: a FOV that
     * closes for a charge the item did not show, or an item that winds up for a charge the FOV
     * ignored, is worse than having neither.</p>
     *
     * <p>Zero the moment the follow-through ends, which is the fix for the field of view staying
     * closed for the rest of the session. The released charge used to be a value that was set
     * once on the release and never cleared, so everything reading it afterwards read a charge
     * that had already resolved.</p>
     */
    public static float currentCharge() {
        return MOTION.currentCharge();
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

    private static ViewModelInput input() {
        return new ViewModelInput(
                MOTION.windup(),
                MOTION.strike(),
                MOTION.recover(),
                cooldownRunning(),
                SwingInputHandler.holding(),
                0.0F,
                MOTION.seed());
    }

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
        if (stack == null || stack.isEmpty()) {
            return ViewModelProfile.NONE;
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
