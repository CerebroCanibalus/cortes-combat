package com.cortes.combat.effect;

import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModArmor;
import com.cortes.combat.registry.ModEffects;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.registry.ModTools;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * The two copper mechanics, wired to the game.
 *
 * <p>Kept in one class because they are one material's identity and neither is big: the armour's
 * antiseptic and the weapon's purification. Everything that could be a number lives in
 * {@link PurificationEffect} or {@link CopperGear}, so this file is only the hooks.</p>
 *
 * <h2>The antiseptic, and why it takes two hooks</h2>
 *
 * <p><b>There is no public way to shorten a {@code MobEffectInstance}.</b> Verified in the runtime:
 * {@code mapDuration} computes a new value and throws it away -- its bytecode ends in
 * {@code ireturn} with no {@code putfield} -- and the field has no setter. So "this debuff lasts
 * twenty percent less" cannot be done after the fact.</p>
 *
 * <p>What can be done is to stop the effect landing and land a shorter one instead.
 * {@code MobEffectEvent.Applicable} fires before the instance is stored and can refuse it, and
 * {@code LivingEntity#forceAddEffect} stores an instance without asking again -- so there is no
 * recursion and no flag to keep. That is the pair below, and it is the whole reason this class has
 * two hooks rather than one.</p>
 */
public final class CopperEffects {

    /**
     * Nine percent per level, so the four pieces add to thirty-six.
     *
     * <p><b>It was five, and the playtest said it was too little.</b> Five percent a level is a
     * fifth off a ten second poison, which a player cannot feel; nine is a little over a third and
     * is the point where the armour's whole reason for existing is visible on the screen. The
     * design asked for a reduction, not for a specific number, and this is the number that makes
     * it read.</p>
     */
    public static final double REDUCTION_PER_LEVEL = 0.09D;

    /** The antiseptic is refreshed every tick, so it never visibly runs out while it is worn. */
    private static final int ANTISEPTIC_DURATION = 40;

    private CopperEffects() {
    }

    // ---------------------------------------------------------------- antiseptic

    /** Keeps the wearer's antiseptic level equal to the number of copper pieces they have on. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        int pieces = copperPieces(player);
        MobEffectInstance current = player.getEffect(ModEffects.antiseptic());
        if (pieces == 0) {
            return;     // it expires on its own, which is what we want when the armour comes off
        }
        int level = pieces - 1;
        // Only touch it when the level actually changed or it is about to lapse, so a steady suit
        // does not re-apply an identical instance sixty times a second.
        if (current != null && current.getAmplifier() == level && current.getDuration() > 10) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                ModEffects.antiseptic(), ANTISEPTIC_DURATION, level, false, true, true));
    }

    /**
     * Shortens a harmful effect that lands on an antiseptic wearer, <b>in place</b>.
     *
     * <p>The instance in this event is the one about to be stored, so writing its duration here is
     * the entire mechanic -- see {@link com.cortes.combat.mixin.MobEffectInstanceAccessor} for why
     * the obvious alternative (refuse it and re-add a shorter one) recursed until the server hung.
     * It never refuses anything: the effect lands exactly as vanilla intended, just shorter.</p>
     *
     * <p>Only on a fresh application. When the effect is already on the target vanilla merges the
     * two durations, and shortening that would let a one second poison cut a minute long one -- so
     * a re-applied debuff keeps its full length, which is the safe half.</p>
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance incoming = event.getEffectInstance();
        if (entity.level().isClientSide() || incoming == null || incoming.isInfiniteDuration()) {
            return;
        }
        // **Harmful only.** A beneficial effect is not a debuff and shortening it would be the
        // opposite of what the armour is for.
        if (incoming.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        MobEffectInstance antiseptic = entity.getEffect(ModEffects.antiseptic());
        if (antiseptic == null || entity.hasEffect(incoming.getEffect())) {
            return;
        }
        int level = antiseptic.getAmplifier() + 1;
        int shortened = (int) Math.max(1, Math.round(incoming.getDuration()
                * (1.0D - REDUCTION_PER_LEVEL * level)));
        ((com.cortes.combat.mixin.MobEffectInstanceAccessor) incoming)
                .cortescombat$setDuration(shortened);
    }

    private static int copperPieces(Player player) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armour
                    && armour.getMaterial().is(ModArmor.COPPER_MATERIAL)) {
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------- purification

    /** Leaves purification on whatever a copper weapon hits. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        if (!isCopperWeapon(attacker.getMainHandItem())) {
            return;
        }
        target.addEffect(new MobEffectInstance(
                ModEffects.purification(), PurificationEffect.DURATION_TICKS, 0, false, true, true),
                attacker);
    }

    /**
     * Whether the held item is copper, checked against the two registries rather than a tag.
     *
     * <p>Ten items: the five catalogue weapons' copper versions and the five copper tools. A tag
     * would have been tidier and wrong -- it would be a third list to keep beside the two that
     * already know. And <b>no set is built here</b>: this runs on every incoming hit, and a
     * {@code HashSet} allocated per hit is exactly the kind of thing that reads as server lag.</p>
     */
    private static boolean isCopperWeapon(ItemStack stack) {
        Item held = stack.getItem();
        for (WeaponFamily family : WeaponFamily.values()) {
            if (held == ModItems.weapon(family.itemName(WeaponMaterial.COPPER)).get()) {
                return true;
            }
        }
        for (var tool : ModTools.tools()) {
            if (held == tool.get()) {
                return true;
            }
        }
        return false;
    }
}
