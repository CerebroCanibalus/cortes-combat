package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the cut is a rotation, which is what three rounds of playtesting failed to ask for.
 *
 * <p>The report was always the same: the charged sword was "patÃƒÂ©tico", then "un empujoncito",
 * then simply not there. Each round pushed the numbers further out, and each round the playtest
 * said the same thing. The numbers were never the problem.</p>
 *
 * <p>Two things were wrong, and this class exists for the first one because it is the one a test
 * can actually hold. The cut was dominated by a <b>translation</b>: 0.72 units along Z. A
 * translation displaces every point of a rigid item by the same amount in the same time, so the
 * grip and the tip of the blade move together. That is the definition of a shove, and it is why
 * making it bigger made it a bigger shove rather than a cut.</p>
 *
 * <p>A rotation pivots on the grip, so the tip of the blade sweeps through an arc and travels
 * several times further than the hand does. That differential velocity is what the eye reads as
 * weight, and it is a property of the shape rather than of the magnitude. Vanilla's own cut is
 * {@code ITEM_SWING_X_ROT_AMOUNT = -80} degrees for exactly this reason, read off the 1.21.1 jar.</p>
 *
 * <p>The second thing was the frame the offset was applied in, and no unit test can catch that
 * one: the profile was being applied before the hand had a position, so every rotation was about
 * the camera. It is fixed in {@code ItemInHandRendererMixin} and recorded there.</p>
 */
class CutIsARotationTest {

    private static final ViewModelProfile SWORD = ViewModelDefaults.of(
            com.cortes.combat.core.weapon.WeaponProfile.SWORD);

    /** A frame at the peak of a full-charge cut. */
    private static ViewModelInput atTheCut() {
        return ViewModelInput.chopping(1.0F);
    }

    @Test
    @DisplayName("the cut's motion is angular, not a displacement of the whole item")
    void cutRotatesRatherThanSlides() {
        Offset cut = SWORD.evaluate(atTheCut());

        double travel = Math.sqrt(cut.x() * cut.x() + cut.y() * cut.y() + cut.z() * cut.z());
        double swing = Math.sqrt(cut.pitch() * cut.pitch()
                + cut.yaw() * cut.yaw() + cut.roll() * cut.roll());

        assertTrue(swing > travel,
                "the cut has to be mostly rotation, or the blade is slid rather than swung: "
                        + "rotation " + swing + " against travel " + travel
                        + ". A translation moves the grip and the tip together, which is a shove.");
    }

    @Test
    @DisplayName("the cut is about as far through its arc as vanilla's own")
    void cutArcsAsFarAsVanilla() {
        // ITEM_SWING_X_ROT_AMOUNT is -80 degrees in 1.21.1, verified with javap. A cut that
        // swings a third of that is a twitch, whatever its travel.
        Offset cut = SWORD.evaluate(atTheCut());
        double pitch = Math.abs(cut.pitch());

        assertTrue(pitch > 60.0,
                "the blade should travel most of vanilla's 80 degree arc, was " + pitch);
    }

    @Test
    @DisplayName("the cut goes away from the camera, because negative Z is forward there")
    void cutGoesForward() {
        // The item sits at ITEM_POS_Z = -0.72 in this frame, so negative Z is away from the
        // camera. The previous profile pushed +0.72, which threw the sword at the player's face
        // and is a large part of why the motion read as wrong rather than merely as weak.
        Offset cut = SWORD.evaluate(atTheCut());

        assertTrue(cut.z() < 0.0,
                "a cut forward has to be a negative Z in the item's frame, was " + cut.z());
    }

    @Test
    @DisplayName("the recovery winds the pitch back, so the sword returns to the vanilla pose")
    void recoveryUnwindsTheCut() {
        // Every channel resting at zero has to give the vanilla pose back, or the sword is left
        // pointing at the floor for the rest of the swing. That is the "drifts off the vanilla
        // pose" trap the whole channel design exists to prevent, and the new cut winds in a much
        // bigger pitch than the old one did, so the recovery has to unwind more of it.
        ViewModelMotion motion = chargedAndReleased();
        // The pitch is negative, so the deepest point of the arc is the MINIMUM, not the maximum.
        // Reading it the other way is how the first version of this test asserted a peak of 0.0
        // and then complained that the sword was level, which it very much was not.
        double deepestPitch = 0.0;
        for (int tick = 0; tick <= ViewModelMotion.STRIKE_TICKS; tick++) {
            deepestPitch = Math.min(deepestPitch,
                    SWORD.evaluate(motion.snapshot(0.0F, 0.0F)).pitch());
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        }
        Offset last = SWORD.evaluate(motion.snapshot(0.0F, 0.0F));
        assertTrue(last.isZero(),
                "the swing must end at the vanilla pose, was " + last);
        assertTrue(deepestPitch < -30.0,
                "and the arc must have gone deep on the way, deepest at " + deepestPitch);
    }

    /**
     * Charges to full and releases, with the follow-through <b>not yet started</b>.
     *
     * <p>The separation matters and it cost this test a run. A version of this helper ticked
     * through the whole swing before handing the motion over, so the test that meant to watch
     * the arc only ever saw the tail of the recovery, and reported that a sword cutting through
     * seventy eight degrees never went below twenty four. The motion was fine; the test was
     * arriving after the interesting part.</p>
     */
    private static ViewModelMotion chargedAndReleased() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / 25.0F), Math.min(1.0F, (i + 1) / 25.0F), false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        return motion;
    }
}
