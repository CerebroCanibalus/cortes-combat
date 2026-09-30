package com.cortes.combat.reach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("the cone follows the gaze, not the ground")
    class GazeAligned {

        /**
         * A helper that looks along an arbitrary direction and asks about a world point.
         * Every test in here would fail on the previous ground-anchored frame, which is the
         * whole point: a test that passes on both versions is not testing the change.
         */
        private static boolean lookAndHit(double lx, double ly, double lz,
                                          double tx, double ty, double tz) {
            return ConeMath.inCone(0, 0, 0, lx, ly, lz, tx, ty, tz, 10, 35, 30);
        }

        @Test
        @DisplayName("a target 20 degrees off a raised gaze is inside, 36 on the ground frame")
        void raisedGazeWidensTheSidewaysCone() {
            // Looking 60 degrees up. A point 20 degrees to the side of that gaze line is the
            // thing the player is obviously aiming at.
            //
            // The old frame measured "horizontal" against the ground projection, so the same
            // point came out at 36 degrees and was rejected by a 35 degree limit. That is the
            // bug: the player is looking at it, and the cone was arguing.
            double g = Math.toRadians(60.0);
            double fx = 0.0, fy = Math.sin(g), fz = Math.cos(g);
            double s = Math.sin(Math.toRadians(20.0));
            double c = Math.cos(Math.toRadians(20.0));
            // 20 degrees to the side of the gaze, in the horizontal-ish right direction.
            double tx = -s * 2.0;
            double ty = fy * c * 2.0;
            double tz = fz * c * 2.0;
            assertTrue(lookAndHit(fx, fy, fz, tx, ty, tz),
                    "a point beside a raised gaze must be inside the cone");
        }

        @Test
        @DisplayName("a target beside a raised gaze is still rejected when it is truly too far")
        void raisedGazeStillHasALimit() {
            // The fix must not have turned the cone into "anything near me counts". 70 degrees
            // off a 60 degree raised gaze is way outside a 35 degree half-angle.
            double g = Math.toRadians(60.0);
            double fy = Math.sin(g), fz = Math.cos(g);
            double s = Math.sin(Math.toRadians(70.0));
            double c = Math.cos(Math.toRadians(70.0));
            assertFalse(lookAndHit(0.0, fy, fz, -s * 2.0, fy * c * 2.0, fz * c * 2.0),
                    "the sideways limit must survive the reorientation");
        }

        @Test
        @DisplayName("looking down, a target below and ahead is inside")
        void lookingDownReachesTheGround() {
            // The mirror of the overhead case, and the one that matters for a zombie at your
            // feet while you are looking down at it.
            double g = Math.toRadians(-50.0);
            double fy = Math.sin(g), fz = Math.cos(g);
            assertTrue(lookAndHit(0.0, fy, fz, 0.0, fy * 2.0, fz * 2.0));
        }

        @Test
        @DisplayName("looking straight up, a target tilted off vertical is still inside")
        void straightUpIsNotADegenerateCone() {
            // The degenerate frame. With the old code the horizontal axis was undefined here
            // and fell back to a fixed direction, so the cone was a flat fan that ignored where
            // the player was looking.
            assertTrue(lookAndHit(0.0, 1.0, 0.0, 0.0, 2.0, 0.0));
            // 20 degrees off vertical, still within the 35 degree sideways limit.
            double s = Math.sin(Math.toRadians(20.0));
            double c = Math.cos(Math.toRadians(20.0));
            assertTrue(lookAndHit(0.0, 1.0, 0.0, s * 2.0, c * 2.0, 0.0),
                    "a bat a bit to the side of straight up is still what the player aimed at");
        }

        @Test
        @DisplayName("looking straight up still rejects something level with the player")
        void straightUpStillRejectsTheGround() {
            assertFalse(lookAndHit(0.0, 1.0, 0.0, 3.0, 0.0, 0.0),
                    "90 degrees off the gaze is outside any sane vertical limit");
        }

        @Test
        @DisplayName("the cone is symmetric about the gaze when raised")
        void raisedGazeIsSymmetric() {
            double g = Math.toRadians(45.0);
            double fy = Math.sin(g), fz = Math.cos(g);
            double c = Math.cos(Math.toRadians(20.0));
            assertTrue(lookAndHit(0.0, fy, fz, -0.7, fy * c * 2.0, fz * c * 2.0));
            assertTrue(lookAndHit(0.0, fy, fz, 0.7, fy * c * 2.0, fz * c * 2.0));
        }

        @Test
        @DisplayName("a target behind the player stays behind, whatever the pitch")
        void behindIsAlwaysBehind() {
            // A 90 degree vertical allowance must not become a loophole that lets a swing land
            // on whatever happens to be nearby, in any direction.
            assertFalse(ConeMath.inCone(0, 0, 0, 0, 0.5, 0.866, 0, -2, 0, 10, 35, 90));
        }
    }

    @Nested
    @DisplayName("the debug overlay draws the cone the game uses")
    class FrameIsShared {

        @Test
        @DisplayName("the frame is orthonormal")
        void orthonormal() {
            // The renderer draws the rim by walking these three axes. If they are not unit
            // length and perpendicular, the drawn cone is a different shape from the tested one
            // and the overlay is lying in a way no test of the hit test alone would catch.
            for (double pitch : new double[] {-80, -30, 0, 30, 80, 90, -90}) {
                double r = Math.toRadians(pitch);
                double lx = 0.0, ly = Math.sin(r), lz = Math.cos(r);
                ConeMath.Frame f = ConeMath.frame(lx, ly, lz);
                assertEquals(1.0, length(f.fx(), f.fy(), f.fz()), 1.0E-9, "forward, pitch " + pitch);
                assertEquals(1.0, length(f.rx(), f.ry(), f.rz()), 1.0E-9, "right, pitch " + pitch);
                assertEquals(1.0, length(f.ux(), f.uy(), f.uz()), 1.0E-9, "up, pitch " + pitch);
                assertEquals(0.0, dot(f.fx(), f.fy(), f.fz(), f.rx(), f.ry(), f.rz()), 1.0E-9,
                        "forward and right must be perpendicular, pitch " + pitch);
                assertEquals(0.0, dot(f.fx(), f.fy(), f.fz(), f.ux(), f.uy(), f.uz()), 1.0E-9,
                        "forward and up must be perpendicular, pitch " + pitch);
                assertEquals(0.0, dot(f.rx(), f.ry(), f.rz(), f.ux(), f.uy(), f.uz()), 1.0E-9,
                        "right and up must be perpendicular, pitch " + pitch);
            }
        }

        @Test
        @DisplayName("the frame tilts with the gaze instead of staying bolted to the ground")
        void frameFollowsGaze() {
            // This is the bug the overlay was hiding: it drew a ground-anchored fan while the
            // game used a gaze-anchored cone, so /cc cone showed a shape the game did not use.
            //
            // Look 60 degrees up and the up axis has to tilt 60 degrees off vertical. It cannot
            // stay pointing at the sky, because it is defined as perpendicular to the gaze: a
            // frame whose up axis did not tilt would no longer be orthonormal.
            double r = Math.toRadians(60.0);
            ConeMath.Frame f = ConeMath.frame(0.0, Math.sin(r), Math.cos(r));
            assertEquals(Math.cos(r), f.uy(), 1.0E-9,
                    "the up axis must tilt with the pitch, was " + f.uy());
            // And right stays horizontal, so "35 degrees to the right" means the same thing on
            // a slope as on flat ground.
            assertEquals(0.0, f.ry(), 1.0E-9, "right must stay horizontal");
        }

        @Test
        @DisplayName("a point on each drawn limit curve is inside the cone")
        void limitCurvesAreInside() {
            // The overlay draws two boundary curves. If every point on them passes the hit test,
            // the drawn boundary is the real boundary. An earlier version walked an ellipse as
            // the "rim" and most of its points sat ninety degrees off the gaze, outside the
            // region it claimed to be showing, so this is the test that would have caught it.
            for (double pitch : new double[] {-70, -20, 0, 45, 89}) {
                double r = Math.toRadians(pitch);
                ConeMath.Frame f = ConeMath.frame(0.0, Math.sin(r), Math.cos(r));
                for (boolean horizontal : new boolean[] {true, false}) {
                    for (int i = 0; i <= 16; i++) {
                        double a = -1.0 + (2.0 * i) / 16.0;
                        double[] d = limitDirection(f, horizontal, a);
                        assertTrue(ConeMath.inCone(
                                0, 0, 0,
                                f.fx(), f.fy(), f.fz(),
                                d[0] * 2.0, d[1] * 2.0, d[2] * 2.0,
                                5, 35, 30),
                                "a drawn limit point at pitch " + pitch
                                        + " horizontal=" + horizontal + " t=" + a
                                        + " must be hittable, or the overlay is drawing a lie");
                    }
                }
            }
        }

        @Test
        @DisplayName("a point just outside a limit curve is rejected")
        void justOutsideIsRejected() {
            // The complement of the test above, and the one that proves the curves are on the
            // boundary rather than comfortably inside it. An overlay whose "edge" is a fifth of
            // the cone in from the real edge is worse than no overlay.
            double r = Math.toRadians(30.0);
            ConeMath.Frame f = ConeMath.frame(0.0, Math.sin(r), Math.cos(r));
            // One degree past the 35 degree horizontal limit, not inside it.
            double over = Math.tan(Math.toRadians(36.0));
            double[] d = ConeMath.direction(
                    f.fx() + f.rx() * over, f.fy() + f.ry() * over, f.fz() + f.rz() * over);
            assertFalse(ConeMath.inCone(0, 0, 0, f.fx(), f.fy(), f.fz(),
                    d[0] * 2.0, d[1] * 2.0, d[2] * 2.0, 5, 35, 30),
                    "one degree past the horizontal limit must be rejected");
        }

        /**
         * A point on the drawn boundary, mirrored from {@code ConeDebugRenderer.limitCurve}.
         *
         * <p>Mirrored rather than shared on purpose: the renderer builds {@code Vec3}s, which
         * this class may not touch, so sharing a helper would drag a Minecraft type in here and
         * kill every test in the file. Writing it out means the two have to be kept in step, and
         * the tests above are what catch them when they are not.</p>
         *
         * <p>The inset matches the renderer's: the drawn line sits a hair inside the limit so
         * that "everything inside the line is hittable" is true rather than approximately true.</p>
         *
         * @param horizontal true to sweep the horizontal limit, false the vertical one
         * @param t -1..1, the position along the other axis as a fraction of its limit
         */
        private static double[] limitDirection(ConeMath.Frame f, boolean horizontal, double t) {
            final double hRad = Math.toRadians(35.0);
            final double vRad = Math.toRadians(30.0);
            final double inset = 0.995;
            double across = Math.tan(horizontal ? vRad : hRad) * inset * t;
            double limit = Math.tan(horizontal ? hRad : vRad) * inset;
            return ConeMath.direction(
                    f.fx() + f.rx() * (horizontal ? limit : across) + f.ux() * (horizontal ? across : limit),
                    f.fy() + f.ry() * (horizontal ? limit : across) + f.uy() * (horizontal ? across : limit),
                    f.fz() + f.rz() * (horizontal ? limit : across) + f.uz() * (horizontal ? across : limit));
        }

        private static double length(double x, double y, double z) {
            return Math.sqrt(x * x + y * y + z * z);
        }

        private static double dot(double ax, double ay, double az, double bx, double by, double bz) {
            return ax * bx + ay * by + az * bz;
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
