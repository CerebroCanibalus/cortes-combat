package com.cortes.combat.survival;

import com.cortes.combat.config.ServerConfig;
import com.cortes.combat.registry.ModAttachments;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;

/**
 * The one class where survival touches Minecraft.
 *
 * <h2>The porting contract, which is the whole reason this file exists separately</h2>
 *
 * <p>The user's requirement for this system was that it be <b>easily portable to other
 * versions</b>. That is achievable only if the version-specific surface is small and named, so it
 * is:</p>
 *
 * <table border="1">
 *   <tr><th>Layer</th><th>Files</th><th>Changes when porting</th></tr>
 *   <tr>
 *     <td>Rules</td>
 *     <td>{@link SurvivalRules}, {@link SurvivalTuning}, {@link SurvivalState}</td>
 *     <td><b>Never.</b> No Minecraft type appears in any of them; a port copies the package
 *     unchanged and the tests come along and still pass.</td>
 *   </tr>
 *   <tr>
 *     <td>Adapter</td>
 *     <td>This file</td>
 *     <td>Only if a method it calls is renamed. Every call it makes is public API on
 *     {@code Player} or {@code FoodData} ({@code heal}, {@code hurt}, {@code isHurt},
 *     {@code getFoodLevel}, {@code setFoodLevel}, {@code setExhaustion}, {@code getDifficulty},
 *     {@code getGameRules}, {@code damageSources}), all verified with {@code javap} against the
 *     runtime jar rather than read from the decompiled sources.</td>
 *   </tr>
 *   <tr>
 *     <td>Seams</td>
 *     <td>{@code FoodDataMixin}, {@code ItemStackDrinkDurationMixin}</td>
 *     <td>Two injection points, each with its descriptor written out in full. This is the only
 *     place a version change can break compilation or, worse, fail silently.</td>
 *   </tr>
 * </table>
 *
 * <h2>Why replace the tick instead of patching it</h2>
 *
 * <p>The obvious alternative -- and the one Combatify takes in {@code refs/} -- is a handful of
 * {@code @ModifyExpressionValue} injections on the <em>integer constants inside</em> vanilla's
 * method: {@code 18} for the healing floor, {@code 10} and {@code 80} for the timers, each
 * addressed by ordinal. It works, and it is the opposite of portable: Mojang moves a constant or
 * adds a branch and every one of those injections silently stops matching, on a method that still
 * compiles and still runs.</p>
 *
 * <p>Replacing the method body costs one injection point that either applies or fails loudly at
 * bootstrap, and everything the method <em>did</em> lives here where it can be read, named and
 * tested. {@code ARCHITECTURE.md} section 5 already lists this as a "total replacement" and ranks
 * it the riskiest change in the project; this is what that means in code.</p>
 *
 * <h2>What vanilla's tick did, and what happened to each part</h2>
 *
 * <ul>
 *   <li><b>Exhaustion</b> (four units drain a saturation point, then a food point) &mdash;
 *       <em>removed entirely</em>, per {@code DESIGN.md} section 5: running, mining and hitting
 *       cost nothing. The value is also zeroed every tick so a save written by an older version
 *       cannot leave a stale number behind for some other mod to read.</li>
 *   <li><b>Fast healing</b> (saturation, every ten ticks, food at twenty) &mdash; <em>removed</em>,
 *       because saturation is no longer a mechanic: the design's rule is one number and one rule.
 *       The field still exists in {@code FoodData} and we simply never read it.</li>
 *   <li><b>Slow healing</b> (food at eighteen, every eighty ticks) &mdash; <em>replaced</em> by
 *       {@link SurvivalRules#step}: floor and interval are config, and the heal costs food.</li>
 *   <li><b>Starvation</b> &mdash; <em>kept, verbatim</em>, including its health and difficulty
 *       conditions. It is vanilla behaviour nobody asked us to change, and it is the only thing
 *       left that punishes reaching zero.</li>
 *   <li><b>{@code lastFoodLevel}</b> &mdash; <em>not written</em>. It is private with only a
 *       getter, and nothing in 1.21.1 reads it (verified by searching every source in the game for
 *       {@code getLastFoodLevel}): the food-bar flash is driven by saturation, not by this. Writing
 *       it would need an accessor mixin for a field with no consumer, which
 *       {@code ARCHITECTURE.md} section 9 vetoes.</li>
 * </ul>
 */
public final class SurvivalTicker {

    private SurvivalTicker() {
    }

    /**
     * Replaces {@code FoodData#tick}. Server side only, which is where vanilla called it.
     *
     * @param player the player whose food and health are being advanced
     * @param data   that player's {@code FoodData}, handed over by the mixin
     */
    public static void tick(Player player, FoodData data) {
        // Vanilla already guards the call with `!level().isClientSide`, so this never fires. It is
        // here because the mixin sits one line above this method and a future caller should not
        // have to know that: survival decides nothing on the client, ever.
        if (player.level().isClientSide) {
            return;
        }

        // The exhaustion system is gone, so the number it maintained is meaningless. Zero it
        // rather than leave it frozen at whatever a pre-rework save wrote -- a field that reads
        // 4.0 forever is worse than one that reads 0, because it claims to be tracking something.
        data.setExhaustion(0.0F);

        SurvivalState state = player.getData(ModAttachments.SURVIVAL.get());
        int foodLevel = data.getFoodLevel();
        boolean regenerationOn = player.level().getGameRules()
                .getBoolean(GameRules.RULE_NATURAL_REGENERATION);

        SurvivalRules.Plan plan = SurvivalRules.step(ServerConfig.tuning(), state, foodLevel,
                player.isHurt(), regenerationOn);

        if (plan.state() != state) {
            player.setData(ModAttachments.SURVIVAL.get(), plan.state());
        }

        if (plan.healAmount() > 0) {
            // Through `heal()` and not around it, so this flows into `LivingHealEvent` exactly like
            // a potion or the regeneration effect -- which is what lets Cicatrizador's anti-healing
            // reduction apply to our own healing too. A second path would be a second truth.
            player.heal(plan.healAmount());
        }

        if (plan.foodDelta() != 0) {
            data.setFoodLevel(foodLevel + plan.foodDelta());
        }

        if (plan.starve()) {
            starve(player);
        }
    }

    /**
     * Vanilla's starvation, branch for branch.
     *
     * <p>The health and difficulty condition is {@link SurvivalRules#starveFires}, which is pure
     * and tested; this method only supplies the two booleans vanilla derived from the difficulty
     * and calls {@code hurt} with the vanilla damage source. On a bare {@code 0} food level this
     * and the food clock are mutually exclusive by construction, so a tick never regenerates and
     * starves at once.</p>
     */
    private static void starve(Player player) {
        Difficulty difficulty = player.level().getDifficulty();
        if (SurvivalRules.starveFires(player.getHealth(), difficulty == Difficulty.HARD,
                difficulty == Difficulty.NORMAL)) {
            player.hurt(player.damageSources().starve(), SurvivalRules.STARVE_DAMAGE);
        }
    }
}
