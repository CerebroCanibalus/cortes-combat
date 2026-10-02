package com.cortes.combat.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The halberd's sweep: a new channel, and the arc it draws.
 *
 * <p>Two claims are under test and they are not the same claim. The first is that a special needs a
 * channel of its own, which is a rule about the framework. The second is that this particular arc
 * is the thrust arriving from the other side rather than a second idea, which is a rule about this
 * weapon.</p>
 */
class SweepArcTest {

    private static final ViewModelProfile HALBERD =
            ViewModelDefaults.of(WeaponProfile.HALBERD);

    private static final Offset THRUST = HALBERD.evaluate(ViewModelInput.chopping(1.0F));
    private static final Offset ARC = HALBERD.evaluate(ViewModelInput.sweeping(1.0F));

    /**
     * The sweep is the halberd's own channel, and nothing else has it.
     *
     * <p>The signal is global -- it belongs to the player, not to the item -- so a gate of "is this
     * the halberd" would be a question about the weapon where the useful question is what the
     * profile can do with the signal. That is the lesson the parry paid for once already, when the
     * channel lit and the sword drew the sabre's lockout.</p>
     */
    @Test
    @DisplayName("the sweep is the halberd's channel and nobody else's")
    void onlyTheHalberdReadsIt() {
        assertTrue(HALBERD.reads(Channel.SWEEP),
                "the halberd has to read the sweep channel, or the arc is an arc for nobody");

        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile == WeaponProfile.HALBERD) {
                continue;
            }
            assertFalse(ViewModelDefaults.of(profile).reads(Channel.SWEEP),
                    profile + " reads the sweep channel. The arc belongs to one weapon and a second "
                            + "one picking it up is the sword drawing the halberd's move");
        }
    }

    /**
     * The arc's geometry, and every sign in it was flipped by a playtest.
     *
     * <p><b>x is lateral in this frame.</b> z is depth, which is why the ropera's lunge spends all
     * of its travel on z and none on x. So the half an item of x here is the weapon travelling
     * across the view -- and the roll goes DOWN on top now, because the ask was that the tip rotate
     * to the side opposite to the one it was on.</p>
     *
     * <p>The story this file used to tell was that the sweep mirrored the thrust, sign for sign, so
     * the pair would read as one weapon swung both ways. That was a theory about what looks good and
     * the player did not agree: rotate toward the front, then toward the side opposite to which it
     * currently rotates, and swing across the screen until it returns to the hand. Four of the tests
     * below had their premise replaced, and the ones that survived are the ones about the SHAPE --
     * one number decaying to zero over a fixed length -- which the feedback never touched.</p>
     */
    @Test
    @DisplayName("the arc travels across the view, to the side the sweep was asked for")
    void theArcCrossesTheScreen() {
        // **Every sign in this test flipped, and the playtest is why.**
        //
        // The ask was: the tip has to rotate toward the FRONT, then toward the side OPPOSITE to
        // which it currently rotates, and sweep across the screen back to the hand. It was built to
        // mirror the thrust, and mirroring an opposite motion is only correct if the player is on
        // the other side of it -- which they are not. So x, yaw and roll all went positive.
        assertTrue(ARC.x() > 0.4,
                "x is " + ARC.x() + ". It is lateral travel in this frame, and the old value of "
                        + "-0.06 was nothing at all -- with nothing to translate, the only motion "
                        + "left was the yaw, which is why it read as the weapon turning sideways "
                        + "instead of sweeping. Half an item is the weapon crossing the view");
        assertTrue(Math.abs(ARC.x()) > Math.abs(THRUST.x()) * 5.0,
                "and far more lateral travel than the thrust's " + Math.abs(THRUST.x())
                        + ", because a thrust goes forward and a sweep goes across");

        assertTrue(ARC.yaw() > 0.0,
                "yaw is " + ARC.yaw() + " and the ask was the side opposite to the one it was "
                        + "turning. It used to be negative, mirroring the thrust's +62, and that "
                        + "was the motion the player called wrong");
        assertTrue(ARC.roll() < 0.0,
                "roll is " + ARC.roll() + ", the blade turned over the other way to match");
    }

    /**
     * It is the same weapon, and the two motions are now on the SAME side.
     *
     * <p><b>This is the assertion that inverted.</b> It used to require the arc's yaw to be the
     * thrust's 62 with the sign flipped, on the theory that a pair reading as one weapon swung both
     * ways is worth more than one reading as two moves. The playtest asked for the arc to rotate to
     * the side opposite to where it was, which is the same side as the thrust -- so the
     * "opposite signs" half of this is gone and what remains is the magnitude, which is the part
     * that was always about them being one weapon.</p>
     */
    @Test
    @DisplayName("the arc is the thrust's own yaw, on the same side now")
    void theArcIsTheThrustFromTheSameSide() {
        assertEquals(62.0, THRUST.yaw(), 1.0E-6, "the thrust's own yaw, for the comparison");
        assertEquals(74.0, Math.abs(ARC.yaw()), 1.0E-6,
                "and the arc's is that, four degrees narrower. It has to be within a whisker: a "
                        + "sweep that swung three times wider would be a different move with its own "
                        + "name");
        assertEquals(Math.signum(THRUST.yaw()), Math.signum(ARC.yaw()),
                "and the same sign, which is the change the playtest asked for. It used to require "
                        + "opposite ones. The two motions are now distinguished by the roll and the "
                        + "lateral travel rather than by which way they turn, so they read as one "
                        + "weapon doing two things rather than as one weapon swung left and right");
    }

    /**
     * The pitch is what the playtest asked for, and it is the opposite of what this used to hold.
     *
     * <p>"Rotate toward the front" is a pitch, and the old rule was that a swept polearm stays
     * level -- which is true of a polearm being <em>held</em> and not of one being swung. Forty six
     * degrees is the tip coming at the camera, and it is what turns an arc into a cut.</p>
     */
    @Test
    @DisplayName("the tip comes at the camera, which is what 'toward the front' means")
    void theTipComesForward() {
        assertTrue(Math.abs(ARC.pitch()) >= 40.0,
                "pitch is " + ARC.pitch() + ". This used to be asserted under thirty with the "
                        + "reason that a big pitch chops an arc back down into a second cut -- and "
                        + "the playtest answered that the tip has to rotate toward the front, which "
                        + "is a pitch and not a choice of taste");
        assertTrue(ARC.pitch() < 0.0,
                "and it is forward. Negative pitch points the tip at the camera in this frame");
    }

    /** It crosses to the other side and back to the hand, rather than pulling back to cock. */
    @Test
    @DisplayName("the blade crosses to the far side on its way back to the hand")
    void itSweepsThrough() {
        // **The sign of x flipped with the rest of the arc.** It used to require negative, on the
        // reason that negative is "through". The ask was the side opposite to the one it was on,
        // and on the same frame that is positive -- the weapon crosses the view and comes back.
        assertTrue(ARC.x() > 0.0,
                "x is " + ARC.x() + ", which is the side the sweep was asked to go to");
        assertTrue(Math.abs(ARC.z()) > Math.abs(THRUST.z()),
                "and it pushes further forward than the thrust does, because it is covering ground "
                        + "instead of stabbing into it");
    }


    /**
     * The bump, and it rests at zero.
     *
     * <p>Twelve ticks, and the shape is the strike's own: it is over in
     * {@link ViewModelMotion#SWEEP_TICKS} and zero every other time. A channel that held at one
     * would leave the blade turned after the motion ended and every profile would need an undo
     * track to match it.</p>
     *
     * <p><b>Read through {@code sweep()} and never {@code sweepAt(0)}.</b> An interpolated channel
     * at offset zero returns the value from the <em>previous</em> tick, on purpose -- that is what
     * makes a twenty hertz state draw smoothly at sixty frames a second. A bump read at offset zero
     * is one tick late, so its first tick reads as zero.</p>
     */
    @Test
    @DisplayName("the arc is twelve ticks and rests at zero")
    void theArcIsABumpThatEnds() {
        ViewModelMotion motion = new ViewModelMotion();
        assertEquals(0.0F, motion.sweep(), 1.0E-9,
                "nothing has been thrown yet, and a weapon flying in when the world loads is a bug "
                        + "the player sees on frame one");

        motion.onSweep();
        float[] seen = new float[ViewModelMotion.SWEEP_TICKS + 1];
        for (int tick = 0; tick <= ViewModelMotion.SWEEP_TICKS; tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
            seen[tick] = motion.sweep();
        }

        assertTrue(seen[0] > 0.0, "the first tick is already out: " + seen[0]
                + ". The press IS the motion; a sweep has no wind-up to unwind first");
        assertEquals(0.0F, motion.sweep(), 1.0E-9,
                "and it is over exactly at SWEEP_TICKS, not a tick earlier and not a tick later");

        for (int tick = 0; tick < ViewModelMotion.SWEEP_TICKS; tick++) {
            assertTrue(seen[tick] > 0.0, "tick " + tick + " is " + seen[tick]
                    + ". A bump that touches zero in the middle draws the weapon through its own "
                    + "resting pose in the middle of a motion, which is the one thing a bump "
                    + "must never do -- and that is exactly what `1 - age / SWEEP_TICKS` did");
        }
        for (int tick = 1; tick <= ViewModelMotion.SWEEP_TICKS; tick++) {
            assertTrue(seen[tick] < seen[tick - 1],
                    "and it only ever comes down: " + seen[tick - 1] + " then " + seen[tick]);
        }
    }

    /**
     * Twice inside the window is one faster swing, not two fighting.
     *
     * <p>Restarts at one rather than queueing, so a player mashing the button sees the arc speed
     * up and nothing else. The queueing version would have made the channel's value depend on how
     * many times the button had been pressed, which is not a thing a readout should encode.</p>
     */
    @Test
    @DisplayName("a second press restarts the arc instead of stacking")
    void aSecondPressRestarts() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onSweep();
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        float before = motion.sweep();
        assertTrue(before > 0.0, "the arc is running");

        motion.onSweep();
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        assertTrue(motion.sweep() > before,
                "the second press puts it back near the top. It cannot stack, because a channel "
                        + "reading two would be a number about how many times the button was "
                        + "pressed rather than how far the blade has gone");

        for (int tick = 0; tick < ViewModelMotion.SWEEP_TICKS + 2; tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
            assertTrue(motion.sweep() >= 0.0F && motion.sweep() <= 1.0F,
                    "and it never leaves 0..1, at tick " + tick);
        }
        assertEquals(0.0F, motion.sweep(), 1.0E-9, "still bounded after the restart");
    }

    /** A weapon the profile does not read is told to forget it, and forgets it immediately. */
    @Test
    @DisplayName("a weapon that cannot sweep clears the arc at once")
    void aWeaponThatCannotSweepClearsIt() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onSweep();
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        assertTrue(motion.sweep() > 0.0);

        motion.clearSweep();
        assertEquals(0.0F, motion.sweep(), 1.0E-9,
                "one call and no tick. The player swapped weapons mid-arc, and the sword must not "
                        + "be drawn carrying the halberd's");
    }

    /** The frame the renderer gets carries the arc in the sweep field and nowhere else. */
    @Test
    @DisplayName("the snapshot puts the arc in its own field")
    void theSnapshotCarriesIt() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onSweep();
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);

        ViewModelInput frame = motion.snapshot(0.5F, 0.0F);
        assertTrue(frame.sweep() > 0.0, "the arc is in the sweep field");
        assertEquals(0.0F, frame.strike(), 1.0E-9,
                "and the strike is still zero. A sweep drawn as a cut is the whole thing this "
                        + "channel exists to prevent");
        assertEquals(0.0F, frame.lunge(), 1.0E-9);
        assertEquals(0.0F, frame.guard(), 1.0E-9);

        ViewModelMotion fresh = new ViewModelMotion();
        ViewModelInput resting = fresh.snapshot(0.0F, 0.0F);
        for (Channel channel : Channel.values()) {
            assertEquals(0.0F, resting.read(channel), 1.0E-9,
                    channel + " does not rest at zero. A profile is a sum of amplitude times "
                            + "curve, and a channel that rested anywhere else would need a "
                            + "special case in every track that reads it");
        }
    }
}
