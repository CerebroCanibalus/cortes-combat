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
        assertEquals(0, DamageParticles.countFor(Float.NaN),
                "and a NaN is not a positive number either, so it cannot reach the cast");
    }

    @Test
    @DisplayName("no damage, however large, can ask the client for an unbounded burst")
    void hugeDamageIsCapped() {
        // `/kill` is `hurt(genericKill, Float.MAX_VALUE)`, and actuallyHurt hands that number to
        // LivingDamageEvent.Post without clamping it to health. A saturating cast would turn it
        // into Integer.MAX_VALUE, and the client's particle loop would try to spawn two billion.
        assertEquals(DamageParticles.MAX_COUNT, DamageParticles.countFor(Float.MAX_VALUE),
                "the number /kill produces must not become Integer.MAX_VALUE particles");
        assertEquals(DamageParticles.MAX_COUNT, DamageParticles.countFor(Float.POSITIVE_INFINITY),
                "and an infinity is the same shape of number");
        assertEquals(DamageParticles.MAX_COUNT, DamageParticles.countFor(1.0E9F),
                "well past any real hit");
        // The clamp must not touch the rule underneath it: a real hit still reads half its damage.
        assertEquals(DamageParticles.MAX_COUNT - 1, DamageParticles.countFor(2.0F * (DamageParticles.MAX_COUNT - 1)),
                "the last value below the lid is untouched");
    }
}
