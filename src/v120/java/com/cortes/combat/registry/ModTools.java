package com.cortes.combat.registry;

import net.minecraftforge.registries.RegistryObject;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;

import java.util.List;

/**
 * The copper tools: a sword, a pickaxe, an axe, a shovel and a hoe.
 *
 * <p>Vanilla 1.21.1 has no copper tier, so these are five items of ours rather than a variant of
 * anything. They are ordinary vanilla tool classes -- {@code SwordItem}, {@code AxeItem} and so on
 * -- which is the point: <b>the combat rework reads a profile off the item, and these resolve
 * through the ordinary derivation.</b> The copper sword is a sword, the copper axe is an axe, and
 * the copper pickaxe is the one that does not fight, exactly as their iron and diamond
 * counterparts.</p>
 *
 * <h2>Why the tools need their own tier</h2>
 *
 * <p>{@link CortesTier} is the <em>weapons'</em> tier and its {@code getSpeed} deliberately returns
 * an inert 1.0, because none of the thirty catalogue weapons is a tool and none of them mines. A
 * pickaxe is the first thing in this mod that does, so it needs a tier whose speed is real. Rather
 * than change what {@code CortesTier} means, {@link CopperToolTier} delegates every other method to
 * it and overrides only the speed -- one number, in one place, and the weapons are untouched.</p>
 */
public final class ModTools {

    public static final DeferredRegister<net.minecraft.world.item.Item> TOOL_ITEMS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, CortesCombat.MODID);

    /**
     * **Every tool carries its attributes, and that is the whole bug this comment exists for.**
     *
     * <p>In 1.21.1 {@code new SwordItem(Tier, Properties)} applies <em>no</em> attributes: the
     * damage and speed come from the {@code Item.Properties} the registration passes, built with
     * {@code createAttributes}. The first version of this class left them out, and the copper
     * sword landed with the player's bare 1.0 damage and the wrong swing speed -- which read as
     * "the stats are not applying", because they were not.</p>
     *
     * <p>The numbers are vanilla's own per tool, copied from {@code Items} so a copper sword
     * swings exactly like an iron one and only its tier bonus differs. The tier supplies the
     * durability, which the tool class reads on its own.</p>
     */
    public static final RegistryObject<SwordItem> COPPER_SWORD =
            TOOL_ITEMS.register(CopperGear.SWORD, properties -> new SwordItem(
                    CopperToolTier.INSTANCE,
                    properties.attributes(SwordItem.createAttributes(
                            CopperToolTier.INSTANCE, 3, -2.4F))));

    public static final RegistryObject<PickaxeItem> COPPER_PICKAXE =
            TOOL_ITEMS.register(CopperGear.PICKAXE, properties -> new PickaxeItem(
                    CopperToolTier.INSTANCE,
                    properties.attributes(PickaxeItem.createAttributes(
                            CopperToolTier.INSTANCE, 1.0F, -2.8F))));

    public static final RegistryObject<AxeItem> COPPER_AXE =
            TOOL_ITEMS.register(CopperGear.AXE, properties -> new AxeItem(
                    CopperToolTier.INSTANCE,
                    properties.attributes(AxeItem.createAttributes(
                            CopperToolTier.INSTANCE, 6.0F, -3.1F))));

    public static final RegistryObject<ShovelItem> COPPER_SHOVEL =
            TOOL_ITEMS.register(CopperGear.SHOVEL, properties -> new ShovelItem(
                    CopperToolTier.INSTANCE,
                    properties.attributes(ShovelItem.createAttributes(
                            CopperToolTier.INSTANCE, 1.5F, -3.0F))));

    public static final RegistryObject<HoeItem> COPPER_HOE =
            TOOL_ITEMS.register(CopperGear.HOE, properties -> new HoeItem(
                    CopperToolTier.INSTANCE,
                    properties.attributes(HoeItem.createAttributes(
                            CopperToolTier.INSTANCE, -2.0F, -1.0F))));

    /** The five tools, in the order they are drawn. */
    public static List<RegistryObject<? extends Item>> tools() {
        return List.of(COPPER_SWORD, COPPER_PICKAXE, COPPER_AXE, COPPER_SHOVEL, COPPER_HOE);
    }

    /**
     * Copper as a {@code Tier}, with the one number a tool reads and a weapon does not.
     *
     * <p>An enum with one value rather than a class with a private constructor, because there is
     * exactly one copper and a singleton that cannot be instantiated twice is the shape the thing
     * actually has.</p>
     */
    public enum CopperToolTier implements Tier {
        INSTANCE;

        private static final CortesTier BASE = CortesTier.of(WeaponMaterial.COPPER);

        @Override
        public int getUses() {
            return BASE.getUses();
        }

        /** The one override, and the reason this enum exists. */
        @Override
        public float getSpeed() {
            return CopperGear.MINING_SPEED;
        }

        @Override
        public float getAttackDamageBonus() {
            return BASE.getAttackDamageBonus();
        }

        /**
         * Stone's list, because copper mines at stone's level.
         *
         * <p>The material is "between stone and iron" in durability and speed, but a mining
         * <em>level</em> is not a spectrum: it is which blocks drop. Copper takes stone's, so a
         * copper pickaxe mines iron ore and not diamond, and the upgrade it sells is speed and
         * lifespan rather than reach.</p>
         */
        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_STONE_TOOL;
        }

        @Override
        public int getEnchantmentValue() {
            return BASE.getEnchantmentValue();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return BASE.getRepairIngredient();
        }
    }

    /** Hands the register to the mod bus. Called from the mod's constructor. */
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        TOOL_ITEMS.register(bus);
    }

    private ModTools() {
    }
}
