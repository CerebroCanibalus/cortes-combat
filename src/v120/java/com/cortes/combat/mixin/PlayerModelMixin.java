package com.cortes.combat.mixin;

import com.cortes.combat.thirdperson.BodyPose;
import com.cortes.combat.thirdperson.BodyPoseDispatcher;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where a third-person pose gets added to a player, after vanilla has drawn the rest of it.
 *
 * * <p><b>Why a mixin and not {@code RenderPlayerEvent.Pre}.</b> Pre is the only third-person
 * event NeoForge has, and it fires <em>before</em> {@code PlayerRenderer#render} calls
 * {@code model.setupAnim(...)}. Anything written to the model's arm angles there is overwritten a
 * few lines later by the walk cycle, the head look and vanilla's own attack swing. There is no
 * {@code ModelEvent.Pre} in 1.21.1; the four {@code ModelEvent} values are all about the baker,
 * not the pose.</p>
 *
 * * <p>So the injection is at TAIL of {@code setupAnim} and it <em>adds</em>: vanilla's numbers are
 * already in place and every degree here is an offset from them. That is the narrow end of what a
 * mixin can be -- one method, one end, arithmetic added to fields another class wrote -- and the
 * project's rule against rewriting half a class is not in tension with it.</p>
 *
 * * <p><b>The pose is not read from the player.</b> It comes from a static that the client fills
 * during {@code RenderPlayerEvent.Pre}, because a mixin that called into our own renderer state
 * would be a second path to the same value and the two would eventually disagree.</p>
 *
 * <p><b>The partial tick is read here, not passed in.</b> {@code setupAnim} does not have one --
 * its six parameters are the entity and five floats about the walk cycle -- and it is called once
 * per frame, so this is the only place in the method that can interpolate. Reading the same timer
 * {@code ItemViewModel} reads is what makes the body and the weapon agree on where they are
 * between two ticks. Without it the pose advanced at twenty hertz inside a sixty frames a second
 * renderer and the playtest called it "no interpolation, it goes in raw frames".</p>
 *
 * <p><b>Public, and declared in this package.</b> Two things were wrong before the client would
 * open, and neither of them was anything the build could see.</p>
 *
 * <p>It was <b>package private</b>. Mixin loads the class by name through the classloader and reads
 * its methods reflectively, and a package private class is not reachable that way.</p>
 *
 * <p>And its <b>{@code package} line said {@code thirdperson}</b>, because it was written next to
 * the class it calls. javac does not require a source file's directory to match its package, so it
 * compiled cleanly and put the class in {@code com/cortes/combat/thirdperson/} -- and the mixin
 * config asks for {@code com.cortes.combat.mixin.PlayerModelMixin}. The build was green, 574 tests
 * were green, and the launch died with {@code ClassNotFoundException: The specified mixin
 * 'PlayerModelMixin' was not found}, a message that names the file and says nothing about either
 * cause.</p>
 *
 * <p>So: a mixin is wrong in three ways the compiler cannot see -- wrong signature, wrong
 * visibility, wrong package -- and the first two launches each cost one of them.</p>
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    /**
     * **Cancels vanilla's own swing, at HEAD, before {@code setupAnim} reads it.**
     *
     * <p>This is the bug behind "when I hit it does a somersault forward with the hand, several
     * times". The order is the whole story and it is not the order one would guess:</p>
     *
     * <ul>
     *   <li>{@code LivingEntityRenderer#render} does {@code model.attackTime = getAttackAnim(...)}
     *   <b>before</b> calling {@code setupAnim}.</li>
     *   <li>{@code setupAnim} reads {@code this.attackTime} inside {@code setupAttackAnimation},
     *   which moves the arm by {@code -f1 * 1.2} -- <b>up to 69 degrees forward</b> -- and turns the
     *   body by up to 11.</li>
     *   <li>My injection is at TAIL, so it was <b>adding 46 degrees on top of those 69</b>. A
     *   hundred and fifteen degrees of forward arm is the somersault, and it repeated for every
     *   frame of vanilla's six tick swing.</li>
     * </ul>
     *
     * <p>So the swing is not something to add to. <b>If this profile draws its own swing, vanilla
     * must not draw one.</b> Zeroing {@code attackTime} at HEAD means
     * {@code setupAttackAnimation} sees nothing to do, and the arm is left on the walk cycle for the
     * TAIL injection to build on.</p>
     *
     * <p>HEAD rather than TAIL because there is no other choice: the field is assigned by the
     * renderer before this method is called, and {@code setupAnim} does not recompute it.</p>
     */
    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void cortescombat$cancelVanillaSwing(net.minecraft.world.entity.LivingEntity entity,
                                                  float limbSwing, float limbSwingAmount,
                                                  float ageInTicks, float netHeadYaw,
                                                  float headPitch, CallbackInfo callback) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        // `ownsSwing` is the gate and not "is this the local player": the local player's swing is
        // synced by the SERVER's `player.swing`, so a vanilla axe with no viewmodel profile still
        // arrives here swinging, and it must keep swinging.
        if (BodyPoseDispatcher.ownsSwing(player)) {
            ((net.minecraft.client.model.EntityModel<?>) (Object) this).attackTime = 0.0F;
        }
    }

    /**
     * Six parameters, and that is not negotiable.
     *
     * <p><b>The seventh one was invented.</b> The first version of this injection took a
     * {@code partialTick} between {@code limbSwingAmount} and {@code ageInTicks}, because every
     * other pose system in this project is interpolated with one and it felt like the obvious
     * place to get it from. {@code PlayerModel#setupAnim} does not have one: it is
     * {@code (T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
     * float headPitch)}, six of them, and {@code T} erases to {@code LivingEntity}. The project
     * compiled perfectly -- <b>a mixin's method signature is not checked by javac, it is resolved
     * when the mixin is applied at launch</b> -- and the client would have died on startup with
     * "no such method" instead of opening.</p>
     *
     * <p>So: every signature a mixin names has to be read out of the sources first. {@code javap}
     * confirms it exists, reading the method is what confirms its shape.</p>
     */
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void cortescombat$applyBodyPose(net.minecraft.world.entity.LivingEntity entity,
                                             float limbSwing, float limbSwingAmount,
                                             float ageInTicks, float netHeadYaw,
                                             float headPitch, CallbackInfo callback) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        BodyPose pose = BodyPoseDispatcher.snapshot(player,
                net.minecraft.client.Minecraft.getInstance().getFrameTime());
        if (pose.isZero()) {
            return;
        }
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;

        // **Only the guard replaces the arm's pitch. Everything else adds to it.**
        //
        // The guard because vanilla's `poseBlockingArm` already wrote the arm at about -54 degrees
        // while the player holds the weapon up, and ours has to displace that rather than sit on
        // top of it -- which is how the second "it is backwards" happened, a replace with a
        // positive number on a pose vanilla had already pulled forward.
        //
        // The strike and the charge add, and the reason is the hand-off: the charge unwinds while
        // the strike ramps, so their sum is one continuous arc. A strike that wrote the arm instead
        // would put a small number on the first tick of the swing and drop the wind-up on the
        // floor. They share a height precisely so that adding them is continuous.
        float guard = BodyPoseDispatcher.guardAmount(player);
        boolean replaceWeaponArm = guard > 0.0F;

        // **The main hand is whichever arm vanilla decided, not a hardcoded right.**
        //
        // `HumanoidArm.getOpposite` is how the model itself resolves it, and a hardcoded right
        // would put the guard on the wrong shoulder for every left-handed player in the game.
        HumanoidArm main = player.getMainArm();
        HumanoidArm off = main.getOpposite();

        // **The sleeve goes with the arm, and not because it is neat.**
        //
        // `PlayerModel` carries a second `ModelPart` per limb for the jacket layer -- `rightSleeve`
        // and `leftSleeve` -- and vanilla's `HumanoidModel#setupAnim` copies the bare arm onto it
        // and then keeps going. The playtest's "the second skin layer does not follow the player's
        // animation" is that copy happening before our TAIL injection: move the arm here and the
        // sleeve stays where vanilla's walk cycle left it, which is a bare arm stabbing through a
        // sleeve that is still hanging at the player's side.
        //
        // `ModelPart#copyFrom` is the same call vanilla makes, so doing it here is not a second
        // mechanism -- it is the same one, run after our angles instead of before them.
        ModelPart weaponArm = main == HumanoidArm.RIGHT ? self.rightArm : self.leftArm;
        ModelPart offArm = main == HumanoidArm.RIGHT ? self.leftArm : self.rightArm;
        ModelPart weaponSleeve = main == HumanoidArm.RIGHT ? self.rightSleeve : self.leftSleeve;
        ModelPart offSleeve = main == HumanoidArm.RIGHT ? self.leftSleeve : self.rightSleeve;

        apply(weaponArm, pose, replaceWeaponArm);
        applyOff(offArm, pose);
        weaponSleeve.copyFrom(weaponArm);
        offSleeve.copyFrom(offArm);

        self.body.xRot += pose.bodyPitch() * DEGREES_TO_RADIANS;
        self.body.yRot += pose.bodyYaw() * DEGREES_TO_RADIANS;
    }

    /**
     * One arm's three angles, in degrees, onto a model part in radians.
     *
     * <p><b>The pitch is written or added, and the other two are always added.</b> Yaw and roll are
     * offsets onto an idle arm that is not doing anything else, and there is no vanilla pose to
     * displace -- {@code poseBlockingArm} writes a yaw of its own, which is precisely the one thing
     * the guard must sit on top of rather than replace.</p>
     *
     * <p>An earlier version added the degrees straight to {@code xRot}. An eighty degree swing added
     * as 80.0 to a field whose unit is radians is eighty <em>radians</em>, which is more than twelve
     * turns -- so the model wrapped and the arm went somewhere no vocabulary describes. It is why
     * {@link BodyPose} is documented in degrees and this method exists.</p>
     */
    private static void apply(ModelPart arm, BodyPose pose, boolean replace) {
        arm.xRot = poseFor(arm.xRot, pose.rightArmPitch(), replace);
        arm.yRot += pose.rightArmYaw() * DEGREES_TO_RADIANS;
        arm.zRot += pose.rightArmRoll() * DEGREES_TO_RADIANS;
    }

    /**
     * The off arm, which only ever adds and only ever in pitch.
     *
     * <p>A second method rather than a flag on {@link #apply} because the two arms read different
     * fields of the record -- {@code leftArmPitch} for this one -- and threading that through a
     * boolean would make the call site read {@code apply(arm, pose, false, true)}, which is a
     * question with no answer at the point where it is asked.</p>
     */
    private static void applyOff(ModelPart arm, BodyPose pose) {
        arm.xRot += pose.leftArmPitch() * DEGREES_TO_RADIANS;
        arm.yRot += pose.leftArmYaw() * DEGREES_TO_RADIANS;
        arm.zRot += pose.leftArmRoll() * DEGREES_TO_RADIANS;
    }

    private static float poseFor(float current, float degrees, boolean replace) {
        float radians = degrees * DEGREES_TO_RADIANS;
        return replace ? radians : current + radians;
    }

    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0D);
}