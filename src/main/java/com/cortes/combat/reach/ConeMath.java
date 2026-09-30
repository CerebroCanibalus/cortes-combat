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
        // The frame is orthonormal and anchored to the look vector: forward is where the player
        // is looking, right is perpendicular to it in the horizontal-ish plane, and up completes
        // the basis. Building it on the world up instead (the obvious shortcut) makes the cone
        // a fan that stays bolted to the ground no matter where you look, which is why hitting
        // a bat directly overhead or a zombie at your feet used to mean fighting the camera.
        //
        // Degenerate case: looking straight up or down, "right" from a cross product with world
        // up is undefined. Any unit vector perpendicular to the look works there, and the one
        // picked below is the compass direction the player is most likely facing, so the cone
        // keeps pointing where it was instead of spinning.
        double rx;
        double ry;
        double rz;
        double horizLen = Math.sqrt(lx * lx + lz * lz);
        if (horizLen < EPSILON) {
            // Vertical gaze. North as right.
            rx = 1.0;
            ry = 0.0;
            rz = 0.0;
        } else {
            // right = normalize(worldUp x forward), the horizontal perpendicular.
            rx = -lz / horizLen;
            ry = 0.0;
            rz = lx / horizLen;
        }

        // up = right x forward. Completes a right-handed orthonormal basis, so the three
        // components below are exact and no re-normalisation is needed.
        double ux = ry * lz - rz * ly;
        double uy = rz * lx - rx * lz;
        double uz = rx * ly - ry * lx;

        double along = dx * lx + dy * ly + dz * lz;
        double lateral = dx * rx + dy * ry + dz * rz;
        double vertical = dx * ux + dy * uy + dz * uz;

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
