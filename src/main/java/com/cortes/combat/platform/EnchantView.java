package com.cortes.combat.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The three things this mod asks vanilla's enchantment system, named once, here.
 *
 * <h2>Why this class exists</h2>
 *
 * <p><b>Because these three statics do not exist one version down.</b> {@code EnchantmentHelper.modifyDamage}
 * and {@code modifyKnockback} are the 1.20.5 data-driven enchantment pipeline; in 1.20.1 the same
 * questions are answered by iterating {@code getEnchantments} by hand. The engine asked them inline,
 * in three places, so a port would have rewritten the damage calculation to get at the storage --
 * which is exactly the mistake {@code CombatState} already fixed for attachments.</p>
 *
 * <p>Asked from here the question is <em>what</em> and not <em>how</em>:
 * {@code EnchantView.damageBonus(level, weapon, target, source, base)}. The answer is
 * {@code EnchantmentHelper} in 1.21.1 and a hand-rolled loop anywhere else, and the difference lands
 * in this file.</p>
 *
 * <h2>Why this facade takes Minecraft types when {@code CombatState} refuses to</h2>
 *
 * <p>{@code CombatState} hands back the mod's own records because the state <em>is</em> the mod's own
 * data. These three answers are vanilla's data about vanilla's enchantments on a vanilla item: there
 * is no pure record to convert to without copying the whole enchantment table. So this class is an
 * adapter and not a boundary -- it lives in {@code platform}, not in a pure package, and it is
 * supposed to import Minecraft. What it buys is that the <em>call sites</em> never spell the helper,
 * so the port rewrites one file and not the engine. (⌒‿⌒)</p>
 *
 * <p><b>Delegation only, no arithmetic.</b> The {@code - base} and the {@code Math.max} stay in
 * {@code CombatEngine.enchantBonus}, where the design decision ("the bonus cannot be negative, and
 * ours stacks on top") is documented. A facade that computed would be a second damage formula
 * wearing a layering argument. (￣ω￣)</p>
 */
public final class EnchantView {

    private EnchantView() {
    }

    /**
     * Vanilla's damage for this weapon against this target, <em>including</em> the base passed in.
     * The caller subtracts the base; the subtraction documents the stacking rule and stays there.
     */
    public static float modifiedDamage(ServerLevel level, ItemStack weapon, Entity target,
            DamageSource source, float base) {
        return EnchantmentHelper.modifyDamage(level, weapon, target, source, base);
    }

    /** Vanilla's knockback strength for this weapon against this target, from the given base. */
    public static float modifiedKnockback(ServerLevel level, ItemStack weapon, Entity target,
            DamageSource source, float base) {
        return EnchantmentHelper.modifyKnockback(level, weapon, target, source, base);
    }

    /** Fire Aspect, Wind Burst and whatever comes next: once per body that took the hit. */
    public static void postAttackEffects(ServerLevel level, Entity target, DamageSource source) {
        EnchantmentHelper.doPostAttackEffects(level, target, source);
    }
}
