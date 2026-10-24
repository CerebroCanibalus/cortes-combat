package com.cortes.combat.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The 1.20.1 copper armour piece: vanilla {@code ArmorItem} plus the right texture path.
 *
 * <p>1.20.1 builds armour textures out of the material name, which would point at the vanilla
 * namespace. This override names the file directly -- legging slot gets the _2 layer, everything
 * else the _1 -- matching the two PNGs the art already ships as. Purely visual: the numbers
 * live in the material. (⌒‿⌒)</p>
 */
public class CopperArmorItem extends ArmorItem {

    public CopperArmorItem(ArmorMaterial material, Type type, Item.Properties properties) {
        super(material, type, properties);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        String layer = slot == EquipmentSlot.LEGS ? "copper_layer_2" : "copper_layer_1";
        return "cortescombat:textures/models/armor/" + layer + ".png";
    }
}
