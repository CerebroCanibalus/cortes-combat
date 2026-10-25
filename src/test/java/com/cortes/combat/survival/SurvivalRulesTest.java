package com.cortes.combat.survival;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every rule in {@code DESIGN.md} section 5, asked as a question.
 *
 * <h2>What is being asserted, and what is deliberately not</h2>
 *
 * <p>The <b>mechanics</b>: that healing costs food, that it stops at the floor, that the gamerule
 * and being at full health both gate it, that food returns on its own clock, that the clock rests
 * at a full bar, that starvation happens at zero and nowhere else, and that a clock that was
 * interrupted starts over. Those are rules, and a rule written backwards looks identical to one
 * written forwards until somebody plays it.</p>
 *
 * <p>Not asserted: whether twenty ticks is the right interval, whether six is the right floor.
 * {@code ARCHITECTURE.md} section 7 is explicit that these are tuned by playing, and a test that
 * confirmed a number against itself would be the worst kind -- green while proving nothing.</p>
 */
class SurvivalRulesTest {

    /** Small numbers so the clocks are readable in a loop. Regen is parked at a huge interval. */
    private static final SurvivalTuning FAST = new SurvivalTuning(3, 1, 0, 1_000_000);

    /** The DESIGN proposal, for the assertions that are about the proposal itself. */
    private static final SurvivalTuning DEFAULTS = SurvivalTuning.DEFAULTS;

    private static SurvivalRules.Plan run(SurvivalTuning tuning, int ticks, int food, boolean hurt) {
        SurvivalState state = SurvivalState.NEW;
        SurvivalRules.Plan plan = null;
        for (int i = 0; i < ticks; i++) {
            plan = SurvivalRules.step(tuning, state, food, hurt, true);
            state = plan.state();
            food += plan.foodDelta();
        }
        return plan;
    }

    // --- Healing ----------------------------------------------------------------

    @Test
    @DisplayName("healing fires on the interval, not before it")
    void healingFiresOnTheInterval() {
        assertEquals(0, run(FAST, 2, 20, true).healAmount(),
                "the first two ticks must not heal: the interval is three");
        assertEquals(1, run(FAST, 3, 20, true).healAmount(),
                "and the third is the one that pays out");
        assertEquals(1, run(FAST, 6, 20, true).healAmount(),
                "a second interval is a second heal, so the clock has to have reset");
        assertEquals(2, countHeals(FAST, 6, 20),
                "six ticks at an interval of three is exactly two heals");
    }

    @Test
    @DisplayName("every point healed costs food, and the cost lands in the same tick")
    void healingCostsFood() {
        SurvivalRules.Plan healed = SurvivalRules.step(
                new SurvivalTuning(1, 4, 0, 1_000_000), SurvivalState.NEW, 20, true, true);

        assertEquals(1, healed.healAmount(), "the interval is one, so the first tick heals");
        assertEquals(-4, healed.foodDelta(),
                "**and it has to cost the configured price, same tick.** Healing that does not "
                        + "spend food is the closed cycle with the price removed, which is the "
                        + "one thing DESIGN section 5 says food is for");
    }

    /**
     * The closed cycle, stated as arithmetic: what healing took out is exactly what healing
     * granted, with the regeneration clock parked so it cannot muddy the sum.
     *
     * <p>This is the assertion that catches a cost applied twice, a cost skipped, or a cost
     * taken from the wrong number -- none of which announce themselves, because each one still
     * produces a game where healing mostly works.</p>
     */
    @Test
    @DisplayName("the food spent over a whole fight is exactly the price times the healing")
    void healingAlwaysPays() {
        SurvivalTuning tuning = new SurvivalTuning(2, 3, 6, 1_000_000);
        int startFood = 20;

        int food = startFood;
        int heals = 0;
        SurvivalState state = SurvivalState.NEW;
        for (int i = 0; i < 400; i++) {
            SurvivalRules.Plan plan = SurvivalRules.step(tuning, state, food, true, true);
            state = plan.state();
            food += plan.foodDelta();
            heals += plan.healAmount();
        }

        assertTrue(heals > 0, "the fight has to have happened for this to be about anything");
        assertEquals(startFood - tuning.healFoodCost() * heals, food,
                "**food spent has to equal price x healing.** A gap here means healing either "
                        + "cost nothing or cost twice, and both read as 'the balance feels off' "
                        + "in playtest rather than as a bug anybody can name");
        assertTrue(food < tuning.minFoodToHeal(),
                "and it has to stop: below the floor nothing heals, which is why the loop ends");
    }

    @Test
    @DisplayName("below the food floor, nothing heals no matter how long you wait")
    void theFloorStopsHealing() {
        SurvivalTuning tuning = new SurvivalTuning(1, 1, 6, 1_000_000);
        SurvivalRules.Plan plan = run(tuning, 500, 5, true);

        assertEquals(0, plan.healAmount(), "five food is under a floor of six");
        assertEquals(0, plan.foodDelta(), "so no food is spent either");

        // And the clock, charged while healing was still legal, has to let go of it.
        //
        // The obvious version of this test only ran at five food, where the clock was never
        // incremented in the first place -- so `healTicks == 0` was true whether or not the reset
        // existed, and the assertion could not fail. A guard that cannot fail asserts nothing;
        // this charges it first and then drops the floor out from under it.
        SurvivalTuning charges = new SurvivalTuning(3, 1, 6, 1_000_000);
        SurvivalState state = SurvivalState.NEW;
        state = SurvivalRules.step(charges, state, 20, true, true).state();
        state = SurvivalRules.step(charges, state, 20, true, true).state();
        assertEquals(2, state.healTicks(), "two of three ticks while the floor allows it");

        state = SurvivalRules.step(charges, state, 5, true, true).state();
        assertEquals(0, state.healTicks(),
                "**dropping below the floor has to reset the clock.** Holding it would hand out a "
                        + "heal the instant food came back -- on top of the one the player was "
                        + "already owed -- and a clock that charged while healing was illegal and "
                        + "then fired when it became legal again is a heal the design never "
                        + "approved");
    }

    @Test
    @DisplayName("the natural-regeneration gamerule gates healing")
    void theGameruleGatesHealing() {
        SurvivalState state = SurvivalState.NEW;
        for (int i = 0; i < 100; i++) {
            SurvivalRules.Plan plan = SurvivalRules.step(FAST, state, 20, true, false);
            state = plan.state();
            assertEquals(0, plan.healAmount(),
                    "a server that turned off natural regeneration must not be overruled by this "
                            + "mod, which is why vanilla's own gate is still asked");
        }
    }

    @Test
    @DisplayName("at full health nothing heals and nothing is spent")
    void fullHealthDoesNotHeal() {
        SurvivalRules.Plan plan = run(new SurvivalTuning(1, 1, 0, 1_000_000), 50, 20, false);

        assertEquals(0, plan.healAmount(), "isHurt() is false, so there is nothing to heal");
        assertEquals(0, plan.foodDelta(), "and nothing may be spent on a heal that did not happen");
    }

    @Test
    @DisplayName("an interrupted heal starts over rather than resuming")
    void anInterruptedHealStartsOver() {
        SurvivalTuning tuning = FAST;
        SurvivalState state = SurvivalState.NEW;

        for (int i = 0; i < 2; i++) {
            state = SurvivalRules.step(tuning, state, 20, true, true).state();
        }
        assertEquals(2, state.healTicks(), "two of three ticks in");

        state = SurvivalRules.step(tuning, state, 20, false, true).state();
        assertEquals(0, state.healTicks(), "one tick without a wound and it is back to zero");

        for (int i = 0; i < 2; i++) {
            state = SurvivalRules.step(tuning, state, 20, true, true).state();
        }
        assertEquals(2, state.healTicks(),
                "**so two more ticks are still not enough.** If the interruption had been ignored "
                        + "the count would be at four and the heal would have fired on tick three "
                        + "of a fresh wound");
    }

    // --- The food clock ---------------------------------------------------------

    @Test
    @DisplayName("food returns on its own clock, and the clock rests at a full bar")
    void foodRegeneratesOnItsOwnClock() {
        SurvivalTuning tuning = new SurvivalTuning(1_000_000, 1, 0, 5);

        SurvivalRules.Plan before = SurvivalRules.step(tuning, SurvivalState.NEW, 10, false, true);
        assertEquals(0, before.foodDelta(), "four ticks in, the fifth has not arrived");

        SurvivalRules.Plan on = run(tuning, 5, 10, false);
        assertEquals(1, on.foodDelta(), "the fifth tick is the one that returns a point");

        SurvivalRules.Plan over = run(tuning, 5, SurvivalTuning.MAX_FOOD, false);
        assertEquals(0, over.foodDelta(), "a full bar has nothing to gain");

        SurvivalRules.Plan under = run(tuning, 5, SurvivalTuning.MAX_FOOD - 1, false);
        assertEquals(1, under.foodDelta(), "and a bar with one missing gains exactly one, not two");
    }

    /**
     * The reason the full-bar branch resets the clock instead of letting it count.
     *
     * <p>If it counted, a player who had been full for a hundred ticks would regain a point
     * instantly the moment they spent one -- which turns "food comes back over time" into "food
     * comes back immediately, once you have ever been full", and quietly makes the floor
     * unreachable as a place you stay.</p>
     */
    @Test
    @DisplayName("a bar that has been full waits a whole interval before it starts regenerating")
    void aFullBarStartsTheClockFresh() {
        SurvivalTuning tuning = new SurvivalTuning(1_000_000, 1, 0, 5);
        SurvivalState state = SurvivalState.NEW;

        for (int i = 0; i < 100; i++) {
            state = SurvivalRules.step(tuning, state, SurvivalTuning.MAX_FOOD, false, true).state();
        }
        assertSame(SurvivalState.NEW, state, "a full bar advances nothing, so the state never moves");

        int food = SurvivalTuning.MAX_FOOD - 1;
        int ticks = 0;
        while (food == SurvivalTuning.MAX_FOOD - 1 && ticks < 20) {
            SurvivalRules.Plan plan = SurvivalRules.step(tuning, state, food, false, true);
            state = plan.state();
            food += plan.foodDelta();
            ticks++;
        }
        assertEquals(5, ticks,
                "**the first point back has to take the full interval.** Inheriting a "
                        + "nearly-expired clock from the time the bar was full would make "
                        + "regeneration instant, which is the failure mode DESIGN warns about "
                        + "from the other side: food that stops mattering");
    }

    @Test
    @DisplayName("when neither clock moves, the state is the same object")
    void anUntouchedClockReturnsTheSameState() {
        SurvivalRules.Plan plan = SurvivalRules.step(
                new SurvivalTuning(40, 1, 6, 80), SurvivalState.NEW,
                SurvivalTuning.MAX_FOOD, false, true);

        assertSame(SurvivalState.NEW, plan.state(),
                "a new record every tick would make the caller write the attachment for players "
                        + "who are standing at full health doing nothing");
    }

    // --- Both at once -----------------------------------------------------------

    @Test
    @DisplayName("a heal and a food point landing together produce one combined delta")
    void twoClocksCanFireTogether() {
        // Interval of four on both, so they meet: the heal spends one and the clock returns one.
        SurvivalTuning tuning = new SurvivalTuning(4, 1, 0, 4);
        SurvivalRules.Plan plan = run(tuning, 4, 19, true);

        assertEquals(1, plan.healAmount(), "the fourth tick heals");
        assertEquals(0, plan.foodDelta(),
                "**one write, net zero.** The caller applies a single delta; two separate writes "
                        + "would order the spend and the gain and the player would see a bar "
                        + "flicker down and back up");
    }

    // --- Starvation -------------------------------------------------------------

    @Test
    @DisplayName("starvation is asked only at zero food")
    void starvationOnlyAtZero() {
        SurvivalTuning tuning = new SurvivalTuning(1_000_000, 1, 0, 5);

        assertFalse(run(tuning, 5, 1, false).starve(), "one food point is not starving");
        assertFalse(run(tuning, 50, 2, false).starve(), "and neither is two");
        assertTrue(run(tuning, 5, 0, false).starve(), "zero food is");
        assertEquals(0, run(tuning, 5, 0, false).foodDelta(),
                "and the clock does not also regenerate on the same tick");
        assertEquals(1, run(tuning, 5, 1, false).foodDelta(),
                "because at one point it regenerates instead -- the two are mutually exclusive");
    }

    @Test
    @DisplayName("vanilla's starvation condition, condition by condition")
    void vanillaStarvationCondition() {
        assertTrue(SurvivalRules.starveFires(11.0F, false, false),
                "above ten hearts it lands even on easy");
        assertFalse(SurvivalRules.starveFires(10.0F, false, false),
                "exactly ten is not above ten");
        assertFalse(SurvivalRules.starveFires(2.0F, false, false),
                "on easy it stops there");
        assertTrue(SurvivalRules.starveFires(2.0F, false, true),
                "on normal it keeps going down to one");
        assertFalse(SurvivalRules.starveFires(1.0F, false, true),
                "exactly one is not above one, so normal stops at half a heart");
        assertTrue(SurvivalRules.starveFires(0.5F, true, false),
                "on hard it always lands, health aside");
    }

    // --- The tuning itself ------------------------------------------------------

    @Test
    @DisplayName("a tuning that could never fire is clamped instead of accepted")
    void theTuningClamps() {
        SurvivalTuning broken = new SurvivalTuning(0, 0, -5, -1);

        assertTrue(broken.healInterval() >= 1,
                "an interval of zero would heal every tick, which is not a balance problem, it is "
                        + "a player who cannot die");
        assertTrue(broken.healFoodCost() >= 1,
                "a price of zero removes the only thing that makes food matter");
        assertTrue(broken.minFoodToHeal() >= 0, "and a floor below zero is not a floor");
        assertTrue(broken.foodCycleInterval() >= 1, "the same clock argument as the heal interval");

        SurvivalTuning tooBig = new SurvivalTuning(40, 1, 99, 80);
        assertEquals(SurvivalTuning.MAX_FOOD, tooBig.minFoodToHeal(),
                "and a floor above the bar is the same as no healing at all");
    }

    @Test
    @DisplayName("the defaults are the numbers the game actually runs")
    void theDefaultsAreTheProposals() {
        assertEquals(40, DEFAULTS.healInterval(),
                "one point every two seconds -- slower healing to leave room for the fight");
        assertEquals(1, DEFAULTS.healFoodCost(), "one food per point healed");
        assertEquals(6, DEFAULTS.minFoodToHeal(), "healing down to six food, against vanilla's 18");
        assertEquals(80, DEFAULTS.foodCycleInterval(),
                "one food point every four seconds -- faster food regen to keep the cycle moving");
    }

    private static int countHeals(SurvivalTuning tuning, int ticks, int food) {
        int heals = 0;
        SurvivalState state = SurvivalState.NEW;
        for (int i = 0; i < ticks; i++) {
            SurvivalRules.Plan plan = SurvivalRules.step(tuning, state, food, true, true);
            state = plan.state();
            food += plan.foodDelta();
            heals += plan.healAmount();
        }
        return heals;
    }
}
