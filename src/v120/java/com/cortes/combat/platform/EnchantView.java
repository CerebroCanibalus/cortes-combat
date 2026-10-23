package com.cortes.combat.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The 1.20.1 body of the vanilla-enchantment facade. Same three questions as the shared twin,
 * answered with the 1.20.5-pipeline's predecessors.
 *
 * <p><b>Each answer is the closest 1.20.1 equivalent, and the differences are written down
 * because they change numbers the playtest has to re-check.</b></p>
 *
 * <ul>
 * <li>Damage: {@code EnchantmentHelper.getDamageBonus(weapon, mobType)} is the vanilla helper
 * the 1.21.1 pipeline replaced. Same scope (the weapon's enchantments against this target),
 * same caller-side {@code - base} stacking in {@code CombatEngine}.</li>
 * <li>Knockback: {@code weapon.getEnchantmentLevel(Enchantments.KNOCKBACK)} reads the
 * <em>weapon's</em> level, the way the 1.21.1 helper reads the weapon's contribution -- not the
 * attacker's hands, which would drag the offhand shield into the number. Dimensional note: the
 * 1.21.1 answer is an attribute strength, this one is a level sum, so the engine's push scale
 * lands in a slightly different place. <b>Culata and bayonet numbers want a playtest eye on
 * 1.20.1.</b></li>
 * <li>Post-attack: {@code EnchantmentHelper.doPostDamageEffects} runs the attacker-side hooks
 * (Fire Aspect and company) exactly where the replaced flow used to inherit them from vanilla.
 * No Wind Burst here -- 1.20.1 has no such thing, and inventing one would be scope creep with
 * a version number.</li>
 * </ul>
 *
 * <p>Method names above are the 1.20.1 Mojang names; the leg's compiler is the guard that they
 * exist. If one doesn't, its error names the real one. (⌒‿⌒)</p>
 */
public final class EnchantView {

    private EnchantView() {
    }

    public static float modifiedDamage(ServerLevel level, ItemStack weapon, Entity target,
            DamageSource source, float base) {
        MobType mob = target instanceof LivingEntity living ? living.getMobType() : MobType.UNDEFINED;
        return EnchantmentHelper.getDamageBonus(weapon, mob);
    }

    public static float modifiedKnockback(ServerLevel level, ItemStack weapon, Entity target,
            DamageSource source, float base) {
        return weapon.getEnchantmentLevel(Enchantments.KNOCKBACK);
    }

    public static void postAttackEffects(ServerLevel level, Entity target, DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker && target instanceof LivingEntity victim) {
            EnchantmentHelper.doPostDamageEffects(attacker, victim);
        }
    }
}
