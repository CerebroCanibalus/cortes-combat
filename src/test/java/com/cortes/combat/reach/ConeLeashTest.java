package com.cortes.combat.reach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a fixed angle is the wrong shape for a held weapon up close, and what replaces it.
 *
 * <p>This is the bug a playtest reported as "the cone gets hard to hit with as it narrows, and
 * worst of all near the player". Both halves of that were true and only one of them was
 * horizontal.</p>
 *
 * <p><b>The horizontal was never the problem.</b> It is measured to the nearest point of the
 * target's box, and that point slides sideways along the box's near face, so the target hands
 * back its own half width as free tolerance: a ropera lets you miss a zombie by nearly a metre at
 * full reach.</p>
 *
 * <p><b>The vertical was the whole problem.</b> The player's eye is at 1.62 and a zombie's box
 * runs from 0 to 1.95, so the eye is <em>inside</em> the box and the nearest point sits at exactly
 * eye height. The vertical test therefore collapses to "how far below the horizon are you
 * looking" -- and looking at a zombie one block away means looking thirty three degrees down,
 * against a limit of fifteen. The target's one and a half blocks of height contributed nothing,
 * because the point they were measured at was not one of them.</p>
 *
 * <p>Measured, not guessed. The distance from which pointing at a zombie starts to work was
 * 2.62m for a ropera with 3.4m of reach, 1.77m for the bare hands with 2.5m, and 0.72m for the
 * warhammer. The ordering is the ordering of the vertical angles, which is why it read as a
 * complaint about narrowness when it is one specific axis on one specific weapon.</p>
 */
class ConeLeashTest {

    // A player standing, eye height.
    private static final double EYE = 1.62;
    // A zombie: 0.6 wide, 1.95 tall, feet on the ground. Its box spans z from 0.7 to 1.3, so its
    // near face is 0.7 away and its centre is 1.0.
    private static final double ZOMBIE_HALF_WIDTH = 0.3;
    private static final double ZOMBIE_TOP = 1.95;
    private static final double ZOMBIE_HALF_HEIGHT = ZOMBIE_TOP / 2.0;

    /** The ropera's limits, the worst case in the game. */
    private static final double ROPERA_REACH = 3.4;
    private static final double ROPERA_H = 12.0;
    private static final double ROPERA_V = 15.0;

    /** Looks at a point, from the eye. */
    private static double[] lookAt(double x, double y, double z) {
        double dx = x;
        double dy = y - EYE;
        double dz = z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[]{dx / len, dy / len, dz / len};
    }

    /**
     * The nearest point of the zombie's box to the eye, which is the point {@code ConeTargeting}
     * hands the test. At a distance where the eye is inside the box vertically this is the eye's
     * own height, and that single fact is the bug.
     */
    private static double[] nearestPointOnZombie(double nearZ) {
        return new double[]{
                0.0,
                Math.min(Math.max(EYE, 0.0), ZOMBIE_TOP),
                Math.min(Math.max(0.0, nearZ), nearZ + 0.6)};
    }

    private static boolean roperaHits(double[] target, double[] look,
                                      double leashWidth, double leashHeight) {
        return ConeMath.inCone(
                0.0, EYE, 0.0,
                look[0], look[1], look[2],
                target[0], target[1], target[2],
                ROPERA_REACH, ROPERA_H, ROPERA_V,
                leashWidth, leashHeight);
    }

    @Nested
    @DisplayName("the reported bug")
    class TheReport {

        @Test
        @DisplayName("a ropera one block from a zombie misses a player looking straight at it")
        void theOldAnswerWasNo() {
            // Looking at the zombie's centre, one block away. The nearest point handed to the test
            // is at eye height, 0.7 in front, so the vertical offset works out to 32.8 degrees
            // against a limit of 15.
            double[] target = nearestPointOnZombie(0.7);
            double[] look = lookAt(0.0, ZOMBIE_HALF_HEIGHT, 1.0);

            assertFalse(roperaHits(target, look, 0.0, 0.0),
                    "this is the shape of the bug: aiming at a zombie one block away and missing");
        }

        @Test
        @DisplayName("the same swing connects once the target's height is worth something")
        void theNewAnswerIsYes() {
            double[] target = nearestPointOnZombie(0.7);
            double[] look = lookAt(0.0, ZOMBIE_HALF_HEIGHT, 1.0);

            assertTrue(roperaHits(target, look,
                            ConeMath.leash(ZOMBIE_HALF_WIDTH),
                            ConeMath.leash(ZOMBIE_HALF_HEIGHT)),
                    "a zombie one block away, aimed at, has to be a hit");
        }

        @Test
        @DisplayName("the offset it accepts is the leash and not a coincidence")
        void itIsTheLeashDoingIt() {
            // Spell the numbers out rather than trusting a boolean. The perpendicular vertical
            // offset is 0.379 blocks; the leash is 0.634; the angle's own tolerance at this depth
            // is 0.157. So the leash is what carries it, four times over.
            double[] target = nearestPointOnZombie(0.7);
            double[] look = lookAt(0.0, ZOMBIE_HALF_HEIGHT, 1.0);
            ConeMath.Frame frame = ConeMath.frame(look[0], look[1], look[2]);

            double dx = target[0];
            double dy = target[1] - EYE;
            double dz = target[2];
            double along = dx * frame.fx() + dy * frame.fy() + dz * frame.fz();
            double vertical = Math.abs(dx * frame.ux() + dy * frame.uy() + dz * frame.uz());

            assertEquals(0.588, along, 0.002, "depth along the gaze");
            assertEquals(0.379, vertical, 0.002, "vertical offset of the nearest point");
            assertTrue(vertical > along * ConeMath.tangent(ROPERA_V),
                    "and that offset is over the angle's limit, which is why the old test said no");
            assertTrue(vertical <= ConeMath.leash(ZOMBIE_HALF_HEIGHT),
                    "and under the leash, which is why it says yes now");
        }
    }

    @Nested
    @DisplayName("the leash itself")
    class TheLeash {

        @Test
        @DisplayName("it is two thirds of the target, up to a ceiling")
        void scalesThenCaps() {
            assertEquals(0.65 * 0.3, ConeMath.leash(0.3), 1.0E-9, "a zombie's width");
            assertEquals(0.65 * 0.975, ConeMath.leash(ZOMBIE_HALF_HEIGHT), 1.0E-9,
                    "a zombie's height, uncapped");
            // An eight block tall dragon would earn five metres of tolerance on this rule alone,
            // which means the narrowest weapon in the game connects without the player pointing
            // at anything. Capping makes a huge target proportionally harder than an ordinary one.
            assertEquals(ConeMath.LEASH_CAP, ConeMath.leash(8.0), 1.0E-9, "the dragon is capped");
        }

        @Test
        @DisplayName("a small mob earns less than a large one")
        void smallTargetsStayHard() {
            double slime = ConeMath.leash(0.45);
            double zombie = ConeMath.leash(ZOMBIE_HALF_HEIGHT);
            assertTrue(slime < zombie,
                    "if every target earned the same leash, size would not mean anything");
            assertEquals(0.2925, slime, 1.0E-9);
        }

        @Test
        @DisplayName("nothing to measure means no leash")
        void degenerateInput() {
            assertEquals(0.0, ConeMath.leash(0.0));
            assertEquals(0.0, ConeMath.leash(-1.0));
            assertEquals(0.0, ConeMath.leash(Double.NaN));
        }
    }

    @Nested
    @DisplayName("what the leash must not do")
    class WhatItMustNotDo {

        @Test
        @DisplayName("it does not reach behind the player")
        void backwardsIsStillBackwards() {
            // The one place the rewrite could have gone wrong quietly. The old test measured
            // atan2(offset, along), which returns more than ninety degrees for anything behind and
            // so failed every limit without a special case. Comparing raw offsets has no such
            // property: a target straight behind has a lateral offset of exactly zero, and zero is
            // inside any leash at all. Without an explicit check the narrowest weapon in the game
            // would hit whatever is behind the player's head.
            double[] look = new double[]{0.0, 0.0, 1.0};
            assertFalse(roperaHits(new double[]{0.0, EYE, -0.7}, look, 0.8, 0.8),
                    "a target behind the player, with the widest leash the cap allows");
            assertFalse(roperaHits(new double[]{0.5, EYE, -0.7}, look, 0.8, 0.8),
                    "and one behind and to the side");
            assertFalse(roperaHits(new double[]{0.0, EYE - 0.5, -0.7}, look, 0.8, 0.8),
                    "and one behind and below");
        }

        @Test
        @DisplayName("it does not reach past the reach")
        void theReachStillBounds() {
            double[] look = new double[]{0.0, 0.0, 1.0};
            assertFalse(roperaHits(new double[]{0.0, EYE, ROPERA_REACH + 0.01}, look,
                            ConeMath.leash(ZOMBIE_HALF_WIDTH),
                            ConeMath.leash(ZOMBIE_HALF_HEIGHT)),
                    "a zombie just past the ropera's reach is still out of reach");
        }

        @Test
        @DisplayName("at range it stops mattering, and the angle takes over")
        void theAngleTakesOverAtRange() {
            // The crossover is leash / tan(limit): 0.634 / 0.268 = 2.37m. Past that the angle is
            // the wider term and the leash is inert, which is the whole reason this is a maximum
            // rather than an extra 0.634 bolted on the side.
            double leash = ConeMath.leash(ZOMBIE_HALF_HEIGHT);
            double crossover = leash / ConeMath.tangent(ROPERA_V);
            assertEquals(2.37, crossover, 0.01, "the ropera hands precision back here");

            // At 2m the leash governs, and a target 0.6 off axis is outside the angle's 0.536.
            assertTrue(roperaHits(new double[]{0.0, EYE + 0.6, 2.0}, new double[]{0.0, 0.0, 1.0},
                            ConeMath.leash(ZOMBIE_HALF_WIDTH), leash),
                    "the leash is carrying this one");
            assertFalse(roperaHits(new double[]{0.0, EYE + 0.6, 2.0}, new double[]{0.0, 0.0, 1.0},
                            0.0, 0.0),
                    "and the angle alone would have refused it");

            // At 3m the angle's own tolerance is 0.804, wider than the leash, so 1.0 off axis is
            // refused no matter how big the target is.
            assertFalse(roperaHits(new double[]{0.0, EYE + 1.0, 3.0}, new double[]{0.0, 0.0, 1.0},
                            ConeMath.leash(ZOMBIE_HALF_WIDTH), leash),
                    "at range the leash must be inert, or the weapon has no precision left");
        }

        @Test
        @DisplayName("without it, the short form and the long form agree everywhere")
        void zeroLeashIsTheOriginalRule() {
            // Every existing cone test keeps meaning what it meant, which is why this is an
            // overload and not a changed signature. Asserted as an equivalence over a spread of
            // geometries rather than as two hand-picked cases: the first version of this test
            // picked a target dead ahead at eye height and called it "refused up close", which
            // the old rule has always accepted -- zero degrees off the gaze is inside any limit,
            // and always was. The assertion was describing a rule that did not exist.
            double[][] looks = {
                    {0.0, 0.0, 1.0},
                    lookAt(0.0, ZOMBIE_HALF_HEIGHT, 1.0),
                    lookAt(0.0, 0.0, -1.0),
                    lookAt(0.0, EYE, 1.0),
            };
            double[][] targets = {
                    {0.0, EYE, 0.7},
                    {0.0, EYE + 0.379, 0.7},
                    {0.4, EYE, 1.2},
                    {0.0, EYE, -0.7},
                    {0.0, EYE, ROPERA_REACH + 0.5},
            };
            int checked = 0;
            int accepted = 0;
            for (double[] look : looks) {
                for (double[] target : targets) {
                    boolean viaLongForm = ConeMath.inCone(
                            0.0, EYE, 0.0,
                            look[0], look[1], look[2],
                            target[0], target[1], target[2],
                            ROPERA_REACH, ROPERA_H, ROPERA_V, 0.0, 0.0);
                    boolean viaShortForm = ConeMath.inCone(
                            0.0, EYE, 0.0,
                            look[0], look[1], look[2],
                            target[0], target[1], target[2],
                            ROPERA_REACH, ROPERA_H, ROPERA_V);
                    assertEquals(viaShortForm, viaLongForm,
                            "the two forms disagreed on look=" + look[1]
                                    + " target=(" + target[0] + "," + target[1] + "," + target[2] + ")");
                    checked++;
                    if (viaShortForm) {
                        accepted++;
                    }
                }
            }
            assertEquals(checked, 20, "the sweep ran");
            // If the sweep could not tell a hit from a miss the equivalence above would be
            // vacuously true, so it has to have crossed the boundary at least once.
            assertTrue(accepted > 0 && accepted < checked,
                    "the sweep exercised both answers, it only agreed about nothing");
        }
    }

    @Nested
    @DisplayName("tangent")
    class Tangent {

        @Test
        @DisplayName("a limit is clamped rather than trusted")
        void degenerateLimits() {
            // The old code never needed a tangent because it compared angles on both sides. It
            // does now, and a data override is free to write a zero or a negative or ninety
            // exactly, and a limit of ninety would divide by nothing.
            assertEquals(0.0, ConeMath.tangent(0.0));
            assertEquals(0.0, ConeMath.tangent(-30.0));
            assertEquals(0.0, ConeMath.tangent(Double.NaN));
            assertEquals(Math.tan(Math.toRadians(89.0)), ConeMath.tangent(90.0), 1.0E-9);
            assertEquals(Math.tan(Math.toRadians(15.0)), ConeMath.tangent(15.0), 1.0E-9);
        }
    }
}