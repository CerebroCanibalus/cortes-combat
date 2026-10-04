package com.cortes.combat.special;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the arcabuz's report is drawn where the gun is, and in the amounts it should be.
 *
 * <p><b>This class exists because the flash was coming out of the camera, twice.</b> It was spawned
 * at {@code eye + look * MUZZLE_OFFSET}, which is a point <em>on the view axis</em> -- dead centre,
 * where the crosshair is, where the camera is. The number was retuned from 0.6 to 1.7 and the
 * complaint never changed, because the distance was never the problem: the two missing components
 * were. So the two tests that matter here are the ones that assert the muzzle has a lateral and a
 * vertical offset at all.</p>
 */
class ArcabuzMuzzleTest {

    /**
     * The bug, as an assertion.
     *
     * <p>Every one of these three would be satisfied by the old code if it had been written as one
     * number: the flash was in front of the player, at a distance that had been retuned twice. What
     * makes it read as the screen rather than as a weapon is that it had no sideways and no up.</p>
     */
    @Test
    @DisplayName("the muzzle is beside and below the eye, not on the view axis")
    void theMuzzleIsNotOnTheViewAxis() {
        assertTrue(ArcabuzMuzzle.RIGHT > 0.0,
                "the flash was dead centre because it had no sideways offset at all. The gun is in "
                        + "the right hand, so it is to the right of the eye");
        assertTrue(ArcabuzMuzzle.UP < 0.0,
                "and below it, because the drawn weapon is carried at hip height");
        assertTrue(ArcabuzMuzzle.FORWARD > 0.0, "and in front, which is the one it had");
    }

    /**
     * The flash is a compact blob and the smoke is a lot of it.
     *
     * <p>Both halves, because either alone passes for the wrong reason: one particle of smoke on a
     * gun that just killed four things is a decoration, and a wide flash is an explosion, which is
     * the thing the shot's damage explicitly is not.</p>
     */
    @Test
    @DisplayName("the flash is tight, the smoke is a lot, and only the smoke drifts")
    void theTwoBurstsAreDifferentBursts() {
        assertEquals(3, ArcabuzMuzzle.FLASH_COUNT, "a flash is a flash: a dot is a spark");
        assertTrue(ArcabuzMuzzle.FLASH_SPREAD <= 0.05,
                "and it is at the barrel, not scattered: a wide one is an explosion");
        assertEquals(0.0, ArcabuzMuzzle.bursts(new double[]{0, 0, 1})[0].speed(),
                "the flash does not travel. Its speed being zero is what makes it a blob at the "
                        + "muzzle rather than a spray across the room");

        assertEquals(18, ArcabuzMuzzle.SMOKE_COUNT,
                "eighteen, and this is the number the request was really about: a black powder shot "
                        + "produces a great deal of smoke and it is most of what the player sees");
        assertTrue(ArcabuzMuzzle.SMOKE_SPREAD > ArcabuzMuzzle.FLASH_SPREAD,
                "the cloud is a much wider volume than the flash");
        assertTrue(ArcabuzMuzzle.SMOKE_SPEED > 0.0,
                "and it has velocity, so it expands away from the muzzle instead of sitting on it");
    }

    /**
     * Four towards each thing hit, and six into the air when nothing was hit.
     *
     * <p>The second half is the requirement and not an afterthought: a gun that fired into an empty
     * field and showed nothing leaves the player unable to tell a wasted shot from a laggy one, and
     * both of those read as the weapon being broken.</p>
     */
    @Test
    @DisplayName("a blast throws four at each thing it hit, and six at nothing")
    void theLavaFollowsWhatTheShotDid() {
        assertEquals(4 * 10, ArcabuzMuzzle.lavaCount(10), "ten things, four each");
        assertEquals(4, ArcabuzMuzzle.lavaCount(1), "one thing, four");
        assertEquals(6, ArcabuzMuzzle.lavaCount(0), "nothing, six");

        assertTrue(ArcabuzMuzzle.drewAtSomething(10));
        assertFalse(ArcabuzMuzzle.drewAtSomething(0),
                "the two cases differ in more than a count -- one draws streaks towards specific "
                        + "points and one draws scatter -- and this is the single question that "
                        + "decides which");
    }

    /**
     * And a full blast still looks like a blast next to a whiff.
     *
     * <p>Six against forty. If the whiff were as loud as a good shot the player could not read
     * their own shot, which is the one thing the effect exists to let them do.</p>
     */
    @Test
    @DisplayName("a whiff is quieter than a blast that hit everything")
    void aWhiffIsQuieter() {
        assertTrue(ArcabuzMuzzle.LAVA_WITHOUT_TARGETS < ArcabuzMuzzle.lavaCount(10),
                "six against forty. If they were the same the effect would be saying nothing");
    }

    /**
     * The random direction is uniform over the sphere, not over a box.
     *
     * <p><b>Sampled, because this is the one property a "looks fine" check cannot see.</b> Spreading
     * with {@code random(-1, 1)} per component gives a cube, and a cube is visibly biased: the
     * particles drift the same way along each axis and the scatter reads as a spray that was blown
     * rather than as one that came out of a barrel. The test looks at the distribution of the
     * length of each vector and of each component.</p>
     */
    @Test
    @DisplayName("every random direction is a unit vector")
    void randomDirectionsAreUnitLength() {
        for (int i = 0; i < 500; i++) {
            double[] d = ArcabuzMuzzle.randomDirection();
            double length = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            assertEquals(1.0, length, 1.0E-6, "a scatter direction of length " + length);
        }
    }

    @Test
    @DisplayName("and the spread is even, not a cube's bias")
    void theScatterIsNotACubesBias() {
        // Sum of |component| for a uniform unit vector is 1.5. For a vector uniform in a cube and
        // then normalised it is nearer 1.73, and the difference is what the eye sees as a spray
        // blowing one way.
        double sum = 0.0;
        int samples = 20_000;
        for (int i = 0; i < samples; i++) {
            double[] d = ArcabuzMuzzle.randomDirection();
            sum += Math.abs(d[0]) + Math.abs(d[1]) + Math.abs(d[2]);
        }
        double mean = sum / samples;
        assertTrue(Math.abs(mean - 1.5) < 0.05,
                "the mean of |x|+|y|+|z| over a sphere is 1.5 and this was " + mean
                        + ". Anything nearer 1.73 is a cube and will read as a spray with a "
                        + "direction");
    }

    /** Every component stays inside the unit ball, which a cube's would not. */
    @Test
    @DisplayName("no component leaves the unit sphere")
    void noComponentLeavesTheSphere() {
        for (int i = 0; i < 2_000; i++) {
            double[] d = ArcabuzMuzzle.randomDirection();
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(Math.abs(d[axis]) <= 1.0,
                        "a component of " + d[axis] + " on a unit vector");
            }
        }
    }
}