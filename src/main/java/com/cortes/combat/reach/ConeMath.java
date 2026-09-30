package com.cortes.combat.reach;

/**
 * The cone test, as pure arithmetic on doubles.
 *
 * <p><strong>Deliberately has no Minecraft types in its signature.</strong> That is what makes
 * this the most testable class in the project: the whole thing runs on a bare JVM, no world,
 * no entities, no game bootstrap. If you add a {@code Vec3} parameter here the unit tests
 * stop working, so keep the adapter in {@link Reach} instead.</p>
 *
 * <p><b>The frame follows the gaze.</b> The look vector is the cone's forward axis, and the
 * other two axes are built perpendicular to it. That is the whole reason a bat directly
 * overhead is hittable while looking up, and a zombie at your feet is hittable while looking
 * down: the cone is bolted to the camera, not to the ground.
 *
 * <p>An earlier version built the frame on the world up vector, which made the horizontal
 * spread a ground-level fan that stayed a ground-level fan no matter where the player looked.
 * The two limits are still checked separately, and separately is the point: a tight vertical
 * limit is what stops a swing connecting with something at the attacker's feet or something
 * sailing overhead, <em>relative to where the player is actually aiming</em>.</p>
 *
 * <p>Looking straight up is the degenerate case and it is handled rather than ignored: the
 * "right" axis falls back to north, so the frame stays finite and the cone keeps pointing the
 * way it was instead of spinning to an arbitrary orientation.</p>
 */
public final class ConeMath {

    private static final double EPSILON = 1.0E-6;

    private ConeMath() {
    }

    /**
     * @param targetX      nearest reachable point on the target's box
     * @param maxDistance  forward reach limit, in blocks
     * @param horizontalAngle  half-angle to each side, in degrees
     * @param verticalAngle    half-angle above and below the look vector, in degrees
     */
    public static boolean inCone(
            double originX, double originY, double originZ,
            double lookX, double lookY, double lookZ,
            double targetX, double targetY, double targetZ,
            double maxDistance, double horizontalAngle, double verticalAngle) {

        double dx = targetX - originX;
        double dy = targetY - originY;
        double dz = targetZ - originZ;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > maxDistance) {
            return false;
        }
        if (distance < EPSILON) {
            // Target sits on the attacker's eye. Everything contains this point.
            return true;
        }

        // Normalise the look vector.
        double lookLen = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (lookLen < EPSILON) {
            return false;
        }
        double lx = lookX / lookLen;
        double ly = lookY / lookLen;
        double lz = lookZ / lookLen;

        // --- The frame, built around the look direction so the cone follows the gaze ---
        //
        // Shared with the debug renderer through frame(), so the shape drawn on screen and the
        // shape the hit test uses cannot drift apart. The debug overlay is only worth anything
        // if it is literally the same cone, and the first version of this class had the maths
        // inlined here and retyped in the renderer, which is exactly how they diverged.
        Frame frame = frame(lx, ly, lz);

        double along = dx * frame.fx() + dy * frame.fy() + dz * frame.fz();
        double lateral = dx * frame.rx() + dy * frame.ry() + dz * frame.rz();
        double vertical = dx * frame.ux() + dy * frame.uy() + dz * frame.uz();

        // Atan2 of the absolute lateral offset: a target behind the attacker yields an angle
        // above 90 degrees, so it fails any sane limit without a special case.
        double horizontal = Math.toDegrees(Math.atan2(Math.abs(lateral), along));
        // The vertical limit is measured in the same plane as the horizontal one, against the
        // gaze line. This is what makes the cone reorient: look up and the vertical axis comes
        // with you, instead of staying vertical in the world and rejecting whatever is above.
        double verticalOffset = Math.toDegrees(Math.atan2(Math.abs(vertical), along));

        return horizontal <= horizontalAngle && verticalOffset <= verticalAngle;
    }

    /**
     * The orthonormal frame the cone is measured in, from a normalised look vector.
     *
     * <p>Forward is where the player is looking, right is perpendicular to it, and up completes
     * a right-handed basis. Anchoring all three to the gaze is what makes the cone reorient with
     * the camera; building the frame on the world up vector instead produces a fan bolted to
     * the ground, which is what made hitting a bat overhead or a zombie at your feet feel like
     * fighting the camera.</p>
     *
     * <p>Degenerate case: looking straight up or down, right as a cross product with the world
     * up is undefined. Any unit vector perpendicular to the gaze is valid there, and north is
     * chosen so the cone keeps pointing the way it was rather than spinning to an arbitrary
     * orientation.</p>
     *
     * <p>Public so the debug renderer draws this exact frame. Two copies of this maths is how
     * the overlay came to show a cone the game was not using.</p>
     */
    public static Frame frame(double lx, double ly, double lz) {
        double horizLen = Math.sqrt(lx * lx + lz * lz);
        double rx;
        double ry;
        double rz;
        if (horizLen < EPSILON) {
            rx = 1.0;
            ry = 0.0;
            rz = 0.0;
        } else {
            rx = -lz / horizLen;
            ry = 0.0;
            rz = lx / horizLen;
        }
        // up = right x forward. Exact, because right and forward are both unit and perpendicular.
        return new Frame(lx, ly, lz,
                rx, ry, rz,
                ry * lz - rz * ly, rz * lx - rx * lz, rx * ly - ry * lx);
    }

    /**
     * A unit vector on the sphere, so the debug renderer can walk the cone's rim without
     * duplicating the trigonometry.
     */
    public static double[] direction(double f, double r, double u) {
        double length = Math.sqrt(f * f + r * r + u * u);
        if (length < EPSILON) {
            return new double[] {0.0, 0.0, 1.0};
        }
        return new double[] {f / length, r / length, u / length};
    }

    /**
     * The cone's three axes, each a unit vector.
     *
     * <p>A record of nine doubles rather than three {@code Vec3}s so this class keeps its
     * promise of having no Minecraft type in its signature, which is the only reason the unit
     * tests can run at all.</p>
     */
    public record Frame(double fx, double fy, double fz,
                        double rx, double ry, double rz,
                        double ux, double uy, double uz) {
    }

    /**
     * Nearest point of an axis-aligned box to a point. Using the box's nearest point rather
     * than its centre is what makes tall targets reachable: a zombie's head counts even though
     * its centre is a block below.
     */
    public static double closestOnAxis(double point, double min, double max) {
        if (point < min) {
            return min;
        }
        return Math.min(point, max);
    }
}
