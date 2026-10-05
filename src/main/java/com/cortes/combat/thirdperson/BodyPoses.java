package com.cortes.combat.thirdperson;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.WeaponProfile;

/**
 * What a weapon does to the body holding it, in degrees.
 *
 * <h2>The one convention, and where it comes from</h2>
 *
 * <p><b>Negative {@code xRot} is the arm coming forward. That is read out of vanilla, not
 * remembered.</b> {@code HumanoidModel#setupAttackAnimation} is the ground truth for all three
 * axes and it is unambiguous:
 * {@code modelpart.xRot -= f1 * 1.2F + f2} -- a swing is <b>negative</b> and peaks at 68.8 degrees,
 * because vanilla's own {@code f} is {@code 1 - (1 - attackTime)^4}, a whip curve that is slow off
 * the mark and fast through the middle. {@code poseRightArm}'s {@code ITEM} case,
 * {@code xRot * 0.5 - PI/10}, is negative too, and that is a player holding a sword.</p>
 *
 * <p>The lateral sign is read from the same method as well: {@code modelpart.zRot = modelpart.zRot
 * + sin(attackTime * PI) * -0.4F}. <b>Vanilla's own swing rolls the arm by up to -22.9 degrees</b>,
 * so a negative roll is the direction a cut travels across the body. Before this was read, the roll
 * was left at zero with a comment saying the sign was a guess -- and a guessed sign on a shoulder
 * puts the shoulder in the wrong place, which is worse than not moving it.</p>
 *
 * <h2>The height a wind-up and a strike agree on</h2>
 *
 * <p><b>{@link #CHARGE_ARM_PITCH} and {@link #STRIKE_ARM_PITCH} are the same number, and that is
 * not a coincidence.</b> They are the height the arm is raised to, and the reason they match is the
 * hand-off: the charge unwinds while the strike ramps, so at the moment the swing starts the arm
 * is already up. If the strike peaked somewhere else, the arm would jump there on one tick and the
 * playtest would call the swing broken -- which is exactly what happened when they were 34 and
 * -46.</p>
 *
 * <p>One height means the swing is a single continuous motion whatever the charge was: the arm goes
 * up to the wind-up, the strike re-asserts the same height rather than reaching for a different
 * one, and it falls to rest as the bump decays. That is "a chop from above, ending at rest", and it
 * is one shape instead of two that have to be reconciled.</p>
 *
 * <p>Pure, so every number here is checked in a unit test rather than by looking at a player.</p>
 */
public final class BodyPoses {

    /**
     * The height the arm is raised to, and the reason it is negative.
     *
     * <p><b>The previous values were all positive and every one of them was reported as "backwards"
     * -- the charge, the guard, and by extension the strike.</b> Positive {@code xRot} is the arm
     * going <em>back</em>: the arm hangs down and rotating the other way takes its lower end behind
     * the body. That is the opposite of a wind-up, and the number that would put it there was 34,
     * which on top of vanilla's own idle of about -18 only moves the arm 16 degrees -- a nudge
     * backwards, which is what "it charges, but towards the back" describes exactly.</p>
     *
     * <p>Negative, and large enough to leave the ground: at 80 degrees on top of the idle the arm
     * is horizontal and the weapon is up and out, which is the posture a person winds up into.</p>
     */
    private static final float CHARGE_ARM_PITCH = -104.0F;

    /** The off hand comes part way, because a two-handed wind-up has both arms in it. */
    private static final float CHARGE_OFF_ARM_PITCH = -52.0F;

    /**
     * The guard, and it is a <em>replacement</em> of the arm's pitch rather than an addition.
     *
     * <p>That is because vanilla's {@code poseBlockingArm} already put the arm at
     * {@code xRot * 0.5 - 0.942} -- 54 degrees forward -- while the player holds the weapon up.
     * Adding to it, or worse replacing it with a positive number, swung the arm from forward-up to
     * hanging-back, which is the second "it is backwards" the playtest reported.</p>
     *
     * <p><b>Seventy four is deliberately past vanilla's own 54.</b> A sword guard holds the blade
     * higher than a shield does, and this arm joint is the only one there is -- there is no elbow
     * to fold -- so the height has to come from here.</p>
     */
    private static final float GUARD_ARM_PITCH = -74.0F;

    /** The off hand barely moves. Two arms converging on the chest is not a stance. */
    private static final float GUARD_OFF_ARM_PITCH = -10.0F;

    /**
     * Five degrees of torso, toward the blade.
     *
     * <p>Asked for by name: "MUY LIGERAMENTE ladeado hacia la espada". Five is nearly nothing on
     * purpose -- this field also carries the walk cycle, the crouch and vanilla's own swing yaw,
     * and an offset big enough to notice lands on top of those instead of beside them, which reads
     * as a lurch rather than as a stance.</p>
     */
    private static final float GUARD_BODY_YAW = 5.0F;

    /**
     * The strike peaks at the wind-up's own height, which is the whole reason the two match.
     *
     * <p><b>Both are additive, and the reason is the hand-off.</b> The charge unwinds while the
     * strike ramps, so the two overlap and their sum is what the arm shows: it reaches the wind-up's
     * height, deepens through the middle of the swing as the strike takes over, and comes down as
     * both decay. A strike that <em>replaced</em> the arm's pitch would write a small number on the
     * first tick of the swing and drop the wind-up on the floor -- the same collapse, in the other
     * direction. Sharing a height is what makes the sum continuous.</p>
     *
     * <p>The bump carries it up and lets it fall, so the arm leaves rest, rises past the horizontal
     * to this, and comes back down to rest as the bump decays. The playtest asked for exactly that
     * shape: "it should start from above in a chop downwards, ending in the resting position".</p>
     */
    private static final float STRIKE_ARM_PITCH = -104.0F;

    /**
     * Fourteen degrees across the body, negative, because that is the direction vanilla swings.
     *
     * <p>Asked for as "slightly lateral so it has some naturalness", and the sign is not a taste
     * call: {@code setupAttackAnimation}'s own {@code zRot += sin(attackTime * PI) * -0.4F} is the
     * lateral component of a cut, and it is negative. Fourteen is about a third of vanilla's own,
     * because vanilla swings flat and this one chops.</p>
     */
    private static final float STRIKE_ARM_ROLL = -14.0F;

    private static final float STRIKE_OFF_ARM_PITCH = -34.0F;

    /**
     * The chop's height, and it is the wind-up's height again on purpose.
     *
     * <p><b>The same number as {@link #CHARGE_ARM_PITCH}, and that is the whole hand-off.</b> The
     * charge is at full when the button comes up and the chop starts at one, so the two meet with
     * no step between them. Change one without the other and the arm jumps that difference on the
     * first tick of every charged swing -- which is what the playtest reported as the release
     * going up, back when the chop was riding the strike's bump instead.</p>
     */
    private static final float CHOP_ARM_PITCH = -140.0F;

    /**
     * Thirty two degrees across, negative, and more than twice the strike's fourteen.
     *
     * <p><b>"More lateral, toward the player's body, diagonal."</b> The direction is vanilla's --
     * {@code setupAttackAnimation} rolls the attacking arm by {@code -0.4} at its peak, and its cut
     * travels diagonally across the body -- and the magnitude is the ask. A chop that only pitchs
     * is a guillotine; the roll is what makes it a swing that ends somewhere other than where it
     * started.</p>
     */
    private static final float CHOP_ARM_ROLL = -54.0F;

    /** The off hand follows at half, as on every other swing. */
    private static final float CHOP_OFF_ARM_PITCH = 0.0F;

    /**
     * The sabre's parry, which is the guard's height and none of the guard's stillness.
     *
     * <p><b>The playtest's "the shotel has no animation for when it parries" was true of the body
     * and not of the viewmodel.</b> The sabre has a parry track there and always did; there was
     * simply nothing behind it in third person, so from behind a parry was an arm doing nothing at
     * all while the weapon in the hand did something.</p>
     *
     * <p><b>Higher than the guard on purpose.</b> A parry is not a stance that is held, it is a
     * blade turned to meet one blow, and the difference reads as height and a little more roll:
     * the weapon is up and across the body rather than up in front of it.</p>
     */
    private static final float PARRY_ARM_PITCH = -96.0F;

    /** Across the body, and negative for the reason the strike's roll is. */
    private static final float PARRY_ARM_ROLL = -22.0F;

    /**
     * A ratio, or nothing.
     *
     * <p><b>And "nothing" for a NaN, which {@code Math.min} and {@code Math.max} do not do.</b>
     * Both propagate it: {@code Math.max(0.0F, Math.min(1.0F, Float.NaN))} is NaN, and a NaN limb
     * angle is not a limb drawn wrong, it is the whole player model failing to render with a stack
     * trace in the log. These ratios come off the network and off attribute values other mods can
     * touch, so "impossible" has to be answered rather than assumed.</p>
     */
    static float clamp01(float value) {
        if (Float.isNaN(value) || value <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }

    private BodyPoses() {
    }

    /**
     * The body while a weapon is charging, and nothing at all while it is not.
     *
     * <p><b>The arms go UP and FORWARD, and that is the bug this rewrite fixes.</b> The previous
     * version was positive, which is the arm going backwards -- and the playtest described exactly
     * that: "now it seems it charges as it should, but in reverse, towards the back".</p>
     *
     * <p>Scaled by the charge, so the pose itself is the readout. The bar under the crosshair is
     * ours; this is the same number said on the body, which is what a player is looking at when
     * they are three seconds into an axe commit with somebody behind them.</p>
     */
    public static BodyPose charging(WeaponProfile profile, ChargeProfile charge,
                                    float chargeRatio) {
        if (charge == null || !charge.canCharge()) {
            return BodyPose.NONE;
        }
        float how = clamp01(chargeRatio);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                CHARGE_ARM_PITCH * how, 0.0F, 0.0F,
                CHARGE_OFF_ARM_PITCH * how, 0.0F, 0.0F,
                0.0F, 0.0F);
    }

    /**
     * The body while covering with a weapon that can.
     *
     * <p><b>Only the weapon arm goes up.</b> The previous version brought the off arm across too,
     * and with no roll to separate them the two arms converged on the chest. The off hand now only
     * comes part of the way up, which is what a person holding a covering stance actually looks
     * like with their other arm.</p>
     */
    public static BodyPose guarding(GuardProfile guard, float howFar) {
        if (guard == null || !guard.canGuard()) {
            return BodyPose.NONE;
        }
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                GUARD_ARM_PITCH * how, 0.0F, 0.0F,
                GUARD_OFF_ARM_PITCH * how, 0.0F, 0.0F,
                0.0F, GUARD_BODY_YAW * how);
    }

    /**
     * The strike, which the first version of this file did not have at all.
     *
     * <p><b>Its absence is why the playtest said "the hit animation is not visible".</b> The local
     * player's vanilla swing is suppressed -- {@code ItemViewModel#ownsSwing} exists so the
     * viewmodel is not drawn on top of the arm's -- and nothing replaced it. Every other player on
     * the server swings normally, from the server's {@code player.swing()}; the local player, in
     * third person, swung with nothing.</p>
     *
     * <p><b>And then it was forty six degrees forward, which is what the second report was: "the
     * normal hit looks really bad, it is like a little forward stab".</b> Forty six on top of
     * vanilla's idle is sixty four, which is a poke. It is now the same height as the wind-up, so
     * the bump lifts the arm and drops it, and the drop is the chop.</p>
     */
    public static BodyPose striking(float howFar) {
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                STRIKE_ARM_PITCH * how, 0.0F, STRIKE_ARM_ROLL * how,
                STRIKE_OFF_ARM_PITCH * how, 0.0F, 0.0F,
                0.0F, 0.0F);
    }

    /**
     * The chop: the normal hit, and the one that only falls.
     *
     * <p><b>Scaled by a number that decays and never rises, which is the whole difference from
     * {@link #striking}.</b> The strike is scaled by a bump -- {@code sin(sqrt(phase) * PI)} -- so
     * the arm starts at rest, rises through the middle and comes back, and the playtest read the
     * rising half as the hit: it starts from below and goes up. The ropera and the dagger sound
     * right doing exactly that, because a thrust goes that way, so this exists rather than the
     * strike being changed.</p>
     *
     * <p>Here the arm is already at the wind-up's height on the first frame and the only thing left
     * is the fall, so what the player sees is a blade coming down and finishing in the resting
     * position -- the two things that were asked for by name.</p>
     */
    public static BodyPose chopping(float howHigh) {
        float how = clamp01(howHigh);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                CHOP_ARM_PITCH * how, 0.0F, CHOP_ARM_ROLL * how,
                CHOP_OFF_ARM_PITCH * how, 0.0F, 0.0F,
                0.0F, 0.0F);
    }
    /**
     * The sabre's parry: the blade up and turned across, for the length of the window.
     *
     * <p>Present because the playtest was right: the viewmodel had a parry and the body had
     * nothing, so from behind the weapon in the hand moved and the arm did not.</p>
     */
    public static BodyPose parrying(float howFar) {
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                PARRY_ARM_PITCH * how, 0.0F, PARRY_ARM_ROLL * how,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F);
    }

    /**
     * The ropera's lunge: the arm out in front and the body turned into it.
     *
     * <p><b>The body turns, and that is the difference from the viewmodel.</b> In first person there
     * is no torso, so the lunge is a blade going forward. Here there is one, and a lunge whose torso
     * stayed square reads as an arm reaching out of a stationary player.</p>
     */
    public static BodyPose lunging(float howFar) {
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                LUNGE_ARM_PITCH * how, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                0.0F, LUNGE_BODY_YAW * how);
    }

    /**
     * The halberd's sweep, and the body carries the arc.
     *
     * <p><b>The arm's roll now carries it too, because the sign has been read.</b> It was zero
     * with a comment saying a wrong sign on a shoulder is worse than not moving it -- true, and the
     * fix was to read {@code setupAttackAnimation} rather than to keep guessing. The torso still
     * turns with it, which is what vanilla's own swing does.</p>
     */
    public static BodyPose sweeping(float howFar) {
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                SWEEP_ARM_PITCH * how, 0.0F, SWEEP_ARM_ROLL * how,
                0.0F, 0.0F, 0.0F,
                0.0F, SWEEP_BODY_YAW * how);
    }

    // **The lunge.** Straight out in front, and the body turned into it.
    private static final float LUNGE_ARM_PITCH = -58.0F;
    private static final float LUNGE_BODY_YAW = -8.0F;

    // **The sweep.** The arm comes up and rolls across, and the torso turns with it.
    private static final float SWEEP_ARM_PITCH = -68.0F;
    private static final float SWEEP_ARM_ROLL = -48.0F;
    private static final float SWEEP_BODY_YAW = 14.0F;
    /**
     * The shouldered aim, and every number in it is read out of vanilla's crossbow.
     *
     * <p><b>{@code AnimationUtils.animateCrossbowHold}, converted.</b> The playtest asked for the
     * rifle to be aimed forward in both arms "parecido a la ballesta", and the ballesta''s third
     * person pose is a method in vanilla with its numbers written down:</p>
     *
     * <pre>
     *   animateCrossbowHold(rightArm, leftArm, head, true)
     *     rightArm.yRot = -0.3F + head.yRot
     *     leftArm.yRot  =  0.6F + head.yRot
     *     rightArm.xRot  = -PI/2 + head.xRot + 0.1F
     *     leftArm.xRot   = -1.5F + head.xRot
     * </pre>
     *
     * <p>In degrees: -17.2 of yaw and -84.3 of pitch on the weapon arm, +34.4 and -85.9 on the
     * other. A player holding a charged crossbow has both arms out at shoulder height, and that is
     * the pose that was asked for.</p>
     *
     * <p><b>Why the pitches are sixteen degrees shallower than vanilla''s.</b> Vanilla
     * <em>writes</em> those numbers onto the arm; this file <em>adds</em> to it, because
     * {@code PlayerModelMixin} sits at the tail of {@code setupAnim} and every other pose here is
     * an offset from the walk cycle. The arm''s own rest is {@code xRot * 0.5 - PI/10} -- about -18
     * degrees for a player standing still holding something -- so -84.3 is reached by adding -66.</p>
     *
     * <p><b>That coupling to vanilla''s -18 is a real debt and it is stated rather than hidden.</b>
     * The alternative was a replace, and the guard has one because {@code poseBlockingArm} had
     * already moved its arm; nothing here has. If a future version changes that idle these three
     * numbers come with it, and the symptom would be an aiming pose a few degrees off horizontal.</p>
     *
     * <p>The yaws need no correction: vanilla''s rest yaw for an item is zero, so adding it and
     * writing it are the same number.</p>
     */
    private static final float AIM_ARM_PITCH = -66.0F;
    private static final float AIM_ARM_YAW = -17.0F;
    private static final float AIM_OFF_ARM_PITCH = -68.0F;
    private static final float AIM_OFF_ARM_YAW = 34.0F;

    /**
     * The reload, and it only ever lowers the weapon.
     *
     * <p><b>Positive, because positive is the way down, and that is the whole reason this file''s
     * "every pitch is negative" rule had to be split.</b> The convention at the top is that negative
     * {@code xRot} is the arm coming <em>forward</em>, so an arm the aim has put out at -66 comes
     * back towards the body''s own rest by adding. The negative rule was not wrong -- it was a rule
     * about poses that <em>raise</em>, and every pose in the file was one until this.</p>
     *
     * <p>The off arm comes down less than the weapon does, which is what a person does: the hand
     * that holds the barrel stays with the barrel and the hand that fetches the ball comes in
     * towards the pouch.</p>
     *
     * <p><b>No roll anywhere.</b> The first person reload carries fourteen degrees of it and it
     * would be easy to copy the number across. A roll sign on a shoulder is a guess whose failure
     * is visible -- the shoulder ends up in the wrong place -- and this file has been bitten by
     * exactly that once, which is why {@link #STRIKE_ARM_ROLL} cites the vanilla line it came from.
     * So the pitch does the work until somebody looks at it and can say which way it leans.</p>
     */
    private static final float RELOAD_ARM_PITCH = 44.0F;
    private static final float RELOAD_OFF_ARM_PITCH = 30.0F;
    private static final float RELOAD_OFF_ARM_YAW = 10.0F;

    /**
     * The bayonet''s share of the body, and it exists because of arithmetic.
     *
     * <p><b>Twelve degrees against the strike''s hundred and four.</b> The aim puts the weapon arm at
     * -66 and this file''s strike peaks at -104, so a left click on a shouldered gun added up to
     * -170: past vertical, on the way to pointing backwards. That is the somersault the
     * {@code PlayerModelMixin} javadoc already documents once, and it arrives the moment a weapon
     * is given a resting posture -- so the pose and the strike have to be chosen together.</p>
     *
     * <p>A weapon held on the shoulder does not get the sword''s strike. It gets a poke, which is
     * what its viewmodel track has always been: forward a little, and still pointed where it was
     * aimed.</p>
     */
    private static final float BAYONET_ARM_PITCH = -12.0F;

    /**
     * The body holding a shouldered weapon: both arms out, level, like a charged crossbow.
     *
     * @param howFar 0..1, and the arcabuz passes one -- this is a posture, not a motion
     */
    public static BodyPose aiming(float howFar) {
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                AIM_ARM_PITCH * how, AIM_ARM_YAW * how, 0.0F,
                AIM_OFF_ARM_PITCH * how, AIM_OFF_ARM_YAW * how, 0.0F,
                0.0F, 0.0F);
    }

    /**
     * The body bringing a shouldered weapon down to reload: the muzzle off the sight line, the off
     * hand coming in.
     *
     * <p>Additive on top of {@link #aiming}, which is why these numbers are positive and small --
     * they are a displacement from a pose already out at shoulder height, not an absolute.</p>
     */
    public static BodyPose reloading(float howFar) {
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                RELOAD_ARM_PITCH * how, 0.0F, 0.0F,
                RELOAD_OFF_ARM_PITCH * how, RELOAD_OFF_ARM_YAW * how, 0.0F,
                0.0F, 0.0F);
    }

    /**
     * The bayonet''s share of the body: a poke, not a swing.
     *
     * @see #BAYONET_ARM_PITCH
     */
    public static BodyPose bayonet(float howFar) {
        float how = clamp01(howFar);
        if (how <= 0.0F) {
            return BodyPose.NONE;
        }
        return new BodyPose(
                BAYONET_ARM_PITCH * how, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F);
    }
}