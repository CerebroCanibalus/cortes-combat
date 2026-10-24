package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.registry.CortesTier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * The 1.20.1 body of the catalogue weapon. Same fields, same factory methods, same profile.
 *
 * <p><b>The only thing that changes is where the two attributes live.</b> 1.21.1 puts them in
 * the properties ({@code createAttributes}) and the constructor takes {@code (tier, props)};
 * 1.20.1 has no per-item attribute map, so the constructor takes them
 * ({@code (tier, damage, speed, props)}) <em>and</em> the item overrides
 * {@code getDefaultAttributeModifiers} with the exact same two numbers. The super call gets
 * zeros because its values would be dead the moment the override exists -- and a damage number
 * that looks like it controls something while doing nothing is the exact bug this file's
 * history documents.</p>
 *
 * <p>The tempo math is unchanged: {@code family.attackSpeedOffset()} is already the offset from
 * the base 4.0, which is what both versions add. And the profile component the shared twin
 * writes becomes {@link WeaponData#tagWeapon} reading back the family on this leg. (⌒‿⌒)</p>
 */
public final class CortesWeaponItem extends SwordItem {

    private final WeaponFamily family;
    private final WeaponMaterial material;

    private CortesWeaponItem(WeaponFamily family, WeaponMaterial material,
                             Item.Properties properties) {
        super(CortesTier.of(material), 0, 0.0F, properties);
        this.family = family;
        this.material = material;
    }

    public static Item.Properties propertiesFor(WeaponFamily family, WeaponMaterial material) {
        return com.cortes.combat.platform.WeaponData.tagWeapon(new Item.Properties()
                .durability(CortesTier.of(material).getUses()), family);
    }

    /**
     * The two attributes with the project's exact numbers: family damage plus the material's
     * bonus, and the tempo offset straight from the family. Same arithmetic the shared twin
     * hands to {@code createAttributes}; here it is a map because 1.20.1 has nowhere else to
     * put it.
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getDefaultAttributeModifiers(slot);
        }
        float damage = family.attackDamage() + CortesTier.of(material).getAttackDamageBonus();
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_UUID,
                "Weapon modifier", damage, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_UUID,
                "Weapon modifier", (double) family.attackSpeedOffset(),
                AttributeModifier.Operation.ADDITION));
        return builder.build();
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

    /** Attack damage for the tooltip, same formula as the shared twin. */
    public float totalAttackDamage() {
        return family.attackDamage() + CortesTier.of(material).getAttackDamageBonus();
    }
}
