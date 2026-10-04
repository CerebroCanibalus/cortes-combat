package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponTables;
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

    private final WeaponProfile profile;
    private final ReachProfile reach;
    private final ChargeProfile charge;
    private final SpecialType special;
    private final KnockbackProfile knockback;

    private WeaponStats(WeaponProfile profile, ReachProfile reach, ChargeProfile charge,
                        SpecialType special, KnockbackProfile knockback, ReachProfile shotReach) {
        this.profile = profile;
        this.reach = reach;
        this.charge = charge;
        this.special = special;
        this.knockback = knockback;
        this.shotReach = shotReach;
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

    private final ReachProfile shotReach;

    /**
     * The cone a weapon's <b>right button</b> hits with.
     *
     * <p><b>Falls back to the melee cone, so it always answers something usable.</b> Thirty-one
     * weapons have no second cone, and a caller asking "where does this weapon's right button
     * reach" should not have to branch on whether the weapon happens to be the arcabuz. For those,
     * this is {@link #reach()}; for the arcabuz it is six and a half blocks at thirty two
     * degrees, and {@link #reach()} is a two point six block bayonet.</p>
     *
     * <p>Which is exactly why the two are separate and not one cone with a modifier: a caller that
     * wanted the left button could not tell them apart afterwards.</p>
     */
    public ReachProfile shotReach() {
        return shotReach == null || shotReach.isNone() ? reach : shotReach;
    }

    /** Whether this weapon has a cone of its own for its right button. */
    public boolean hasOwnShotCone() {
        return shotReach != null && !shotReach.isNone();
    }

    /**
     * The cone the player is shown, which is the shot's when there is one.
     *
     * <p><b>Written down as its own question because "the reach" is two questions.</b> The melee
     * cone is a fact about a hit; this is a fact about what the brackets around the crosshair are
     * measuring. On thirty-one weapons they are the same number, which is why nobody noticed they
     * were two numbers until one weapon had both.</p>
     *
     * <p>And the shot wins, because a player holding a loaded arcabuz is aiming the gun: the
     * crosshair has to describe where the blast will go, or the player is aiming with a marker
     * that lies about the thing they are about to do. The bayonet's short cone is still what the
     * left click uses, and it still decides what a click can hit -- the marker simply stops
     * describing it.</p>
     */
    public ReachProfile displayedReach() {
        return hasOwnShotCone() ? shotReach : reach;
    }

    public static WeaponStats of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return bareHands();
        }
        WeaponProfile profile = readProfile(stack);
        return new WeaponStats(
                profile,
                readOr(stack, ModComponents.REACH_PROFILE, WeaponTables.reach(profile)),
                readOr(stack, ModComponents.CHARGE_PROFILE, WeaponTables.charge(profile)),
                readOr(stack, ModComponents.SPECIAL, WeaponTables.special(profile)),
                readOr(stack, ModComponents.KNOCKBACK_PROFILE, WeaponTables.knockback(profile)),
                // The second cone, resolved through the same four layers as every other number:
                // a data component first, then the profile's row, then NONE for the thirty-one
                // weapons that have none. **It is the one stat whose component name is the same as
                // the field it feeds** -- reach_profile is the bayonet, shot_reach is the barrel --
                // and that is worth a test rather than a comment, because a swapped pair would
                // make the arcabuz's left click six and a half blocks long and nothing else would
                // notice.
                readOr(stack, ModComponents.SHOT_REACH, WeaponTables.shotReach(profile)));
    }

    private static WeaponStats bareHands() {
        return new WeaponStats(
                WeaponProfile.UNARMED,
                WeaponTables.reach(WeaponProfile.UNARMED),
                WeaponTables.charge(WeaponProfile.UNARMED),
                SpecialType.NONE,
                WeaponTables.knockback(WeaponProfile.UNARMED),
                ReachProfile.NONE);
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

    /**
     * The tempo multiplier for a profile, which is the third of the four numbers behind an attack
     * interval and the only one that used to be unreachable from outside.
     *
     * <p>Exposed because a tooltip that printed vanilla's untouched attribute would be printing
     * a number this table then changes: the sword's real tempo is 0.85 and its real interval is
     * not the one its item declares. A readout that skips this step is a readout of a weapon that
     * does not exist.</p>
     */
    public static float tempoFor(WeaponProfile profile) {
        return TEMPO.getOrDefault(profile, 1.0F);
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
        // The dagger is NOT in this table. It was, at 0.7, and the entry was dead: there was no
        // dagger item for it to apply to, so nothing had ever been measured against it. The
        // moment the item existed, the entry started doing the opposite of what it looked like:
        // a dagger declaring 2.6 attacks per second would have been multiplied down to 1.82 and
        // swung every eleven ticks instead of eight, which is slower than a sword.
        //
        // So the tempo for the five new families lives on the item's own ATTACK_SPEED, where the
        // number a reader finds in WeaponFamily is the number that runs. This table is for
        // retuning vanilla's weapons, whose attribute Mojang owns and we cannot change.
        // Bare hands are the fastest thing in the game, and the only entry here above 1.0.
        //
        // The player's base attack speed is vanilla's 4.0, which is already five ticks, so
        // "fastest" cannot mean leaving this alone: at 1.0 the fists tie with a dagger and lose
        // to nothing. 1.25 puts them on four ticks, and the only thing that keeps that from
        // being strictly better than a weapon is that a punch does one damage.
        TEMPO.put(WeaponProfile.UNARMED, 1.25F);

        // The axe, slowed to twenty four ticks from vanilla's twenty.
        //
        // Vanilla's axe is attack speed 1.0, which is twenty ticks -- already the slowest
        // thing in the catalogue by one tick against the warhammer's nineteen. That is not
        // "bastante lento", it is a rounding error, and a player cannot feel a difference
        // between nineteen and twenty.
        //
        // 0.85 puts it at twenty four, which is the slowest swing in the game by five ticks
        // and a fifth above the bare hand's four. The axe is the weapon whose base is
        // deliberately weak -- seven damage in a second and a fifth is less than half the
        // ropera's throughput -- and a base like that only reads as a choice if the rhythm
        // is also a cost. At twenty ticks it was a slightly worse sword; at twenty four it
        // is an axe.
        TEMPO.put(WeaponProfile.AXE, 0.85F);
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
    /**
     * Whether the held item can charge, resolved the same way every other stat is.
     *
     * <p>It read {@code WeaponTables.charge(profile)} instead, which skips the component layer and
     * the override file and asks the profile's default directly. So a mod that puts a real charge
     * profile on the ropera -- a component, through the compat path this project promises --
     * got an item the game had already decided cannot charge. The answer that survives a
     * component is the one that goes through {@link #of(ItemStack)} like every other question
     * about a weapon.
     */
    public static boolean canCharge(ItemStack stack) {
        return WeaponStats.of(stack).charge().canCharge();
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

    /**
     * The reach table, for the places that need to see all of it.
     *
     * <p>{@code /cc stats} prints one weapon; the tag provider and the tests walk all thirteen.
     * The map handed back is unmodifiable, because a caller that could write to it would be a
     * second, invisible copy of this table -- which is the bug this class was rewritten to
     * remove.</p>
     */
    public static Map<WeaponProfile, ReachProfile> defaultReach() {
        return WeaponTables.reachTable();
    }
}
