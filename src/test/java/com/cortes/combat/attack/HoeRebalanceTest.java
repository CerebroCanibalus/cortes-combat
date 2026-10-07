package com.cortes.combat.attack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * That the hoe's damage comes up by one and a half, off whatever baseline it has.
 *
 * <p>Vanilla's hoe is the one tool whose attack damage is a penalty, and its baselines run
 * {@code 0, -1, -2, -3, -4} by material. The raise is a <b>delta</b> and not a table, so the test
 * walks the five baselines rather than naming five answers -- a seventh material needs no line
 * here, which is the whole reason the live code adds to the modifier instead of writing one.</p>
 */
class HoeRebalanceTest {

    /** Vanilla's five hoe baselines, read out of {@code Items} in 1.21.1. */
    private static final float[] VANILLA = {0.0F, -1.0F, -2.0F, -3.0F, -4.0F};

    @Test
    @DisplayName("every hoe baseline comes up by one and a half")
    void everyBaselineIsRaised() {
        for (float offset : VANILLA) {
            assertEquals(offset + 1.5F, HoeRebalance.raised(offset), 1.0E-6F,
                    "the hoe's baseline of " + offset + " has to become " + (offset + 1.5F)
                            + ". The weapon is a six-target clearing motion in this game, so the "
                            + "number Mojang set for a farm tool is the wrong number");
        }
    }

    @Test
    @DisplayName("the raise is a delta, so it cannot be pinned to one material")
    void itIsADelta() {
        assertEquals(1.5F, HoeRebalance.raised(0.0F), 1.0E-6F);
        assertEquals(-2.5F, HoeRebalance.raised(-4.0F), 1.0E-6F,
                "**and the worst baseline stays a penalty, just a smaller one.** A netherite hoe "
                        + "takes four points off the player's own one; at one and a half back it "
                        + "takes two and a half, and there is no floor to clamp because the "
                        + "player's own one is still underneath it");
        assertEquals(HoeRebalance.ADDED,
                HoeRebalance.raised(3.0F) - 3.0F, 1.0E-6F,
                "the amount is the same for a baseline nobody ships, so the delta is the rule and "
                        + "the five numbers above are only where it lands");
    }
}
