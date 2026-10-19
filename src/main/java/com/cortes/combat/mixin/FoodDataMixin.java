package com.cortes.combat.mixin;

import com.cortes.combat.config.ServerConfig;
import com.cortes.combat.survival.FoodDataSeam;
import com.cortes.combat.survival.SurvivalTicker;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's food tick, replaced; and the exhaustion tap, shut.
 *
 * <h2>Two injections, both at HEAD, both cancellable -- and that is the whole seam</h2>
 *
 * <p>Every number this changes lives in {@link SurvivalTicker} and the rules behind it live in
 * {@code SurvivalRules}. Nothing here is a conditional, a constant or an ordinal, which is the
 * point: {@code refs/combatify} patches this same method with {@code @ModifyExpressionValue}
 * against the literal {@code 18}, {@code 10} and {@code 80} inside it, each addressed by ordinal,
 * and every one of those stops matching the day Mojang moves a number -- silently, on a method
 * that still compiles. One injection that applies or fails loudly at bootstrap, and the behaviour
 * somewhere it can be read, is the portable shape.</p>
 *
 * <h2>Verified with {@code javap}, per the project's own rule</h2>
 *
 * <p>Run against {@code neoforge-21.1.250-merged.jar} before this file was written, because a
 * descriptor guessed from decompiled sources is a hard crash on the bootstrap thread that names
 * the mixin and not the guess:</p>
 *
 * <pre>
 *   public void tick(net.minecraft.world.entity.player.Player);
 *       descriptor: (Lnet/minecraft/world/entity/player/Player;)V
 *   public void addExhaustion(float);
 *       descriptor: (F)V
 * </pre>
 *
 * <p>Both are public on {@code FoodData} in 1.21.1, and {@code FoodData} carries no NeoForge patch
 * at all &mdash; verified by reading the whole class and finding no {@code // Neo:} comment, which
 * {@code DESIGN.md} section 5 also records.</p>
 *
 * <h2>Why shutting {@code addExhaustion} matters even though the tick never reads it</h2>
 *
 * <p>Cancelling the tick alone would already remove exhaustion's <em>effect</em>, because that is
 * the only place the number is consumed. But the number would go on being written by
 * {@code Player#causeFoodExhaustion} &mdash; sprinting, jumping, landing a hit, breaking a block
 * &mdash; and would climb to its cap of forty and sit there. Any other mod reading
 * {@code getExhaustionLevel()} would then be told this game still has a hunger system that is
 * quietly full. {@code addExhaustion} is the single funnel every one of those writers goes through
 * ({@code causeFoodExhaustion} calls it, and so did vanilla's own tick), so cancelling it here
 * shuts the system at its source instead of at its consequence.</p>
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin implements FoodDataSeam {

    /**
     * {@code SurvivalTicker} takes the {@code FoodData} as an argument instead of casting inside
     * it, so the cast that a mixin needs &mdash; {@code this} has no declared relation to the
     * target at compile time &mdash; happens exactly once, here, next to the thing it is for.
     */
    @Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true)
    private void cortescombat$survivalTick(Player player, CallbackInfo ci) {
        if (!ServerConfig.survivalRework()) {
            return;
        }
        ci.cancel();
        SurvivalTicker.tick(player, (FoodData) (Object) this);
    }
 
    @Inject(method = "addExhaustion(F)V", at = @At("HEAD"), cancellable = true)
    private void cortescombat$noExhaustion(float exhaustion, CallbackInfo ci) {
        if (!ServerConfig.survivalRework()) {
            return;
        }
        // Two jobs on one line each, and the order is not the point: `ci.cancel()` is what keeps
        // the value from being written at all, `setExhaustion` is what clears a value a save
        // written before this rework already carries. Removing either leaves the other's job
        // undone, and neither failure is visible -- one is a stale number, the other is a hunger
        // system quietly running.
        ((FoodData) (Object) this).setExhaustion(0.0F);
        ci.cancel();
    }
}
