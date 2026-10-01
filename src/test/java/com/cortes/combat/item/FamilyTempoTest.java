package com.cortes.combat.item;

import com.cortes.combat.attack.AttackTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tempo of the catalogue, going through the arithmetic the item actually uses.
 *
 * <p>This exists because the previous interval test could not have caught what was wrong. It read
 * {@code family.attackSpeed()} and asserted the interval that number implies -- which is the
 * declared number, asserting itself. Every weapon in the game was swinging at roughly double its
 * written tempo for a whole round of playtesting, with a green build and a passing test, because
 * {@code SwordItem.createAttributes} takes an <em>offset</em> from the player's base attack speed
 * and the catalogue handed it the total.</p>
 *
 * <p>So these go through {@link WeaponFamily#attackSpeedOffset()} and add the base back, which is
 * literally what the attribute modifier does when the item is built. The design table lives here
 * as five numbers.</p>
 */
class FamilyTempoTest {

    /**
     * A player's base ATTACK_SPEED. Vanilla's own sword is 1.6 and vanilla writes
     * {@code createAttributes(tier, 3, -2.4F)}, so it is four minus two point four.
     */
    private static final double BASE = 4.0;

    @Test
    @DisplayName("the five intervals the design wrote")
    void theTable() {
        assertEquals(10, WeaponFamily.ROPERA.swingInterval(),
                "the ropera: ten ticks, a deliberate twenty ticks per second");
        assertEquals(8, WeaponFamily.DAGA.swingInterval(),
                "the dagger is the fastest weapon in the game");
        assertEquals(19, WeaponFamily.MARTILLO.swingInterval(),
                "the warhammer was 22 and the playtest called it unusable rather than heavy; "
                        + "it is now the second slowest thing in the game, behind the halberd's 18");
        assertEquals(18, WeaponFamily.ALABARDA.swingInterval(),
                "the halberd is slow and long");
        assertEquals(10, WeaponFamily.SHOTEL.swingInterval(),
                "the sabre matches the ropera on speed and beats it on cone");
    }

    @Test
    @DisplayName("an offset put back on the base gives back the declared speed")
    void theRoundTrip() {
        for (WeaponFamily family : WeaponFamily.values()) {
            double total = BASE + family.attackSpeedOffset();
            assertEquals(family.attackSpeed(), total, 1.0E-6,
                    family + " does not survive the round trip through the attribute");
        }
    }

    @Test
    @DisplayName("every offset is an offset, and not the total handed over")
    void offsetsAreOffsets() {
        // The bug, stated as an invariant. A total passed where an offset belongs is four too
        // fast on every single weapon, and the difference is the player's base attack speed, which
        // is a number that appears exactly once in the whole catalogue.
        for (WeaponFamily family : WeaponFamily.values()) {
            assertTrue(family.attackSpeedOffset() < 0.0F,
                    family + " has a positive speed offset, which means the total was passed "
                            + "through as an offset and it would swing at "
                            + (BASE + family.attackSpeed()) + " instead of " + family.attackSpeed());
        }
    }

    @Test
    @DisplayName("nobody is faster than the dagger or slower than the warhammer")
    void theOrderingIsTheCatalogue() {
        int fastest = WeaponFamily.DAGA.swingInterval();
        int slowest = WeaponFamily.MARTILLO.swingInterval();
        for (WeaponFamily family : WeaponFamily.values()) {
            int ticks = family.swingInterval();
            assertTrue(ticks >= fastest,
                    family + " at " + ticks + " beats the dagger's " + fastest
                            + ", and the dagger is the fastest weapon in the game");
            assertTrue(ticks <= slowest,
                    family + " at " + ticks + " is slower than the warhammer's " + slowest
                            + ", and it is the slowest thing in the game");
            assertTrue(ticks <= AttackTiming.MAX_INTERVAL,
                    family + " exceeds the cap");
        }
    }

    @Test
    @DisplayName("the base is four, and it is written down in one place")
    void theBaseIsFour() {
        // Asserted because it is a literal in vanilla's source and a literal here, and if either
        // one is wrong every weapon in the game is twice as fast with no test failing.
        assertEquals(4.0F, WeaponFamily.PLAYER_BASE_ATTACK_SPEED);
        // Vanilla's own sword, reconstructed: base four plus an offset of negative two point four.
        assertEquals(13, AttackTiming.ticksFor(4.0 - 2.4),
                "this is the interval a vanilla sword gets, and the sword profile has to match it");
    }
}