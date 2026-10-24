package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The 1.20.1 twin of gold's nine. Same ids, same levels, same costs, older base class.
 *
 * <p>1.21.1 builds each enchantment out of data ({@code definition} + costs + slot group);
 * 1.20.1 wants a class, so every one here is a tiny anonymous {@code Enchantment} carrying the
 * same numbers. The cost curves are the shared twin's arithmetic, written the old way:
 * min = {@code 5 + max(1, minBase/5) * (level-1)}, max =
 * {@code minBase + max(1, maxBase/5) * (level-1)}. Same tables, same levels, same table
 * behavior. (⌒‿⌒)</p>
 *
 * <p>Two mappings that are approximations and say so:</p>
 * <ul>
 *   <li>{@code anvilCost} 1/2 becomes {@code COMMON}/{@code UNCOMMON}. The numbers only feed
 *       the anvil's XP math; nothing about balance reads them.</li>
 *   <li>{@code maxScale} -- the seventh number in the shared call table -- is unused there
 *       too (it never reaches the {@code definition} call), so it is accepted and ignored
 *       here as well, keeping the call table identical.</li>
 * </ul>
 *
 * <p>Supported items are answered per stack ({@code canEnchant}) instead of per holder set:
 * anything whose registry path contains {@code golden}, which covers vanilla's golden tools
 * and ours at once. The names come from the same lang keys
 * ({@code enchantment.cortescombat.<id>}), which 1.20.1 derives from the registry key on its
 * own -- no component to build.</p>
 */
public final class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, CortesCombat.MODID);

    /** Midas on kill: nuggets always, ingots from the second level, and one apple in fifty. */
    public static final RegistryObject<Enchantment> MIDAS_TOUCH = weapon("midas_touch",
            3, 1, 25, 50, 25);

    /** Gold echo: a marked enemy takes a fifth more, from everyone. */
    public static final RegistryObject<Enchantment> GOLD_ECHO = weapon("gold_echo",
            1, 1, 20, 40, 20);

    /** Life steal: five percent of the damage that was actually dealt, per level. */
    public static final RegistryObject<Enchantment> LIFE_STEAL = weapon("life_steal",
            3, 2, 16, 40, 15);

    /** Wind: movement, five percent per level, up to five. */
    public static final RegistryObject<Enchantment> WIND_WALKER = weapon("wind_walker",
            5, 1, 10, 30, 10);

    /** Feather weight: haste bought with a slower swing. */
    public static final RegistryObject<Enchantment> FEATHER_WEIGHT = weapon(
            "feather_weight", 3, 2, 12, 30, 12);

    /** Phantom strike: fifteen percent per level of going through armour. */
    public static final RegistryObject<Enchantment> PHANTOM_STRIKE = weapon(
            "phantom_strike", 2, 2, 18, 45, 20);

    /** Midas on a tool: mined ore comes out gold, and the third level adds a nugget. */
    public static final RegistryObject<Enchantment> MIDAS_TOOL = tool("midas_tool",
            3, 1, 25, 50, 25);

    /** Wind on a tool: ten percent dig speed per level. Replaced by the frenzy. */
    public static final RegistryObject<Enchantment> MINER_FRENZY = tool("miner_frenzy",
            3, 1, 20, 40, 20);

    /** Manos Doradas: whatever the golden shovel breaks comes straight into the inventory. */
    public static final RegistryObject<Enchantment> GOLDEN_HANDS = tool("golden_hands",
            1, 1, 25, 50, 25);

    /** Cosecha Dorada: the golden hoe replants what it cuts, and doubles it at the second level. */
    public static final RegistryObject<Enchantment> GOLDEN_HARVEST = tool(
            "golden_harvest", 2, 1, 20, 45, 20);

    /** Phantom block: a tenth of the time, the drop comes out twice. */
    public static final RegistryObject<Enchantment> PHANTOM_BLOCK = tool("phantom_block",
            1, 2, 18, 40, 20);

    private ModEnchantments() {
    }

    /**
     * A weapon enchantment: sword and axe, on the golden ones of ours and vanilla's.
     *
     * <p><b>The vanilla golden axe is here because our combat eats it.</b> The six weapons of the
     * rework swing through {@code CombatEngine}, and a vanilla golden axe is one of those six.</p>
     */
    private static RegistryObject<Enchantment> weapon(String id, int maxLevel, int anvilCost,
            int minBase, int maxBase, int maxScale) {
        return build(id, maxLevel, anvilCost, minBase, maxBase, maxScale,
                EnchantmentCategory.WEAPON, true);
    }

    /**
     * A tool enchantment: the four vanilla golden tools, and only those.
     *
     * <p><b>The mod's own thirty have no golden shovel, hoe or pickaxe</b> -- read out of the
     * generated lang, where the golden set is alabarda, daga, martillo, ropera and shotel. So the
     * shovel and the hoe a player will ever enchant here are vanilla's.</p>
     */
    private static RegistryObject<Enchantment> tool(String id, int maxLevel, int anvilCost,
            int minBase, int maxBase, int maxScale) {
        return build(id, maxLevel, anvilCost, minBase, maxBase, maxScale,
                EnchantmentCategory.DIGGER, false);
    }

    /**
     * <b>A weight of two is a design statement.</b> Vanilla gives that to its mid tier: gold is a
     * fragile material on purpose, and an enchantment that showed up as often as Unbreaking would
     * make every other material a worse gold rather than a different one.
     */
    private static RegistryObject<Enchantment> build(String id, int maxLevel, int anvilCost,
            int minBase, int maxBase, int maxScale, EnchantmentCategory category,
            boolean weapons) {
        Enchantment.Rarity rarity = anvilCost <= 1 ? Enchantment.Rarity.COMMON
                : Enchantment.Rarity.UNCOMMON;
        int minStep = Math.max(1, minBase / 5);
        int maxStep = Math.max(1, maxBase / 5);
        return ENCHANTMENTS.register(id, () -> new Enchantment(rarity, category,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}) {
            @Override
            public int getMaxLevel() {
                return maxLevel;
            }

            @Override
            public int getMinCost(int level) {
                return 5 + minStep * (level - 1);
            }

            @Override
            public int getMaxCost(int level) {
                return minBase + maxStep * (level - 1);
            }

            @Override
            public boolean canEnchant(ItemStack stack) {
                if (weapons) {
                    if (stack.is(Items.GOLDEN_SWORD) || stack.is(Items.GOLDEN_AXE)) {
                        return true;
                    }
                } else {
                    if (stack.is(Items.GOLDEN_PICKAXE) || stack.is(Items.GOLDEN_SHOVEL)
                            || stack.is(Items.GOLDEN_HOE) || stack.is(Items.GOLDEN_AXE)) {
                        return true;
                    }
                }
                var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                return key != null && CortesCombat.MODID.equals(key.getNamespace())
                        && key.getPath().contains("golden");
            }

            @Override
            public boolean canApplyAtEnchantingTable(ItemStack stack) {
                return canEnchant(stack);
            }
        });
    }
}
