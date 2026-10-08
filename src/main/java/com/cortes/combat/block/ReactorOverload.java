package com.cortes.combat.block;

/**
 * Phase two of the nether reactor: the fuse, the two overloads, the staged blast and the quake.
 *
 * <h2>Pure on purpose, and for the usual reason</h2>
 *
 * <p>Every number the overload runs on lives here, below the Minecraft line, so a bare-JVM test can
 * read it and the block entity, the blast scheduler and the client's camera shake all read the same
 * constant. The block entity cannot be tested without booting the game; the arithmetic can, and the
 * arithmetic is where a wrong number hides: a fuse one tick short, a warning band one charge wide,
 * or a budget that lets one tick do four seconds of work are all invisible in a green build.</p>
 *
 * <h2>The two overloads are not the same mechanic</h2>
 *
 * <p><b>Firestone is a runaway; the nether star is a bomb.</b> The firestone overload is what a
 * player reaches by force-feeding the reactor past the automation ceiling -- thirty-one to
 * thirty-nine is the warning, forty lights the fuse -- and the lever is the way out, because the
 * lever is what was feeding it. The nether star is not a state of the machine at all: it is dropped
 * in by hand, it ignores the lever, and the only thing that stops it is water. Keeping both in one
 * enum and one fuse length is the point: they are the same countdown with two different exits, and
 * two chunk of state would be two truths about "overheating".</p>
 *
 * <h2>The star blast does not fit in one tick, and the numbers say so</h2>
 *
 * <p>A single vanilla {@code level.explode(.., 120F, ..)} is not shippable, and it is worth being
 * exact about why rather than saying "it lags". {@code Explosion.explode} casts 1352 rays (the shell
 * of a 16x16x16 grid) and walks each one 0.3 blocks at a time while its budget drains 0.225 a step
 * in open air, so a power-120 blast is roughly {@code 1352 * 120 / 0.225 =~ 720_000} steps, every one
 * a block and fluid read, and every one at or past a block that can be added to the to-blow set --
 * so the set can hold the better part of a million positions. Those are then walked again in
 * {@code finalizeExplosion} and serialised into a {@code ClientboundExplodePacket}. That is a
 * multi-hundred-millisecond stall inside one tick, and the server's watchdog does not forgive it.
 * {@link #rayMarchSteps(float)} is that measurement, and the test asserts it, so the reason the
 * blast is staged cannot quietly stop being true.</p>
 *
 * <p><b>So the crater is an explicit sphere, queued and paid for by the tick.</b> A sphere is not
 * what vanilla's ray-march produces -- vanilla's shape is material-dependent, it reaches far
 * through soft ground and stops dead in stone, and it sometimes leaves air inside the crater. A
 * nuclear core vaporising everything to an even radius is both simpler and more honest to the
 * fiction; the trade is that a bedrock-adjacent reactor leaves bedrock and takes everything else.
 * {@link #sphereVolume(int)} and {@link #ticksToClear(int, int)} are the two halves of that
 * promise, and {@code ReactorBlast} is the scheduler that keeps it.</p>
 */
public final class ReactorOverload {

    /** Which countdown, if any, is running. */
    public enum Kind {
        /** Nothing is overloading. The reactor is a reactor. */
        NONE,
        /** Firestones pushed past the ceiling; the lever is the way out. */
        FIRESTONE,
        /** A nether star dropped in by hand; only water stops it. */
        STAR
    }

    // ------------------------------------------------------------------ the fuse

    /**
     * The fuse, in ticks: fifteen seconds, which is exactly the length of the two overheat
     * recordings.
     *
     * <p><b>The sound is the timer and the timer is the sound.</b> {@code reactor_overheat_firestone}
     * is 15.53 seconds and {@code reactor_overheat_netherstar} is 15.33; both round to three hundred
     * ticks at twenty a second. A countdown that outlasts its own warning is a countdown the player
     * stops believing, so the recording's length is the number rather than a round figure chosen
     * beside it.</p>
     */
    public static final int FUSE_TICKS = 300;

    /** Seconds the fuse lasts, derived. For a readout and for the test that ties the two. */
    public static final float FUSE_SECONDS = FUSE_TICKS / 20.0F;

    /**
     * The first charge that is "over the line", and the start of the warning band.
     *
     * <p>One past {@link ReactorCharge#MAX_NORMAL}: the design says automation stops at thirty and
     * everything above it is a player forcing the issue by hand. Thirty-one to thirty-nine is smoke
     * and fire and no countdown; forty lights the fuse.</p>
     */
    public static final int WARNING_CHARGE = ReactorCharge.MAX_NORMAL + 1;

    /** The charge that starts the firestone fuse: the slot's own ceiling. */
    public static final int DETONATION_CHARGE = ReactorCharge.MAX_OVERLOAD;

    /**
     * The energy radius a nether star pushes the reactor to, in blocks.
     *
     * <p>The design's number, and it is four times the star-less maximum (forty charges reach a
     * hundred and twenty, but the star wants a round hundred). It only applies while the star
     * overload is running; the reactor's own charge is untouched.</p>
     */
    public static final int STAR_RADIUS = 100;

    // ------------------------------------------------------------------ the blasts

    /**
     * Vanilla TNT's power, named so the star's is a multiple of a fact and not a guess.
     */
    public static final float TNT_POWER = 4.0F;

    /**
     * The star's nominal power: thirty times TNT, which is the design's headline.
     *
     * <p><b>This is the number that is NOT used as an explosion radius.</b> It is kept because it is
     * what the design asked for and what {@link #rayMarchSteps(float)} measures; the actual crater
     * is {@link #STAR_DESTRUCTION_RADIUS}, because a point blast of this power cannot be run in one
     * tick. See the class comment.</p>
     */
    public static final float STAR_POWER = 30.0F * TNT_POWER;

    /**
     * The radius, in blocks, of the staged crater the nether star leaves.
     *
     * <p><b>Chosen for the tick budget, not for the power.</b> A power-120 point blast reaches
     * further than this in open ground and less in stone; a sphere has one number and this is the
     * largest one whose removal still fits a sane per-tick budget ({@link #BLAST_REMOVALS_PER_TICK})
     * without the watchdog noticing. Forty-eight blocks is a ninety-six block hole: it deletes a
     * base and the ground under it, and it clears in about four seconds rather than at once.</p>
     */
    public static final int STAR_DESTRUCTION_RADIUS = 48;

    /**
     * Cells the staged blast is allowed to examine per tick, across every pending blast.
     *
     * <p>The second budget, and it exists because the first one is not enough on its own: a sphere
     * that is mostly air has almost nothing to remove and would otherwise be walked in a single
     * tick. Sections that are all air are skipped whole before this counts, so in practice this
     * bounds the work of a <em>dense</em> crater.</p>
     */
    public static final int BLAST_POSITIONS_PER_TICK = 24_000;

    /**
     * Blocks the staged blast is allowed to remove per tick, across every pending blast.
     *
     * <p>Six thousand block writes a tick is already heavy -- each is a {@code setBlock} with
     * neighbours, light and a client update -- and it is the number that decides how long the wave
     * takes. Lower it for a machine that stutters; the crater is identical, just slower.</p>
     */
    public static final int BLAST_REMOVALS_PER_TICK = 6_000;

    // ------------------------------------------------------------------ the show

    /**
     * Ticks between plays of the running loop, matched to the recording's own length.
     *
     * <p>{@code reactor_loop_on} is 12.25 seconds, which is 245 ticks. One play per recording, so
     * the loop is continuous without a scheduling system.</p>
     */
    public static final int LOOP_INTERVAL = 245;

    /** Ticks between the ambient particle bursts, so a running reactor is not a per-tick packet. */
    public static final int AMBIENT_PARTICLE_INTERVAL = 10;

    /** Ticks between the star fuse's particle bursts: denser, because it is a countdown. */
    public static final int FUSE_PARTICLE_INTERVAL = 4;

    /** Ticks between the low rumble during a star overload. */
    public static final int RUMBLE_INTERVAL = 40;

    /** Volume for the reactor's own sounds: a base-sized reach. */
    public static final float REACTOR_VOLUME = 2.0F;

    /**
     * Volume for the nuclear explosion: thirty-two times the base reach, so it is heard a long way
     * off. <b>Adjustable to playtest:</b> sixteen was the first number and the request was simply
     * "louder", so it doubled.
     *
     * <p>{@code SoundEvent.getRange(volume)} scales the audible radius with volume above one, and
     * thirty-two gives a five-hundred-and-twelve block reach. Vanilla's own explosions cap their
     * packet at sixty-four blocks, which is why this is played by hand rather than through
     * {@code explode}.</p>
     */
    public static final float NUCLEAR_VOLUME = 32.0F;

    // ------------------------------------------------------------------ the quake

    /** How far, in blocks, the star overload's camera shake can be felt. */
    public static final double QUAKE_RADIUS = 64.0;

    /** The shake at its strongest, in degrees. Small: a rumble, not a ride. */
    public static final float QUAKE_MAX_DEGREES = 1.2F;

    /**
     * Damage at the dead centre of the star blast, falling to nothing at the crater's edge.
     *
     * <p>Two hundred at the core is "everything unarmoured and most things armoured dies if it is
     * standing on the core", which is the fiction; the linear falloff means the edge is survivable,
     * which is what keeps a nuke from being a map-wide no-go. It is deliberately not tied to the
     * TNT power: explosion entity damage and crater size are two different dials.</p>
     */
    public static final float STAR_DAMAGE_AT_CENTER = 200.0F;

    /** Seconds anything caught in the star blast burns for. */
    public static final float STAR_FIRE_SECONDS = 15.0F;

    /** The outward shove at the centre of the star blast, in blocks a tick. */
    public static final double STAR_KNOCKBACK = 6.0;

    private ReactorOverload() {
    }

    // ------------------------------------------------------------------ the bands

    /** Whether a charge is in the warning band: over the line but not yet at the fuse. */
    public static boolean isWarning(int charge) {
        return charge >= WARNING_CHARGE && charge < DETONATION_CHARGE;
    }

    /** Whether a charge is high enough to light the firestone fuse. */
    public static boolean detonates(int charge) {
        return charge >= DETONATION_CHARGE;
    }

    // ------------------------------------------------------------------ the arithmetic

    /**
     * The number of block positions in a solid sphere of this radius, rounded to the nearest.
     *
     * <p>{@code (4/3) * pi * r^3}. The count is the thing the budget is spent against, so it has to
     * be computed the same way in the scheduler and in the test.</p>
     */
    public static long sphereVolume(int radius) {
        if (radius <= 0) {
            return 0L;
        }
        double r = radius;
        return Math.round(4.0 / 3.0 * Math.PI * r * r * r);
    }

    /** How many ticks a crater of this radius takes to clear at this many removals a tick. */
    public static long ticksToClear(int radius, int removalsPerTick) {
        long volume = sphereVolume(radius);
        if (volume <= 0L || removalsPerTick <= 0) {
            return 1L;
        }
        return (volume + removalsPerTick - 1L) / removalsPerTick;
    }

    /**
     * How many steps a single vanilla explosion of this power ray-marches, as a measurement.
     *
     * <p><b>The documented cost of the blast that is <em>not</em> run.</b> {@code Explosion.explode}
     * casts one ray per cell on the shell of a 16x16x16 grid -- {@code 16^3 - 14^3 = 1352} rays --
     * and each ray takes {@code power / 0.225} steps when it travels through open air, because the
     * loop header drains the budget by 0.225 a step and nothing else. That product is the lower
     * bound on the block reads, and every step can contribute a position to the to-blow set. It is
     * approximate by design: solid ground drains the budget faster.</p>
     */
    public static long rayMarchSteps(float power) {
        if (power <= 0.0F) {
            return 0L;
        }
        int rays = 16 * 16 * 16 - 14 * 14 * 14;
        return Math.round(rays * (power / 0.225F));
    }

    /**
     * The camera shake's amplitude at a distance, in degrees, with a squared falloff.
     *
     * <p>Zero at {@link #QUAKE_RADIUS} and beyond, the maximum at the core, and harsh in between:
     * the squared falloff keeps the shake to the immediate area and stops a reactor on the horizon
     * from wobbling the camera across the map. Pure so the curve can be asserted without a
     * renderer.</p>
     */
    public static float shakeDegrees(double distance, float max) {
        if (distance >= QUAKE_RADIUS || max <= 0.0F) {
            return 0.0F;
        }
        double clamped = Math.max(0.0, distance);
        double falloff = 1.0 - clamped / QUAKE_RADIUS;
        return (float) (max * falloff * falloff);
    }

    /** The radius a running reactor publishes: the star's flat hundred, or the charge's own curve. */
    public static int energyRadius(Kind overload, boolean lit, int charge) {
        if (overload == Kind.STAR) {
            return STAR_RADIUS;
        }
        return lit ? ReactorCharge.radius(charge) : 0;
    }
}
