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
 * runs before a config has been loaded. Two of them are no longer the numbers {@code DESIGN.md}
 * section 5 proposed: the General halved the healing interval and doubled the food clock on
 * 2026-10-12, before any playtest. They are still <b>proposals to be tuned</b>, not facts --
 * {@code foodCycleInterval} in particular is called out there as the most important number in the
 * project -- so the reason each one moved is written below next to the number it moved.</p>
 *
 * <h2>Why the constructor clamps instead of trusting</h2>
 *
 * <p>Because a clock that can never fire is a silent bug. {@code ModConfigSpec} already restricts
 * each value to a range, but this record is also constructed by tests and may one day be read from
 * somewhere that does not clamp -- and an interval of zero or a negative one would make healing
 * happen every tick, which is not a balance problem anybody would report, it is a player who cannot
 * die. Clamping costs four comparisons at construction and removes a class of failure outright.</p>
 *
 * @param healInterval     ticks between one point of natural healing. {@code DESIGN.md} proposed 40
 *                         (against vanilla's eighty); <b>halved to 20 on 2026-10-12</b>, one point
 *                         every second, so that recovering in a fight is something a player can feel
 *                         rather than something they wait through.
 * @param healFoodCost     food points spent per point healed. This is the price that closes the
 *                         cycle: healing is not free, food is what pays for it, and food comes back
 *                         on its own.
 * @param minFoodToHeal    the food floor. Below it natural healing does not start. Vanilla's floor
 *                         is eighteen; the design lowers it to six so that food is a resource you
 *                         spend rather than a bar you must keep near full.
 * @param foodCycleInterval ticks per turn of the food clock, which both regenerates a food point
 *                          and paces starvation. Vanilla starves on eighty and has no food
 *                          regeneration at all. <b>Doubled to 160 on 2026-10-12</b>, one point every
 *                          eight seconds.
 *
 * <h2>Why the two moved in opposite directions, and what that does to the cycle</h2>
 *
 * <p>Healing twice as fast while food returns half as fast is not two unrelated tweaks: it makes
 * <b>the food bar the thing that decides a fight</b> instead of the clock. A full bar of twenty now
 * buys twenty points of health in twenty seconds, and getting that bar back takes 320 ticks of
 * waiting -- so eating becomes the way to fight again now, and not eating becomes the way to fight
 * again later. Sustained healing, with no food at all, is one point every eight seconds.</p>
 */
public record SurvivalTuning(int healInterval, int healFoodCost, int minFoodToHeal,
                             int foodCycleInterval) {

    /** What runs before a config has been read. DESIGN section 5, with the two numbers the
     *  General tuned on 2026-10-12 already moved. Kept in step with {@code ServerConfig} by
     *  {@code SurvivalWiringTest.theConfigDefaultsAreTheOnlyTruth}. */
    public static final SurvivalTuning DEFAULTS = new SurvivalTuning(20, 1, 6, 160);

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
