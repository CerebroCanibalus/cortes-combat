package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Random;

/**
 * Gold's nine, and what each one actually does.
 *
 * <h2>Why this is a set of events and not a mixin</h2>
 *
 * <p>Every one of them hangs off something NeoForge already publishes: a kill, a hit, a broken
 * block, a tick. <b>Nine mixins for nine numbers would be nine things to maintain across versions
 * for behaviour the bus already exposes</b>, and the project's own rule is the simplest
 * intervention that answers the question.</p>
 *
 * <h2>The two events that are not the same event</h2>
 *
 * <p><b>{@link LivingIncomingDamageEvent} is before armour and {@link LivingHurtEvent} is after
 * it.</b> That single ordering decides which rules read which: life steal must heal from the number
 * the target actually lost, so it reads the late one, and phantom strike has to change the number
 * before armour sees it, so it reads the early one.</p>
 */
public final class GoldEnchantmentHandler {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, CortesCombat.MODID);

    /** The mark; one effect, one timer, and the multiplier is read from the effect. */
    public static final DeferredHolder<MobEffect, MobEffect> DEATH_MARKED =
            EFFECTS.register("death_marked",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0xFFC94A) {
                    });

    private static final int MARK_TICKS = 100;

    private GoldEnchantmentHandler() {
    }

    // ------------------------------------------------------------------ helper

    /** The level of one of ours on the stack, and what a {@code Holder<Enchantment>} costs. */
    private static int level(ItemStack stack, DeferredHolder<Enchantment, Enchantment> enchantment) {
        return stack.getEnchantmentLevel(enchantment);
    }

    /** A fresh roll source per table, so two kills in the same tick do not correlate. */
    private static Random roll(ServerLevel level) {
        return new Random(level.random.nextLong());
    }

    // ------------------------------------------------------------------ midas, weapon

    /**
     * A kill, and the drop table comes out of it.
     *
     * <p><b>Only a player's weapon, and only a living victim.</b> The design's gold is for the
     * fights a player takes: a skeleton that died in a fall is not a kill the player made, and the
     * logs say the same thing about a drowned that drowned.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)
                || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity killer = victim.getKillCredit() instanceof LivingEntity living ? living : null;
        if (!(killer instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        int enchantLevel = level(weapon, ModEnchantments.MIDAS_TOUCH);
        if (enchantLevel <= 0) {
            return;
        }
        Random random = roll(level);
        add(event.getDrops(), victim, enchantLevel, random);
    }

    /** The items themselves, so the numbers stay in {@link GoldEnchantRules}. */
    private static void add(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops, LivingEntity victim,
                            int level, Random random) {
        int nuggets = GoldEnchantRules.midasNuggets(level, random);
        if (nuggets > 0) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, nuggets));
        }
        int ingots = GoldEnchantRules.midasIngots(level, random);
        if (ingots > 0) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, ingots));
        }
        if (GoldEnchantRules.midasGoldenApple(level, random)) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE));
        }
        int carrots = GoldEnchantRules.midasGoldenCarrots(level, random);
        if (carrots > 0) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLDEN_CARROT, carrots));
        }
        if (GoldEnchantRules.midasEnchantedApple(level, random)) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE));
        }
    }

    /**
     * A new entity per stack, at the victim's feet, exactly where its own drops land.
     *
     * <p><b>Not a mutation of the existing drops.</b> {@code LivingDropsEvent} owns the list, and
     * pushing onto it is the contract; the entity is built by hand because the victim has already
     * dropped its own and this table is a second, separate pile.</p>
     */
    private static void addStack(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops, LivingEntity victim,
                                 ItemStack stack) {
        drops.add(new net.minecraft.world.entity.item.ItemEntity(victim.level(), victim.getX(),
                victim.getY(), victim.getZ(), stack));
    }

    // ------------------------------------------------------------------ the mark

    /** Life steal and the mark, both reading the number the target actually lost. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // **The mark multiplies for everyone, and that is the point of a mark.** One player with
        // echo of gold makes the whole party hit harder, which is the only reading of "marcado de
        // muerte" that is not a solo buff wearing a name.
        if (victim.hasEffect(DEATH_MARKED)) {
            event.setAmount(event.getAmount() * GoldEnchantRules.DEATH_MARK_MULTIPLIER);
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        int steal = level(weapon, ModEnchantments.LIFE_STEAL);
        if (steal <= 0) {
            return;
        }
        float heal = GoldEnchantRules.lifeStealHeal(event.getAmount(), steal);
        if (heal > 0.0F) {
            player.heal(heal);
        }
        int echo = level(weapon, ModEnchantments.GOLD_ECHO);
        if (echo > 0) {
            victim.addEffect(new MobEffectInstance(DEATH_MARKED, MARK_TICKS, echo - 1,
                    false, true, true));
        }
    }

    // ------------------------------------------------------------------ the weapon extras

    /**
     * Wind and feather weight, both attributes and therefore both recomputed, not stacked.
     *
     * <p><b>On the tick rather than on the change event.</b> An enchantment can arrive without the
     * item ever changing -- a grindstone, a command, another mod writing the stack -- and a
     * modifier that is applied once and forgotten becomes a permanent buff. Recomputing from the
     * equipment every tick is one pass over six slots, which is what this project already pays for
     * the guard.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel)) {
            return;
        }
        apply(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                CortesCombat.MODID + ":wind_walker", AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                GoldEnchantRules.windMovementBonus(level(player.getMainHandItem(),
                        ModEnchantments.WIND_WALKER)));
        // **An offset and not a ratio on this one, because vanilla's own is an offset.**
        // `SwordItem.createAttributes` puts `attackSpeed - 4.0` on the same attribute with ADD_VALUE,
        // so a speed bonus has to be added in the same unit or it stops meaning "ten percent faster".
        apply(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                CortesCombat.MODID + ":feather_weight", AttributeModifier.Operation.ADD_VALUE,
                GoldEnchantRules.featherAttackSpeedOffset(level(player.getMainHandItem(),
                        ModEnchantments.FEATHER_WEIGHT)));
    }

    /** Idempotent by construction: the modifier is replaced, never added a second time. */
    private static void apply(Player player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                             String name, AttributeModifier.Operation operation, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(net.minecraft.resources.ResourceLocation.parse(name));
        if (amount == 0.0) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(
                net.minecraft.resources.ResourceLocation.parse(name), amount, operation));
    }
}
