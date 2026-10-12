package com.cortes.combat.survival;

/**
 * The two clocks survival runs on, and nothing else.
 *
 * <p>Two, because there are two independent cycles: healing runs on its own interval and the food
 * clock runs on its own. Vanilla got away with one field ({@code FoodData.tickTimer}) because its
 * three branches were mutually exclusive -- regenerating, starving, or neither. Ours are not: a
 * player can be mid-heal and mid-food-cycle in the same tick.</p>
 *
 * <h2>Why this is an attachment and not a field on the mixin</h2>
 *
 * <p>Because {@code ModAttachments} says so, and it is right: a mutable field on a mixin does not
 * serialise, does not clear on death and is not copied on respawn, so its lifecycle is whatever
 * happens to happen. {@code SurvivalState} is a plain record with no codec and no
 * {@code copyOnDeath}, both deliberate: these are countdowns of at most four seconds, and a
 * countdown that survives a save would be progress the player did not earn -- the same argument
 * {@code ContragolpeWindow} and {@code SecondWindWindow} already make. Dying or relogging restarts
 * both clocks from zero, which costs at most one tick of latency nobody can feel.</p>
 *
 * <p>It is pure: no Minecraft type, so the rules that advance it are testable on a bare JVM.</p>
 *
 * @param healTicks      how far into {@link SurvivalTuning#healInterval()} the healing clock is
 * @param foodCycleTicks how far into {@link SurvivalTuning#foodCycleInterval()} the food clock is
 */
public record SurvivalState(int healTicks, int foodCycleTicks) {

    /** Every player starts with nothing running. */
    public static final SurvivalState NEW = new SurvivalState(0, 0);
}
