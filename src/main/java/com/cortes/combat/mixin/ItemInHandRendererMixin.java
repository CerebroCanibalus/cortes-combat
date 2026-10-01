package com.cortes.combat.mixin;

import com.cortes.combat.client.ItemViewModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the viewmodel in the frame where a blade actually cuts.
 *
 * <p>This mixin exists because of a bug that three rounds of playtesting could not fix, and the
 * reason is worth writing down. The viewmodel used to be applied from {@code RenderHandEvent},
 * on the strength of a claim that the event "composes around everything vanilla does". The claim
 * was checked and it was wrong about the one thing that mattered.</p>
 *
 * <p>Verified against the 1.21.1 bytecode, inside {@code renderHandsWithItems}:</p>
 *
 * <pre>
 *   offset 185  ClientHooks.renderSpecificFirstPersonHand   <- RenderHandEvent fired here
 *   offset 212  renderArmWithItem
 *                  pushPose
 *                  applyItemArmTransform                   <- hand moved to ITEM_POS, offsets 227/523
 *                  applyItemArmAttackTransform             <- vanilla's swing arc, offset 533
 *                  renderItem                              <- offset 622
 * </pre>
 *
 * <p>So the event fires <b>before</b> the hand has been placed. A rotation applied there is a
 * rotation about the camera: the whole arm swings around the eye instead of the blade turning in
 * the player's grip. That is a completely different motion, and it is why the pitch sign was
 * never readable off the screen and why the cut read as a shove no matter how far the numbers
 * were pushed.</p>
 *
 * <p>{@code applyItemArmTransform}'s tail is the right place, and it is the same place vanilla
 * puts its own swing arc one call later, which is the strongest evidence available that it is
 * the item's own frame. Concretely it means:</p>
 *
 * <ul>
 *   <li>the item sits at {@code ITEM_POS_X/Y/Z = 0.56 / -0.52 / -0.72}, so <b>{@code -Z} points
 *       away from the camera</b> and a cut forward is a negative Z;</li>
 *   <li>a rotation pivots on the grip, so the tip of the blade sweeps and the hand does not
 *       move, which is the entire difference between a cut and a shove;</li>
 *   <li>the numbers in a profile are now written against vanilla's frame, so a value copied out
 *       of a decompiled {@code ItemInHandRenderer} lands in a profile and behaves.</li>
 * </ul>
 *
 * <p>It runs once per hand per frame and needs no cancellation. The vanilla arc that follows is
 * already the identity when the profile owns the swing, because {@code swingProgress} is zero:
 * with {@code f6 = sin(sqrt(0) * PI) = 0} every rotation vanilla multiplies by it is zero. Two
 * animations playing at once was a bug this project already paid for once.</p>
 *
 * <p>On {@code RenderArmEvent}, which looks like the obvious tool for animating a hand: it is
 * never fired from {@code ItemInHandRenderer}. The only caller of {@code renderPlayerArm} is the
 * branch taken when the stack is <b>empty</b>, so a held item has no arm to animate and the event
 * cannot help. The hand here is the item.</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    /**
     * The trailing {@code CallbackInfo} is <b>mandatory</b>, even though nothing here cancels.
     *
     * <p>It was removed once, on the reasoning that two DEBUG lines complaining about it were
     * noise in a log that gets read for real problems. They were not noise. They were mixin
     * saying the descriptor was wrong, and the next launch died with:</p>
     *
     * <pre>
     *   InvalidInjectionException: Invalid descriptor ... Expected (...FCallbackInfo;)V
     *   but found (...F)V
     * </pre>
     *
     * <p>Which is the whole lesson in one line: <b>a warning you did not understand is not a
     * warning you can delete.</b> The two lines were DEBUG rather than WARN, and that is exactly
     * why they were easy to skim past. Dropping a parameter from a signature to quiet a message
     * is not the same operation as fixing what the message is about, and the message was right
     * both times.</p>
     */
    /**
     * One seam per hand per frame, at the call site rather than at a method tail.
     *
     * <p><b>Why not {@code @At("TAIL")} on {@code applyItemArmTransform}.</b> That method is
     * called <b>twice</b> for a single held item when the player is using it. Read off the
     * 1.21.1 bytecode inside {@code renderArmWithItem}:</p>
     *
     * <pre>
     *   227: applyItemArmTransform        inside the isUsingItem() branch
     *         translate / rotateX / rotateY / rotateZ    vanilla's guard pose, 235-301
     *   523: applyItemArmTransform        the general path, which the guard branch falls into
     *   533: applyItemArmAttackTransform
     *   622: renderItem
     * </pre>
     *
     * <p>A tail injection fires at both, so the profile's offset was applied twice per frame
     * whenever anything was being used. It went unnoticed because every profile whose item can
     * be <em>used</em> was empty: the shield, which vanilla animates itself. The first profile
     * with a track that is live while the button is held -- the sword guard -- would have made it
     * visible immediately, and the symptom would have been "the sword teleports into guard".</p>
     *
     * <p>The call site is the same point in space: the tail of {@code applyItemArmTransform} and
     * the call to {@code applyItemArmAttackTransform} are one statement apart in the common path,
     * and the second is the frame vanilla puts its own swing arc in. One invocation, one arm, one
     * frame.</p>
     */
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;"
                    + "applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/entity/HumanoidArm;F)V",
            shift = At.Shift.BEFORE))
    private void cortescombat$viewmodelInTheItemsOwnFrame(
            AbstractClientPlayer player, float equipProgress, float partialTick,
            InteractionHand hand, float swingProgress, ItemStack stack,
            float equipProgressAnimation, PoseStack pose,
            MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        logSeam("item");
        ItemViewModel.applyInHand(pose, hand == InteractionHand.MAIN_HAND
                ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
    }

    /**
     * The same thing for a <b>bare hand</b>, and it needed two separate corrections to find.
     *
     * <p><b>It is a different method.</b> {@code applyItemArmTransform} is not called when the
     * stack is empty. Verified against the 1.21.1 bytecode, inside {@code renderArmWithItem}:</p>
     *
     * <pre>
     *   50: ItemStack.isEmpty()
     *   53: ifeq 87              -&gt; not empty: the item path, which reaches the transform
     *   81: renderPlayerArm(...)  -&gt; empty: draws the arm and jumps to the end
     *   84: goto 1518
     * </pre>
     *
     * <p>So the seam above cannot fire for a fist, and no amount of fixing {@code resolve()}
     * would have made one move.</p>
     *
     * <p><b>And it cannot go at the tail of this one either.</b> The first version of this
     * injectable used {@code @At("TAIL")} and the fist got <em>worse</em>: the arm stopped
     * animating altogether. The log said the seam was live and the profile was fine, and both were
     * true, because {@code renderPlayerArm} draws the arm itself:</p>
     *
     * <pre>
     *   260: mulPose      the last of the arm's own rotations
     *   272: translate    the last positioning
     *   281: getRenderer
     *   301: renderRightHand   &lt;-- the model is submitted here
     *   314: renderLeftHand
     * </pre>
     *
     * <p>A pose change at the tail lands after the vertices have been written to the buffer, so
     * it transforms nothing at all. And because the profile owns the swing, vanilla's arm swing
     * was suppressed as well, which is why an ordinary punch went from animating to standing
     * still: the first version of this removed the only animation that was working and replaced
     * it with one that was drawn off screen.</p>
     *
     * <p>So it goes immediately before {@code getRenderer}, which is after every transform
     * vanilla applies to the arm and before the model exists. The item case gets this for free
     * because positioning and drawing are separate methods there; here they are one, and the
     * injection point has to name the draw rather than the end.</p>
     */
    @Inject(method = "renderPlayerArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;"
                    + "getRenderer(Lnet/minecraft/world/entity/Entity;)"
                    + "Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            shift = At.Shift.BEFORE))
    private void cortescombat$viewmodelOnTheBareArm(
            PoseStack pose, MultiBufferSource buffers, int packedLight,
            float partialTick, float swingProgress, HumanoidArm arm, CallbackInfo ci) {
        logSeam("arm");
        ItemViewModel.applyToArm(pose, arm);
    }

    /** One line the first time each seam runs, so "is it reached" is never a guess again. */
    private static void logSeam(String which) {
        long n = which.equals("arm")
                ? ItemViewModel.ARM_FRAMES.incrementAndGet()
                : ItemViewModel.FRAMES.incrementAndGet();
        if (n == 1L) {
            org.slf4j.LoggerFactory.getLogger("cortescombat").info(
                    "[viewmodel] seam live (" + which + ")");
        }
    }
}
