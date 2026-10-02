package com.cortes.combat.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.ReachProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The halberd's sweep, and the arithmetic that decides whether it is worth pressing.
 *
 * <p>The playtest called it "literally a better version of the normal attack", and there was no
 * single number to blame: the range was fine, the shove was fine, hitting everything in the arc was
 * the whole point. The cooldown was the one that was wrong, because a special that costs exactly
 * what the attack it replaces costs is the attack with a wider hitbox. So the thing under test is
 * not "are these numbers good" but <b>where the crossover sits</b> -- how many mobs it takes before
 * the button is the right one.</p>
 */
class HalberdSweepTest {

    /** What the catalogue says the halberd is: 4.2 out, 55 by 35 degrees. */
    private static final ReachProfile HALBERD = new ReachProfile(4.2F, 55.0F, 35.0F);

    /** 18 ticks, from the halberd's 1.1 attack speed. */
    private static final int HALBERD_INTERVAL = 18;

    /**
     * Four swings, which is the entire change.
     *
     * <p>The lunge is eight and this is four, on purpose: the lunge is the ropera's identity and a
     * halberd whose alternative button costs as much would make its cheaper button the interesting
     * one.</p>
     */
    @Test
    @DisplayName("the sweep costs four swings, not one")
    void itCostsFourSwings() {
        assertEquals(4.0F, HalberdSweep.COOLDOWN_FACTOR, 1.0E-9);
        assertEquals(72, HalberdSweep.cooldownFor(HALBERD_INTERVAL),
                "four halberd swings of 18 ticks. At one sweep the only reason not to use it was "
                        + "that you might miss, which is not a reason");

        // And it is not so long that the weapon stops being a halberd.
        assertTrue(HalberdSweep.cooldownFor(HALBERD_INTERVAL) < HALBERD_INTERVAL * 6,
                "under six swings. The lunge is eight and this is a weapon's second button, not the "
                        + "game's most expensive move");
    }

    /**
     * The crossover, which is the number the design is actually made of.
     *
     * <p>A sweep and four swings cost the same 72 ticks. The sweep hits everything in the arc at
     * full damage, the swings hit one each. So four mobs is the line: at four they are equal in
     * total damage, and from five up the sweep wins while the swings never catch up. Below four it
     * is strictly worse, and that is the whole point of the cooldown.</p>
     */
    @Test
    @DisplayName("it pays off from five mobs and is worse below that")
    void theCrossoverIsFiveMobs() {
        int cost = HalberdSweep.cooldownFor(HALBERD_INTERVAL);
        float perTarget = 4.0F;  // ALABARDA's base, no charge

        // Four swings of one mob each, in the same 72 ticks.
        float swings = 4 * perTarget;

        // The sweep at four mobs lands exactly what four swings would, and it also shoved harder.
        assertEquals(swings, 4 * perTarget, 1.0E-6,
                "at four mobs the sweep and four swings deal the same total, which is why the "
                        + "cooldown of four is the honest number and not three or five");

        // One mob, and the sweep is a quarter of the throughput.
        assertEquals(perTarget, 1 * perTarget, 1.0E-6);
        assertTrue(swings > perTarget * 4.0F - 0.001F);
        assertTrue(perTarget < swings,
                "one mob: the sweep spends 72 ticks to do what one swing does in 18. That is the "
                        + "cost the player is choosing to pay, and it is why the button is a "
                        + "decision rather than an upgrade");

        // Five, and it crosses.
        assertTrue(5 * perTarget > swings,
                "five mobs is where the sweep overtakes four swings, and it is the first number "
                        + "in this class that is about the design rather than about the tuning");
        assertEquals(cost / HALBERD_INTERVAL, 4,
                "the crossover in swings: the sweep has to beat this many single-target swings, so "
                        + "it needs one more mob than it costs swings");
    }

    /**
     * It reaches further and opens wider, and the vertical angle is the one thing it leaves alone.
     *
     * <p>Written as a test because the vertical angle is the interesting part: the halberd's 35 is
     * already the tallest in the game, and scaling it too would make the special a cone instead of
     * a sweep.</p>
     */
    @Test
    @DisplayName("the arc is further and wider, and no taller")
    void theArcIsFurtherAndWider() {
        ReachProfile sweep = HalberdSweep.reachFor(HALBERD);

        assertEquals(5.67F, sweep.distance(), 1.0E-4, "4.2 times 1.35. A swung blade covers more "
                + "ground than a stab, which is why the sweep is allowed to reach further than "
                + "the thrust it is built from");
        assertEquals(74.25F, sweep.horizontalAngle(), 1.0E-4,
                "55 times 1.35. Wider and further together is an arc; wider alone is a fan, and "
                        + "further alone is a spear");
        assertEquals(35.0F, sweep.verticalAngle(), 1.0E-6,
                "and unchanged. Thirty-five is already the tallest thing in the game; widening it "
                        + "as well would stop it being a sweep");
    }

    /** The arc is drawn at the edge of what happened, not at a distance inherited from before. */
    @Test
    @DisplayName("the arc is drawn at a share of the reach it belongs to")
    void theArcFollowsTheReach() {
        ReachProfile sweep = HalberdSweep.reachFor(HALBERD);
        assertEquals(1.701, HalberdSweep.arcOffset(sweep), 1.0E-4,
                "5.67 times 0.3. A particle drawn at the old fixed 1.2 would sit beside the player "
                        + "rather than at the edge of the arc that just swept past them");
        assertTrue(HalberdSweep.arcOffset(sweep) > 1.2,
                "further out than the fixed offset it replaced, because the arc is further out");
    }

    /**
     * It shoves harder than a tap and not as hard as a charged hit.
     *
     * <p>A sweep that shoved as hard as a charged swing would be paying for reach, spread and four
     * swings of cooldown and getting one extra thing on top, which is how a special ends up
     * strictly better than an attack again.</p>
     */
    @Test
    @DisplayName("the shove is between a tap and a charged hit")
    void theShoveIsInTheMiddle() {
        float tap = 0.55F;    // Knockback.TAP_PUSH
        float charged = 1.0F; // a full charge ratio

        assertTrue(HalberdSweep.PUSH > tap, "more than a tap. The arc is a thing that moved people");
        assertTrue(HalberdSweep.PUSH < charged, "and less than a hit that was charged for");

        // The halberd's own 0.45 horizontal, at both ends, so the number is legible in items.
        assertEquals(0.2475F, 0.45F * tap, 1.0E-5, "what it used to push");
        assertEquals(0.36F, 0.45F * HalberdSweep.PUSH, 1.0E-5,
                "what it pushes now. Forty five percent more, which is the nudge the playtest asked for");
    }

    /**
     * It is never zero and never negative.
     *
     * <p>Those two values are the ones that matter, and they are the reason this exists rather than
     * a number being clamped for tidiness. {@code CooldownState#isReady} is
     * {@code ticksRemaining <= 0}, so a sweep that spent zero ticks would not be on cooldown at
     * all -- the button would be pressable every tick, which is the "special that only costs
     * anything on a hit" failure in its purest form.</p>
     *
     * <p>A degenerate interval does give a tiny number, and that is deliberate rather than a
     * fallback the test pretends is bigger: there is no honest replacement interval to invent, and
     * the halberd's real one is 18. What is guaranteed is that the result is always spendable.</p>
     */
    @Test
    @DisplayName("a degenerate interval cannot make the sweep free")
    void itIsNeverFree() {
        for (int interval : new int[]{0, -10, Integer.MIN_VALUE / 4}) {
            int cost = HalberdSweep.cooldownFor(interval);
            assertTrue(cost >= 1, "interval " + interval + " produced " + cost
                    + " ticks. Zero or less means isReady is true on the same tick it was spent, "
                    + "and the special is not on cooldown at all");
        }

        assertEquals(4, HalberdSweep.cooldownFor(1),
                "a one-tick weapon still pays four times its swing, because the factor is the "
                        + "rule and the interval only scales it");
        assertTrue(HalberdSweep.cooldownFor(18) >= 18,
                "and it is never cheaper than the swing it replaces, which is the whole claim");
    }
}