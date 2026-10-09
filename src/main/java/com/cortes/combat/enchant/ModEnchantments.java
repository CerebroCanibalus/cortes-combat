package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Gold's nine: the enchantments that make the material a choice rather than a downgrade.
 *
 * <h2>The shape, and why this file is only names and costs</h2>
 *
 * <p>Six for the weapons, three for the tools, and <b>none of them do anything on their own.</b>
 * In 1.21 an {@link Enchantment} is a description, a cost curve and the items it may sit on; every
 * number that means anything -- what a kill drops, how much life steal heals, whether armour was
 * ignored -- lives in {@link GoldEnchantRules}, which is pure and tested, and the behaviour hangs
 * off the events in {@link GoldEnchantmentHandler}. That split is the whole reason this file can be
 * read in a minute.</p>
 *
 * <h2>Why the supported items are a direct set and not a tag</h2>
 *
 * <p>Because the design's gold is <b>twenty six of our weapons plus vanilla's three tools</b>, and
 * a tag has no opinion about which of ours are the golden ones. The list is built from
 * {@link ModItems#weapons()} by name -- {@code golden} in the path -- so adding the tier to a new
 * family adds it to all six enchantments without a second edit.</p>
 */
public final class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Registries.ENCHANTMENT, CortesCombat.MODID);

    // ------------------------------------------------------------------ the weapons

    /** Midas on kill: nuggets always, ingots from the second level, and one apple in fifty. */
    public static final DeferredHolder<Enchantment, Enchantment> MIDAS_TOUCH = weapon("midas_touch",
            "Toque de Midas", 3, 1, 25, 50, 25);

    /** Gold echo: a marked enemy takes a fifth more, from everyone. */
    public static final DeferredHolder<Enchantment, Enchantment> GOLD_ECHO = weapon("gold_echo",
            "Eco de Oro", 1, 1, 20, 40, 20);

    /** Life steal: five percent of the damage that was actually dealt, per level. */
    public static final DeferredHolder<Enchantment, Enchantment> LIFE_STEAL = weapon("life_steal",
            "Robo de Vida", 3, 2, 16, 40, 15);

    /** Wind: movement, five percent per level, up to five. */
    public static final DeferredHolder<Enchantment, Enchantment> WIND_WALKER = weapon("wind_walker",
            "Velocidad del Viento", 5, 1, 10, 30, 10);

    /** Feather weight: haste bought with a slower swing. */
    public static final DeferredHolder<Enchantment, Enchantment> FEATHER_WEIGHT = weapon(
            "feather_weight", "Peso Pluma", 3, 2, 12, 30, 12);

    /** Phantom strike: fifteen percent per level of going through armour. */
    public static final DeferredHolder<Enchantment, Enchantment> PHANTOM_STRIKE = weapon(
            "phantom_strike", "Golpe Fantasma", 2, 2, 18, 45, 20);

    // ------------------------------------------------------------------ the tools

    /** Midas on a tool: mined ore comes out gold, and the third level adds a nugget. */
    public static final DeferredHolder<Enchantment, Enchantment> MIDAS_TOOL = tool("midas_tool",
            "Toque de Midas", 3, 1, 25, 50, 25);

    /** Wind on a tool: ten percent dig speed per level. Replaced by the frenzy. */
    public static final DeferredHolder<Enchantment, Enchantment> MINER_FRENZY = tool("miner_frenzy",
            "Fiebre del Minero", 3, 1, 20, 40, 20);

    /** Manos Doradas: whatever the golden shovel breaks comes straight into the inventory. */
    public static final DeferredHolder<Enchantment, Enchantment> GOLDEN_HANDS = tool("golden_hands",
            "Manos Doradas", 1, 1, 25, 50, 25);

    /** Cosecha Dorada: the golden hoe replants what it cuts, and doubles it at the second level. */
    public static final DeferredHolder<Enchantment, Enchantment> GOLDEN_HARVEST = tool(
            "golden_harvest", "Cosecha Dorada", 2, 1, 20, 45, 20);

    /** Phantom block: a tenth of the time, the drop comes out twice. */
    public static final DeferredHolder<Enchantment, Enchantment> PHANTOM_BLOCK = tool("phantom_block",
            "Golpe Fantasma", 1, 2, 18, 40, 20);

    private ModEnchantments() {
    }

    /**
     * A weapon enchantment: sword and axe, on the golden ones of ours and vanilla's.
     *
     * <p><b>The vanilla golden axe is here because our combat eats it.</b> The six weapons of the
     * rework swing through {@code CombatEngine}, and a vanilla golden axe is one of those six.</p>
     */
    private static DeferredHolder<Enchantment, Enchantment> weapon(String id, String name,
                                                                   int maxLevel, int anvilCost,
                                                                   int minBase, int maxBase,
                                                                   int maxScale) {
        List<Holder<Item>> supported = new ArrayList<>();
        supported.add(Items.GOLDEN_SWORD.builtInRegistryHolder());
        supported.add(Items.GOLDEN_AXE.builtInRegistryHolder());
        for (DeferredItem<? extends Item> item : ModItems.weapons()) {
            if (item.getId().getPath().contains("golden")) {
                supported.add(item);
            }
        }
        return build(id, name, maxLevel, anvilCost, minBase, maxBase, maxScale,
                HolderSet.direct(supported));
    }

    /** A tool enchantment: the four vanilla golden tools, and only those. */
    private static DeferredHolder<Enchantment, Enchantment> tool(String id, String name, int maxLevel,
                                                                 int anvilCost, int minBase,
                                                                 int maxBase, int maxScale) {
        return build(id, name, maxLevel, anvilCost, minBase, maxBase, maxScale,
                HolderSet.direct(List.of(Items.GOLDEN_PICKAXE.builtInRegistryHolder(),
                        Items.GOLDEN_SHOVEL.builtInRegistryHolder(),
                        Items.GOLDEN_HOE.builtInRegistryHolder(),
                        Items.GOLDEN_AXE.builtInRegistryHolder())));
    }

    /**
     * <b>A weight of two is a design statement.</b> Vanilla gives that to its mid tier: gold is a
     * fragile material on purpose, and an enchantment that showed up as often as Unbreaking would
     * make every other material a worse gold rather than a different one.
     */
    private static DeferredHolder<Enchantment, Enchantment> build(String id, String name, int maxLevel,
                                                                  int anvilCost, int minBase,
                                                                  int maxBase, int maxScale,
                                                                  HolderSet<Item> supported) {
        return ENCHANTMENTS.register(id, () -> new Enchantment(
                Component.translatable("enchantment." + CortesCombat.MODID + "." + id),
                // **`dynamicCost(base, perLevel)` and not what it looks like, read out of
                // `Enchantment.Cost`: cost = base + perLevelAboveFirst * (level - 1).** Passing
                // the same two numbers to both min and max would make every level of every one of
                // the nine cost the same, and above thirty -- which no table can reach.
                Enchantment.definition(supported, supported, 2, maxLevel,
                        Enchantment.dynamicCost(5, Math.max(1, minBase / 5)),
                        Enchantment.dynamicCost(minBase, Math.max(1, maxBase / 5)),
                        anvilCost, EquipmentSlotGroup.MAINHAND),
                HolderSet.empty(), DataComponentMap.EMPTY));
    }
}
