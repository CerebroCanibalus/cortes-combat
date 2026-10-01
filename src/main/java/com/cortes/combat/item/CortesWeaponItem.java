package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.registry.CortesTier;
import com.cortes.combat.registry.ModComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * One of the mod's own weapons: a family and a material, and nothing else decided anywhere else.
 *
 * <p>The item carries no gameplay logic. It names its family and its material and everything a
 * player experiences is derived from those two:</p>
 *
 * <ul>
 *   <li>{@link #WEAPON_PROFILE}, set on the properties, is what tells {@code WeaponStats} which
 *       reach, charge, knockback and special to answer with. Without it the item derives to
 *       {@code WeaponProfile.NONE} off the vanilla sword tag, and a weapon that cannot charge and
 *       reaches three blocks is a worse sword.</li>
 *   <li>{@code ATTACK_DAMAGE} and {@code ATTACK_SPEED} are vanilla's own attributes, so potions,
 *       other mods and the tooltip all read the truth rather than a parallel number.</li>
 * </ul>
 *
 * <p>Reach, cone, charge and knockback are deliberately <b>not</b> also set as components. They
 * live in {@code WeaponStats}, keyed by the profile, so retuning a weapon moves all six of its
 * materials at once. Writing them here too would mean two tables to keep in step, and the one
 * that gets forgotten is the one the player feels.</p>
 *
 * <p>Extends {@link SwordItem} for the parts that are genuinely about being a hand-held blade
 * rather than about this mod: the tool component, breaking cobwebs, and the anvil repair path.
 * What it does <b>not</b> inherit is the attack itself, because {@code Player#attack} does not run
 * in this mod -- {@code CombatEngine} replaces it.</p>
 */
public final class CortesWeaponItem extends SwordItem {

    private final WeaponFamily family;
    private final WeaponMaterial material;

    private CortesWeaponItem(WeaponFamily family, WeaponMaterial material,
                             Item.Properties properties) {
        super(CortesTier.of(material), properties);
        this.family = family;
        this.material = material;
    }

    /**
     * The properties for one exact weapon, shared by every item that resolves to the same pair.
     *
     * <p>All the interesting work is delegated: durability and the repair ingredient come from
     * the material's tier, the two attributes come from vanilla's own {@code createAttributes},
     * and the profile component is ours.</p>
     */
    public static Item.Properties propertiesFor(WeaponFamily family, WeaponMaterial material) {
        return new Item.Properties()
                .durability(CortesTier.of(material).getUses())
                .attributes(attributesFor(family, material))
                .component(ModComponents.WEAPON_PROFILE.get(), family.profile())
                .component(ModComponents.GUARD_PROFILE.get(), family.guard());
    }

    private static ItemAttributeModifiers attributesFor(WeaponFamily family, WeaponMaterial material) {
        // Vanilla's own call is createAttributes(tier, 3, -2.4F) and it produces a 1.6 attack
        // speed, because the player's base ATTACK_SPEED is 4.0 and the third argument is an
        // ADD_VALUE offset from it, not a total. 4.0 - 2.4 = 1.6.
        //
        // WeaponFamily stores the number the design cares about, which is that total: a ropera is
        // a 2.0 speed weapon. Passing it straight through added it to the base instead of
        // subtracting from it, so every one of the thirty came out at base + speed -- a ropera at
        // 6.0, which is three ticks between swings, and a dagger at 6.6, which is three. Every new
        // weapon was roughly twice as fast as written and the whole catalogue's tempo was wrong.
        //
        // A test that read family.attackSpeed() and asserted the interval it implied would have
        // passed the entire time, because the number was right and the item was not.
        return SwordItem.createAttributes(CortesTier.of(material),
                family.attackDamage(), family.attackSpeedOffset());
    }

    /** The one and only way an item of this kind is built. */
    public static CortesWeaponItem of(WeaponFamily family, WeaponMaterial material) {
        return new CortesWeaponItem(family, material, propertiesFor(family, material));
    }

    public WeaponFamily family() {
        return family;
    }

    public WeaponMaterial material() {
        return material;
    }

    /** The profile every other system asks about, which this item fixes at creation. */
    public WeaponProfile profile() {
        return family.profile();
    }

    /**
     * Attack damage for this exact material, as the tooltip will show it.
     *
     * <p>Vanilla's own formula: the family's number plus the material's bonus. A wooden dagger is
     * 2 and a netherite ropera is 8, out of the same two inputs, which is the reason materials
     * are data rather than thirty hand-written items.</p>
     */
    public float totalAttackDamage() {
        return family.attackDamage() + CortesTier.of(material).getAttackDamageBonus();
    }
}
