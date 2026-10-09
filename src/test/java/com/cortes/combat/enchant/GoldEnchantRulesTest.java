package com.cortes.combat.enchant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frenzy, the harvest and the shape of the registration, as read from the files that carry them.
 *
 * <p>The arithmetic is asserted by running it; the fact that a replaced enchantment is really gone
 * is asserted by reading, because nothing else in the suite looks at {@code ModEnchantments} and a
 * build that keeps a dead registration would still be green.</p>
 */
class GoldEnchantRulesTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "com", "cortes", "combat",
            "enchant", "ModEnchantments.java");

    private static String read() {
        try {
            return Files.readString(SOURCE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + SOURCE + ": " + e.getMessage(), e);
        }
    }

    /** The stack grows by one per block, and the ceiling rises with the level. */
    @Test
    void theFrenzyCeilingRisesWithTheLevel() {
        assertEquals(5, GoldEnchantRules.frenzyMaxStacks(1), "one level is five blocks");
        assertEquals(15, GoldEnchantRules.frenzyMaxStacks(3), "three levels are fifteen");
        assertEquals(0, GoldEnchantRules.frenzyMaxStacks(0), "no level, no ceiling");

        // A level three pick at fifteen blocks sits at seventy five percent, and never past it.
        assertEquals(0.75F, GoldEnchantRules.frenzyBonus(3, 15), 1e-6F,
                "fifteen blocks at level three is the ceiling");
        assertEquals(0.75F, GoldEnchantRules.frenzyBonus(3, 999), 1e-6F,
                "**the ceiling is what makes it a level** -- past it, the level stops mattering");
        assertEquals(0.25F, GoldEnchantRules.frenzyBonus(1, 999), 1e-6F,
                "level one tops out at a quarter");
        assertEquals(0.0F, GoldEnchantRules.frenzyBonus(3, 0), 1e-6F, "an empty window pays nothing");
    }

    /** The window closes four seconds after the last block, and not before. */
    @Test
    void theFrenzyWindowCloses() {
        assertFalse(GoldEnchantRules.frenzyExpired(1000, 1000 + GoldEnchantRules.FRENZY_WINDOW_TICKS),
                "the last tick of the window still counts");
        assertTrue(GoldEnchantRules.frenzyExpired(1000, 1000 + GoldEnchantRules.FRENZY_WINDOW_TICKS + 1),
                "**a window that never closed would be a permanent buff**, which is not what a "
                        + "frenzy is: it is a tempo");
    }

    /** Both answers of the harvest are boolean, and neither is a percentage. */
    @Test
    void theHarvestIsTwoAnswers() {
        assertTrue(GoldEnchantRules.harvestReplants(1), "level one replants");
        assertFalse(GoldEnchantRules.harvestDoubles(1), "level one does not double");
        assertTrue(GoldEnchantRules.harvestDoubles(2), "level two doubles");
    }

    /**
     * **The replaced enchantment is gone from the file, not just from the game.** Two speed
     * enchantments in the same hole fight over the same modifier, and the frenzy was chosen
     * because its ceiling makes the pick a different tool for a different job.
     */
    @Test
    void windDiggerWasReplaced() {
        String source = read();
        assertFalse(source.contains("wind_digger"),
                "`wind_digger` sigue registrado: dos encantamientos de velocidad de minado en el "
                        + "mismo hueco se pelean por el mismo atributo y el techo de la fiebre deja "
                        + "de ser una decision de nivel");
        assertTrue(source.contains("miner_frenzy"), "pero la fiebre si esta");
        assertTrue(source.contains("golden_hands"), "y manos doradas");
        assertTrue(source.contains("golden_harvest"), "y cosecha dorada");
    }

    /** The roll itself, so the ceiling is seen to bind with a real random source. */
    @Test
    void theFrenzyNeverOvershootsWithARealRandom() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            int level = 1 + random.nextInt(3);
            FrenzyState state = FrenzyState.EMPTY;
            int now = 1000;
            for (int block = 0; block < 40; block++) {
                state = state.broken(now, GoldEnchantRules.frenzyMaxStacks(level));
                now++;
            }
            assertTrue(state.stacks() <= GoldEnchantRules.frenzyMaxStacks(level),
                    "un estado por encima de su techo: " + state.stacks());
            assertEquals(state.stacks(),
                    (int) (GoldEnchantRules.frenzyBonus(level, state.stacks()) * 20),
                    "el bonus y el stack dicen lo mismo, porque el bonus ES el stack por cinco por ciento");
        }
    }
}
