package com.cortes.combat.registry;

import com.cortes.combat.platform.ResourceIds;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * The copper armour: one material and four pieces.
 *
 * <p>The mod had no armour at all before this, so everything here is new and none of it is a
 * variant of anything. The material is registered in code rather than through a datapack JSON
 * because {@code ArmorMaterials.bootstrap} does the same and a JSON-only material would be a
 * second way to say the same thing.</p>
 *
 * <h2>The three things 1.21.1 makes easy to get wrong</h2>
 *
 * <p><b>{@code ArmorMaterial} is a record, not the interface older versions had.</b> Its defence is
 * a {@code Map<ArmorItem.Type, Integer>} and it carries <em>no durability field at all</em>: the
 * uses come from {@code Item.Properties.durability(Type.getDurability(multiplier))}, which is how
 * vanilla registers its own iron. Reading the decompiled sources instead of the runtime is what
 * produced the first attempt at this file, and the runtime is the only one that is true.</p>
 *
 * <p><b>And the texture path is the old one.</b> {@code ArmorMaterial.Layer} resolves a layer named
 * {@code cortescombat:copper} to
 * {@code textures/models/armor/copper_layer_1.png} and {@code ..._layer_2.png} -- <em>not</em> the
 * {@code textures/entity/equipment/armor/} path 1.21.2 moved it to. The art sitting in the new
 * folder is the fifth time in this project a file has existed in a place the game never reads.</p>
 */
public final class ModArmor {

    public static final DeferredRegister<net.minecraft.world.item.Item> ARMOR_ITEMS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, CortesCombat.MODID);

    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, CortesCombat.MODID);

    /**
     * The copper armour material.
     *
     * <p>No toughness and no knockback resistance: iron and chainmail have neither, and copper is
     * not meant to be a metal that turns a hit aside. Its whole difference is the defence number
     * above iron and the antiseptic effect the four pieces grant together.</p>
     */
    public static final RegistryObject<ArmorMaterial> COPPER_MATERIAL =
            ARMOR_MATERIALS.register("copper", () -> new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.HELMET, CopperGear.HELMET_DEFENSE,
                            ArmorItem.Type.CHESTPLATE, CopperGear.CHESTPLATE_DEFENSE,
                            ArmorItem.Type.LEGGINGS, CopperGear.LEGGINGS_DEFENSE,
                            ArmorItem.Type.BOOTS, CopperGear.BOOTS_DEFENSE),
                    WeaponMaterial.COPPER.enchantmentValue(),
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(Items.COPPER_INGOT),
                    List.of(new ArmorMaterial.Layer(
                            ResourceIds.rl("copper"))),
                    0.0F,
                    0.0F));

    public static final RegistryObject<ArmorItem> COPPER_HELMET =
            piece(CopperGear.HELMET, ArmorItem.Type.HELMET);

    public static final RegistryObject<ArmorItem> COPPER_CHESTPLATE =
            piece(CopperGear.CHESTPLATE, ArmorItem.Type.CHESTPLATE);

    public static final RegistryObject<ArmorItem> COPPER_LEGGINGS =
            piece(CopperGear.LEGGINGS, ArmorItem.Type.LEGGINGS);

    public static final RegistryObject<ArmorItem> COPPER_BOOTS =
            piece(CopperGear.BOOTS, ArmorItem.Type.BOOTS);

    /** One armour piece, with the uses computed the way vanilla computes them. */
    private static RegistryObject<ArmorItem> piece(String name, ArmorItem.Type type) {
        return ARMOR_ITEMS.register(name, properties -> new ArmorItem(
                COPPER_MATERIAL, type,
                properties.durability(type.getDurability(CopperGear.ARMOR_DURABILITY_MULTIPLIER))));
    }

    /** The four pieces, in the order the player wears them. */
    public static List<RegistryObject<ArmorItem>> pieces() {
        return List.of(COPPER_HELMET, COPPER_CHESTPLATE, COPPER_LEGGINGS, COPPER_BOOTS);
    }

    /** Hands the two registers to the mod bus. Called from the mod's constructor. */
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        ARMOR_ITEMS.register(bus);
        ARMOR_MATERIALS.register(bus);
    }

    private ModArmor() {
    }
}
