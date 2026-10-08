package com.cortes.combat.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the held-button loop is wired, and that it is wired the three ways that matter.
 *
 * <h2>Why this reads the source</h2>
 *
 * <p>{@code SwingInputHandler} takes a {@code Minecraft}, so none of it is reachable from a test,
 * and deleting one line from it compiles, keeps every assertion in the project green, and leaves a
 * weapon that has to be clicked a hundred times. That is the shape this project has paid for
 * repeatedly: a component in place and nothing drawing it.</p>
 *
 * <p>Brittle by design. The two ways to be wrong here are a false positive when the file is
 * reformatted and an invisible bug when nobody checks, and the first one is cheap.</p>
 */
class SwingInputHandlerWiringTest {

    private static final String HANDLER =
            "src/main/java/com/cortes/combat/client/SwingInputHandler.java";

    /**
     * The automatic strike happens, and it happens from the tick.
     *
     * <p><b>Not from the press.</b> A press is an event; holding a button is a state, and the
     * button is down for hundreds of ticks with no further events arriving. Whichever way it is
     * written, the loop has to be a per-tick question or there is no loop at all.</p>
     */
    @Test
    @DisplayName("the tick is what repeats the strike, not the press")
    void theTickRepeatsTheStrike() {
        String handler = readSource();

        assertTrue(handler.contains("autoStrikeWhileHeld("),
                "**there has to be a method for it.** Without one the press is a single event, "
                        + "and a button that is down for two seconds produces one strike");

        int tick = handler.indexOf("public static void tick()");
        int call = handler.indexOf("autoStrikeWhileHeld(mc, player);");
        assertTrue(tick > 0 && call > tick,
                "and it has to be called from tick(). The press is an event and holding is a "
                        + "state: no events arrive while the button stays down, so a loop written "
                        + "as a press is a loop that runs once");
    }

    /**
     * The loop asks the question that has the maza in it.
     *
     * <p>Not "does not charge". The bare fist charges, the sword charges, and asking only that
     * would make every charging weapon repeat -- which is the third state, where a tap is already
     * a charge and holding means two things at once.</p>
     *
     * <p><b>It asks {@code canHoldDown} and it cannot ask {@code autoRepeats(.., cocks)} any
     * more.</b> The cock gate was here for one round and it took the repeat away from the ropera
     * and the dagger -- the two weapons whose profiles read the wind-up -- which are exactly the
     * two the playtest reported as "holding does nothing". The rule the project asserts everywhere
     * else is "no charge means it repeats, the maza aside", and this call site was the one not
     * asking it.</p>
     */
    @Test
    @DisplayName("the loop asks canHoldDown, which is not the same as asking about charge")
    void theLoopAsksTheRightQuestion() {
        String handler = readSource();

        assertTrue(handler.contains("WeaponTables.canHoldDown("),
                "**it has to ask canHoldDown.** Asking whether the weapon charges is the trap: "
                        + "the fist charges at 0.3 over twelve ticks and the sword charges at 0.5 "
                        + "over twenty five, so both would repeat and a tap on either is already "
                        + "a charge in progress. And the cock argument that used to be passed here "
                        + "is what silently disabled the repeat for the ropera and the dagger");
    }

    /**
     * A weapon that cannot charge fires on the press, and the press still claims the click.
     *
     * <h2>The bug this is for</h2>
     *
     * <p>The press/release split only has a question to answer on a weapon that <em>charges</em>:
     * how long did the button go down. On one that does not, resolving on the release is pure
     * latency and it reads as the game not hearing the click -- <b>"I hold it down and nothing
     * happens until I let go"</b>, which is what the ropera and the dagger were reported as.</p>
     *
     * <p><b>And the fix must not hand the click to vanilla.</b> A {@code return} that set
     * {@code NOT_OURS} for a non-charging weapon is the exact bug that once ran the ropera on
     * vanilla's whole attack path, so the assertion here is two-sided: the branch fires <em>and</em>
     * the claim was already {@code OURS} above it.</p>
     *
     * <p>A source read, like every other guard in this class, because {@code press} takes a
     * {@code Minecraft}.</p>
     */
    @Test
    @DisplayName("a weapon that cannot charge is claimed and fires on the press")
    void thePressFiresWhenItCannotCharge() {
        String handler = readSource();

        assertTrue(handler.contains("WeaponTables.firesOnPress("),
                "**press() has to ask firesOnPress and resolve there.** Without it a weapon that "
                        + "cannot charge opens a swing on the press and pays nothing until the "
                        + "release, which is the reported bug");

        // The question and the claim, in the press body.
        int press = handler.indexOf("private static void press(Minecraft mc)");
        assertTrue(press > 0, "press() has to exist for this to be about anything");
        int end = handler.indexOf("\n    /**", press);
        String body = handler.substring(press, end > press ? end : handler.length());

        int claim = body.indexOf("claim = Claim.OURS;");
        int fires = body.indexOf("firesOnPress(");
        assertTrue(claim > 0 && fires > claim,
                "**the claim has to come before the fire.** `claim` is what stops vanilla from "
                        + "also attacking; a return that handed the click back would run the "
                        + "ropera and the dagger on vanilla's whole attack path, which is a bug "
                        + "this project already paid for once");
        assertTrue(body.contains("new SwingPayload("),
                "and the open swing is still sent, so the server has something to resolve the "
                        + "immediate fire against");
    }

    /**
     * It does not close the swing.
     *
     * <p><b>This is the one that is easy to get right by accident.</b> The ropera and the dagger
     * cannot be charged, and their viewmodel profiles are the ones that read the wind-up -- so
     * closing {@code holding} to send a strike is precisely what made them fail to render once.
     * The swing stays open and the strike goes underneath.</p>
     */
    @Test
    @DisplayName("the automatic strike leaves the open swing open")
    void itDoesNotCloseTheSwing() {
        String auto = readSource();

        int start = auto.indexOf("private static void autoStrikeWhileHeld(");
        int end = auto.indexOf("private static void autoStrikeWhileHeld(") + 1;
        // The method body runs to the next top-level javadoc or the end of the class.
        int stop = auto.indexOf("\n    /**", end);
        String body = auto.substring(start, stop > 0 ? stop : auto.length());

        assertTrue(start > 0, "the method has to exist for this to be about anything");
        assertTrue(!body.contains("holding = false"),
                "**it must not set `holding = false`.** The ropera and the dagger cannot be "
                        + "charged and their viewmodel profiles read the wind-up, so closing the "
                        + "swing to send a strike is what made them never render once -- which is "
                        + "the bug this line exists to not bring back");
    }

    /**
     * Letting go does not pay for a hit that was already paid.
     *
     * <p><b>The hole in the design, found while writing it.</b> The swing stays open so the wind-up
     * survives, which means the release still fires -- and by then the automatic strike has already
     * moved the cooldown. Without a guard, one held button is many strikes and then one more the
     * player did not ask for, on the way up.</p>
     *
     * <p>Asserted as a guard inside {@code release}, not as "release calls ready", because the
     * shape that matters is that it <em>stops</em>: the charge is discarded and nothing is sent.</p>
     */
    @Test
    @DisplayName("releasing inside the cooldown closes the swing without swinging")
    void theReleaseCannotPayTwice() {
        String handler = readSource();

        int release = handler.indexOf("private static void release(Minecraft mc)");
        int end = handler.indexOf("private static void release(Minecraft mc)") + 1;
        int stop = handler.indexOf("\n    /**", end);
        String body = handler.substring(release, stop > 0 ? stop : handler.length());

        assertTrue(release > 0, "release() has to exist for this to be about anything");
        int guard = body.indexOf("if (!ready())");
        assertTrue(guard > 0,
                "**release() has to refuse inside the cooldown.** It used to swing anyway -- "
                        + "ready() was checked on the press and never here -- and that was "
                        + "survivable while every swing came from a press. It is not now: a held "
                        + "button has already paid several, and letting the release through on top "
                        + "is a second hit from one press, arriving when the player lets go");
        assertTrue(body.indexOf("holding = false;", guard) > guard,
                "and it has to close the swing, or the wind-up keeps running after the button is "
                        + "up");

        // **One packet, counted.**
        //
        // <p>The first two versions of this assertion were about <em>where</em> the guard sat, and
        // both were evaded: `pay > guard` walked past a second check inserted after the packet,
        // and `lastIndexOf(guard) < pay` walked past a second check inserted before it. Both
        // sabotage numbers were technically satisfied and the bug was still reachable, which is
        // the assertion trying to be clever about ordering instead of saying the thing.</p>
        //
        // <p>The thing is simpler than either: <b>a release sends exactly one swing</b>. The
        // double hit is not "the guard is in the wrong place", it is two packets, and counting
        // them cannot be talked around by moving a line.</p>
        assertEquals(1, countOccurrences(body, "new SwingPayload("),
                "**a release sends exactly one swing.** The double hit this exists to prevent is "
                        + "not a guard in the wrong place, it is two packets leaving: one from the "
                        + "automatic strike and one from the button coming up. Count them, and the "
                        + "position of any check stops mattering");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    private static String readSource() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "client",
                "SwingInputHandler.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
