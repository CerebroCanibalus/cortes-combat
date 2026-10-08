package com.cortes.combat.block;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That hardening water is one door, and the door fizzes.
 *
 * <h2>Why this reads the source</h2>
 *
 * <p>The block converts a water cell to obsidian in two places -- {@code neighborChanged} for the
 * cell that changed and {@code onPlace} for water already touching the block -- and the fizz has to
 * ride along with <em>every</em> conversion. A bare-JVM test cannot drive the block, but the shape
 * worth guarding is textual: one private {@code harden} that both paths call, and the fizz inside
 * it. A second conversion written straight into a caller would be silent steam loss, and the build
 * would stay green.</p>
 *
 * <p>The event is vanilla's {@code LevelEvent.LAVA_FIZZ} and not a number typed here, so the guard
 * asserts the symbol: {@code LevelEvent.LAVA_FIZZ} is what the client's own hander for lava-water
 * quenching dispatches on, and a bare {@code 1501} would be a copy of that contract.</p>
 */
class FirestoneFizzTest {

    @Test
    @DisplayName("the fizz is vanilla's LAVA_FIZZ event, and only the conversion emits it")
    void theFizzRidesWithTheObsidian() {
        String src = read();

        assertTrue(src.contains("LevelEvent.LAVA_FIZZ"),
                "**the fizz has to be vanilla's own event.** It plays the hiss and the steam for "
                        + "lava quenching on water, server side, for every player in range -- the "
                        + "exact effect wanted, with no new asset and no per-client code");
        assertTrue(src.contains("levelEvent(LevelEvent.LAVA_FIZZ, water, 0)"),
                "and it is emitted at the cell that actually turned, not the block");

        int conversions = count(src, "Blocks.OBSIDIAN.defaultBlockState()");
        assertEquals(1, conversions,
                "**every water-to-obsidian write goes through the one `harden`, so the fizz cannot "
                        + "be dropped from one path and kept in the other.** A second conversion "
                        + "written straight into a caller is a silent missing fizz; this assertion "
                        + "is what makes that a red build. It found " + conversions + " writes");

        assertTrue(src.contains("private static void harden(ServerLevel level, BlockPos water)"),
                "and the door is the helper both callers use");
    }

    private static int count(String haystack, String needle) {
        int hits = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            hits++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return hits;
    }

    private static String read() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "block",
                "FirestoneBlock.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
