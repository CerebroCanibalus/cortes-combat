package com.cortes.combat.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.thirdperson.BodyPoses;
import com.cortes.combat.thirdperson.BodyPose;
import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Who chops and who thrusts, and the shape each one has.
 *
 * <h2>This is two swings and not one with a setting</h2>
 *
 * <p><b>The playtest found that one bump cannot be both motions.</b> Every swing in the library was
 * {@code amplitude × sin(sqrt(phase) × PI)}: the weapon leaves rest, rises through the middle and
 * comes back. That is right for a thrust, and the ropera and the dagger were reported as
 * <em>excellent</em> doing it. On everything else it was reported twice as starting from below and
 * going up -- once for the normal hit, and once for what happens when a charge is let go of.</p>
 *
 * <p>A chop cannot be a bump. It starts above, because the charge raised the arm there, and the
 * only thing left is the fall. So {@link Channel#CHOP} is a separate number that only ever
 * decreases, and <b>the profile decides which one it has</b> -- not a list in the dispatcher, which
 * would be a second copy of "which weapons thrust" that could fall out of step with the profiles
 * themselves.</p>
 */
class ChopVersusThrustTest {

    /** The two profiles whose swing is a bump, and which the playtest asked to leave alone. */
    private static final WeaponProfile[] THRUSTS = {
            WeaponProfile.ROPERA,
            WeaponProfile.DAGGER,
    };

    private static ViewModelProfile of(WeaponProfile profile) {
        return ViewModelDefaults.of(profile);
    }

    /**
     * The split itself, weapon by weapon.
     *
     * <p><b>Named explicitly on both sides.</b> "Every profile reads exactly one of the two" would
     * pass if the chop list silently lost the halberd, and the symptom would be a weapon that
     * stabs upward with nothing in the log -- the same class of silent default this project has
     * been bitten by twice, in the two folder names that were one character away from being found.
     */
    @Test
    @DisplayName("the ropera and the dagger thrust; the rest chop")
    void theSplitIsExplicit() {
        for (WeaponProfile profile : THRUSTS) {
            assertTrue(of(profile).reads(Channel.STRIKE),
                    profile + " thrust, so it has to read the strike channel. The playtest called "
                            + "this version of the swing excellent and asked for it to be kept");
            assertFalse(of(profile).reads(Channel.CHOP),
                    profile + " reads the chop channel as well. A thrust that also falls reads as "
                            + "two motions fighting over one arm, and the one the player asked to "
                            + "keep is the one that got lost");
        }

        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile == WeaponProfile.ROPERA || profile == WeaponProfile.DAGGER) {
                continue;
            }
            // **NONE is the most useful exclusion in this loop.** It is the profile an
            // unrecognised item derives to, and it has no tracks on purpose: treating the
            // unrecognised as the most common is only safe because the most common here is
            // nothing. A dirt block with a chop would swing the ground.
            if (profile == WeaponProfile.NONE) {
                assertFalse(of(profile).reads(Channel.CHOP),
                        "NONE has a chop. It is the default every unrecognised item derives to, so "
                                + "a swing on it is the whole world swinging");
                assertFalse(of(profile).reads(Channel.STRIKE),
                        "NONE has a strike, for the same reason");
                continue;
            }
            // **And the bare fist, which is a thrust and was left alone on purpose.** Nobody asked
            // about it, a punch goes forward rather than down, and it is the most visible animation
            // in the game because there is no weapon covering it. Changing it on a hunch would be a
            // change nobody asked for.
            if (profile == WeaponProfile.UNARMED) {
                assertTrue(of(profile).reads(Channel.STRIKE), "the fist thrusts, so it keeps the bump");
                assertFalse(of(profile).reads(Channel.CHOP),
                        "and it does not also chop. If a playtest says the punch should fall, this "
                                + "is the line that has to change, and not the punch's numbers");
                continue;
            }

            // The arcabuz used to have neither, and that was a real hole rather than a decision:
            // its left click is a bayonet poke and its profile had been written for the
            // right click's three states, so a swing moved nothing in first person.
            //
            // It reads STRIKE now. **A bayonet is a thrust**, and the reason to write that
            // down is that the alternative was leaving a weapon whose primary
            // button is silent -- the same shape as the sabre's dead BREAK channel.
            if (profile == WeaponProfile.ARCABUZ) {
                assertTrue(of(profile).reads(Channel.STRIKE),
                        "the arcabuz's left click is a bayonet poke, and a poke is a thrust. "
                                + "Without a STRIKE track the gun does not move when it hits");
                assertFalse(of(profile).reads(Channel.CHOP),
                        "and it is not a chop: the weapon is held two-handed and comes "
                                + "forward, not down");
                continue;
            }
            // **The four vanilla tools that have no viewmodel profile at all.**
            //
            // The pickaxe, the shovel, the mace and the trident are a documented gap, not an
            // oversight: a hand-drawn motion over a silhouette nobody in this project drew fights
            // with the model, so they are left on vanilla's exact pose and get vanilla's swing.
            // The axe is NOT in this group -- it has a profile, and it chops.
            //
            // They are named one by one rather than skipped by a predicate, because "every profile
            // that has a profile reads one of the two" is a rule that passes while the list of
            // profiles quietly shrinks. This way the day one of them gets a profile, the test says
            // so.
            if (profile == WeaponProfile.PICKAXE || profile == WeaponProfile.SHOVEL
                    || profile == WeaponProfile.MACE || profile == WeaponProfile.TRIDENT) {
                assertFalse(of(profile).reads(Channel.CHOP) || of(profile).reads(Channel.STRIKE),
                        profile + " has a swing channel now, so it is no longer a vanilla tool with "
                                + "no profile. Either this line or the entry above is wrong, and "
                                + "which one depends on whether it was meant");
                continue;
            }

            assertTrue(of(profile).reads(Channel.CHOP),
                    profile + " has neither a chop nor a thrust, so its swing is nothing. The "
                            + "normal hit on this weapon was reported as starting from below and "
                            + "going up, and a missing track is the quietest way to still have "
                            + "that");
            assertFalse(of(profile).reads(Channel.STRIKE),
                    profile + " reads both the strike and the chop. The bump and the fall drive the "
                            + "same arm, and whichever one is larger wins -- so the motion a weapon "
                            + "actually gets is whichever track happens to be bigger, and not "
                            + "anything anybody decided");
        }
    }

    /**
     * The chop only ever goes down.
     *
     * <p><b>This is the whole point of the channel, and a bump cannot satisfy it.</b> Sampled every
     * tick of a swing: each value is at or below the one before it, and the last is zero. A bump
     * would pass "starts high" and fail this, which is the distinction the playtest drew.</p>
     */
    @Test
    @DisplayName("the chop falls from one to zero and never comes back up")
    void theChopOnlyFalls() {
        ViewModelMotion motion = swinging(1.0F);

        // **One tick first, and the reason is `pendingStrike`.** The release only ANNOUNCES the
        // strike; `strikeAge` is set to zero on the tick after it, which is the rule that stopped a
        // swing's first frame from being drawn at the previous swing's wind-up. So the chop reads
        // zero on the tick of the release and one on the next.
        //
        // That is not the chop rising. It is its first value arriving a frame late, and the
        // difference between the two readings is the difference between a one tick delay and the
        // upward swing this whole channel exists to delete.
        idle(motion);

        float previous = Float.MAX_VALUE;
        float first = -1.0F;
        for (int tick = 0; tick < ViewModelMotion.MAX_STRIKE_TICKS + 2; tick++) {
            float value = motion.chop();
            if (tick == 0) {
                first = value;
            }
            assertTrue(value <= previous + 1.0E-6F,
                    "the chop rose at tick " + tick + ": " + previous + " then " + value
                            + ". A chop that lifts is a bump with extra steps, which is the motion "
                            + "the playtest rejected");
            previous = value;
            idle(motion);
        }

        assertTrue(first > 0.9F,
                "and it starts at the top, not at zero. A swing whose first frame is at rest is a "
                        + "weapon that begins by lifting, and the blade spends the whole motion "
                        + "arriving instead of leaving");
        assertEquals(0.0F, previous, 1.0E-6,
                "and it ends at rest, because the chop finishes in the resting position");
    }

    /**
     * The wind-up's height and the chop's height are the same number.
     *
     * <p><b>This is the hand-off, and it is why the charge release reads as one motion.</b> The
     * charge is at full when the button comes up and the chop starts at one, so there is no step
     * between them. Change one without the other and the arm jumps that difference on the first
     * tick of every charged swing -- which is what "when I let go, the hit goes up" was, before
     * the chop existed and the release inherited the bump's upward half.</p>
     */
    @Test
    @DisplayName("a wind-up and a chop meet at the same height")
    void theWindUpAndTheChopMeetAtOneHeight() {
        assertEquals(1.0F, AttackTiming.chargeRatioFor(40, 20, true), 1.0E-6,
                "a full hold has to be a full charge, or the hand-off has nothing to hand off "
                        + "from");
        assertEquals(1.0F, firstChopValue(), 1.0E-6,
                "and the chop's first value is one, which is the height the charge leaves the arm "
                        + "at");
    }

    /** The chop's amplitude follows the charge, or a full charge and a tap look the same. */
    @Test
    @DisplayName("the chop carries further on a full charge than on a tap")
    void theChopCarriesTheCharge() {
        ViewModelMotion charged = swinging(1.0F);
        idle(charged);
        float deep = charged.chop();

        ViewModelMotion tapped = swinging(0.0F);
        idle(tapped);
        float shallow = tapped.chop();

        assertTrue(deep > shallow,
                "a full charge is at " + deep + " and a tap at " + shallow + ". The first version "
                        + "of the chop was the fall on its own, and the whole swing stopped being a "
                        + "swing: a full charge travelled exactly as far as a tap. The wind-up "
                        + "still shows the charge building; this is the half that shows it being "
                        + "spent");
    }

    /** Before a swing and after it, there is no chop at all. */
    @Test
    @DisplayName("a resting weapon has no chop")
    void restHasNoChop() {
        ViewModelMotion motion = new ViewModelMotion();
        assertEquals(0.0F, motion.chop(), 1.0E-9, "a motion that is not running is nothing");

        for (ViewModelInput frame : new ViewModelInput[]{
                ViewModelInput.RESTING, ViewModelInput.of(0.0F, 0.0F), ViewModelInput.chopping(0.0F)}) {
            assertEquals(0.0F, frame.chop(), 1.0E-9, "and a frame with no chop in it reads zero");
        }
    }

    /** The channel is in the frame, on its own field, like every other one. */
    @Test
    @DisplayName("the chop is in its own field and nowhere else")
    void theChopIsItsOwnField() {
        ViewModelInput frame = ViewModelInput.chopping(0.75F);
        assertEquals(0.75F, frame.chop(), 1.0E-6, "the chop's own field");
        assertEquals(0.0F, frame.strike(), 1.0E-9,
                "and it does not leak into the strike. The two are separate numbers because they "
                        + "are separate motions, and a test that reads one and expects the other "
                        + "would be asking a question the record cannot answer");
        assertEquals(0.0F, frame.windup(), 1.0E-9, "nor into the wind-up");
        assertEquals(0.0F, frame.recover(), 1.0E-9, "nor into the follow-through");
    }

    /**
     * A swing that has just been released, at the given charge.
     *
     * <p>Held for thirty ticks first, so the charge has somewhere to build, and then released
     * through {@code onStrike} -- the same shape {@code ChargeIsReadBeforeTheResetTest} uses, and
     * for the same reason: {@code released} is what the chop's amplitude is scaled by, and it is
     * only set by a real release.</p>
     */
    /**
     * The chop's very first frame is HIGH, and this is the assertion for the report it fixes.
     *
     * <p>"It still starts from rest and then hits." The chop used to be read off {@code strikeAge},
     * and {@code strikeAge} is not set on the tick of the release -- the release announces the strike
     * and the age starts on the next one. That is correct for the bump, which begins at zero anyway.
     * It is wrong for the chop, which is a <em>height</em>: one frame of zero is one frame of the
     * arm hanging at the player's side, and then it is at the top.</p>
     *
     * <p>So the chop reads its own clock, started by the release itself, and this checks the number
     * on the release tick. A weapon at zero here is a weapon that was resting, whatever every other
     * frame of the motion does.</p>
     */
    @Test
    @DisplayName("the chop's first frame is already high, not the resting position")
    void theChopDoesNotStartAtRest() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);

        // No tick. This is the frame of the release itself.
        float onRelease = motion.chop();
        assertTrue(onRelease > 0.7F,
                "the chop is at " + onRelease + " on the tick the button comes up. Anything near "
                        + "zero is one frame of the weapon hanging at the player's side, which is "
                        + "the 'it starts from rest and then hits' the playtest reported -- and it "
                        + "is what a channel read off `strikeAge` does, because that age is not set "
                        + "until the tick after");

        // And it never dips below that while it runs, which is the same statement over the whole
        // motion rather than on one frame.
        float lowest = onRelease;
        for (int tick = 0; tick < ViewModelMotion.MAX_STRIKE_TICKS + 4; tick++) {
            lowest = Math.min(lowest, motion.chop());
            idle(motion);
        }
        assertEquals(0.0F, lowest, 1.0E-6,
                "and it goes to zero at the end, because the chop finishes in the resting "
                        + "position. A minimum above zero means the fall never completed");
    }

    /**
     * The chop's start height is the wind-up's height, and that is arithmetic rather than taste.
     *
     * <p>It used to be two constants that happened to be equal, which is a coincidence that survives
     * exactly one retune: change the chop and the arm steps on the tick the button comes up. So
     * {@code CHOP_START} is the ratio between them, and this asserts the relationship survives
     * someone editing either number.</p>
     */
    @Test
    @DisplayName("the chop starts where the wind-up left the arm")
    void theChopStartsWhereTheWindUpEnded() {
        float windUp = Math.abs(BodyPoses.charging(WeaponProfile.WARHAMMER,
                new ChargeProfile(true, 0.6F, 32), 1.0F).rightArmPitch());
        float chopFull = Math.abs(BodyPoses.chopping(1.0F).rightArmPitch());
        float chopStart = chopFull * ViewModelMotion.CHOP_START;

        assertEquals(windUp, chopStart, 2.0,
                "the wind-up leaves the arm at " + windUp + " degrees and the chop's first frame "
                        + "puts it at " + chopStart + " (" + chopFull + " full times CHOP_START "
                        + "). They have to meet, or the arm steps on the release -- and the number "
                        + "that ties them together is CHOP_START, not two constants agreeing");

        assertTrue(chopFull > windUp,
                "and the chop's peak is above the wind-up's height, because the build-up is what "
                        + "gives it weight. Equal heights would be a chop with no anticipation in "
                        + "it, which is the motion that was called 'it hits suddenly'");
    }

    /**
     * A swing that has just been released, at the given charge.
     *
     * <p>Held for thirty ticks first, so the charge has somewhere to build, and then released
     * through {@code onStrike} -- the same shape {@code ChargeIsReadBeforeTheResetTest} uses, and
     * for the same reason: {@code released} is what the chop's amplitude is scaled by, and it is
     * only set by a real release.</p>
     */
    /**
     * The fall ACCELERATES, and the previous curve did the opposite.
     *
     * <p>"It feels too soft and has no impact." The old falloff was {@code (1 - t)^2}, whose slope is
     * steepest at {@code t = 0} and flat at {@code t = 1}: the blade left the top at full speed and
     * coasted to a stop. **That is a weapon being thrown**, and a decelerating fall has no impact
     * because the fastest part of the motion is over before the eye catches up.</p>
     *
     * <p>What a falling weight does is the other thing, and this asserts it tick by tick rather than
     * by shape: the per tick drop grows towards the middle of the fall. If a future curve is smooth
     * and monotone but decelerating, this is the test that catches it, and the shape assertions
     * above will all still be green.</p>
     */
    @Test
    @DisplayName("the chop accelerates into the arrival instead of coasting to it")
    void theChopAccelerates() {
        ViewModelMotion motion = swinging(1.0F);
        while (motion.chop() <= 0.0F) {
            idle(motion);
        }
        // **Exactly past the build-up, and no further.** `chopAge` is set to zero by the release and
        // advanced at the end of every tick, so the full height is the value at
        // `CHOP_BUILDUP_TICKS` and the first tick of the fall is already one past it. Skipping one
        // tick too many reads the steepest drop as the first one, which is this assertion passing
        // for the wrong reason -- and it did, once.
        for (int i = 0; i < ViewModelMotion.CHOP_BUILDUP_TICKS; i++) {
            idle(motion);
        }
        assertTrue(motion.chop() > 0.99F,
                "this has to be the top of the fall, and it is at " + motion.chop());

        float previous = motion.chop();
        float firstDrop = -1.0F;
        float biggestDrop = 0.0F;
        for (int tick = 0; tick < ViewModelMotion.CHOP_FALL_TICKS + 2; tick++) {
            idle(motion);
            float now = motion.chop();
            float drop = previous - now;
            if (drop > 0.0F) {
                if (firstDrop < 0.0F) {
                    firstDrop = drop;
                }
                biggestDrop = Math.max(biggestDrop, drop);
            }
            previous = now;
        }

        // **The first tick is not the fastest one, and that is the whole assertion.**
        //
        // It is not "the drop never shrinks" -- a smoothstep is symmetric and settles at both ends,
        // which is correct. It is that the motion is not at its steepest when it leaves the top.
        // `(1 - t)^2` fails this on its first tick: it drops more in tick one than in any other, so
        // the blade is thrown rather than swung, and the eye has not caught up by the time it stops.
        assertTrue(firstDrop > 0.0F && firstDrop < biggestDrop,
                "the first tick of the fall drops " + firstDrop + " and the steepest tick drops "
                        + biggestDrop + ". If the first one is the biggest, the blade leaves the top "
                        + "at full speed and coasts to a stop, which is a weapon being thrown");
        assertTrue(biggestDrop > 0.15F,
                "and the steepest single tick only moved " + biggestDrop + " of the height. A chop "
                        + "that never moves much in one tick reads as a glide no matter which "
                        + "direction the curve goes");
        assertTrue(ViewModelMotion.CHOP_FALL_TICKS <= 5,
                "the fall is " + ViewModelMotion.CHOP_FALL_TICKS + " ticks. Impact is speed over "
                        + "distance and the distance was never the problem, so six ticks -- a tenth "
                        + "of a second for a weapon described as heavy -- is what had to change");
    }

    /**
     * One arm, and it is the one holding the weapon.
     *
     * <p>"The player raises both arms instead of one." The chop carried an off-hand pitch from the
     * days when it was a copy of the strike's shape, and it is the only pose in this file with a
     * non-zero off arm during a swing. Two arms going up is a player reaching, and a reach is not a
     * chop.</p>
     *
     * <p><b>The guard and the wind-up still use both</b> and that is deliberate: a guard is a
     * stance held with two hands of a two-handed weapon, and a wind-up gathers with the whole body.
     * The strike is the one that commits with a single arm, and that is the difference between
     * preparing and swinging.</p>
     */
    @Test
    @DisplayName("the chop swings one arm and the other one stays down")
    void theChopIsOneArmed() {
        BodyPose full = BodyPoses.chopping(1.0F);
        assertEquals(0.0, full.leftArmPitch(), 1.0E-9,
                "the off arm is at " + full.leftArmPitch() + " degrees during the strike. Every "
                        + "other pose in this file moves the second hand, which is right for a stance "
                        + "and for a wind-up and wrong for the cut: two arms going up is a player "
                        + "reaching for something, not a blade coming down");
        assertTrue(Math.abs(full.rightArmPitch()) > 100.0,
                "and the weapon arm has to be doing all of it, which is " + full.rightArmPitch());
    }

        /**
     * A swing that has just been released, at the given charge.
     *
     * <p>Held for thirty ticks first, so the charge has somewhere to build, and then released
     * through {@code onStrike} -- the same shape {@code ChargeIsReadBeforeTheResetTest} uses, and
     * for the same reason: {@code released} is what the chop's amplitude is scaled by, and it is
     * only set by a real release.</p>
     */
    private static ViewModelMotion swinging(float charge) {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, charge, false, 1.0F, false, false);
        }
        motion.onStrike(charge, ViewModelMotion.STRIKE_TICKS);
        return motion;
    }

    /** One tick of a swing with the button up. */
    private static void idle(ViewModelMotion motion) {
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
    }

    private static float firstChopValue() {
        ViewModelMotion motion = swinging(1.0F);
        idle(motion);
        return motion.chop();
    }
}
