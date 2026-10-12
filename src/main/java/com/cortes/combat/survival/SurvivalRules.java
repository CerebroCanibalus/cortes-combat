package com.cortes.combat.survival;

/**
 * Food and health, as rules over integers.
 *
 * <h2>The whole design in one paragraph</h2>
 *
 * <p>The player heals one point on a clock, and every point healed <em>costs food</em>. Food comes
 * back on its own clock. So healing is never free and never impossible: fast while you have food to
 * spend, slow while you wait for it to return, which is exactly the closed cycle
 * {@code DESIGN.md} section 5 asks for. Exhaustion is gone -- nothing costs food any more, the only
 * thing that spends it is healing itself.</p>
 *
 * <h2>Why nothing in this file imports Minecraft</h2>
 *
 * <p>Because the decision is the part that goes wrong. {@code step} takes integers and booleans and
 * returns what should happen; applying it -- {@code heal}, {@code hurt}, reading the gamerule,
 * asking the difficulty -- lives in {@link SurvivalTicker}, which is the one class that has to know
 * about Minecraft. That split is what lets every rule here be asserted from a bare JVM, and the
 * project's own history says the alternative: a rule written into a class that cannot be loaded by
 * a test is a rule nobody checks.</p>
 *
 * <h2>Order inside a tick, and why it is heal-then-food</h2>
 *
 * <p>Healing runs first because it is the action that spends, and it has to be paid out of the food
 * the player has <em>at this instant</em> -- running the food clock first would let a heal be
 * approved on food that had not arrived yet. The food clock runs second and sees the post-spend
 * number, so a heal that bottoms out at the floor is visible to the clock in the same tick.</p>
 */
public final class SurvivalRules {

    /** How much a starve costs. Vanilla's own number, kept. */
    public static final float STARVE_DAMAGE = 1.0F;

    private SurvivalRules() {
    }

    /**
     * What one tick should do.
     *
     * @param state      the two clocks after this tick; the same instance as the input when neither
     *                   moved, so a caller can skip writing it
     * @param healAmount points of health to give, {@code 0} when nothing heals this tick
     * @param foodDelta  net change in food level: the heal's spend and the clock's gain, already
     *                   combined, so the caller writes the level exactly once
     * @param starve     the food clock fired while there was no food at all, so the caller should
     *                   apply vanilla's starvation
     */
    public record Plan(SurvivalState state, int healAmount, int foodDelta, boolean starve) {
    }

    /**
     * Advance one tick.
     *
     * <p>Pure: same inputs, same output, no clock and no randomness. {@code foodLevel} is read from
     * the world and not clamped here -- a value above twenty is possible if something outside set
     * it, and the honest response to a full bar is to leave it alone rather than quietly rewrite
     * what someone else wrote.</p>
     *
     * @param tuning          the four balance numbers
     * @param state           the two clocks as they stand
     * @param foodLevel       the player's food right now, {@code 0..20} in normal play
     * @param hurt            the player is below maximum health
     * @param regenerationOn  the {@code naturalRegeneration} gamerule, which vanilla respects for
     *                        healing and which we respect for exactly the same reason
     */
    public static Plan step(SurvivalTuning tuning, SurvivalState state, int foodLevel,
                            boolean hurt, boolean regenerationOn) {
        int food = foodLevel;
        int healTicks = state.healTicks();

        // --- Healing -------------------------------------------------------------
        //
        // Three gates and the third one is the price. `minFoodToHeal` is the floor from the
        // design (vanilla's eighteen, lowered to six); `food >= healFoodCost` is what stops a heal
        // being approved when the player cannot pay for it, which only matters once the floor is
        // configured low enough to let food reach zero.
        int healAmount = 0;
        boolean mayHeal = regenerationOn && hurt
                && food >= tuning.minFoodToHeal()
                && food >= tuning.healFoodCost();
        if (mayHeal) {
            healTicks++;
            if (healTicks >= tuning.healInterval()) {
                healAmount = 1;
                food -= tuning.healFoodCost();
                healTicks = 0;
            }
        } else {
            // Reset, not hold. A clock that keeps its value through an interruption is a clock
            // that hands out a heal the moment the condition returns, on top of the one it was
            // already about to give -- vanilla's own `tickTimer` resets in its else branch for
            // the same reason.
            healTicks = 0;
        }

        // --- The food clock --------------------------------------------------------
        //
        // One clock, two outcomes, and which one it is follows from the number it is looking at.
        // It regenerates whenever there is room, and it starves only at exactly zero -- so the
        // cycle really is closed: healing spends food, the clock returns it, and the only way to
        // be stuck is to have drained it all the way down, which the floor above prevents by
        // default.
        int foodCycleTicks = state.foodCycleTicks();
        boolean starve = false;
        if (food >= SurvivalTuning.MAX_FOOD) {
            // Full: nothing owed and nothing to recover, so the clock rests instead of counting.
            // That matters later -- a bar that drops from full should wait a whole interval, not
            // inherit a nearly-expired one.
            foodCycleTicks = 0;
        } else if (food > 0) {
            foodCycleTicks++;
            if (foodCycleTicks >= tuning.foodCycleInterval()) {
                food++;
                foodCycleTicks = 0;
            }
        } else {
            foodCycleTicks++;
            if (foodCycleTicks >= tuning.foodCycleInterval()) {
                starve = true;
                foodCycleTicks = 0;
            }
        }

        SurvivalState next = (healTicks == state.healTicks()
                && foodCycleTicks == state.foodCycleTicks())
                ? state
                : new SurvivalState(healTicks, foodCycleTicks);
        return new Plan(next, healAmount, food - foodLevel, starve);
    }

    /**
     * Whether a starving tick actually lands, and this is vanilla's condition, copied.
     *
     * <p>Read out of {@code FoodData#tick} in the 1.21.1 sources rather than guessed: on easy the
     * player must still be above ten, on normal above one, on hard it always lands -- and on
     * peaceful it is <em>not</em> gated by difficulty at all, only by the health check, which is
     * why vanilla can get away with never reaching that branch there (its exhaustion cannot drop
     * food on peaceful, and ours can only reach zero through an extreme config).</p>
     *
     * <p>Kept verbatim instead of "improved", because it is vanilla behaviour we were not asked to
     * change: the brief is to remove exhaustion and close the healing cycle, not to rewrite what
     * happens at zero.</p>
     *
     * @param health current health, in points
     * @param hard   difficulty is hard
     * @param normal difficulty is normal
     */
    public static boolean starveFires(float health, boolean hard, boolean normal) {
        return health > 10.0F || hard || (health > 1.0F && normal);
    }
}
