package com.cortes.combat.effect;

import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModArmor;
import com.cortes.combat.registry.ModEffects;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.registry.ModTools;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The 1.20.1 wiring of the two copper mechanics. Same hooks, older events.
 *
 * <ul>
 *   <li>Tick: 1.20.1 has no {@code Post} subclass -- every tick event carries a
 *       {@code phase}, so this gates on {@code END} and reads {@code event.player}.</li>
 *   <li>Hit: 1.20.1 has no {@code LivingIncomingDamageEvent}. Purification applies on
 *       {@code LivingHurtEvent}, which fires before armour in {@code actuallyHurt} --
 *       the same pre-armour moment, verified in the Forge patches.</li>
 *   <li>Effect checks take the bare {@code MobEffect} here
 *       ({@code incoming.getEffect().getCategory()}), and the copper check compares
 *       material singletons with {@code ==}.</li>
 * </ul>
 * (⌒‿⌒)
 */
public final class CopperEffects {

    /**
     * Nine percent per level, so the four pieces add to thirty-six.
     *
     * <p><b>It was five, and the playtest said it was too little.</b> See the shared twin
     * for the full note; the number is identical on both legs.</p>
     */
    public static final double REDUCTION_PER_LEVEL = 0.09D;

    /** The antiseptic is refreshed every tick, so it never visibly runs out while it is worn. */
    private static final int ANTISEPTIC_DURATION = 40;

    private CopperEffects() {
    }

    /** Keeps the wearer's antiseptic level equal to the number of copper pieces they have on. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        int pieces = copperPieces(player);
        MobEffectInstance current = player.getEffect(ModEffects.antiseptic());
        if (pieces == 0) {
            return;     // it expires on its own, which is what we want when the armour comes off
        }
        int level = pieces - 1;
        if (current != null && current.getAmplifier() == level && current.getDuration() > 10) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                ModEffects.antiseptic(), ANTISEPTIC_DURATION, level, false, true, true));
    }

    /**
     * Shortens a harmful effect that lands on an antiseptic wearer, <b>in place</b>.
     *
     * <p>See the shared twin for why: no public way to shorten an instance, so the event's
     * own instance gets its duration rewritten through the accessor. Fresh applications
     * only -- a re-applied debuff keeps its full length.</p>
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance incoming = event.getEffectInstance();
        if (entity.level().isClientSide() || incoming == null || incoming.isInfiniteDuration()) {
            return;
        }
        if (incoming.getEffect().getCategory() != MobEffectCategory.HARMFUL) {
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
                    && armour.getMaterial() == ModArmor.COPPER_MATERIAL) {
                count++;
            }
        }
        return count;
    }

    /** Leaves purification on whatever a copper weapon hits. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
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
     * <p>Ten items: the five catalogue weapons' copper versions and the five copper tools.
     * No set is built here: this runs on every incoming hit.</p>
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
