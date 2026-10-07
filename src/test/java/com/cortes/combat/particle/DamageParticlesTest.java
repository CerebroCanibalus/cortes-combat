package com.cortes.combat.particle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * That the damage indicator is vanilla's own count, and that a small hit shows nothing.
 *
 * <p>Vanilla is {@code (int)(damage * 0.5)}, verified at {@code Player:1400} rather than guessed.
 * A floored half means a one-point hit spawns nothing, and that is asserted here so that "there is
 * no feedback for a weak hit" reads as the rule it is instead of as a bug somebody finds later.</p>
 */
class DamageParticlesTest {

    @Test
    @DisplayName("it is half the damage, floored, like vanilla")
    void itIsHalfTheDamage() {
        assertEquals(3, DamageParticles.countFor(7.7F), "seven point seven is three");
        assertEquals(13, DamageParticles.countFor(26.0F), "twenty six is thirteen");
        assertEquals(1, DamageParticles.countFor(2.0F), "two is one");
    }

    @Test
    @DisplayName("a hit under two points shows nothing, and neither does nonsense")
    void smallHitsAndNonsense() {
        assertEquals(0, DamageParticles.countFor(1.9F),
                "**under two points is zero particles**, which is vanilla's own floor and not an "
                        + "off-by-one: a hit that small does not need a burst");
        assertEquals(0, DamageParticles.countFor(0.0F));
        assertEquals(0, DamageParticles.countFor(-5.0F), "and a negative cannot spawn a count");
    }
}
