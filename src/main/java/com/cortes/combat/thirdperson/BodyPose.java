package com.cortes.combat.thirdperson;

import com.cortes.combat.core.weapon.WeaponProfile;

/**
 * One limb, in degrees, as the player model wants it.
 *
 * <p><b>Zero is vanilla's pose, and every number here is an offset from it.</b> That is the whole
 * design and it is the reason the record is this small: {@code HumanoidModel#setupAnim} has
 * already written the walk cycle, the head look and the attack swing by the time we get to add to
 * it, so anything we set is added on top and zero has to mean "do not touch". A field whose
 * resting value was something else would need a special case every time the pose was read.</p>
 *
 * <p>Degrees and not a {@code Quaternionf} because the model takes degrees, and building a
 * quaternion per limb per frame to hand back three floats would be three allocations a frame for
 * nothing.</p>
 *
 * <p>Pure: no Minecraft, no blaze3d, no joml.</p>
 */
public record BodyPose(float rightArmPitch, float rightArmYaw, float rightArmRoll,
                       float leftArmPitch, float leftArmYaw, float leftArmRoll,
                       float bodyPitch, float bodyYaw) {

    /** The model exactly as vanilla drew it. What every pose starts from and most frames are. */
    public static final BodyPose NONE = new BodyPose(0, 0, 0, 0, 0, 0, 0, 0);

    /**
     * Whether this pose asks for nothing.
     *
     * <p><b>Written out rather than {@code equals(NONE)}, because of negative zero.</b> A record's
     * generated {@code equals} compares floats with {@code Float.compare}, and
     * {@code Float.compare(-0.0f, 0.0f)} is <em>not</em> zero. So {@code lunge(1.0).times(0.0f)} is
     * a pose that asks for nothing at all and did not compare equal to the pose that asks for
     * nothing at all, and the mixin would have written eight negative zeros into a model instead of
     * returning early.</p>
     *
     * <p>The {@code ==} here treats {@code -0.0f == 0.0f} as true, which is the answer this
     * question wants: the mixin only needs to know whether there is anything to add.</p>
     */
/**
 * Halfway to another pose.
 *
 * <p><b>This is the whole reason the pose is a record and not written straight into the model.</b>
 * The state advances on the tick and the screen draws sixty times a second, so the frame in
 * between has to be interpolated -- exactly as the viewmodel's {@code ViewModelInput} already is.
 * Without it the torso moves at twenty hertz while everything else moves at sixty, and a swing
 * reads as a flipbook.</p>
 *
 * <p>The first version of the third-person code ticked the pose and the mixin read the same
 * static value every frame. The playtest called it "no interpolation, it goes in raw frames",
 * which is what a twenty hertz animation inside a sixty frames a second renderer looks like.</p>
 */
public BodyPose lerp(BodyPose other, float t) {
    if (other == null || other == this) {
        return this;
    }
    if (Float.isNaN(t)) {
        return this;
    }
    float k = Math.max(0.0F, Math.min(1.0F, t));
    return new BodyPose(
            rightArmPitch + (other.rightArmPitch - rightArmPitch) * k,
            rightArmYaw + (other.rightArmYaw - rightArmYaw) * k,
            rightArmRoll + (other.rightArmRoll - rightArmRoll) * k,
            leftArmPitch + (other.leftArmPitch - leftArmPitch) * k,
            leftArmYaw + (other.leftArmYaw - leftArmYaw) * k,
            leftArmRoll + (other.leftArmRoll - leftArmRoll) * k,
            bodyPitch + (other.bodyPitch - bodyPitch) * k,
            bodyYaw + (other.bodyYaw - bodyYaw) * k);
    }

    public boolean isZero() {
        return rightArmPitch == 0.0F && rightArmYaw == 0.0F && rightArmRoll == 0.0F
                && leftArmPitch == 0.0F && leftArmYaw == 0.0F && leftArmRoll == 0.0F
                && bodyPitch == 0.0F && bodyYaw == 0.0F;
    }

    /** Summed, for a pose made of several pieces. */
    public BodyPose plus(BodyPose other) {
        return new BodyPose(
                rightArmPitch + other.rightArmPitch,
                rightArmYaw + other.rightArmYaw,
                rightArmRoll + other.rightArmRoll,
                leftArmPitch + other.leftArmPitch,
                leftArmYaw + other.leftArmYaw,
                leftArmRoll + other.leftArmRoll,
                bodyPitch + other.bodyPitch,
                bodyYaw + other.bodyYaw);
    }

    public BodyPose times(float factor) {
        return new BodyPose(
                rightArmPitch * factor, rightArmYaw * factor, rightArmRoll * factor,
                leftArmPitch * factor, leftArmYaw * factor, leftArmRoll * factor,
                bodyPitch * factor, bodyYaw * factor);
    }
}