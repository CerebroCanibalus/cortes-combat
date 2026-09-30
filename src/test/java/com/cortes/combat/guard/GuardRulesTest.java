package com.cortes.combat.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block arc, in a bare JVM.
 *
 * <p>Every coordinate is explicit on purpose: the bug this class is most likely to have is
 * getting the sign of "in front of me" backwards, and a test that hides its geometry behind a
 * helper cannot catch that. It did. The first version of this class pointed the vector from
 * the attacker to the player, which inverts the rule: you block what is behind you and eat
 * what is in front of you.</p>
 */
class GuardRulesTest {

    /** Player at the origin, looking down +Z. That is what "facing" means here. */
    private static final double ARC = GuardRules.GUARD_ARC_DEGREES;

    @Test
    @DisplayName("a hit straight ahead is frontal")
    void straightAhead() {
        assertTrue(GuardRules.isFromFront(0, 0, 0, 0, 0, 1, 0, 0, 5, ARC));
    }

    @Test
    @DisplayName("a hit from directly behind is not frontal")
    void directlyBehind() {
        assertFalse(GuardRules.isFromFront(0, 0, 0, 0, 0, 1, 0, 0, -5, ARC));
    }

    @Test
    @DisplayName("the arc is 60 degrees either side with the default width")
    void arcWidth() {
        // 50 degrees off to the side: inside.
        double fifty = Math.toRadians(50);
        assertTrue(GuardRules.isFromFront(0, 0, 0, 0, 0, 1,
                -5 * Math.sin(fifty), 0, 5 * Math.cos(fifty), ARC));
        // 80 degrees off to the side: outside.
        double eighty = Math.toRadians(80);
        assertFalse(GuardRules.isFromFront(0, 0, 0, 0, 0, 1,
                -5 * Math.sin(eighty), 0, 5 * Math.cos(eighty), ARC));
    }

    @Test
    @DisplayName("a hit straight from above is not frontal: the player has to be facing it")
    void straightAbove() {
        // Looking at the horizon and taking a hit from directly overhead is 90 degrees off, so
        // outside the arc. That is the rule, not an accident: the guard rewards turning towards
        // a threat, and a player who never looked up did not choose to face it.
        assertFalse(GuardRules.isFromFront(0, 0, 0, 0, 0, 1, 0, 5, 0, ARC));
    }

    @Test
    @DisplayName("a hit from in front and above is frontal once the player looks up")
    void fromAboveWhenLookingUp() {
        // The player pitches up to 45 degrees, so the same source is now dead ahead.
        double pitch = Math.toRadians(45);
        assertTrue(GuardRules.isFromFront(0, 0, 0, 0, Math.sin(pitch), Math.cos(pitch),
                0, 5 * Math.cos(pitch), 5 * Math.sin(pitch), ARC));
    }

    @Test
    @DisplayName("a source on top of the player counts as frontal, not as a division by zero")
    void samePosition() {
        // The degenerate case a player hits within a tick of spawning on a mob.
        assertTrue(GuardRules.isFromFront(0, 1, 0, 0, 0, 1, 0, 1, 0, ARC));
    }

    @Test
    @DisplayName("turning around makes a previously frontal hit a back hit")
    void turningAround() {
        double x = 0;
        double z = 5;
        // Looking at it: blocked.
        assertTrue(GuardRules.isFromFront(0, 0, 0, 0, 0, 1, x, 0, z, ARC));
        // Spun 180 degrees: the same hit is now from behind.
        assertFalse(GuardRules.isFromFront(0, 0, 0, 0, 0, -1, x, 0, z, ARC));
    }
}
