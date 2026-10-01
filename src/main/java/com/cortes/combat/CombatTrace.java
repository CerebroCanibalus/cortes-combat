package com.cortes.combat;

/**
 * Whether the per-swing trace lines are written, and where.
 *
 * <p>This started as four unconditional {@code info} lines per attack, on both sides. It found
 * four real bugs that nothing else could see, so it stays -- but it was on by default and that
 * turned out to be its own problem. A playtest session spams hundreds of attacks, and a log with
 * four lines per click is a log nobody reads, and <b>a log nobody reads is how a real error gets
 * missed</b>: an hour of it looked identical to an hour of a working build.</p>
 *
 * <p>So it is off until asked for, with {@code /cc trace}. The knowledge stays, the noise does
 * not.</p>
 *
 * <h2>The static is two statics</h2>
 *
 * <p>In singleplayer the integrated server loads its own copy of every mod class, so this field
 * exists once per classloader and {@code /cc trace} only ever reached the server's. A session
 * logged two hundred and ninety-nine server lines and no client lines, which reads exactly like
 * "the client produced nothing" and was really "the command flipped the other copy". The flag now
 * travels to the client as a packet. See {@code DebugTogglePayload}, whose javadoc had already
 * written down the lesson this field then contradicted.</p>
 */
public final class CombatTrace {

    private static volatile boolean enabled;

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("cortescombat");

    private CombatTrace() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** @return the new state, so the command can report it */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    /** Set from a packet, because the command only ever reaches the server's copy. */
    public static void set(boolean on) {
        enabled = on;
    }

    /** From the client thread. Cheap when off: a volatile read and nothing else. */
    public static void client(String message) {
        if (enabled) {
            LOG.info("[client] {}", message);
        }
    }

    /** From the server thread, tagged with who swung. */
    public static void server(String playerName, String message) {
        if (enabled) {
            LOG.info("[server] {}: {}", playerName, message);
        }
    }
}
