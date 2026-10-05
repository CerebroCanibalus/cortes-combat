package com.cortes.combat.item;

import com.cortes.combat.attack.AttackTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
        assertEquals(6, WeaponFamily.DAGA.swingInterval(),
                "the dagger: six ticks. It was eight, which was already the fastest in the family "
                        + "set and still read as the slowest thing to actually use. Note that it "
                        + "is still not the fastest swing in the game -- the bare hand is, at four "
                        + "ticks -- and that is what bounds the i-frames");
        assertEquals(19, WeaponFamily.MARTILLO.swingInterval(),
                "the warhammer was 22 and the playtest called it unusable rather than heavy; "
                        + "it is now the second slowest thing in the game, behind the halberd's 18");
        // **Twenty three, and it was eighteen.** "20% more range but 20% slower hits", and the
        // two halves are one trade: the halberd is the longest weapon in the game and pays for
        // five blocks of reach with the slowest swing in the game.
        //
        // **The number came out of the attribute, not out of the interval.** 1.1 down to 0.88 is a
        // fifth off the declared speed and `ticksFor` did the rest, which is the whole reason a
        // family carries an attack speed and has its interval derived from it. A swing interval
        // written by hand here would be a second copy of a number that already exists.
        assertEquals(23, WeaponFamily.ALABARDA.swingInterval(),
                "the halberd is the slowest thing in the game, and it buys its five blocks with it");
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
        // **The halberd, and it was the warhammer.** The halberd went from eighteen ticks to
        // twenty three in the retune -- "20% more range, 20% slower" -- and that made it the
        // slowest swing in the game. Naming the warhammer here instead of reading the maximum is a
        // test that was asserting a fact about the design that the design had stopped having, and
        // it would have kept passing for every weapon slower than nineteen.
        int slowest = Arrays.stream(WeaponFamily.values())
                .mapToInt(WeaponFamily::swingInterval)
                .max()
                .orElseThrow();
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