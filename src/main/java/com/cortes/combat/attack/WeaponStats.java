package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
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
    private static final Map<WeaponProfile, KnockbackProfile> DEFAULT_KNOCKBACK = new HashMap<>();

    static {
        // Knockback, per weapon, and the numbers are deliberately low.
        //
        // Horizontal is what a hit actually is: a shove. Vertical is a separate, much smaller
        // number, because scaling the two together is what made every hit feel like a trebuchet
        // and popped mobs off the ground for no reason the player earned.
        //
        // A sword barely lifts at all, which is the honest reading of a sword. A warhammer lifts
        // most of what it pushes, because that is what a warhammer is for. A block pushes almost
        // nothing, because swinging a dirt block is not an attack.
        DEFAULT_KNOCKBACK.put(WeaponProfile.NONE, new KnockbackProfile(0.12F, 0.02F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.UNARMED, new KnockbackProfile(0.10F, 0.02F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.SWORD, new KnockbackProfile(0.28F, 0.05F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.AXE, new KnockbackProfile(0.34F, 0.08F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.PICKAXE, new KnockbackProfile(0.22F, 0.05F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.SHOVEL, new KnockbackProfile(0.22F, 0.05F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.MACE, new KnockbackProfile(0.30F, 0.10F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.TRIDENT, new KnockbackProfile(0.30F, 0.12F));
        // The rapier is the narrow one: it moves you out of the way, it does not move them.
        DEFAULT_KNOCKBACK.put(WeaponProfile.RAPIER, new KnockbackProfile(0.16F, 0.03F));
        DEFAULT_KNOCKBACK.put(WeaponProfile.DAGGER, new KnockbackProfile(0.14F, 0.03F));
        // The halberd is the widest and the heaviest at the tip.
        DEFAULT_KNOCKBACK.put(WeaponProfile.HALBERD, new KnockbackProfile(0.45F, 0.14F));
        // The warhammer's whole character is that it lifts.
        DEFAULT_KNOCKBACK.put(WeaponProfile.WARHAMMER, new KnockbackProfile(0.55F, 0.30F));
        // The sabre is fast and light: a lot of push for very little lift.
        DEFAULT_KNOCKBACK.put(WeaponProfile.SABRE, new KnockbackProfile(0.38F, 0.06F));

        // Not a weapon: a block, a stick, anything held that is in no tool tag. It swings, and
        // it reaches about as far as a punch, but it has no charge and no viewmodel. The reach is
        // vanilla's 3.0 rather than bare hands' 1.9 because swinging a block is a heavier gesture
        // than swinging a fist, and the difference matters for whether you can hit something
        // while holding a wall.
        DEFAULT_REACH.put(WeaponProfile.NONE, new ReachProfile(3.0F, 25.0F, 20.0F));
        DEFAULT_CHARGE.put(WeaponProfile.NONE, ChargeProfile.NONE);
        DEFAULT_SPECIAL.put(WeaponProfile.NONE, SpecialType.NONE);
        // Bare hands are the shortest reach in the game, below even the dagger's 2.4. A punch
        // that reaches as far as a sword is not a punch, it is a worse sword, and the player
        // would carry a weapon for nothing. Short, fast and nearly harmless is the whole
        // character: the speed is what makes it a real option when you have nothing else, and
        // it is the only thing that makes it one.
        DEFAULT_REACH.put(WeaponProfile.UNARMED, new ReachProfile(1.9F, 25.0F, 20.0F));
        // 3.0 -> 3.6. The sword was 3.2, a fifth of a block over vanilla, which the player read
        // as "the same reach, shorter animation": the whole point of a hard cooldown is that you
        // commit to a spacing, and committing to vanilla's spacing is not a change. At 3.6 a
        // sword outranges an axe (3.0), which inverts vanilla's relationship between the two and
        // is deliberate: the sword is the weapon you are expected to have, so it should be the
        // one that reaches far enough to matter, and the tools pay for it with their own reach.
        DEFAULT_REACH.put(WeaponProfile.SWORD, new ReachProfile(3.6F, 35.0F, 30.0F));
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
    private final KnockbackProfile knockback;

    private WeaponStats(WeaponProfile profile, ReachProfile reach, ChargeProfile charge,
                        SpecialType special, KnockbackProfile knockback) {
        this.profile = profile;
        this.reach = reach;
        this.charge = charge;
        this.special = special;
        this.knockback = knockback;
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

    /** How hard this weapon pushes, and how much it lifts. */
    public KnockbackProfile knockback() {
        return knockback;
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
                readOr(stack, ModComponents.SPECIAL, DEFAULT_SPECIAL.get(profile)),
                readOr(stack, ModComponents.KNOCKBACK_PROFILE, DEFAULT_KNOCKBACK.get(profile)));
    }

    private static WeaponStats bareHands() {
        return new WeaponStats(
                WeaponProfile.UNARMED,
                DEFAULT_REACH.get(WeaponProfile.UNARMED),
                DEFAULT_CHARGE.get(WeaponProfile.UNARMED),
                SpecialType.NONE,
                DEFAULT_KNOCKBACK.get(WeaponProfile.UNARMED));
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
    /**
     * Which vanilla tag an item sits in, or {@link WeaponProfile#NONE}.
     *
     * <p>Anything unrecognised is not a weapon. Falling back to the sword here was the bug behind
     * "every object I hit with can charge": a dirt block has no tag, derived to a sword, and
     * inherited a sword's charge profile. An unknown item is an unknown item, and the honest
     * answer for an unknown item is that it is not a weapon.</p>
     */
    private static WeaponProfile deriveProfile(ItemStack stack) {
        for (Map.Entry<TagKey<net.minecraft.world.item.Item>, WeaponProfile> entry : VANILLA_TAGS.entrySet()) {
            if (stack.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        return WeaponProfile.NONE;
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
     *
     * <p>The vanilla sword lands on 12 or 13 ticks, which is a hair over half a second and
     * reads as sluggish the moment a fight has more than one enemy in it. The per-profile
     * multiplier is how that gets tuned: a data row, not a branch, so the next weapon that
     * needs a different tempo is a number here instead of code.</p>
     */
    public static int attackInterval(LivingEntity holder) {
        return attackInterval(holder, WeaponStats.of(holder.getMainHandItem()).profile());
    }

    /** As above, for a profile whose tempo is already known. */
    public static int attackInterval(LivingEntity holder, WeaponProfile profile) {
        double base = holder.getAttributeValue(Attributes.ATTACK_SPEED);
        return AttackTiming.scaleFor(base, TEMPO.getOrDefault(profile, 1.0F));
    }

    /** Per-profile interval multiplier. 1.0 is vanilla's own tempo, unmodified. */
    private static final Map<WeaponProfile, Float> TEMPO = new HashMap<>();

    static {
        // Vanilla's sword is 12-13 ticks. 0.85 puts it at 10, which is what a sword should feel
        // like when it is the weapon in your hand and the thing in front of you needs two hits.
        TEMPO.put(WeaponProfile.SWORD, 0.85F);
        // The dagger buys its speed back with a 2.4 block reach and a 45 degree cone.
        TEMPO.put(WeaponProfile.DAGGER, 0.7F);
        // Bare hands are the fastest thing in the game, and the only entry here above 1.0.
        //
        // The player's base attack speed is vanilla's 4.0, which is already five ticks, so
        // "fastest" cannot mean leaving this alone: at 1.0 the fists tie with a dagger and lose
        // to nothing. 1.25 puts them on four ticks, and the only thing that keeps that from
        // being strictly better than a weapon is that a punch does one damage.
        TEMPO.put(WeaponProfile.UNARMED, 1.25F);
    }

    /**
     * Whether this weapon can charge at all.
     *
     * <p>Asked by the input code before it opens a swing and by the viewmodel before it shows a
     * wind-up, so a block in the hand does not get a charge bar and does not look like it is
     * winding up. A single lookup rather than a check of the resolved profile's boolean, because
     * the caller has a stack and not a profile and building one to read one boolean would be
     * silly.</p>
     */
    public static boolean canCharge(ItemStack stack) {
        return DEFAULT_CHARGE.get(WeaponStats.of(stack).profile()).canCharge();
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
