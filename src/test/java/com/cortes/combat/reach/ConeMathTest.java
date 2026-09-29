package com.cortes.combat.reach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry tests for the cone. Runs on a bare JVM, no game bootstrap.
 *
 * <p>If this class ever stops compiling because of a missing Minecraft class, something has
 * leaked a game type into {@link ConeMath}. That is the failure mode worth catching.</p>
 */
class ConeMathTest {

    /** Attacker at the origin, looking straight down +Z. */
    private static final double ORIGIN = 0.0;

    private static boolean hit(double tx, double ty, double tz, double dist, double horiz, double vert) {
        return ConeMath.inCone(
                ORIGIN, 0.0, ORIGIN,
                0.0, 0.0, 1.0,
                tx, ty, tz,
                dist, horiz, vert);
    }

    @Nested
    @DisplayName("distance")
    class Distance {

        @Test
        @DisplayName("a target at exactly the reach limit is inside")
        void atLimit() {
            assertTrue(hit(0, 0, 3.0, 3.0, 45, 45));
        }

        @Test
        @DisplayName("a target one step past the reach limit is outside")
        void pastLimit() {
            assertFalse(hit(0, 0, 3.001, 3.0, 45, 45));
        }

        @Test
        @DisplayName("distance is 3D, so a target at the limit but one block up is outside")
        void diagonalIsLonger() {
            // (0, 1, 3) is sqrt(10) ~= 3.162 blocks away, past a 3.0 limit.
            assertFalse(hit(0, 1, 3.0, 3.0, 45, 45));
        }
    }

    @Nested
    @DisplayName("horizontal angle")
    class Horizontal {

        @Test
        @DisplayName("a target dead ahead is inside")
        void ahead() {
            assertTrue(hit(0, 0, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("a target at the horizontal limit is inside")
        void atLimit() {
            // 35 degrees off axis at 2 blocks: x = 2 * tan(35) ~= 1.400
            assertTrue(hit(1.40, 0, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("a target past the horizontal limit is outside")
        void pastLimit() {
            assertFalse(hit(1.60, 0, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("the cone is symmetric left to right")
        void symmetric() {
            assertTrue(hit(-1.40, 0, 2, 5, 35, 30));
            assertFalse(hit(-1.60, 0, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("anything behind the attacker is outside, with no special case")
        void behind() {
            assertFalse(hit(0, 0, -2, 5, 35, 30));
        }
    }

    @Nested
    @DisplayName("vertical angle")
    class Vertical {

        @Test
        @DisplayName("a target at eye height is inside")
        void level() {
            assertTrue(hit(0, 0, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("a target at the vertical limit is inside")
        void atLimit() {
            // 30 degrees up at 2 blocks forward: y = 2 * tan(30) ~= 1.155
            assertTrue(hit(0, 1.15, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("a target past the vertical limit is outside")
        void pastLimit() {
            assertFalse(hit(0, 1.50, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("below is treated the same as above")
        void below() {
            assertTrue(hit(0, -1.15, 2, 5, 35, 30));
            assertFalse(hit(0, -1.50, 2, 5, 35, 30));
        }

        @Test
        @DisplayName("the vertical limit is independent of the horizontal one")
        void independent() {
            // Far off to the side but level: outside horizontally regardless of the 90 degree
            // vertical allowance.
            assertFalse(hit(3, 0, 1, 6, 35, 90));
        }
    }

    @Nested
    @DisplayName("look direction")
    class LookDirection {

        @Test
        @DisplayName("looking up raises the ceiling of the cone")
        void lookingUp() {
            // Looking 30 degrees up. A target 30 degrees up is now dead centre.
            double r = Math.toRadians(30.0);
            double tx = 0, ty = Math.tan(r) * 2, tz = 2;
            boolean inCone = ConeMath.inCone(
                    0, 0, 0,
                    0, Math.sin(r), Math.cos(r),
                    tx, ty, tz,
                    5, 35, 30);
            assertTrue(inCone);
        }

        @Test
        @DisplayName("looking up means something straight up is now inside")
        void lookingUpVertical() {
            boolean inCone = ConeMath.inCone(
                    0, 0, 0,
                    0, 1, 0,
                    0, 2, 0,
                    5, 35, 30);
            assertTrue(inCone);
        }

        @Test
        @DisplayName("looking straight up accepts what is straight up and rejects what is not")
        void straightUp() {
            // Straight ahead, along the gaze line.
            assertTrue(ConeMath.inCone(
                    0, 0, 0, 0, 1, 0, 0, 2, 0, 5, 35, 30));

            // Level with the attacker, i.e. 90 degrees below the gaze line: rejected by the
            // vertical limit even though the horizontal frame degenerates here.
            assertFalse(ConeMath.inCone(
                    0, 0, 0, 0, 1, 0, 3, 0, 0, 5, 35, 30));
        }
    }

    @Nested
    @DisplayName("degenerate input")
    class Degenerate {

        @Test
        @DisplayName("a target on the attacker's eye is inside")
        void samePoint() {
            assertTrue(hit(0, 0, 0, 5, 35, 30));
        }

        @Test
        @DisplayName("a zero-length look vector is rejected rather than dividing by zero")
        void noLook() {
            boolean inCone = ConeMath.inCone(
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, 1,
                    5, 35, 30);
            assertFalse(inCone);
        }
    }

    @Nested
    @DisplayName("closest point on a box")
    class ClosestPoint {

        @Test
        @DisplayName("clamps below the minimum")
        void below() {
            assertTrue(ConeMath.closestOnAxis(-5, 0, 10) == 0.0);
        }

        @Test
        @DisplayName("clamps above the maximum")
        void above() {
            assertTrue(ConeMath.closestOnAxis(50, 0, 10) == 10.0);
        }

        @Test
        @DisplayName("leaves an inside point alone")
        void inside() {
            assertTrue(ConeMath.closestOnAxis(5, 0, 10) == 5.0);
        }
    }
}
