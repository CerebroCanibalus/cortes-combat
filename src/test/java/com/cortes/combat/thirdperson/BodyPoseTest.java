package com.cortes.combat.thirdperson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The body's poses, the one convention they hang off, and how far they are allowed to go.
 *
 * <h2>This file used to assert the bugs</h2>
 *
 * <p><b>Eleven of its assertions failed when the poses were fixed, and every one of them was
 * pinning the thing the playtest had just reported as wrong.</b> The worst was called "a strike
 * goes negative and a wind-up goes positive" -- it asserted that a wind-up raises the arm by
 * rotating it <em>backwards</em>, which is the opposite of a wind-up, and it was green while the
 * playtest reported "now it charges as it should, but in reverse, towards the back".</p>
 *
 * <p>A test is supposed to be the memory of a decision. When the decision turns out to have been
 * wrong, the memory has to be rewritten rather than worked around, and the new one has to say what
 * the rule <em>is</em> rather than what the number happens to be.</p>
 *
 * <h2>The three rules now</h2>
 *
 * <ul>
 *   <li><b>Every arm pitch is negative.</b> Negative {@code xRot} is the arm coming forward and up,
 *   read out of {@code setupAttackAnimation}'s {@code modelpart.xRot -= f1 * 1.2F} and out of
 *   {@code poseRightArm}'s {@code ITEM} case. Positive is the arm going <em>backwards</em>. There is
 *   no pose here that is positive, and that is the single assertion that would have caught the two
 *   "it is backwards" reports.</li>
 *   <li><b>A wind-up and a strike peak at the same height.</b> They are two halves of one motion and
 *   the charge unwinds while the strike ramps, so if they disagreed the arm would jump on one tick
 *   of every swing.</li>
 *   <li><b>The torso is untouched except for five degrees of guard lean.</b> This is the third round
 *   of that rule and the playtest named it: "the torso is what we should touch least".</li>
 * </ul>
 */
class BodyPoseTest {

    private static final ChargeProfile WARHAMMER_CHARGE = new ChargeProfile(true, 0.6F, 32);

    /**
     * How far past horizontal the weapon arm may go, in degrees.
     *
     * <p><b>And it replaced a sixty degree ceiling that was protecting the wrong thing.</b> The old
     * one existed because the poses were offsets added to vanilla's idle of about -18, and 60 on
     * top of that put the arm near horizontal -- which is exactly vanilla's own swing peak of 68.8,
     * so the ceiling was forbidding the motion the game already ships. The complaint was "the hit
     * looks like a little forward stab", and the ceiling is why it was one.</p>
     *
     * <p>What this bounds instead is the thing that actually breaks: past roughly 150 degrees the
     * arm has swung over the top and is on its way to pointing backwards, which no pose here means
     * to do. The deepest is the parry at 96.</p>
     */
    private static final float ARM_PITCH_CEILING = 150.0F;

    /**
     * The ceiling on the two axes whose signs used to be guessed.
     *
     * <p>Still tight, and deliberately so. Yaw and roll are offsets onto an idle arm, they were zero
     * for three rounds because a wrong sign puts a shoulder in the wrong place, and a shoulder in
     * the wrong place is a worse artefact than an arm that does not move. They are no longer zero
     * -- the roll sign was read out of vanilla -- so this is the test that the reading did not also
     * let them run away.</p>
     */
    private static final float LATERAL_CEILING = 55.0F;

    private static BodyPose[] everyPose() {
        return new BodyPose[]{
                BodyPoses.striking(1.0F),
                BodyPoses.parrying(1.0F),
                BodyPoses.lunging(1.0F),
                BodyPoses.sweeping(1.0F),
                BodyPoses.guarding(GuardProfile.SWORD, 1.0F),
                BodyPoses.guarding(GuardProfile.SHIELD, 1.0F),
                BodyPoses.charging(WeaponProfile.WARHAMMER, WARHAMMER_CHARGE, 1.0F),
                BodyPoses.charging(WeaponProfile.AXE, new ChargeProfile(true, 2.0F, 60), 1.0F),
                // **The chop was missing from this list for three rounds**, which is the same failure
                // as the sabre's dead BREAK wearing a different hat: a guard that does not walk the
                // poses cannot guard them. It is the deepest pitch in the file at -140 and nothing
                // here was ever looking at it.
                BodyPoses.chopping(1.0F),
                // And the shouldered gun's three.
                BodyPoses.aiming(1.0F),
                BodyPoses.reloading(1.0F),
                BodyPoses.bayonet(1.0F),
        };
    }

    /**
     * Every arm pitch is negative, and this is the assertion that was missing.
     *
     * <p><b>It is the direct answer to "it charges but in reverse, towards the back" and to "the
     * guard is backwards too".</b> Both were positive. Positive {@code xRot} on an arm that hangs
     * down takes its lower end behind the body, so a positive pose is an arm swinging backwards --
     * which is what a follow-through is and is not what a wind-up or a stance is.</p>
     *
     * <p>There were three positive poses and the playtest reported two of them as backwards, so the
     * third was going to be reported next.</p>
     */
    @Test
    @DisplayName("every arm pitch is negative: forward and up, never backwards")
    void everyArmPitchIsNegative() {
        for (BodyPose pose : everyPose()) {
            for (String which : new String[]{"right arm", "left arm"}) {
                float pitch = which.equals("right arm")
                        ? pose.rightArmPitch() : pose.leftArmPitch();
                // **And the positive branch is a list, not a hole in the rule.**
                //
                // The rule is not "every pitch is negative". The rule is "no pose rotates an arm
                // *backwards*", and negative pitch happens to be the only shape every pose had
                // while they were all raises. The arcabuz's reload is the first pose that lowers an
                // arm, and a lower is positive by arithmetic: the aim has it out at -66 and coming
                // back towards the body's rest means adding.
                //
                // So the assertion splits. A pose that is not on the lowering list must still be
                // negative, and one that is on it must be *smaller than the aim it lowers from* --
                // which is what stops a "lower the weapon" pose from swinging the arm up behind the
                // player's head, which is what the negative rule was actually protecting against.
                boolean lowers = isLowering(pose);
                if (lowers) {
                    assertTrue(pitch < 0.0F || Math.abs(pitch) <= AIM_ARM_PITCH_MAGNITUDE,
                            which + " pitch is " + pitch + " on a lowering pose, which is further than "
                                    + "the aim it lowers from (" + -AIM_ARM_PITCH_MAGNITUDE
                                    + "). A lower that overshoots the aim swings the arm up behind "
                                    + "the head, which is the same failure as a backwards wind-up "
                                    + "with the sign flipped");
                    continue;
                }
                assertTrue(pitch <= 0.0F,
                        which + " pitch is " + pitch + ". Negative is the arm coming forward and up; "
                                + "positive is it going backwards, which is a follow-through and not "
                                + "a wind-up, a stance or a chop. Two poses were positive and the "
                                + "playtest called both of them 'backwards'");
            }
        }
    }

    /** The pitch the aim holds the weapon arm at, read from the pose rather than the constant. */
    private static final float AIM_ARM_PITCH_MAGNITUDE =
            Math.abs(BodyPoses.aiming(1.0F).rightArmPitch());

    /**
     * Whether this pose is one that brings an arm back down.
     *
     * <p>Named by identity against {@link #everyPose()}'s order rather than by a field, because
     * there is no field on a pose that says what it is for -- and a new lowering pose that nobody
     * adds here would be caught by the negative branch instead, which is the correct direction to
     * fail in.</p>
     */
    private static boolean isLowering(BodyPose pose) {
        return pose.equals(BodyPoses.reloading(1.0F));
    }

    /**
     * The wind-up and the strike peak at the same height, so the hand-off is not a jump.
     *
     * <p><b>This is the invariant that came out of the strike complaint rather than out of taste.</b>
     * "It should start from above in a chop downwards, ending in the resting position" only works
     * if the thing above <em>is</em> the wind-up's height -- because the charge unwinds while the
     * strike ramps, so the arm is already up when the swing starts. At 34 and -46 they disagreed by
     * eighty degrees and the arm snapped across that gap on one tick of every swing.</p>
     */
    @Test
    @DisplayName("a wind-up and a strike peak at the same height")
    void theWindUpAndTheStrikeShareAHeight() {
        float windUp = Math.abs(BodyPoses
                .charging(WeaponProfile.WARHAMMER, WARHAMMER_CHARGE, 1.0F).rightArmPitch());
        float strike = Math.abs(BodyPoses.striking(1.0F).rightArmPitch());
        assertEquals(windUp, strike, 0.01F,
                "the charge holds the arm at " + windUp + " degrees and the strike peaks at "
                        + strike + ". The charge unwinds while the strike ramps, so any difference "
                        + "here is a jump on the tick the swing starts -- which is what the arm did "
                        + "when they were 34 and 46");
    }

    /**
     * The strike's peak is above the arm's horizontal and the strike ends at rest.
     *
     * <p>Both halves were asked for by name: "start from above" is past ninety degrees of pitch,
     * and "ending in the resting position" is the bump returning to zero, which the pose already
     * does because it is scaled by the bump.</p>
     */
    /**
     * The poses that can be on screen at the same time have to be added before the ceiling means
     * anything.
     *
     * <p><b>This is the guard with teeth, and the per-pose ceiling above it is not.</b> The ceiling
     * walks one pose at a time, and the somersault was never one pose: the arcabuz aims at -66 and
     * the strike it would have inherited peaks at -104, and -188 is an arm over the player's head.
     * Every individual pose was inside 150. The bug lived in the sum, so the sum is what has to be
     * measured.</p>
     *
     * <p>And the second assertion is the reason the bayonet exists at all, stated as a fact about
     * the numbers rather than as a fact about the code: if someone hands the shouldered weapon the
     * sword's strike back, this fails before the game does.</p>
     */
    @Test
    @DisplayName("the poses that share a screen add up under the ceiling, and the poke is why")
    void thePosesThatShareAScreenAddUpUnderTheCeiling() {
        // **The sum has to clear the ceiling, and the first version of this test did not say
        // which sum.** It asserted the dispatcher *avoided* the sword's strike, which is a true
        // fact about the code and no assertion at all: swapping the bayonet for the strike in
        // `BodyPoses` left this test green with the arm at -126, comfortably inside 150.
        //
        // The assertion that has teeth is the arithmetic itself. Aim at -66 plus this file's strike
        // at -104 is -170: past vertical, past horizontal, the arm pointing down and behind. That
        // is the somersault `PlayerModelMixin` documents, and it is what a shouldered weapon has to
        // be protected from. So the strike is added here on purpose, and the ceiling is what
        // catches it.
        float somersault = BodyPoses.aiming(1.0F).plus(BodyPoses.striking(1.0F)).rightArmPitch();
        assertTrue(Math.abs(somersault) > ARM_PITCH_CEILING,
                "this test is only meaningful while the sword's strike really would throw the arm "
                        + "past the ceiling of " + ARM_PITCH_CEILING + ". Aim plus strike is "
                        + somersault + ", which is inside it -- so either the aim or the strike has "
                        + "moved and the reason this guard exists needs writing down again");

        float aimed = BodyPoses.aiming(1.0F).rightArmPitch();
        float poked = BodyPoses.bayonet(1.0F).rightArmPitch();
        float withThePoke = aimed + poked;
        assertTrue(Math.abs(withThePoke) <= ARM_PITCH_CEILING,
                "the poke is supposed to replace the strike. Aim plus poke is " + withThePoke
                        + ", past the ceiling of " + ARM_PITCH_CEILING + ", so the bayonet has "
                        + "become a second strike");
        assertTrue(Math.abs(withThePoke) < Math.abs(somersault),
                "the poke has to be the smaller of the two motions, or it is not a poke: "
                        + withThePoke + " against " + somersault);
    }

    /**
     * A shouldered weapon's arm stays out in front, and lowering it never takes it past vertical.
     *
     * <p>Two assertions about one number, and the second is the one that matters. Aiming plus
     * reloading is the pose a player spends the whole reload looking at, and if the reload were
     * bigger than the aim it would swing the barrel up and over rather than down.</p>
     */
    @Test
    @DisplayName("a reload lowers the shouldered arm and does not swing it up behind the head")
    void aReloadLowersTheShoulderedArm() {
        float aim = BodyPoses.aiming(1.0F).rightArmPitch();
        float reloading = BodyPoses.reloading(1.0F).rightArmPitch();
        assertTrue(reloading > 0.0F,
                "a reload that lowers the weapon is positive by arithmetic -- the aim has the arm "
                        + "out at " + aim + " and coming back towards the body's rest means adding. "
                        + "This one is " + reloading);
        assertTrue(Math.abs(aim + reloading) < Math.abs(aim),
                "aim plus reload is " + (aim + reloading) + ", which is further from the horizontal "
                        + "than the aim's own " + aim + ". That is a reload that swings the barrel "
                        + "up instead of down");
    }

    @Test
    @DisplayName("the strike peaks above the horizontal and returns to rest")
    void theStrikeIsAChopAndNotAStab() {
        float peak = Math.abs(BodyPoses.striking(1.0F).rightArmPitch());
        assertTrue(peak > 90.0F,
                "the strike peaks at " + peak + " degrees, which is " + (peak > 90
                        ? "above the horizontal" : "a poke")
                        + ". Vanilla's own swing peaks at 68.8, which is a slash and not a chop; "
                        + "'it looks like a little forward stab' is what a value under ninety "
                        + "looks like in third person");

        BodyPose resting = BodyPoses.striking(0.0F);
        assertTrue(resting.isZero(), "a strike at zero of its bump has to be nothing at all, "
                + "because that is the resting position it is supposed to end in");

        // And it really does come down: monotonic in the bump, so no part of the swing reads as a
        // second lift.
        float previous = Float.MAX_VALUE;
        for (int i = 10; i >= 0; i--) {
            float height = Math.abs(BodyPoses.striking(i / 10.0F).rightArmPitch());
            assertTrue(height <= previous + 0.01F,
                    "the arm rose again at " + (i / 10.0F) + " of the bump (" + height
                            + " after " + previous + "). A chop that lifts twice is two jabs");
            previous = height;
        }
    }

    /**
     * The guard replaces vanilla's arm, and past vanilla's own number.
     *
     * <p><b>Past 54 degrees, and 54 is read from {@code poseBlockingArm}:
     * {@code arm.xRot * 0.5 - 0.9424779F}.</b> That is where vanilla leaves the arm while the player
     * holds a weapon up, so a guard that added to it would sit on top of a forward pose and a guard
     * that used a smaller positive number would swing it back to hanging.</p>
     */
    @Test
    @DisplayName("the guard stands on its own and past vanilla's blocking arm")
    void theGuardIsHigherThanVanillasBlockingArm() {
        float guard = BodyPoses.guarding(GuardProfile.SWORD, 1.0F).rightArmPitch();
        assertTrue(guard < -54.0F,
                "the guard is " + guard + " degrees and vanilla's own blocking arm is -54 "
                        + "(`xRot * 0.5 - 0.9424779F`). A sword guard holds the blade higher than a "
                        + "shield does, and this arm joint is the only one there is -- there is no "
                        + "elbow to fold -- so the height has to come from here");
    }

    /**
     * The lateral roll is negative, which is vanilla's own direction for a cut.
     *
     * <p><b>Read, not chosen: {@code setupAttackAnimation} does
     * {@code modelpart.zRot = modelpart.zRot + sin(attackTime * PI) * -0.4F}.</b> Three rounds of
     * this file asserted the roll was zero because the sign was a guess, and a guessed sign on a
     * shoulder is a worse artefact than a shoulder that does not move. That was the right call then;
     * reading the sign is the right call now, and this asserts the reading was right.</p>
     */
    @Test
    @DisplayName("the lateral roll is negative, the direction vanilla cuts")
    void theLateralRollFollowsVanillasDirection() {
        float strikeRoll = BodyPoses.striking(1.0F).rightArmRoll();
        assertTrue(strikeRoll < 0.0F,
                "the strike's roll is " + strikeRoll + ". Vanilla's swing rolls the arm by "
                        + "-0.4 radians at its peak, so negative is the direction a cut travels "
                        + "across the body; positive would put the shoulder on the wrong side");

        assertTrue(Math.abs(strikeRoll) > 0.0F,
                "and it has to be non-zero. 'Slightly lateral so it has some naturalness' was asked "
                        + "for by name, and the roll existed as a field that nothing ever wrote");
    }

    /**
     * Nothing goes past the ceiling, and the two ceilings are not the same number.
     *
     * <p><b>Pitch is allowed to be large and the lateral axes are not.</b> That split is the
     * correction: the old ceiling of sixty was forbidding a motion vanilla itself ships, while
     * saying nothing useful about the two axes that were genuinely unguessed for three rounds.</p>
     */
    @Test
    @DisplayName("no pose throws an arm past a ceiling, and pitch's ceiling is not the lateral one")
    void nothingPassesTheCeiling() {
        for (BodyPose pose : everyPose()) {
            assertTrue(Math.abs(pose.rightArmPitch()) <= ARM_PITCH_CEILING,
                    "arm pitch " + pose.rightArmPitch() + " is past " + ARM_PITCH_CEILING
                            + ". Past that the arm has swung over the top and is on its way to "
                            + "pointing backwards, which no pose here means to do");
            for (float lateral : new float[]{pose.rightArmRoll(), pose.leftArmRoll(),
                    pose.rightArmYaw(), pose.leftArmYaw()}) {
                assertTrue(Math.abs(lateral) <= LATERAL_CEILING,
                        "a lateral angle is " + lateral + ", against a ceiling of "
                                + LATERAL_CEILING + ". These two axes were zero for three rounds "
                                + "because a wrong sign puts a shoulder in the wrong place");
            }
        }
    }

    /**
     * The torso leans five degrees for the guard and not one degree more for anything else.
     *
     * <p>It carries the walk cycle, the crouch, the swim and vanilla's own swing yaw. An offset on
     * it fights all four, and the playtest said it plainly: "the torso is what we should touch
     * least, we should barely touch it". So the guard leans five -- which was asked for by name --
     * and everything else is zero.</p>
     */
    @Test
    @DisplayName("only the guard leans, and it leans five degrees")
    void onlyTheGuardLeans() {
        // **Named, not inferred.** The first version of this test tried to skip "the guard itself"
        // by comparing arm pitches against the guard's, and it let the lunge through -- so the
        // assertion below passed on a pose that does lean, by eight degrees. A list of which poses
        // lean is a fact about the code; a test that guesses which ones lean is a second copy of
        // it that can be wrong in a way nobody sees.
        BodyPose[] square = {
                BodyPoses.striking(1.0F),
                BodyPoses.parrying(1.0F),
                BodyPoses.charging(WeaponProfile.WARHAMMER, WARHAMMER_CHARGE, 1.0F),
                BodyPoses.charging(WeaponProfile.AXE, new ChargeProfile(true, 2.0F, 60), 1.0F),
        };
        for (BodyPose pose : square) {
            assertEquals(0.0, pose.bodyYaw(), 1.0E-9,
                    "body yaw is " + pose.bodyYaw() + ". The torso is the thing we should touch "
                            + "least and it is not the thing that should move when the arm does");
            assertEquals(0.0, pose.bodyPitch(), 1.0E-9,
                    "body pitch is " + pose.bodyPitch() + ", same reason");
        }

        BodyPose guard = BodyPoses.guarding(GuardProfile.SWORD, 1.0F);
        assertEquals(5.0, guard.bodyYaw(), 1.0E-9,
                "the guard leans five degrees toward the blade, which is what the playtest asked "
                        + "for by name: 'MUY LIGERAMENTE ladeado hacia la espada'. Five is nearly "
                        + "nothing on purpose -- this field also carries the walk cycle and the "
                        + "crouch, and a lean big enough to notice lands on top of those instead "
                        + "of beside them, which reads as a lurch and not as a stance");
        assertEquals(0.0, guard.bodyPitch(), 1.0E-9, "and it leans rather than nods");
        assertEquals(guard.bodyYaw(),
                BodyPoses.guarding(GuardProfile.SHIELD, 1.0F).bodyYaw(), 1.0E-9,
                "and it leans the same for either guard profile. The lean is the stance and not the "
                        + "weapon, so a profile cannot opt out of it -- which is also why this "
                        + "assertion belongs here and not in the list of poses that do not lean");

        BodyPose lunge = BodyPoses.lunging(1.0F);
        assertEquals(-8.0, lunge.bodyYaw(), 1.0E-9,
                "the lunge is the other exception: eight degrees, and nothing else. A lunge whose "
                        + "torso stays square reads as an arm reaching out of a stationary player");
        assertEquals(0.0, lunge.bodyPitch(), 1.0E-9, "and no pitch on it. A lean is not a lunge");

        BodyPose sweep = BodyPoses.sweeping(1.0F);
        assertEquals(14.0, sweep.bodyYaw(), 1.0E-9,
                "and the sweep is the third: ten degrees, in the other direction. The arc is "
                        + "horizontal and the torso goes round with it -- which is what vanilla's "
                        + "own swing does, `body.yRot = sin(sqrt(f) * 2PI) * 0.2F`. This was in "
                        + "the list of poses that do not lean and it is not one, and the test was "
                        + "wrong about a fact rather than the code being wrong");
        assertEquals(0.0, sweep.bodyPitch(), 1.0E-9, "and none of the three pitches the torso");
    }

    /**
     * Every pose stays inside half a turn once it is in radians, and that is not a formality.
     *
     * <p><b>The somersault was degrees added to a radians field.</b> The pose is documented in
     * degrees because that is how a weapon designer thinks, and the mixin converts on the way in.
     * It did not: an eighty degree swing added as {@code 80.0} to {@code ModelPart.xRot} is eighty
     * <em>radians</em>, which is more than twelve turns. The arm wrapped and went somewhere no
     * vocabulary describes, once per frame, on the strike and the charge and the guard alike.</p>
     */
    @Test
    @DisplayName("no pose reaches an angle that is impossible in radians")
    void nothingIsImpossibleInRadians() {
        for (BodyPose pose : everyPose()) {
            for (float degrees : new float[]{pose.rightArmPitch(), pose.leftArmPitch(),
                    pose.bodyYaw(), pose.bodyPitch(), pose.rightArmRoll(), pose.leftArmRoll()}) {
                float asRadians = degrees * (float) (Math.PI / 180.0D);
                assertTrue(Math.abs(asRadians) < Math.PI,
                        degrees + " degrees is " + asRadians + " radians, and the mixin adds THAT "
                                + "to a ModelPart whose unit is radians. Adding the degree value "
                                + "directly is what made the arm somersault");
            }
        }
    }

    /**
     * A weapon that cannot charge does not move the body.
     *
     * <p>The ropera and the dagger are the two profiles with {@code ChargeProfile.NONE}, and this
     * is the rule that keeps them from acquiring a wind-up they have no mechanic for.</p>
     */
    @Test
    @DisplayName("a weapon that cannot charge does not move the body")
    void aWeaponThatCannotChargeIsStill() {
        BodyPose still = BodyPoses.charging(WeaponProfile.ROPERA, ChargeProfile.NONE, 1.0F);
        assertTrue(still.isZero(), "the ropera cannot charge and its body moved anyway: " + still);

        BodyPose nothing = BodyPoses.charging(WeaponProfile.DAGGER, null, 1.0F);
        assertTrue(nothing.isZero(), "a null charge profile moved the body: " + nothing);
    }

    /**
     * The parry exists, and only the weapon that can parry has one.
     *
     * <p>The playtest said "the shotel has no animation for when it parries" and it was right about
     * the body: the viewmodel has carried a {@code PARRY} track the whole time, so in first person
     * the blade snapped across while in third person the arm stood still and the weapon in its hand
     * did something. A signal in its place and nothing drawing it.</p>
     */
    @Test
    @DisplayName("the parry is a pose, and only the sabre reads the channel")
    void theParryIsAPoseOnTheSabreAlone() {
        BodyPose parry = BodyPoses.parrying(1.0F);
        float guard = Math.abs(BodyPoses.guarding(GuardProfile.SWORD, 1.0F).rightArmPitch());

        assertTrue(Math.abs(parry.rightArmPitch()) > guard,
                "the parry is " + parry.rightArmPitch() + " and the guard is " + guard
                        + ". A parry is not a stance that is held, it is a blade turned to meet "
                        + "one blow, and the difference has to read as height");
        assertTrue(parry.rightArmRoll() < 0.0F,
                "and it is turned across the body, which is the same negative roll as the cut");

        assertTrue(BodyPoses.parrying(0.0F).isZero(), "a parry at zero of its window is nothing");

        // **The gate is `reads(PARRY)`, not "is this the sabre".**
        //
        // The parry signal is global -- it rides the player's arm, not the item -- so asking which
        // weapon it is asks a question about the item where the useful question is what the profile
        // can do with the signal. That stays true if the sabre profile is ever reused.
        assertTrue(ViewModelDefaults.of(WeaponProfile.SABRE).reads(Channel.PARRY),
                "the sabre has to read the parry channel, or the parry pose is a pose for "
                        + "nobody -- which is exactly how it was for three rounds");
        for (WeaponProfile profile : new WeaponProfile[]{WeaponProfile.SWORD, WeaponProfile.ROPERA,
                WeaponProfile.DAGGER, WeaponProfile.HALBERD, WeaponProfile.WARHAMMER}) {
            assertFalse(ViewModelDefaults.of(profile).reads(Channel.PARRY),
                    profile + " reads the parry channel. The signal belongs to the player and the "
                            + "channel is global, so a weapon that cannot parry reading it is how "
                            + "the sword ended up drawing the sabre's five second lockout once "
                            + "already");
        }
    }

    /**
     * A charge raises the weapon arm and the off hand only half follows.
     *
     * <p>Two arms converging on the chest is not a wind-up, it is a player hugging themselves, and
     * that was the first version's look.</p>
     */
    @Test
    @DisplayName("a charge raises the weapon arm further than the off hand")
    void theWeaponArmDoesTheWork() {
        BodyPose charge = BodyPoses.charging(WeaponProfile.WARHAMMER, WARHAMMER_CHARGE, 1.0F);
        float weapon = Math.abs(charge.rightArmPitch());
        float off = Math.abs(charge.leftArmPitch());
        assertTrue(weapon > off, "the weapon arm goes to " + weapon + " and the off hand to " + off
                + ". It is the one doing the work and the silhouette has to say so");
    }

    /** There is a strike, and the off hand only half follows it. */
    @Test
    @DisplayName("there is a strike, and the off hand only half follows")
    void theStrikeExistsAndTheOffHandOnlyHalfFollows() {
        BodyPose strike = BodyPoses.striking(1.0F);
        assertTrue(Math.abs(strike.rightArmPitch()) > Math.abs(strike.leftArmPitch()),
                "the off hand is at " + strike.leftArmPitch() + " against the weapon arm's "
                        + strike.rightArmPitch() + ". Two arms swinging equally is a character "
                        + "gesturing, not a person cutting");
    }

    /** The guard raises the weapon arm and barely touches the rest. */
    @Test
    @DisplayName("the guard raises the weapon arm and little else")
    void theGuardIsMostlyTheWeaponArm() {
        BodyPose guard = BodyPoses.guarding(GuardProfile.SWORD, 1.0F);
        assertTrue(Math.abs(guard.rightArmPitch()) > 3.0F * Math.abs(guard.leftArmPitch()),
                "the off arm is at " + guard.leftArmPitch() + " against the weapon arm's "
                        + guard.rightArmPitch() + ". The off hand should come part way up, which is "
                        + "what a person holding a covering stance looks like, and no further");
    }

    /** The sweep and the lunge turn the torso in opposite directions. */
    @Test
    @DisplayName("the sweep turns the body the other way than the lunge")
    void theSweepAndTheLungeTurnOppositeWays() {
        BodyPose sweep = BodyPoses.sweeping(1.0F);
        BodyPose lunge = BodyPoses.lunging(1.0F);
        assertTrue(sweep.bodyYaw() * lunge.bodyYaw() < 0.0F,
                "the sweep's torso yaw is " + sweep.bodyYaw() + " and the lunge's is "
                        + lunge.bodyYaw() + ". They have to oppose, because a sweep cuts across the "
                        + "body and a lunge goes through it");
        assertTrue(sweep.rightArmRoll() < 0.0F,
                "and the arm rolls with it, negative like every other cut here");
    }

    /**
     * A pose interpolates between ticks instead of stepping.
     *
     * <p>The twenty hertz bug: state on the tick, frame by {@code snapshot(partialTick)}. Reading it
     * the other way round is a one frame lag on a smooth value, which is unnoticeable, so this
     * asserts the middle of the interval is strictly between the two ends.</p>
     */
    @Test
    @DisplayName("a pose interpolates between ticks instead of stepping")
    void aPoseInterpolates() {
        BodyPose from = BodyPoses.striking(0.0F);
        BodyPose to = BodyPoses.striking(1.0F);
        BodyPose middle = from.lerp(to, 0.5F);

        assertTrue(Math.abs(middle.rightArmPitch()) > Math.abs(from.rightArmPitch())
                        && Math.abs(middle.rightArmPitch()) < Math.abs(to.rightArmPitch()),
                "half way is " + middle.rightArmPitch() + ", between " + from.rightArmPitch()
                        + " and " + to.rightArmPitch() + ". A step is either end and never between");
        assertTrue(Math.abs(middle.leftArmPitch()) > 0.0F,
                "and both arms interpolate. One arm arriving with the other still at the last tick "
                        + "is a pose that arrives in pieces");
    }

    /**
     * An out of range value cannot throw a limb off the model.
     *
     * <p>{@code Math.min} and {@code Math.max} both propagate a NaN, and a NaN limb angle is not a
     * limb drawn wrong, it is the whole player model failing to render with a stack trace in the
     * log. These ratios come off the network and off attribute values other mods can touch, so
     * "impossible" has to be answered rather than assumed.</p>
     */
    @Test
    @DisplayName("an out of range value cannot throw a limb off the model")
    void anOutOfRangeValueIsAnswered() {
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, -1.0F, 7.0F}) {
            BodyPose pose = BodyPoses.striking(bad);
            assertFalse(Float.isNaN(pose.rightArmPitch()),
                    "a ratio of " + bad + " produced a NaN angle");
            assertTrue(pose.rightArmPitch() >= -ARM_PITCH_CEILING
                            && pose.rightArmPitch() <= ARM_PITCH_CEILING,
                    "a ratio of " + bad + " produced " + pose.rightArmPitch()
                            + ", which is outside the ceiling");
        }

        // **An infinite ratio is the full pose, not nothing.** `Math.min(1.0F, POSITIVE_INFINITY)`
        // is 1.0 and `Math.min(1.0F, NEGATIVE_INFINITY)` is negative infinity, which `clamp01`
        // turns into 0 -- so one infinity is a maximum and the other is a minimum, and only the
        // first is a pose at all. Asserting both were "off" was a third thing: it was wrong, and it
        // was wrong in a way that would have hidden a sign error.
        assertTrue(Math.abs(BodyPoses.striking(Float.POSITIVE_INFINITY).rightArmPitch())
                        == Math.abs(BodyPoses.striking(1.0F).rightArmPitch()),
                "an infinite ratio is the pose at one");
        assertTrue(BodyPoses.striking(Float.NEGATIVE_INFINITY).isZero(),
                "and a negative infinite one is nothing, because it came from below the floor");
        assertTrue(BodyPoses.striking(Float.NaN).isZero(),
                "and a NaN is nothing. Both propagate through `Math.min`, so the guard against "
                        + "them has to be explicit");

        // And nothing at any input passes the ceiling, including the ones that are not clamped.
        for (int i = -50; i <= 150; i++) {
            assertTrue(Math.abs(BodyPoses.striking(i / 10.0F).rightArmPitch())
                    <= ARM_PITCH_CEILING, "nothing passes the ceiling at any input");
        }
    }

    /** The record's resting value is vanilla's pose: nothing added to nothing. */
    @Test
    @DisplayName("the record's resting value is vanilla's pose")
    void restIsVanilla() {
        assertTrue(BodyPose.NONE.isZero(), "the resting pose is not zero");
        assertEquals(0.0F, BodyPose.NONE.rightArmPitch());
        assertEquals(0.0F, BodyPose.NONE.bodyYaw());
    }
}
