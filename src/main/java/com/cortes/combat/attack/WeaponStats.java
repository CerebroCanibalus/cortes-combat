package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.registry.ModComponents;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.CooldownState;
import com.cortes.combat.state.GuardState;
import com.cortes.combat.state.ParryState;
import com.cortes.combat.state.StreakState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads a weapon's behaviour off whatever the player is holding.
 *
 * <p>Four sources, in priority order, and the order is the contract:
 * <ol>
 *   <li>Our data components, when the item has them. That covers our own weapons and any
 *       mod that adopted our API.</li>
 *   <li>Anything the player wrote in the override config (step 12 of the plan; the call site
 *       is left for that step and currently falls through).</li>
 *   <li>Automatic derivation from the item's own attributes and vanilla tags, so a foreign
 *       weapon works without any code of ours. This is the compat layer.</li>
 *   <li>Defaults, which for an empty hand means bare hands.</li>
 * </ol>
 *
 * <p>No gameplay number is hardcoded in the combat code: it all arrives through here.</p>
 */
public final class WeaponStats {

    private static final Map<WeaponProfile, ReachProfile> DEFAULT_REACH = new HashMap<>();
    private static final Map<WeaponProfile, ChargeProfile> DEFAULT_CHARGE = new HashMap<>();
    private static final Map<WeaponProfile, SpecialType> DEFAULT_SPECIAL = new HashMap<>();

    static {
        DEFAULT_REACH.put(WeaponProfile.UNARMED, new ReachProfile(3.0F, 25.0F, 20.0F));
        DEFAULT_REACH.put(WeaponProfile.SWORD, new ReachProfile(3.2F, 35.0F, 30.0F));
        DEFAULT_REACH.put(WeaponProfile.AXE, new ReachProfile(3.0F, 45.0F, 35.0F));
        DEFAULT_REACH.put(WeaponProfile.PICKAXE, new ReachProfile(3.0F, 35.0F, 30.0F));
        DEFAULT_REACH.put(WeaponProfile.SHOVEL, new ReachProfile(3.0F, 35.0F, 30.0F));
        DEFAULT_REACH.put(WeaponProfile.MACE, new ReachProfile(3.4F, 30.0F, 40.0F));
        DEFAULT_REACH.put(WeaponProfile.TRIDENT, new ReachProfile(4.0F, 15.0F, 20.0F));
        // Rapier: tiny cone, buys it back with speed. Dagger: tiny range, wide cone.
        // Halberd: widest and longest. Sabre: the widest cone in the game, shortest range.
        // Warhammer: deliberately poor range, the escalation is the payoff.
        DEFAULT_REACH.put(WeaponProfile.RAPIER, new ReachProfile(3.4F, 12.0F, 15.0F));
        DEFAULT_REACH.put(WeaponProfile.DAGGER, new ReachProfile(2.4F, 45.0F, 30.0F));
        DEFAULT_REACH.put(WeaponProfile.HALBERD, new ReachProfile(4.2F, 55.0F, 35.0F));
        DEFAULT_REACH.put(WeaponProfile.WARHAMMER, new ReachProfile(2.6F, 40.0F, 40.0F));
        DEFAULT_REACH.put(WeaponProfile.SABRE, new ReachProfile(2.5F, 75.0F, 25.0F));

        DEFAULT_CHARGE.put(WeaponProfile.UNARMED, new ChargeProfile(true, 0.3F, 24));
        DEFAULT_CHARGE.put(WeaponProfile.SWORD, new ChargeProfile(true, 0.5F, 25));
        DEFAULT_CHARGE.put(WeaponProfile.AXE, new ChargeProfile(true, 0.6F, 40));
        DEFAULT_CHARGE.put(WeaponProfile.PICKAXE, new ChargeProfile(true, 0.4F, 33));
        DEFAULT_CHARGE.put(WeaponProfile.SHOVEL, new ChargeProfile(true, 0.4F, 33));
        DEFAULT_CHARGE.put(WeaponProfile.MACE, new ChargeProfile(true, 0.7F, 40));
        DEFAULT_CHARGE.put(WeaponProfile.TRIDENT, new ChargeProfile(true, 0.5F, 25));
        DEFAULT_CHARGE.put(WeaponProfile.RAPIER, ChargeProfile.NONE);
        DEFAULT_CHARGE.put(WeaponProfile.DAGGER, ChargeProfile.NONE);
        DEFAULT_CHARGE.put(WeaponProfile.HALBERD, new ChargeProfile(true, 0.6F, 36));
        DEFAULT_CHARGE.put(WeaponProfile.WARHAMMER, new ChargeProfile(true, 0.6F, 32));
        // The sabre's full charge doubles the damage, the only weapon that does.
        DEFAULT_CHARGE.put(WeaponProfile.SABRE, new ChargeProfile(true, 1.0F, 20));

        DEFAULT_SPECIAL.put(WeaponProfile.SWORD, SpecialType.NONE);
        DEFAULT_SPECIAL.put(WeaponProfile.AXE, SpecialType.SLAM);
        DEFAULT_SPECIAL.put(WeaponProfile.PICKAXE, SpecialType.LUNGE);
        DEFAULT_SPECIAL.put(WeaponProfile.SHOVEL, SpecialType.SWEEP);
        DEFAULT_SPECIAL.put(WeaponProfile.MACE, SpecialType.THROW);
        DEFAULT_SPECIAL.put(WeaponProfile.TRIDENT, SpecialType.THROW);
        DEFAULT_SPECIAL.put(WeaponProfile.HALBERD, SpecialType.MULTI_HIT);
        DEFAULT_SPECIAL.put(WeaponProfile.SABRE, SpecialType.PARRY);
        for (WeaponProfile profile : WeaponProfile.values()) {
            DEFAULT_SPECIAL.putIfAbsent(profile, SpecialType.NONE);
        }
    }

    private final WeaponProfile profile;
    private final ReachProfile reach;
    private final ChargeProfile charge;
    private final SpecialType special;

    private WeaponStats(WeaponProfile profile, ReachProfile reach, ChargeProfile charge, SpecialType special) {
        this.profile = profile;
        this.reach = reach;
        this.charge = charge;
        this.special = special;
    }

    public WeaponProfile profile() {
        return profile;
    }

    public ReachProfile reach() {
        return reach;
    }

    public ChargeProfile charge() {
        return charge;
    }

    public SpecialType special() {
        return special;
    }

    public static WeaponStats of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return bareHands();
        }
        WeaponProfile profile = readProfile(stack);
        return new WeaponStats(
                profile,
                readOr(stack, ModComponents.REACH_PROFILE, DEFAULT_REACH.get(profile)),
                readOr(stack, ModComponents.CHARGE_PROFILE, DEFAULT_CHARGE.get(profile)),
                readOr(stack, ModComponents.SPECIAL, DEFAULT_SPECIAL.get(profile)));
    }

    private static WeaponStats bareHands() {
        return new WeaponStats(
                WeaponProfile.UNARMED,
                DEFAULT_REACH.get(WeaponProfile.UNARMED),
                DEFAULT_CHARGE.get(WeaponProfile.UNARMED),
                SpecialType.NONE);
    }

    private static WeaponProfile readProfile(ItemStack stack) {
        WeaponProfile explicit = stack.get(ModComponents.WEAPON_PROFILE);
        if (explicit != null) {
            return explicit;
        }
        return deriveProfile(stack);
    }

    /**
     * The compat layer. A foreign weapon is classified purely by which vanilla tag it sits
     * in, so we never need to know a mod exists to make its weapons work.
     */
    private static WeaponProfile deriveProfile(ItemStack stack) {
        for (Map.Entry<TagKey<net.minecraft.world.item.Item>, WeaponProfile> entry : VANILLA_TAGS.entrySet()) {
            if (stack.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        return WeaponProfile.SWORD;
    }

    private static final Map<TagKey<net.minecraft.world.item.Item>, WeaponProfile> VANILLA_TAGS = new HashMap<>();

    static {
        VANILLA_TAGS.put(itemTag("swords"), WeaponProfile.SWORD);
        VANILLA_TAGS.put(itemTag("axes"), WeaponProfile.AXE);
        VANILLA_TAGS.put(itemTag("pickaxes"), WeaponProfile.PICKAXE);
        VANILLA_TAGS.put(itemTag("shovels"), WeaponProfile.SHOVEL);
        VANILLA_TAGS.put(itemTag("maces"), WeaponProfile.MACE);
        VANILLA_TAGS.put(itemTag("tridents"), WeaponProfile.TRIDENT);
    }

    private static TagKey<net.minecraft.world.item.Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(path));
    }

    private static <T> T readOr(ItemStack stack,
                                net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
                                        net.minecraft.core.component.DataComponentType<T>> holder,
                                T fallback) {
        T value = stack.get(holder.get());
        return value == null ? fallback : value;
    }

    /**
     * Ticks between attacks for whoever is holding this, straight from the attack speed
     * attribute. Reading it off the entity rather than the stack means equipment, potions and
     * other attribute sources are accounted for automatically.
     */
    public static int attackInterval(LivingEntity holder) {
        return AttackTiming.ticksFor(holder.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    public static CooldownState cooldownOf(Player player) {
        return player.getData(com.cortes.combat.registry.ModAttachments.COOLDOWN.get());
    }

    public static ChargeState chargeOf(Player player) {
        return player.getData(com.cortes.combat.registry.ModAttachments.CHARGE.get());
    }

    public static GuardState guardOf(Player player) {
        return player.getData(com.cortes.combat.registry.ModAttachments.GUARD_STAMINA.get());
    }

    public static StreakState streakOf(Player player) {
        return player.getData(com.cortes.combat.registry.ModAttachments.HAMMER_STREAK.get());
    }

    public static ParryState parryOf(Player player) {
        return player.getData(com.cortes.combat.registry.ModAttachments.PARRY.get());
    }

    public static Map<WeaponProfile, ReachProfile> defaultReach() {
        return Collections.unmodifiableMap(DEFAULT_REACH);
    }
}
