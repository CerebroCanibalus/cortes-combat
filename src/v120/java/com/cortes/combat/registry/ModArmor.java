package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.CopperArmorItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;

/**
 * The 1.20.1 twin of the copper armour. Same four pieces, same numbers, no registry for the
 * material itself: 1.20.1 has no armor-material registry, so the material is a plain anonymous
 * class instead of a registered object.
 *
 * <p>Durability lands in exactly the same place as vanilla's: {@code getDurabilityForType}
 * multiplies the per-slot base by the copper multiplier, and {@code ArmorItem}'s constructor
 * feeds that number into the properties itself (verified in its bytecode). The bases are
 * vanilla's own -- helmet 11, chestplate 16, leggings 15, boots 13 -- so a copper helm lasts
 * 88 hits exactly the way the shared twin computes it. (⌒‿⌒)</p>
 *
 * <p>The texture resolves through {@code getArmorTexture} on a tiny per-leg item class, because
 * 1.20.1 builds the path out of {@code getName()} and would otherwise look in the vanilla
 * namespace. See {@code CopperArmorItem}.</p>
 */
public final class ModArmor {

    public static final DeferredRegister<Item> ARMOR_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CortesCombat.MODID);

    /** The copper armour material, unregistered: 1.20.1 has no registry to put it in. */
    public static final ArmorMaterial COPPER_MATERIAL = new ArmorMaterial() {
        private static final Map<ArmorItem.Type, Integer> BASE_DURABILITY = Map.of(
                ArmorItem.Type.HELMET, 11,
                ArmorItem.Type.CHESTPLATE, 16,
                ArmorItem.Type.LEGGINGS, 15,
                ArmorItem.Type.BOOTS, 13);

        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return BASE_DURABILITY.get(type) * CopperGear.ARMOR_DURABILITY_MULTIPLIER;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> CopperGear.HELMET_DEFENSE;
                case CHESTPLATE -> CopperGear.CHESTPLATE_DEFENSE;
                case LEGGINGS -> CopperGear.LEGGINGS_DEFENSE;
                case BOOTS -> CopperGear.BOOTS_DEFENSE;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return WeaponMaterial.COPPER.enchantmentValue();
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.COPPER_INGOT);
        }

        @Override
        public String getName() {
            return "cortescombat:copper";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    public static final RegistryObject<ArmorItem> COPPER_HELMET =
            piece(CopperGear.HELMET, ArmorItem.Type.HELMET);

    public static final RegistryObject<ArmorItem> COPPER_CHESTPLATE =
            piece(CopperGear.CHESTPLATE, ArmorItem.Type.CHESTPLATE);

    public static final RegistryObject<ArmorItem> COPPER_LEGGINGS =
            piece(CopperGear.LEGGINGS, ArmorItem.Type.LEGGINGS);

    public static final RegistryObject<ArmorItem> COPPER_BOOTS =
            piece(CopperGear.BOOTS, ArmorItem.Type.BOOTS);

    /** One armour piece. Durability comes from the material; the properties carry nothing. */
    private static RegistryObject<ArmorItem> piece(String name, ArmorItem.Type type) {
        return ARMOR_ITEMS.register(name,
                () -> new CopperArmorItem(COPPER_MATERIAL, type, new Item.Properties()));
    }

    /** The four pieces, in the order the player wears them. */
    public static List<RegistryObject<ArmorItem>> pieces() {
        return List.of(COPPER_HELMET, COPPER_CHESTPLATE, COPPER_LEGGINGS, COPPER_BOOTS);
    }

    /** Hands the item register to the mod bus. There is no material register on 1.20.1. */
    public static void register(IEventBus bus) {
        ARMOR_ITEMS.register(bus);
    }

    private ModArmor() {
    }
}
