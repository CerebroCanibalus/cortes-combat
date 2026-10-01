package com.cortes.combat.special;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunge's steering, which is the fix for "it pulls me forward no matter what I do".
 *
 * <p>Both extremes shipped. A lunge locked to the direction of the press is a rail, and a lunge
 * re-read from the camera every tick is a flight the player can curve at will. The steering share is
 * the one number between them, and these are the properties that make it a compromise rather than
 * one of the two mistakes wearing a hat.</p>
 */
class LungeSteeringTest {

    private static double[] unit(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        return new double[]{x / length, z / length};
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1]);
    }

    /**
     * Looking the same way changes nothing.
     *
     * <p>The base case, and the one a naive blend breaks: with no difference in direction the
     * result must be the committed direction exactly, or every lunge drifts even when the player
     * never touches the mouse.</p>
     */
    @Test
    @DisplayName("looking the same way leaves the lunge exactly where it was aimed")
    void noChangeNoChange() {
        double[] forward = unit(0.0, 1.0);
        double[] result = LungeDamage.steered(forward, unit(0.0, 1.0));
        assertEquals(0.0, result[0], 1.0E-9);
        assertEquals(1.0, result[1], 1.0E-9);
    }

    /**
     * Turning bends the lunge instead of rewriting it.
     *
     * <p>This is the complaint itself. Look to the side and the step should lean there, but the
     * committed direction has to still be most of it -- otherwise the press is not a commitment
     * and the lunge is something the camera drives.</p>
     */
    @Test
    @DisplayName("turning bends the lunge without taking it over")
    void turningBendsIt() {
        double[] committed = unit(0.0, 1.0);          // forward
        double[] toTheRight = unit(1.0, 0.0);

        double[] result = LungeDamage.steered(committed, toTheRight);

        assertTrue(result[0] > 0.0,
                "it leans toward where the player looks: " + result[0] + " of sideways");
        assertTrue(result[1] > 0.0,
                "but it keeps going forward as well: " + result[1]
                        + ". A lunge that turned ninety degrees on a sideways glance is not a lunge");
        // The forward component has to dominate, because the press decides where it goes.
        assertTrue(result[1] > result[0],
                "committed direction is the spine of it");
    }

    /**
     * Looking backwards fights the lunge without reversing it.
     *
     * <p>Damped on purpose. A straight reverse would put the player moving backwards at full speed
     * on a press aimed forwards, which reads as the game throwing them -- and it would make the
     * distance a tier promises depend on where the mouse happened to be.</p>
     */
    @Test
    @DisplayName("looking backwards fights it instead of reversing it")
    void backwardsIsDamped() {
        double[] committed = unit(0.0, 1.0);
        double[] result = LungeDamage.steered(committed, unit(0.0, -1.0));

        assertTrue(result[1] > 0.0,
                "the lunge still goes where the press aimed: " + result[1]
                        + ". A full reverse would send the player the other way at the same speed, "
                        + "which is the game throwing them rather than a lunge");
    }

    /**
     * The result is always unit length.
     *
     * <p>The bug a blended direction always has: if the caller multiplies a step length by the
     * result, a vector longer than one makes a diagonal lunge faster than a straight one. A netherite
     * lunge aimed into a wall would cover more ground than one aimed down a corridor.</p>
     */
    @Test
    @DisplayName("the result is unit length whatever the angle")
    void alwaysUnitLength() {
        double[] committed = unit(0.0, 1.0);
        for (int degrees = 0; degrees <= 360; degrees += 15) {
            double rad = Math.toRadians(degrees);
            double[] look = unit(Math.sin(rad), Math.cos(rad));
            double[] result = LungeDamage.steered(committed, look);
            assertEquals(1.0, length(result), 1.0E-9,
                    "at " + degrees + " degrees the step came out " + length(result)
                            + " long, and a longer vector travels further per tick");
        }
    }

    /** A zero look vector is survivable rather than a division by zero on the movement tick. */
    @Test
    @DisplayName("a zero look direction leaves the lunge alone")
    void aZeroLookIsSurvivable() {
        double[] committed = unit(0.0, 1.0);
        double[] result = LungeDamage.steered(committed, new double[]{0.0, 0.0});
        assertTrue(length(result) > 0.5,
                "it comes out usable rather than as a NaN, because a player looking straight up or "
                        + "down has a horizontal look vector of zero on the server");
    }

    @Test
    @DisplayName("and the share is a third, which is the compromise between a rail and a flight")
    void theShareIsTheCompromise() {
        assertTrue(LungeDamage.STEER_SHARE > 0.15 && LungeDamage.STEER_SHARE < 0.5,
                "at " + LungeDamage.STEER_SHARE + " the player can argue with the lunge without steering "
                        + "it. Under about 0.15 the lunge is the rail the playtest complained about; "
                        + "over about 0.5 it is the flight");
    }
}