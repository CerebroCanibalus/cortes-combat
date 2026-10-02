package com.cortes.combat.registry;

import com.cortes.combat.item.ArcabuzStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * The arcabuz's {@link Tier}, which exists for one reason: it has no material.
 *
 * <p>{@link CortesTier} adapts the seven-material catalogue and its {@code getAttackDamageBonus}
 * is a switch over them, so borrowing one of those would have meant picking a material for a
 * firearm and then <em>subtracting its bonus</em> out of the damage argument to get back the
 * number that was written down. That is the exact shape of bug this project keeps hitting -- a
 * number that looks like it controls something and does not -- and it is one subtraction away
 * from a gun that quietly hits like a golden sword.</p>
 *
 * <p>So the bonus here is <b>zero and only zero</b>, which makes
 * {@code createAttributes(tier, 5.0F, -2.6F)} mean precisely "the attribute reads 6.0 and the
 * tempo is 1.4", with nothing else folded in. The zero has a name in {@link ArcabuzStats} so a
 * test compares against that rather than against a bare literal.</p>
 *
 * <p>Durability and repair are a decision and not an omission, and both live in
 * {@link ArcabuzStats} where a test can read them: <b>250 uses, repaired with iron.</b> A musket
 * that lasted a whole netherite sword's worth of hits (1561) would make it a strictly better
 * sword than the sword, and one that lasted a wooden club's worth (59) would make the firestone
 * economy irrelevant next to the gun's own. Iron's 250 is the vanilla number for the tier a
 * cannon barrel is made of.</p>
 */
public record ArcabuzTier() implements Tier {

    public static final ArcabuzTier INSTANCE = new ArcabuzTier();

    /**
     * {@code minecraft:iron_tool_materials}.
     *
     * <p>A constant rather than a literal inside the method, so the tag a reader is looking for
     * has a name to look up. Spelled out rather than read from {@code ItemTags} because only three
     * of the seven tool-material tags have constants in 1.21.1 -- {@code CortesTier} resolves the
     * other four by name for the same reason.</p>
     */
    public static final String REPAIR_TAG = "iron_tool_materials";

    @Override
    public int getUses() {
        return ArcabuzStats.USES;
    }

    /**
     * Inert, exactly like {@code CortesTier#getSpeed}: nothing here mines and the number is never
     * read. Written as 1.0 rather than as a plausible 6.0 so a reader knows the field does not
     * apply, which the type comment on {@code CortesTier} argues is better than a number that
     * looks meaningful and does nothing.
     */
    @Override
    public float getSpeed() {
        return 1.0F;
    }

    /**
     * Zero, and the whole reason this class exists.
     *
     * <p>Vanilla's {@code SwordItem#createAttributes} adds {@code damage + tier.getAttackDamageBonus()}
     * to the attribute, and the attribute's own base is 1.0. A non-zero bonus here would silently
     * move the gun's damage without anybody editing a number that looks like damage.</p>
     */
    @Override
    public float getAttackDamageBonus() {
        return ArcabuzStats.ArcabuzTierBonus.ZERO;
    }

    /** Stone's list rather than an empty tag, for the reason {@code CortesTier} gives. */
    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return BlockTags.INCORRECT_FOR_STONE_TOOL;
    }

    @Override
    public int getEnchantmentValue() {
        return ArcabuzStats.ENCHANTMENT_VALUE;
    }

    /**
     * Repaired with iron ingots, because that is what the barrel is made of.
     *
     * <p>By name rather than through a constant, and spelling the name out also means the anvil,
     * the tooltip and anything a datapack writes agree on one string. A tag nothing defines is an
     * empty ingredient rather than a crash.</p>
     */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(TagKey.create(Registries.ITEM,
                ResourceLocation.withDefaultNamespace(REPAIR_TAG)));
    }
}
