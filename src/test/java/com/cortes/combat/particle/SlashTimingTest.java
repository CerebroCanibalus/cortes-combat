package com.cortes.combat.particle;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sabre's double, as numbers.
 *
 * <p>The player reported being able to tell the damage was doubled and unable to see why, which is
 * the exact failure of a number without a picture. The fix is a second cut a beat after the first,
 * and this pins the three properties that make it read as <em>twice</em> rather than as one cut
 * with extra brightness.</p>
 *
 * <p><b>These live on {@link SlashTiming} and not on the particle's options</b>, because the
 * options record implements {@code ParticleOptions} and a bare JVM cannot load it. The first
 * version of this test touched the options directly and died with {@code error: cannot access
 * ParticleOptions} -- which is the game being absent, not a value being wrong, and a test that
 * cannot run teaches nobody anything.</p>
 */
class SlashTimingTest {

    @Test
    @DisplayName("the gap puts the second cut after the first is gone")
    void theSecondCutArrivesAfterTheFirst() {
        // Eight, against a seven tick strip. Sixteen was tried and rejected as too long to read as
        // one swing; four was tried and rejected as too little to read as two. The strip is seven
        // ticks, so eight is the first whole number where the first cut is finished before the
        // second begins.
        //
        // The number only started mattering once the sprite was fixed. It was two, then four, then
        // sixteen, all of them adjusting a particle that was never being drawn at all -- its sprite
        // was nulled at spawn and never restored. Three rounds of tuning an invisible thing.
        assertEquals(8, SlashTiming.DOUBLE_GAP_TICKS);
        assertTrue(SlashTiming.DOUBLE_GAP_TICKS > SlashTiming.STRIP_TICKS,
                "the first cut has to be GONE before the second begins. Any overlap at all turns "
                        + "two cuts of the same sprite into one shape, which is why four ticks was "
                        + "always going to be the wrong answer");
    }

    /**
     * The second cut goes to the side, because two of the same sprite in the same place are one
     * shape drawn twice -- however far apart in time.
     *
     * <p>The gap alone cannot carry this. The player reads the silhouette, and the silhouette of
     * the second cut is identical to the first one's, so no amount of waiting turns one shape into
     * two. Moving it across is what does.</p>
     */
    @Test
    @DisplayName("and it is drawn beside the first, not on top of it")
    void theSecondCutIsBesideTheFirst() {
        double[] forward = {0.0, 1.0};
        double[] sideways = SlashTiming.secondCutOffset(forward);

        assertEquals(0.5, Math.hypot(sideways[0], sideways[1]), 1.0E-6,
                "half a block, which puts the pair across the body of the thing that was cut");
        assertEquals(0.0, sideways[1], 1.0E-6,
                "and NOT along the swing: a cut further down the same line is indistinguishable "
                        + "from the same cut drawn twice, because the player has no frame of "
                        + "reference for where the blade already went");
        assertTrue(sideways[0] != 0.0,
                "so it really is across, and not a no-op offset that leaves the two stacked");

        // Unit length in, half a block out, whatever the aim.
        for (int degrees = 0; degrees <= 360; degrees += 15) {
            double rad = Math.toRadians(degrees);
            double[] aim = {Math.sin(rad), Math.cos(rad)};
            double[] offset = SlashTiming.secondCutOffset(aim);
            assertEquals(0.5, Math.hypot(offset[0], offset[1]), 1.0E-6,
                    "at " + degrees + " degrees the offset came out "
                            + Math.hypot(offset[0], offset[1]));
        }

        // And a degenerate aim is survivable rather than a NaN reaching the spawn call.
        double[] broken = SlashTiming.secondCutOffset(new double[]{0.0, 0.0});
        assertEquals(0.0, Math.hypot(broken[0], broken[1]), 1.0E-6);
    }

    /**
     * A doubled strike is decided by the charge profile, not by the weapon's name.
     *
     * <p>The damage multiplier and the picture have to agree about what "full charge" means, and
     * the only thing that guarantees it is reading both from one number. Asking "is this the
     * sabre" would be a second place to keep the rule, and the day somebody retunes the bonus
     * through a data pack the weapon would double its damage while drawing a single cut.</p>
     */
    /**
     * A doubling is an identity, and this is the test that says so.
     *
     * <p>The first version asked whether the charge bonus was big enough. The sword and the
     * trident carry the sabre's bonus <em>exactly</em> -- both are 0.5 -- so a threshold handed the
     * doubling to three weapons, and this test caught it before a playtest did. A bonus is a damage
     * number and two weapons sharing one is unremarkable; "lands two hits" is not a number at all,
     * and asking a shared number to carry it was the mistake.</p>
     */
    @Test
    @DisplayName("the doubling is the sabre's identity, and no bonus number can grant it")
    void theDoubleIsAnIdentityNotANumber() {
        // The sabre, at its own bonus.
        assertTrue(SlashTiming.chargesToDouble(WeaponProfile.SABRE, new ChargeProfile(true, 0.5F, 20)));
        // The sword and the trident, at the SAME bonus. This pair is the whole test: if the rule
        // were a threshold, both of these would double too.
        assertFalse(SlashTiming.chargesToDouble(WeaponProfile.SWORD, new ChargeProfile(true, 0.5F, 25)),
                "the sword carries the sabre's exact charge bonus and is still not a doubler");
        assertFalse(SlashTiming.chargesToDouble(WeaponProfile.TRIDENT, new ChargeProfile(true, 0.5F, 25)));
        // And a weapon that cannot charge cannot double, whatever it is.
        assertFalse(SlashTiming.chargesToDouble(WeaponProfile.SABRE, ChargeProfile.NONE),
                "the sabre with charge removed is not a doubling weapon, and a test that said "
                        + "otherwise would pass while the swing landed once");
    }

    /**
     * Two hits, and only at a full charge.
     *
     * <p>The threshold matters more than it looks. A swing five ticks into a twenty tick window is
     * "charging", and the earlier version counted that as charged -- so the promise appeared before
     * the player had earned it, which is the exact mistake the charge threshold exists to prevent
     * everywhere else in this project.</p>
     */
    @Test
    @DisplayName("two hits at a full charge, and one at anything less")
    void theSecondHitNeedsTheWholeWindow() {
        ChargeProfile sabre = new ChargeProfile(true, 0.5F, 20);
        assertEquals(2, SlashTiming.hitsAt(WeaponProfile.SABRE, sabre, 1.0F));
        // The threshold is exactly 1.0F and the test asserts it on the value the game can actually
        // produce. The charge path is a remap that ends at the window and is clamped to one, so the
        // only full-charge reading is 1.0 itself -- and a value one ULP below it is genuinely "not
        // full", which is the point of testing 0.9 rather than something that rounds to one.
        assertEquals(1, SlashTiming.hitsAt(WeaponProfile.SABRE, sabre, 0.9F),
                "not quite full is one hit");
        assertEquals(1, SlashTiming.hitsAt(WeaponProfile.SABRE, sabre, 0.0F), "a tap is one hit");
        assertEquals(1, SlashTiming.hitsAt(WeaponProfile.SWORD, new ChargeProfile(true, 0.5F, 25), 1.0F),
                "a full charge on the sword is one hit at double weight, which is a different thing");
    }

    /**
     * The wait is added to the strip, never taken out of it.
     *
     * <p>This is the one that is easy to get backwards and impossible to see. Measuring a delayed
     * slash's animation against its full lifetime starts it a third of the way through the strip,
     * so the second cut appears pre-faded and cuts out -- which reads as a second, <em>weaker</em>
     * hit. That is worse than showing one hit at all, because it tells the player the weapon has a
     * light swing and a heavy one rather than that it swung twice.</p>
     */
    @Test
    @DisplayName("a delayed slash still lives long enough to play its whole strip")
    void theWaitIsAddedNotSubtracted() {
        for (int delay : new int[]{0, SlashTiming.DOUBLE_GAP_TICKS, 5}) {
            int total = SlashTiming.totalTicks(delay);
            assertEquals(SlashTiming.STRIP_TICKS, total - delay,
                    "delay=" + delay + ": the strip gets its full " + SlashTiming.STRIP_TICKS
                            + " ticks on top of the wait, not " + (SlashTiming.STRIP_TICKS - delay)
                            + " ticks squeezed into it");
            assertTrue(total > delay,
                    "delay=" + delay + ": and it has to still be alive once the wait is over, or "
                            + "the second cut never appears at all");
        }
    }

    @Test
    @DisplayName("a negative delay is refused here, where a test can see it")
    void negativeDelaysAreClamped() {
        // Clamping keeps the particle's age counter a plain comparison. Letting a negative through
        // would mean either drawing a slash before it existed or special-casing the sprite index,
        // and both are worse than a zero.
        assertEquals(0, SlashTiming.delayTicks(-4));
        assertEquals(0, SlashTiming.delayTicks(0));
        assertEquals(3, SlashTiming.delayTicks(3));
        // And the clamp has to reach the lifetime too, or a clamped delay would leave a particle
        // living longer than its strip.
        assertEquals(SlashTiming.STRIP_TICKS, SlashTiming.totalTicks(-4));
    }
}
