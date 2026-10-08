package com.cortes.combat.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That Density takes the fall and bends it, and never stops bending.
 *
 * <h2>Why the root is the whole test</h2>
 *
 * <p>Vanilla's Density is <b>linear</b> in the fall: {@code 0.5 * level * fallDistance}, read in
 * {@code MaceItem#getAttackDamageBonus} and multiplied straight into the height. Here the fall
 * itself is capped at {@link MaceFall#BONUS_CEILING}, so a linear enchantment on top would grow
 * past the cap and turn the weapon into the enchantment. The square root is the shape that keeps
 * Density monotonic without letting it take the curve over, and <b>the way to assert a square root
 * is the doubling property, not four sampled numbers</b> &mdash; the invariant is
 * {@code f(4x) == 2 f(x)}, which is true of exactly the root and false of anything straight.</p>
 *
 * <p>The gate is the fall's own: a Density maza on the floor is a club, exactly like a maza on the
 * floor. Density scales a fall and does not create one.</p>
 */
class MaceFallDensityTest {

    /** Swinging the same level at two heights, both above the fall's threshold. */
    private static final int LEVEL = 2;

    /**
     * The gate, asserted in both directions.
     *
     * <p>Each zero here is a different way the weapon could stop being itself: a level of zero that
     * still paid would make the enchantment free, and a fall under the threshold that paid would
     * make the threshold a formality.</p>
     */
    @Test
    @DisplayName("no level and no fall both pay nothing, and nothing is ever negative")
    void theGate() {
        assertEquals(0.0F, MaceFall.densityBonus(0, 20.0F), "no enchantment, no bonus");
        assertEquals(0.0F, MaceFall.densityBonus(-3, 20.0F), "a negative level cannot pay either");
        assertEquals(0.0F, MaceFall.densityBonus(LEVEL, MaceFall.THRESHOLD),
                "the threshold itself pays nothing, same as the fall");
        assertEquals(0.0F, MaceFall.densityBonus(LEVEL, -5.0F), "and a negative fall cannot pay");

        for (float fall = -5.0F; fall <= 300.0F; fall += 0.25F) {
            assertTrue(MaceFall.densityBonus(LEVEL, fall) >= 0.0F,
                    "the bonus cannot be negative, and it was " + MaceFall.densityBonus(LEVEL, fall)
                            + " at " + fall + " blocks");
        }
    }

    /**
     * More level and more fall are both worth more, monotonically.
     *
     * <p>Monotonic on purpose: an enchantment that punished a taller fall would make the player
     * afraid of their own weapon.</p>
     */
    @Test
    @DisplayName("it grows with the level and with the fall")
    void itGrows() {
        float atOne = MaceFall.densityBonus(1, 9.0F);
        float atTwo = MaceFall.densityBonus(2, 9.0F);
        float atThree = MaceFall.densityBonus(3, 9.0F);
        assertTrue(atOne < atTwo && atTwo < atThree,
                "every level has to be worth more than the one below it");

        float previous = -1.0F;
        for (float fall = 0.0F; fall <= 200.0F; fall += 0.25F) {
            float bonus = MaceFall.densityBonus(LEVEL, fall);
            assertTrue(bonus >= previous - 0.0001F,
                    "the curve went backwards between " + (fall - 0.25F) + " and " + fall
                            + " blocks: " + previous + " then " + bonus);
            previous = bonus;
        }
        assertTrue(MaceFall.bonusFor(9.0F) > 0.0F, "and the fall it rides on still pays something");
    }

    /**
     * It is a square root, and that is the assertion that catches a straight line.
     *
     * <p><b>Quadruple the fall and the bonus only doubles.</b> A linear Density would quadruple it,
     * and four sampled values could not tell the two apart &mdash; this can, because it is a
     * property of the shape and not a reading of it.</p>
     */
    @Test
    @DisplayName("four times the fall is twice the bonus, not four times")
    void itIsASquareRoot() {
        for (float fall = 2.0F; fall <= 50.0F; fall += 0.5F) {
            float base = MaceFall.densityBonus(LEVEL, fall);
            float quadrupled = MaceFall.densityBonus(LEVEL, 4.0F * fall);
            assertEquals(2.0F * base, quadrupled, 0.02F,
                    "at " + fall + " blocks a straight curve would give " + (4.0F * base)
                            + " and the root gives " + quadrupled);
            assertTrue(quadrupled < 4.0F * base + 0.0001F,
                    "and it is strictly below proportional, which is what bends the curve");
        }
    }

    /**
     * No cap, which is the point of taking the root instead of a ceiling.
     *
     * <p>The base fall is capped; Density is not. That asymmetry is deliberate &mdash; the base
     * curve is the weapon and Density is a rider &mdash; and a {@code min()} accidentally bolted on
     * here would be invisible until a player fell off a mountain and found the number stopped
     * moving.</p>
     */
    @Test
    @DisplayName("it keeps climbing past the base curve's own cap")
    void noCeiling() {
        assertTrue(MaceFall.densityBonus(LEVEL, 1000.0F) > MaceFall.densityBonus(LEVEL, 400.0F),
                "a taller fall is still worth more at a thousand blocks");
        assertTrue(MaceFall.densityBonus(LEVEL, 100000.0F) > MaceFall.densityBonus(LEVEL, 10000.0F),
                "and there is no height where it stops");

        assertEquals(MaceFall.bonusFor(1000.0F), MaceFall.bonusFor(400.0F), 0.0001F,
                "**while the base curve it rides on is capped.** The two disagree on purpose: a "
                        + "cap on Density would make it the same weapon as the fall");
    }
}
