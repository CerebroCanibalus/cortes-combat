package com.cortes.combat.registry;

import com.cortes.combat.core.weapon.WeaponMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * {@link WeaponMaterial} as something vanilla accepts: a {@code Tier}.
 *
 * <p>This class is the whole reason the material enum is pure data. Every Minecraft type the tier
 * interface needs lives here, in one small adapter, and the enum it adapts stays loadable by a
 * unit test on a bare JVM.</p>
 *
 * <p>No cache: a record with one field is free to allocate and thirty items are built once at
 * registry time.</p>
 */
public record CortesTier(WeaponMaterial material) implements Tier {

    public static CortesTier of(WeaponMaterial material) {
        return new CortesTier(material);
    }

    @Override
    public int getUses() {
        return material.uses();
    }

    /**
     * Mining speed, and deliberately 1.0 rather than copied from the material.
     *
     * <p>None of these weapons is a tool and none of them mines, so the number is never read. It is
     * written as 1.0 rather than as a plausible-looking 6.0 so that anyone reading this knows the
     * value is inert: a field that looks meaningful and does nothing is worse than one that
     * obviously does not apply.</p>
     */
    @Override
    public float getSpeed() {
        return 1.0F;
    }

    /**
     * The material's damage bonus, on vanilla's own scale.
     *
     * <p><b>Copper sits below stone, and that is the price of its durability.</b> It used to share
     * stone's 1.0, which made it strictly better than stone -- same damage, three hundred and
     * twenty uses against a hundred and thirty one -- and the playtest asked for the trade to be
     * real: copper lasts and does not hit. At 0.5 the copper weapon is weaker than the stone one
     * and outlives it by nearly two and a half times, and the choice between them is a choice.</p>
     *
     * <p><b>Which is why the copper weapon with the highest ceiling earns it from escalation.</b>
     * The warhammer's streak is the only damage in the game that grows with use, and a tier whose
     * base is the lowest in the set is exactly the tier that needs it.</p>
     */
    @Override
    public float getAttackDamageBonus() {
        return switch (material) {
            case WOODEN, GOLDEN -> 0.0F;
            case COPPER -> 0.5F;
            case STONE -> 1.0F;
            case IRON -> 2.0F;
            case DIAMOND -> 3.0F;
            case NETHERITE -> 4.0F;
        };
    }

    /**
     * The mining level, which is a ladder and not a spectrum: it decides which blocks drop.
     * Copper takes stone's rung -- a copper pickaxe mines iron ore and not diamond -- which is
     * the level the shared twin's tag expresses the other way around. Weapons never read this
     * (none of the thirty mines), tools do.
     */
    @Override
    public int getLevel() {
        return switch (material) {
            case WOODEN, GOLDEN -> 0;
            case STONE, COPPER -> 1;
            case IRON -> 2;
            case DIAMOND -> 3;
            case NETHERITE -> 4;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return material.enchantmentValue();
    }

    /**
     * Repaired with anything in the material's tool-material tag.
     *
     * <p>Read by name rather than through {@code ItemTags}' constants because two of the seven
     * have no constant there: {@code wooden_tool_materials} is not a vanilla tag and
     * {@code copper_tool_materials} is ours, generated beside the recipes. Resolving by name means
     * the recipes, the anvil and the tooltip all agree on one spelling, and a tag no datapack
     * defines is an empty ingredient rather than a crash.</p>
     */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(repairTag(material));
    }

    /** The item tag whose members this material repairs. Public because the recipes read it. */
    public static TagKey<Item> repairTag(WeaponMaterial material) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.withDefaultNamespace(material.repairTag()));
    }

    /**
     * The tag our own items are added to, so vanilla's tag-driven code can see them.
     *
     * <p>Nothing in this mod's combat reads it -- {@code WeaponStats} reads its own components --
     * but a foreign mod asking "is this a sword" should get a truthful answer, and so should any
     * vanilla path that treats tagged items as blades.</p>
     */
    public static TagKey<Item> swordsTag() {
        return ItemTags.SWORDS;
    }
}
