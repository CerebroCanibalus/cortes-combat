package com.cortes.combat.survival;

/**
 * The four numbers that decide survival, and nothing else.
 *
 * <p>No Minecraft type appears in this file, on purpose. Every rule the design makes about food and
 * health is expressed as an argument to {@link SurvivalRules}, so the whole system can be asked a
 * question from a bare JVM test -- which is the only way this project has ever caught a rule that
 * was written backwards.</p>
 *
 * <h2>Where the numbers come from</h2>
 *
 * <p>{@code ServerConfig} reads them out of the server config; {@link #DEFAULTS} is what the game
 * runs before a config has been loaded. They are the same numbers {@code DESIGN.md} section 5
 * proposes, and they are <b>proposals to be tuned in playtest</b>, not facts: {@code foodCycleInterval}
 * in particular is called out there as the most important number in the project.</p>
 *
 * <h2>Why the constructor clamps instead of trusting</h2>
 *
 * <p>Because a clock that can never fire is a silent bug. {@code ModConfigSpec} already restricts
 * each value to a range, but this record is also constructed by tests and may one day be read from
 * somewhere that does not clamp -- and an interval of zero or a negative one would make healing
 * happen every tick, which is not a balance problem anybody would report, it is a player who cannot
 * die. Clamping costs four comparisons at construction and removes a class of failure outright.</p>
 *
 * @param healInterval     ticks between one point of natural healing. {@code DESIGN.md} proposes 40
 *                         (one point every two seconds, against vanilla's eighty).
 * @param healFoodCost     food points spent per point healed. This is the price that closes the
 *                         cycle: healing is not free, food is what pays for it, and food comes back
 *                         on its own.
 * @param minFoodToHeal    the food floor. Below it natural healing does not start. Vanilla's floor
 *                         is eighteen; the design lowers it to six so that food is a resource you
 *                         spend rather than a bar you must keep near full.
 * @param foodCycleInterval ticks per turn of the food clock, which both regenerates a food point
 *                          and paces starvation. Vanilla starves on eighty and has no food
 *                          regeneration at all.
 */
public record SurvivalTuning(int healInterval, int healFoodCost, int minFoodToHeal,
                             int foodCycleInterval) {

    /** What runs before a config has been read. The {@code DESIGN.md} section 5 proposal. */
    public static final SurvivalTuning DEFAULTS = new SurvivalTuning(40, 1, 6, 80);

    /** Vanilla's own food ceiling, and {@code FoodData} clamps every write to it. */
    public static final int MAX_FOOD = 20;

    public SurvivalTuning {
        healInterval = Math.max(1, healInterval);
        // Never zero: a heal that costs nothing is the closed cycle with the price removed, and the
        // design's whole argument for food is that healing spends it.
        healFoodCost = Math.max(1, healFoodCost);
        minFoodToHeal = Math.max(0, Math.min(MAX_FOOD, minFoodToHeal));
        foodCycleInterval = Math.max(1, foodCycleInterval);
    }
}
