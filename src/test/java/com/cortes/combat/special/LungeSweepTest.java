package com.cortes.combat.special;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunge's authority model and its hit volume.
 *
 * <p>This file was rewritten twice in one session, and both rewrites were caused by the same thing:
 * <b>a number that was invented instead of measured.</b></p>
 *
 * <p>The first version derived an impulse's distance as {@code impulse / friction} with a friction of
 * 0.6. There is no such friction. {@code Entity#move} ends by multiplying the velocity by
 * {@code getBlockSpeedFactor()}, and {@code BlockBehaviour}'s default {@code speedFactor} is 1.0. So
 * the relation was made up, and it was made up in the direction that made lunges look <em>longer</em>
 * than the ceiling derived from the same made-up number, and thirteen lunges in a row were rejected
 * as illegal before they could hit anything.</p>
 *
 * <p>The second version had the corridor inflated by the weapon's whole reach, to fix "it does not
 * damage", and that fixed the width and broke the design: the blade points where the lunge points, so
 * a mob off to the side that the dash never approaches should not be cut.</p>
 *
 * <p>What is left is a cube of half the weapon's reach around the player, and a ceiling derived from a
 * distance measured in the log.</p>
 */
class LungeSweepTest {

    /**
     * The ropera's reach, read from the table rather than written down here.
     *
     * <p>An earlier version hardcoded 3.4 and got it right by luck, and the two failures that came out
     * of the edit around it were both the test's arithmetic rather than the code's. A number that
     * lives in two places is a number one of them will get wrong.</p>
     */
    private static final double ROPERA_REACH =
            WeaponTables.reach(WeaponProfile.ROPERA).distance();

    // ------------------------------------------------------- the measured relation --

    /**
 * The impulse-to-distance ratio is the number the whole ceiling rests on, so it is pinned.
 *
 * <p>It took four rounds to get a measurement of it, and two of those rounds were spent measuring
 * the wrong thing: once from a run with {@code /cc speed} going, and once from a formula that
 * assumed an entity's velocity is scaled by the block's slipperiness. It is not --
 * {@code BlockBehaviour}'s default {@code speedFactor} is 1.0.</p>
 *
 * <p>Three clean runs, standing still, no harness command: 6.01 from an impulse of 1.12, 6.71 from
 * 1.08, 6.76 from 1.12.</p>
     */
    @Test
    @DisplayName("the distance an impulse covers is the one the log measured")
    void theRelationIsTheMeasuredOne() {
        assertEquals(6.0, LungeDamage.DISTANCE_PER_IMPULSE, 1.0E-9,
                "from three clean runs: 6.01, 6.71 and 6.76 blocks. It is not a friction constant and "
                        + "must never be written as one -- there is no friction to read");
        assertEquals(LungeDamage.BASE_IMPULSE * LungeDamage.DISTANCE_PER_IMPULSE,
                LungeDamage.estimatedDistance(0), 1.0E-9,
                "a wooden ropera covers about " + round(LungeDamage.estimatedDistance(0))
                        + " blocks. An estimate from a measurement, not a promise");
    }

    // ------------------------------------------------------------------ the ceiling --

    /**
     * The bound is a cheating bound, and the property that matters is that no honest lunge reaches it.
     *
     * <p>This is the test that should have existed before the ceiling did. Thirteen lunges in a row
     * were refused by a bound derived from the same invented friction, and every one of them ended
     * before its damage code ran, which is why the playtest reported a lunge that neither dealt
     * damage nor went invulnerable.</p>
     */
    @Test
    @DisplayName("no honest lunge reaches the ceiling")
    void theCeilingIsWellAboveTheLunge() {
        for (int rank = 0; rank <= 6; rank++) {
            double real = LungeDamage.estimatedDistance(rank);
            double ceiling = LungeDamage.ceilingFor(rank);
            assertTrue(ceiling > real * 2.0,
                    "rank " + rank + " covers about " + round(real) + " blocks and the bound is "
                            + round(ceiling) + ". The margin has to survive a lunge taken at a run, "
                            + "since push is additive, and the /cc speed harness, which multiplies "
                            + "the player's own movement");
        }
    }

    /**
     * The bound is derived from the impulse, and this is the test for why.
     *
     * <p>The ceiling used to be its own pair of constants beside the impulse's: four numbers
     * describing two curves, and retuning one left the other behind. It was then derived from a
     * friction that does not exist, which is the same failure wearing a better disguise.</p>
     */
    @Test
    @DisplayName("the ceiling moves with the impulse, because it is computed from it")
    void theCeilingCannotDriftAwayFromTheImpulse() {
        for (int rank = 0; rank <= 6; rank++) {
            assertEquals(LungeDamage.estimatedDistance(rank) * LungeDamage.CEILING_MARGIN,
                    LungeDamage.ceilingFor(rank), 1.0E-9,
                    "rank " + rank + ": the bound is a second description of the same curve as the "
                            + "impulse. There is one number to retune now and this fails if that "
                            + "stops being true");
        }
        assertEquals(18.0, LungeDamage.ceilingFor(0), 1.0E-6,
                "a wooden ropera covers about six and is allowed eighteen. The margin has to clear a "
                        + "lunge taken at a run and the /cc speed harness, which roughly doubles it");
    }

    @Test
    @DisplayName("and it still stops a client that pushes itself four times too hard")
    void theCeilingStillBites() {
        for (int rank = 0; rank <= 6; rank++) {
            double cheated = LungeDamage.estimatedDistance(rank) * 4.0;
            assertFalse(LungeDamage.legal(rank, cheated),
                    "rank " + rank + ": a client that covered " + round(cheated)
                            + " blocks against a bound of " + round(LungeDamage.ceilingFor(rank))
                            + " was let through. It gets no damage and no invulnerability for it");
        }
    }

    @Test
    @DisplayName("a negative tier is the lowest material, not a backwards bonus")
    void aNegativeTierIsTheLowestMaterial() {
        assertEquals(LungeDamage.impulseFor(0), LungeDamage.impulseFor(-5), 1.0E-9);
        assertEquals(LungeDamage.ceilingFor(0), LungeDamage.ceilingFor(-5), 1.0E-9);
    }

    // ------------------------------------------------------------------- the cube --

    /**
     * The cube is half the weapon's reach around the player, which is about twice the player.
     *
     * <p>And <b>not</b> the whole reach, which is what the previous version used. That was reasoning
     * about a lunge that never happens: the player travels. A mob three blocks ahead is one and a
     * third blocks away by the time the dash finishes, comfortably inside this cube, so the lunge
     * still reaches what a standing swing reaches. What the whole reach got wrong was the mobs
     * <em>beside</em> the dash, which the blade never approached and which a six point eight wide box
     * would have cut through.</p>
     */
    /**
     * The cube is half the weapon's reach, and a bit bigger than the player.
     *
     * <p><b>And not the whole reach, which is what the version before this used.</b> That was
     * reasoning about a lunge that never happens: the player travels. A mob three blocks ahead is
     * one and a third blocks away by the time the dash finishes, comfortably inside this cube, so the
     * lunge still reaches what a standing swing reaches. What the whole reach got wrong was the mobs
     * <em>beside</em> the dash, which the blade never approached and which a seven wide box would
     * have cut through.</p>
     *
     * <p>It was briefly four tenths of the reach, because the lunge was reported as overpowered and
     * this was the number that looked like the radius. It was the wrong one: the player reads the
     * <em>swing's</em> cone as the weapon's reach, and that is what the retune belonged on. The cube
     * follows the weapon on its own, so a fraction on top of it was a second place for one axis.</p>
     */
    @Test
    @DisplayName("the cube is half the reach, and a bit bigger than the player")
    void theCubeIsAFractionOfTheReach() {
        double[] cube = LungeDamage.cube(10.0, 64.0, 20.0, ROPERA_REACH);
        double half = ROPERA_REACH * LungeDamage.CUBE_RADIUS;

        assertEquals(0.5, LungeDamage.CUBE_RADIUS, 1.0E-9,
                "half the reach, and not a number of its own on top of it");
        assertEquals(half, cube[3] - 10.0, 1.0E-9);
        assertEquals(half, 10.0 - cube[0], 1.0E-9);
        assertEquals(half, cube[5] - 20.0, 1.0E-9);
        assertEquals(1.75, half, 1.0E-6,
                "and it follows the weapon: the ropera's reach is three and a half now, so the cube "
                        + "grew by five hundredths of a block when the cone did");

        // A cube, so all three axes are the same, centred on the player's centre rather than the feet.
        double size = cube[3] - cube[0];
        assertEquals(size, cube[4] - cube[1], 1.0E-9, "it is a cube, not a corridor");
        assertEquals(size, cube[5] - cube[2], 1.0E-9);
        assertEquals(64.0 + LungeDamage.PLAYER_CENTRE_HEIGHT, (cube[1] + cube[4]) * 0.5, 1.0E-9,
                "centred on the player's middle. Anchored at the feet it reaches a body height below "
                        + "the ground and half as high again above the head");

        // And it is a bit bigger than the player, which is the whole argument for the shape.
        assertTrue(half > 0.3 && half > LungeDamage.PLAYER_CENTRE_HEIGHT,
                "the cube's half extent is " + round(half) + ", which should be about twice the "
                        + "player's own half width and half height");
    }

    /**
     * A stationary lunge still covers the reach, and it does not grow.
     *
     * <p>The version that swept a corridor could double its own width as the player moved, because
     * the box was drawn from the last position to this one and inflated at both ends. The cube is
     * the same size everywhere, which is the property that makes it predictable enough to reason
     * about.</p>
     */
    @Test
    @DisplayName("and the same size wherever the player is")
    void theCubeDoesNotGrowWithMovement() {
        double[] still = LungeDamage.cube(0.0, 64.0, 0.0, ROPERA_REACH);
        double[] after = LungeDamage.cube(2.5, 64.0, -1.5, ROPERA_REACH);
        double[] midway = LungeDamage.cube(1.0, 64.0, -0.5, ROPERA_REACH);

        assertEquals(still[3] - still[0], after[3] - after[0], 1.0E-9);
        assertEquals(still[3] - still[0], midway[3] - midway[0], 1.0E-9);
        // And it followed the body, rather than staying where the lunge began.
        assertEquals(1.0, midway[0] - still[0], 1.0E-9);
    }

    // -------------------------------------------------------------- the duration --

    /**
     * The window ends when the player stops, which is what makes the immunity the length of the dash.
     *
     * <p>Twenty ticks was nearly double the movement, so half of every lunge was a player standing
     * perfectly still, untouchable, while a sweep described a standstill. The invulnerability had to
     * be exactly as long as the dash to be a thing the player could perceive, and there is no way to
     * perceive a fixed number.</p>
     */
    @Test
    @DisplayName("the lunge is over when the player stops moving, not when a counter runs out")
    void theLungeEndsWhenThePlayerStops() {
        assertTrue(LungeDamage.stopped(0.0), "a player who did not move at all");
        assertTrue(LungeDamage.stopped(LungeDamage.STILL * 0.5),
                "below the precision of the position report");
        assertFalse(LungeDamage.stopped(LungeDamage.STILL * 2));
        assertFalse(LungeDamage.stopped(0.4), "a dash covers about 0.4 a tick to begin with");
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}