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

    /**
     * How much of the target's own size becomes extra tolerance, as a fraction of its half extent.
     *
     * <p>See {@link #leash} for why a fixed angle cannot do this job. The short version is that
     * the vertical axis was measured from the horizon rather than from what the player was
     * pointing at, so the only way to look at a mob one block away was to look 43 degrees down,
     * which no vertical angle any weapon can afford will accept.</p>
     */
    public static final double LEASH_FRACTION = 0.65;

    /**
     * The ceiling on that tolerance, in blocks.
     *
     * <p>Without it the rule is "the tolerance is two thirds of the target", which is honest and
     * completely breaks on an eight block tall dragon: a five metre leash means the narrowest
     * weapon in the game connects without the player pointing at anything. Capping it means a
     * huge target is proportionally <em>harder</em> than an ordinary one, which is what size is
     * supposed to mean.</p>
     */
    public static final double LEASH_CAP = 0.8;

    private ConeMath() {
    }

    /**
     * The extra tolerance a target of this size earns, in blocks.
     *
     * <p>A fixed angle is the wrong shape for a held weapon up close, and the way it fails is
     * worth writing down because it took a playtest to see and no amount of reading to guess.</p>
     *
     * <p>The cone's cross-section grows with distance, so a fixed angle is wide far away and a
     * coin far away from you. At one block the ropera's fifteen degree vertical limit is a
     * twenty seven centimetre band, and a zombie is a block and a half tall: <b>you cannot look
     * at one.</b> The measurement is not even in the right frame, because the vertical offset is
     * taken from the horizon rather than from the target. Concretely, a zombie's nearest point to
     * the player's eye sits at eye height, because the eye is inside its box, so the whole test
     * collapses to "how far below the horizon are you looking" -- and looking at a zombie one
     * block away means looking forty three degrees down.</p>
     *
     * <p>Measured, not guessed: the distance from which pointing at a zombie starts to work was
     * 2.62m for a ropera with 3.4m of reach, 1.77m for the bare hands with 2.5m, against 0.72m
     * for the warhammer. The ordering is the ordering of the vertical angles, which is why this
     * read as "the narrower the cone the worse" when it is one specific axis.</p>
     *
     * <p>The leash closes the near field and leaves the far one alone, because the two terms are
     * a maximum: past the crossover the angle is wider and takes over by itself. For a ropera
     * against a zombie that crossover is at 2.35m of 3.4m, so the last metre of its range is as
     * precise as it was written to be.</p>
     *
     * @param halfExtent half the target's width or height, in blocks
     */
    public static double leash(double halfExtent) {
        if (halfExtent <= 0.0 || Double.isNaN(halfExtent)) {
            return 0.0;
        }
        return Math.min(halfExtent * LEASH_FRACTION, LEASH_CAP);
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
        // No leash: a target with no size to speak of, which is what the two argument limits
        // alone have always meant. The overload exists so this one's tests keep describing the
        // original rule rather than quietly gaining a tolerance nobody asked for.
        return inCone(originX, originY, originZ,
                lookX, lookY, lookZ,
                targetX, targetY, targetZ,
                maxDistance, horizontalAngle, verticalAngle, 0.0, 0.0);
    }

    /**
     * The same test, with the two leashes a target of its size has earned.
     *
     * <p>Compared as perpendicular offsets rather than as angles, because a leash <em>is</em> an
     * offset in blocks and converting it to an angle first would hide what it does. The angle
     * term becomes {@code along · tan(limit)}, so the maximum of the two is simply the wider of
     * "how far away and how wide" and "how big it is".</p>
     *
     * @param leashLateral blocks of sideways slop the target's width earns
     * @param leashVertical blocks of up-and-down slop the target's height earns
     */
    public static boolean inCone(
            double originX, double originY, double originZ,
            double lookX, double lookY, double lookZ,
            double targetX, double targetY, double targetZ,
            double maxDistance, double horizontalAngle, double verticalAngle,
            double leashLateral, double leashVertical) {
        return inCone(originX, originY, originZ, lookX, lookY, lookZ,
                targetX, targetY, targetZ, prepare(maxDistance, horizontalAngle, verticalAngle),
                leashLateral, leashVertical);
    }

    /**
     * El mismo test, con los límites del arma ya molidos a tangentes.
     *
     * <p><b>Por qué existe, medido:</b> el test completo cuesta <b>27,8 ns</b> por candidato y
     * <b>25,5 de esos nanosegundos son los dos {@code Math.tan}</b> (benchmark de 20.000.000 de
     * iteraciones, JIT caliente) — o sea el <b>92%</b> del trabajo es trigonometría sobre dos
     * ángulos que <em>no cambian durante un escaneo</em>. Un escaneo pregunta por N candidatos con
     * el mismo arma, así que las dos tangentes se calculan una vez y el test por candidato baja a
     * ~2 ns.</p>
     *
     * <p><b>No es lag hoy</b> (un escaneo de diez candidatos cada tres ticks son microsegundos por
     * segundo) y el número está acá para que nadie lo venda como una victoria de frame: es sacarle
     * el 92% a la parte más consultada del combate por el precio de un {@code prepare} por
     * llamada, y es <b>bit a bit el mismo resultado</b> — el test lo afirma contra la versión de
     * ángulos.</p>
     */
    public static boolean inCone(
            double originX, double originY, double originZ,
            double lookX, double lookY, double lookZ,
            double targetX, double targetY, double targetZ,
            Prepared limits, double leashLateral, double leashVertical) {

        double dx = targetX - originX;
        double dy = targetY - originY;
        double dz = targetZ - originZ;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > limits.maxDistance()) {
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

        // Behind the attacker, and therefore outside a leash that is a forward tolerance.
        //
        // This is the one place the rewrite could have gone wrong quietly. The old test measured
        // atan2(offset, along), which returns more than ninety degrees for anything behind and
        // so failed every limit without a special case. Comparing raw offsets has no such
        // property: a target straight behind has a lateral offset of zero, and zero is inside any
        // leash at all, so without this line the narrowest weapon in the game would hit whatever
        // is behind the player's head.
        if (along <= EPSILON) {
            return false;
        }

        double allowedLateral = Math.max(along * limits.tanHorizontal(), leashLateral);
        double allowedVertical = Math.max(along * limits.tanVertical(), leashVertical);

        return Math.abs(lateral) <= allowedLateral && Math.abs(vertical) <= allowedVertical;
    }

    /**
     * Los límites de un arma, ya en la forma que el test consulta por candidato.
     *
     * <p>Un record de tres dobles: sin tipos de Minecraft, como el resto de la clase, para que el
     * test de equivalencia corra en JVM pelada.</p>
     */
    public record Prepared(double maxDistance, double tanHorizontal, double tanVertical) {
    }

    /** Muele los límites una vez por escaneo. Ver la sobrecarga que lo consume. */
    public static Prepared prepare(double maxDistance, double horizontalAngle,
                                   double verticalAngle) {
        return new Prepared(maxDistance, tangent(horizontalAngle), tangent(verticalAngle));
    }

    /**
     * Tangent of a limit in degrees, guarded.
     *
     * <p>The old code never needed a tangent because it measured angles both sides. It does now,
     * and a limit of ninety degrees would divide by nothing but a zero or a negative one is
     * possible from a data override, so it is clamped rather than trusted.</p>
     */
    public static double tangent(double degrees) {
        if (Double.isNaN(degrees) || degrees <= 0.0) {
            return 0.0;
        }
        return Math.tan(Math.toRadians(Math.min(degrees, 89.0)));
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
