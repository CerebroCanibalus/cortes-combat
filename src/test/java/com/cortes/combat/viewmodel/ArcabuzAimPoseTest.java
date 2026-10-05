package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the arcabuz's two aiming postures point different ways, and that the fuse's smoke is asked
 * for in the right place.
 *
 * <h2>Why this file exists</h2>
 *
 * <p><b>The playtest said "the loaded pose and the unloaded one point the same way", and it was
 * true, and nothing in the build noticed.</b> The cause was arithmetic rather than a typo: the
 * {@code HELD} track carried a pitch of exactly zero, so both states evaluated to the resting
 * pose's own -12 degrees and the only thing that differed between them was how deep the weapon sat.
 * Two postures that differ in depth and not in angle are one posture.</p>
 *
 * <p><b>Nothing was guarding it, because nothing said the two were supposed to differ.</b> There was
 * no test over the {@code HELD} track at all, and setting its pitch back to zero -- reproducing the
 * bug exactly -- left the suite green. A guard that does not name the rule cannot catch a violation
 * of it, so the rule had to be written down before it could be enforced.</p>
 *
 * <p><b>And it asserts the relationship, not the number.</b> "Loaded points higher than unloaded"
 * survives a retune of either one. "Loaded is exactly +18" is a test that fails every time somebody
 * adjusts the weapon and passes every time somebody breaks it, which is backwards.</p>
 */
class ArcabuzAimPoseTest {

    /** The arcabuz's profile. */
    private static ViewModelProfile profile() {
        return ViewModelDefaults.of(WeaponProfile.ARCABUZ);
    }

    /** The pose with nothing happening: no firestone, no fuse, no attack. */
    private static Offset unloaded() {
        return profile().evaluate(ViewModelInput.RESTING);
    }

    /**
     * The pose with a firestone in the pan.
     *
     * <p>Built by hand rather than by driving {@link ViewModelMotion}, because the channel's
     * <em>value</em> is what is under test and the motion is a ramp that has to be ticked to get
     * anywhere. Going through the motion would test the ramp instead of the posture.</p>
     */
    private static Offset loaded() {
        for (var track : profile().tracks()) {
            if (track.channel() == Channel.HELD) {
                return unloaded().plus(track.evaluate(ViewModelInput.loaded(1.0F)));
            }
        }
        throw new AssertionError("the arcabuz has no HELD track, so it cannot be loaded at all");
    }

    /**
     * The two postures point different ways, and the loaded one points higher.
     *
     * <p><b>The sign, read rather than remembered.</b> {@code pitch} becomes a {@code rotateX} in the
     * item's own frame, {@code -Z} points away from the camera, and a positive rotation carries the
     * muzzle up. So "aim a little at the ground" is the <em>more negative</em> number. If a future
     * change flipped the frame's handedness this test would fail rather than quietly invert, which
     * is the point of writing the reason next to the assertion.</p>
     */
    @Test
    @DisplayName("the unloaded gun points at the ground and the loaded one points at the front")
    void theTwoPosturesPointDifferentWays() {
        // **`Offset`'s fields are doubles**, unlike every number in the third person poses. Reading
        // them into a float is not a style choice: it would round the comparison and the rounding
        // is the thing under test.
        double unloadedPitch = unloaded().pitch();
        double loadedPitch = loaded().pitch();

        assertTrue(unloadedPitch < 0.0F,
                "an unloaded gun rests nose down, so its pitch is negative. This one is "
                        + unloadedPitch);
        assertTrue(loadedPitch > unloadedPitch,
                "loading the weapon brings the muzzle UP, and up is the larger pitch. Unloaded is "
                        + unloadedPitch + " and loaded is " + loadedPitch + ", so the two postures "
                        + "point the same way again -- which is the bug this test was written for");

        assertNotEquals(unloadedPitch, loadedPitch,
                "the two postures are identical in pitch. They differ in depth and that is not "
                        + "enough: two postures that differ only in depth are one posture");
    }

    /**
     * A full load puts the muzzle level, and the rest does not.
     *
     * <p><b>The playtest's words: unloaded "apuntar un poco al suelo", loaded "apuntar más al
     * frente".</b> "Al frente" is the sight line, so a loaded gun at or very near level is what
     * was asked for, and the tolerance is deliberately generous -- a musket held on the shoulder
     * does not need to be at exactly zero to read as aimed.</p>
     */
    @Test
    @DisplayName("a full load brings the muzzle to the sight line")
    void aFullLoadBringsTheMuzzleToTheSightLine() {
        assertTrue(Math.abs(loaded().pitch()) <= 8.0F,
                "a loaded gun points along the sight line, which is a pitch of about zero. This one "
                        + "is " + loaded().pitch() + ", so the loaded posture is still tilted and "
                        + "the two states differ without one of them being aimed");
        assertTrue(unloaded().pitch() <= -14.0F,
                "the unloaded gun is tilted down far enough to read as pointing at the ground in "
                        + "front of the player. This one is " + unloaded().pitch());
    }

    /**
     * The rest does all of it, and the loaded track only tilts.
     *
     * <p><b>Why this matters beyond tidiness.</b> A track that carried an absolute would make the
     * unloaded posture depend on whether a firestone happens to be in the gun, which is the same
     * class of bug as the one {@code ViewModelProfile} documents: a resting pose is a field, not a
     * channel, precisely so that a weapon held differently is described once.</p>
     */
    @Test
    @DisplayName("the HELD track is a tilt and not a second copy of the whole pose")
    void theHeldTrackIsATiltAndNotASecondPose() {
        for (var track : profile().tracks()) {
            if (track.channel() != Channel.HELD) {
                continue;
            }
            Offset by = track.evaluate(ViewModelInput.loaded(1.0F));
            assertEquals(0.0F, by.x(), 1.0E-9,
                    "the track shifts the weapon sideways by " + by.x() + ". A loaded gun is "
                            + "tilted, not shoved to one side");
            // **The depth is claimed and not asserted to be zero.** The track does carry -0.06 on z
            // and it did before this playtest: bringing a loaded gun slightly nearer the eye is
            // what "shouldered" looks like, and it is a displacement, not a second resting pose.
            //
            // The first version of this assertion demanded zero and failed against working code,
            // which is the project's own rule about tests coming up: a test can be the thing that
            // is wrong. What is actually load-bearing is the relationship below -- that the two
            // postures still differ in pitch once the depth is taken out, because a difference in
            // depth alone is the bug this whole file exists for.
            assertTrue(Math.abs(by.z()) <= 0.10,
                    "the track pushes the weapon " + by.z() + " along the view axis. A small "
                            + "displacement towards the eye is what shouldering looks like; a "
                            + "large one is a second resting pose wearing a track's clothes");
            assertTrue(by.pitch() > 0.0F,
                    "the track's own pitch is " + by.pitch() + ". It has to be positive, because "
                            + "it is added to a rest that is already nose down and a negative one "
                            + "would tilt the loaded gun further down than the unloaded one");
        }
    }

    /**
     * A half-loaded gun sits between the two, so the pose travels rather than teleporting.
     *
     * <p><b>This is a guard on the ramp, not on the profile.</b> The channel used to be a flag: the
     * value was assigned and the previous value was the same number, so the pose interpolated
     * between nothing and something inside a single tick. That was invisible while the track moved
     * the weapon five centimetres. It is not invisible now that the track carries a real angle.</p>
     */
    @Test
    @DisplayName("half a load is between the two postures, so the weapon travels there")
    void halfALoadIsBetweenTheTwoPostures() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.observeLoaded(true);

        // Nothing else is happening: no button, no charge, no guard, no stagger. Those are the
        // seven arguments `tick` takes and they are all irrelevant here, which is exactly why a
        // guard on the ramp belongs in this file rather than in the motion's own suite.
        // **`heldAt(1)`, not `heldAt(0)`, and that is not a detail.**
        //
        // `heldAt` interpolates between the previous tick and this one, so `heldAt(0)` is the value
        // the tick *started* at and `heldAt(1)` is where it ended. Reading zero after the first tick
        // is the arithmetic working correctly -- the tick began unloaded -- and an assertion written
        // against `heldAt(0)` would fail on a ramp that is behaving exactly as intended.
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        float afterOneTick = motion.heldAt(1.0F);
        assertTrue(afterOneTick > 0.0F && afterOneTick < 1.0F,
                "the loaded posture arrives over several ticks, not on the first one. After one "
                        + "tick the channel is at " + afterOneTick + ", so the weapon either "
                        + "teleports or does not move at all");

        for (int tick = 0; tick < 40; tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        }
        assertEquals(1.0F, motion.heldAt(0.0F), 1.0E-6F,
                "and it does arrive: a channel that approaches one without reaching it is a weapon "
                        + "that is never quite shouldered, and every track multiplied by it is a "
                        + "little short forever");

        // **And it comes back down when the firestone is spent**, which is the half of the flag bug
        // that a one-way test would miss. `clearHeld` used to reset the value and leave the thing
        // that sets it, so with a ramp the weapon would have walked all the way back up on its own.
        motion.clearHeld();
        for (int tick = 0; tick < 40; tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        }
        assertEquals(0.0F, motion.heldAt(0.0F), 1.0E-6F,
                "a forgotten arcabuz leaves the channel at " + motion.heldAt(0.0F) + ", so the "
                        + "weapon is still shouldered after the gun is gone");
    }
}
