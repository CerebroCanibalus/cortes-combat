package com.cortes.combat.reach;

/**
 * The cone test, as pure arithmetic on doubles.
 *
 * <p><strong>Deliberately has no Minecraft types in its signature.</strong> That is what makes
 * this the most testable class in the project: the whole thing runs on a bare JVM, no world,
 * no entities, no game bootstrap. If you add a {@code Vec3} parameter here the unit tests
 * stop working, so keep the adapter in {@link Reach} instead.</p>
 *
 * <p>The two limits are measured in different frames, on purpose:
 * <ul>
 *   <li><b>Horizontal</b> is measured in the attacker's horizontal frame: the angle off the
 *       ground projection of the look direction. Something directly behind is 180 degrees and
 *       fails without needing a special case.</li>
 *   <li><b>Vertical</b> is the difference between the target's world elevation and the look's
 *       world elevation. This stays well defined at the poles, which is the whole point: when
 *       the player looks straight up, both elevations are 90 and a target straight up is dead
 *       ahead rather than 90 degrees off.</li>
 * </ul>
 * Checking them separately is the point of the whole class: a tight vertical limit is what
 * stops a swing from connecting with something at the attacker's feet or something sailing
 * overhead.</p>
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

        // Horizontal forward, with the vertical part removed.
        double fwdLen = Math.sqrt(lx * lx + lz * lz);
        double fx;
        double fz;
        if (fwdLen < EPSILON) {
            // Looking straight up or down: the horizontal axis is undefined. Fall back to an
            // arbitrary one so the math stays finite. The vertical limit still does the work.
            fx = 1.0;
            fz = 0.0;
        } else {
            fx = lx / fwdLen;
            fz = lz / fwdLen;
        }

        // right = forward x worldUp, i.e. perpendicular to forward in the horizontal plane.
        double rx = -fz;
        double rz = fx;

        // Decompose the target vector in the horizontal (forward / right) frame.
        double along = dx * fx + dz * fz;
        double lateral = dx * rx + dz * rz;

        // Atan2 of the absolute lateral offset: a target behind the attacker yields an angle
        // above 90 degrees, so it fails any sane limit without a special case.
        double horizontal = Math.toDegrees(Math.atan2(Math.abs(lateral), along));

        // The vertical limit is the target's world elevation minus the look's world elevation.
        // Measuring it as an offset from the gaze line (rather than from the upLocal axis) is
        // what keeps it well defined when the player looks straight up: at the pole both
        // elevations are 90 and the offset is 0, i.e. dead ahead. Deriving the angle from
        // upLocal instead degenerates there and rejects a target the player is staring at.
        double targetElevation = Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        double lookElevation = Math.toDegrees(Math.atan2(ly, Math.sqrt(lx * lx + lz * lz)));
        double verticalOffset = Math.abs(targetElevation - lookElevation);

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
