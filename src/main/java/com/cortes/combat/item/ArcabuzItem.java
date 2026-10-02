package com.cortes.combat.item;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.registry.ArcabuzTier;
import com.cortes.combat.registry.ModComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;

/**
 * The arcabuz: a cane gun, registered by hand because it is not a (family, material) pair.
 *
 * <p>{@link CortesWeaponItem} builds thirty items out of two inputs, and there is no honest pair
 * to give a firearm. Putting it in a family would have invented six identical tiers and a
 * {@code WeaponShape} grid nobody drew, so this is a separate item that reads its numbers out of
 * {@link ArcabuzStats} and holds none of its own.</p>
 *
 * <h2>Why it is still a {@code SwordItem}</h2>
 *
 * <p>For the parts that are genuinely about being a hand-held weapon rather than about this mod:
 * the {@code TOOL} component, breaking cobwebs, and the anvil repair path through the tier. It
 * inherits the attack <em>attributes</em>, which is the point -- potions, other mods and the
 * tooltip all read the real ones.</p>
 *
 * <p>What it does not inherit is the attack. {@code Player#attack} does not run in this mod;
 * {@code CombatEngine} replaces it, and the arcabuz's right button goes through
 * {@code SpecialHandler} instead.</p>
 *
 * <h2>What is deliberately not set here</h2>
 *
 * <p>Reach, cone, charge and knockback are <b>not</b> components on this item, exactly as
 * {@code CortesWeaponItem#propertiesFor} argues: they live in {@code WeaponTables} keyed by
 * {@link WeaponProfile#ARCABUZ}, so there is one table and retuning the gun moves every number at
 * once. Writing them here too would be two tables to keep in step, and the one that gets
 * forgotten is the one the player feels.</p>
 *
 * <h2>It cannot guard</h2>
 *
 * <p>{@link GuardProfile#NONE} on the properties, the same answer all five new families give, and
 * for the same underlying reason: its right button is the shot. Without this component
 * {@code GuardStats} falls back to {@code instanceof SwordItem} and hands the arcabuz the sword's
 * one point of guard, and then the right button would be a block instead of a gun.</p>
 */
public final class ArcabuzItem extends SwordItem {

    private ArcabuzItem(Item.Properties properties) {
        super(ArcabuzTier.INSTANCE, properties);
    }

    /**
     * The properties, and the whole of the item.
     *
     * <p>Two components and the attributes. Every number is read from {@link ArcabuzStats}, so
     * this class is an adapter and nothing here is a second copy of anything.</p>
     */
    public static Item.Properties properties() {
        return new Item.Properties()
                .durability(ArcabuzStats.USES)
                .attributes(SwordItem.createAttributes(ArcabuzTier.INSTANCE,
                        ArcabuzStats.DAMAGE, ArcabuzStats.SPEED_OFFSET))
                .component(ModComponents.WEAPON_PROFILE.get(), WeaponProfile.ARCABUZ)
                .component(ModComponents.GUARD_PROFILE.get(), GuardProfile.NONE);
    }

    /** The one and only way an item of this kind is built. */
    public static ArcabuzItem of(Item.Properties ignored) {
        // The registrar hands over the properties it built for this name and every one of ours
        // is a decision made here rather than in the registration. That is what
        // CortesWeaponItem#of does with the same argument.
        return new ArcabuzItem(properties());
    }

    /** The profile every other system asks about, which this item fixes at creation. */
    public WeaponProfile profile() {
        return WeaponProfile.ARCABUZ;
    }
}
