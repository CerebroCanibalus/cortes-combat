package com.cortes.combat.guard;

/**
 * The geometry of a block, in plain {@code double} and with no Minecraft type anywhere in a
 * signature.
 *
 * <p>Same reason as {@code ConeMath}: a rule that needs the game running cannot be tested in a
 * bare JVM, and a rule that cannot be tested is a rule that will be wrong. Everything here can
 * be checked with a unit test and nothing here can drift from what the game does, because the
 * game only calls it.</p>
 */
public final class GuardRules {

    /**
     * Total width of the frontal block, in degrees. The design says "frontal arc only" without
     * a number, so this is the one to tune: 120 total means 60 degrees either side of where the
     * player is looking. Wide enough to feel like a shield in front of you, narrow enough that
     * being hit from behind is a real mistake.
     */
    public static final double GUARD_ARC_DEGREES = 120.0;

    private GuardRules() {
    }

    /**
     * Whether a source at {@code (sx, sy, sz)} is inside the block arc of a player standing at
     * {@code (px, py, pz)} and looking along {@code (lx, ly, lz)}.
     *
     * <p>Measures the full 3D angle rather than a horizontal yaw, so a hit dropped on you from
     * in front and above counts as frontal and one from behind does not. Same choice as the
     * cone: measure what the player would describe as "in front of me".</p>
     *
     * @param arcDegrees total width of the arc, so 120 means 60 either side
     */
    public static boolean isFromFront(double px, double py, double pz,
                                      double lx, double ly, double lz,
                                      double sx, double sy, double sz,
                                      double arcDegrees) {
        // From the player towards the attacker. Pointing this the other way is a sign error
        // that inverts the whole rule: the player would block from behind and eat from the
        // front, and no amount of reading the code makes that obvious.
        double dx = sx - px;
        double dy = sy - py;
        double dz = sz - pz;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared < 1.0E-12) {
            // The source is on top of us. Standing in someone's fist is the least "behind" a
            // hit can be, and refusing to block it would be a hole players find instantly.
            return true;
        }
        double inverse = 1.0 / Math.sqrt(lengthSquared);
        double dot = (dx * inverse) * lx + (dy * inverse) * ly + (dz * inverse) * lz;
        // A dot drifting past 1 makes acos return NaN and the whole comparison goes false.
        if (dot > 1.0) {
            dot = 1.0;
        } else if (dot < -1.0) {
            dot = -1.0;
        }
        double angle = Math.toDegrees(Math.acos(dot));
        return angle <= arcDegrees / 2.0;
    }
}
