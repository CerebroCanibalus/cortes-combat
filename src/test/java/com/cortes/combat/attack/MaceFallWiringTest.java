package com.cortes.combat.attack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the engine hands the fall over.
 *
 * <h2>The hole this found, and it is the project's oldest one</h2>
 *
 * <p>Every other test in this pair passes the fall <em>by hand</em> to {@code DamageResolver}.
 * That means the curve was verified, the arithmetic was verified, the cap was verified, and a
 * player falling out of the sky could still have hit for a flat fourteen -- because the engine was
 * calling the five-argument overload, which has no notion of gravity in it.</p>
 *
 * <p>Verified by sabotage: replacing {@code player.fallDistance} with {@code 0.0F} in
 * {@code CombatEngine} left the entire suite green. <b>A tested function with no caller, and a
 * caller with no test.</b> That is the shape of the bug the sable's dead {@code BREAK} track had
 * and of the "every object I hit with can charge" classification fix: something in place, nothing
 * drawing it, and a green build.</p>
 *
 * <p>So this reads the source. Brittle by design, and the alternative is invisible.</p>
 */
class MaceFallWiringTest {

    @Test
    @DisplayName("the engine passes the player's fall, and it is the only caller that matters")
    void theEngineHandsOverTheFall() {
        String engine = read();

        assertTrue(engine.contains("player.fallDistance)"),
                "**CombatEngine has to pass `player.fallDistance` into the resolver.** The curve, "
                        + "the cap and the arithmetic are all tested and all of them call "
                        + "DamageResolver directly with a fall in hand -- so without this line the "
                        + "maza is a flat fourteen in the game and every test in the project is "
                        + "green. Verified by sabotage: a literal 0.0F here passes all of them");

        assertTrue(!engine.contains("0.0F);"),
                "**and not a zero.** The five-argument overload exists precisely so that the "
                        + "fall-free callers stay readable, and it is a trap from this side: "
                        + "calling it compiles, means nothing, and looks correct in the source");
    }

    /**
     * The maza's attribute is this game's number and not vanilla's.
     *
     * <p>Six in vanilla, ten here. The whole weapon's argument is the gap between the two,
     * and a {@code removeModifier} that silently stops matching leaves the item at six with no
     * error anywhere &mdash; the damage would simply be wrong, on one item, forever.</p>
     */
    @Test
    @DisplayName("the maza's damage comes from our number, not vanilla's five")
    void theOffsetIsOurs() {
        assertTrue(MaceRebalance.OFFSET == 14.0F,
                "**the offset has to be fourteen.** Vanilla is five, which with the player's own "
                        + "one is six damage; this game gives it fifteen. A modifier that fails to "
                        + "apply is not an error: it is one item quietly doing a third of what the "
                        + "tooltip says");
    }

    /**
     * And the speed, which is the one number here that a naive derivation gets backwards.
     *
     * <p>{@code ATTACK_SPEED} is an <b>offset from the player's own 4.0</b>, so ten percent slower
     * is not ten percent off the offset. Scaling -3.4 by 0.9 gives -3.06, which is 0.94 attacks a
     * second -- <em>faster</em> than vanilla's 0.6, from a constant whose name reads "slower".</p>
     */
    @Test
    @DisplayName("the maza swings ten percent slower, and the offset is derived from the speed")
    void theSpeedIsSlower() {
        assertEquals(-3.46F, MaceRebalance.SPEED_OFFSET, 0.0001F,
                "**the offset, and it is not -3.4 times 0.9.** That would be -3.06, which is "
                        + "faster than vanilla: the attribute is an offset from the player's 4.0, "
                        + "so the slowdown has to be applied to the speed and subtracted back");
        assertEquals(0.54F, MaceRebalance.ATTACK_SPEED, 0.0001F,
                "which is vanilla's 0.6, ten percent slower");
        assertTrue(MaceRebalance.ATTACK_SPEED < MaceRebalance.PLAYER_BASE_ATTACK_SPEED
                        + MaceRebalance.VANILLA_SPEED_OFFSET,
                "and slower than vanilla, which is the assertion the sign error breaks");
    }

    private static String read() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "attack",
                "CombatEngine.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
