package com.cortes.combat.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nether reactor's phase two numbers, read on a bare JVM.
 *
 * <p>Every one of these is a number the code acts on and a person cannot check by looking: a fuse
 * one tick short, a warning band one charge wider than it should be, a crater budget that lets a
 * single tick do seconds of work, or a shake that reaches across the map. The block entity needs a
 * running game; the arithmetic does not, and the arithmetic is where these live -- that is the whole
 * reason {@link ReactorOverload} sits below the Minecraft line.</p>
 *
 * <p><b>The explosion measurement is the one that matters most.</b> The reason the star blast is a
 * staged sphere instead of one {@code explode} is a number -- roughly three quarters of a million ray
 * steps and as many candidate positions for a point blast of the nominal power -- and a claim like
 * that is exactly the kind that stops being true and takes the design with it. Asserting it here
 * means the staging cannot be "simplified" back into a single call without a red build.</p>
 */
class ReactorOverloadTest {

    @Test
    @DisplayName("the fuse is fifteen seconds, which is the overheat recording's own length")
    void theFuseIsFifteenSeconds() {
        assertEquals(300, ReactorOverload.FUSE_TICKS, "the fuse is 300 ticks");
        assertEquals(15.0F, ReactorOverload.FUSE_SECONDS, 1.0E-6F, "and that is fifteen seconds");
        assertEquals(ReactorOverload.FUSE_TICKS, 20 * 15,
                "twenty ticks a second for fifteen seconds -- the two recordings round to this");
    }

    @Test
    @DisplayName("the warning band is 31 to 39 and forty lights the fuse")
    void theBandsAreOnePastTheCeilingAndAtTheCeiling() {
        assertEquals(ReactorCharge.MAX_NORMAL + 1, ReactorOverload.WARNING_CHARGE);
        assertEquals(31, ReactorOverload.WARNING_CHARGE, "the design's first overloaded charge");
        assertEquals(40, ReactorOverload.DETONATION_CHARGE, "and the ceiling is where it blows");

        assertFalse(ReactorOverload.isWarning(30), "thirty is the normal maximum, not a warning");
        assertTrue(ReactorOverload.isWarning(31));
        assertTrue(ReactorOverload.isWarning(39));
        assertFalse(ReactorOverload.isWarning(40), "forty is past warning and into the fuse");

        assertFalse(ReactorOverload.detonates(39));
        assertTrue(ReactorOverload.detonates(40));
    }

    @Test
    @DisplayName("the star pushes the energy radius to a hundred, and the charge keeps its own curve")
    void theEnergyRadiusFollowsTheOverload() {
        assertEquals(100, ReactorOverload.STAR_RADIUS);
        assertEquals(100, ReactorOverload.energyRadius(ReactorOverload.Kind.STAR, false, 0),
                "a star reaches a hundred whether or not the reactor is lit: the lever cannot stop it");
        assertEquals(100, ReactorOverload.energyRadius(ReactorOverload.Kind.STAR, true, 5));
        assertEquals(ReactorCharge.radius(30),
                ReactorOverload.energyRadius(ReactorOverload.Kind.NONE, true, 30),
                "without a star, the plain charge curve is the authority");
        assertEquals(0, ReactorOverload.energyRadius(ReactorOverload.Kind.NONE, false, 30),
                "an unlit reactor reaches nowhere, however full it is");
    }

    @Test
    @DisplayName("the sphere volume is the sphere formula and the tick count covers it")
    void theCraterIsMeasuredAsASphere() {
        assertEquals(0L, ReactorOverload.sphereVolume(0));
        assertEquals(4L, ReactorOverload.sphereVolume(1), "round(4/3 pi) is four");

        long volume = ReactorOverload.sphereVolume(ReactorOverload.STAR_DESTRUCTION_RADIUS);
        double exact = 4.0 / 3.0 * Math.PI
                * Math.pow(ReactorOverload.STAR_DESTRUCTION_RADIUS, 3);
        assertTrue(Math.abs(volume - exact) <= 1.0,
                "the volume has to be the formula the scheduler spends, not a rounded copy: got "
                        + volume + " against " + exact);
        assertTrue(volume > 400_000L,
                "the crater is a few hundred thousand blocks, which is why it cannot be one tick");

        long ticks = ReactorOverload.ticksToClear(ReactorOverload.STAR_DESTRUCTION_RADIUS,
                ReactorOverload.BLAST_REMOVALS_PER_TICK);
        assertTrue(ticks * ReactorOverload.BLAST_REMOVALS_PER_TICK >= volume,
                "the budget has to be able to finish the crater: " + ticks + " ticks at "
                        + ReactorOverload.BLAST_REMOVALS_PER_TICK + " does not cover " + volume);
        assertTrue(ticks > 20L, "if this fits twenty ticks the crater is no longer a wave");
        assertTrue(ReactorOverload.ticksToClear(48, 6_000)
                        < ReactorOverload.ticksToClear(48, 3_000),
                "a bigger budget clears sooner -- the relation the scheduler depends on");
    }

    @Test
    @DisplayName("a power-120 point blast is the unshippable number the staging replaces")
    void thePointBlastIsTooBigToRun() {
        assertEquals(4.0F, ReactorOverload.TNT_POWER);
        assertEquals(120.0F, ReactorOverload.STAR_POWER, 1.0E-4F, "thirty times TNT is 120");

        long big = ReactorOverload.rayMarchSteps(120.0F);
        long small = ReactorOverload.rayMarchSteps(ReactorOverload.TNT_POWER);
        assertTrue(big > 700_000L,
                "**the measurement that justifies the staged crater.** A single explode of the "
                        + "nominal power ray-marches the better part of a million block reads in one "
                        + "tick; got " + big);
        assertTrue(Math.abs(big - 30L * small) <= 30L,
                "and it is linear in power, so the figure is the formula and not a guess");
        assertTrue(big > ReactorOverload.sphereVolume(ReactorOverload.STAR_DESTRUCTION_RADIUS),
                "the point blast is larger than the staged crater, which is the trade being made");
    }

    @Test
    @DisplayName("the shake is strong at the core and gone at the radius")
    void theShakeFallsOffWithDistance() {
        float max = ReactorOverload.QUAKE_MAX_DEGREES;
        assertEquals(max, ReactorOverload.shakeDegrees(0.0, max), 1.0E-4F, "strongest at the core");
        assertEquals(0.0F, ReactorOverload.shakeDegrees(ReactorOverload.QUAKE_RADIUS, max), 1.0E-6F,
                "nothing at the edge");
        assertEquals(0.0F, ReactorOverload.shakeDegrees(ReactorOverload.QUAKE_RADIUS + 100.0, max),
                1.0E-6F, "and nothing beyond it");
        assertTrue(ReactorOverload.shakeDegrees(8.0, max) > ReactorOverload.shakeDegrees(40.0, max),
                "closer is stronger");
        assertEquals(ReactorOverload.shakeDegrees(0.0, max),
                ReactorOverload.shakeDegrees(-5.0, max), 1.0E-6F,
                "a negative distance is the core, not a smaller number");
    }

    @Test
    @DisplayName("the blast numbers are present and positive")
    void theBlastNumbersAreSane() {
        assertTrue(ReactorOverload.STAR_DAMAGE_AT_CENTER > 0.0F);
        assertTrue(ReactorOverload.STAR_FIRE_SECONDS > 0.0F);
        assertTrue(ReactorOverload.STAR_KNOCKBACK > 0.0);
        assertTrue(ReactorOverload.BLAST_POSITIONS_PER_TICK > 0);
        assertTrue(ReactorOverload.BLAST_REMOVALS_PER_TICK > 0);
        assertTrue(ReactorOverload.BLAST_POSITIONS_PER_TICK >= ReactorOverload.BLAST_REMOVALS_PER_TICK,
                "there can never be more removals than positions examined");
        assertEquals(245, ReactorOverload.LOOP_INTERVAL,
                "the loop recording is 12.25 seconds, so the interval is its own length");
    }
}
