package com.cortes.combat.enchant;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;

import java.util.List;

/**
 * The 1.20.1 bodies of the thirty-seven combat enchantments. Same ids, same levels, same
 * costs, older base class.
 *
 * <p>On 1.21.1 these live in {@code data/cortescombat/enchantment/*.json} and no code names
 * them: the datapack registry carries the description, the cost curves and the item filter,
 * and the hooks only read levels through {@code CombatEnchantments}. 1.20.1 has no datapack
 * enchantment registry, so each one is a tiny anonymous {@code Enchantment} here, with the
 * numbers transcribed from its JSON -- the JSON stays the source of truth and this file the
 * transcription, the same split the sable pose keeps with its Java twin. (⌒‿⌒)</p>
 *
 * <p>Four mappings, each exact where it can be and loud where it cannot:</p>
 * <ul>
 *   <li>Costs are the JSON's arithmetic verbatim: {@code base + perLevelAboveFirst * (level-1)},
 *       which is what {@code Enchantment.dynamicCost} means on the other leg.</li>
 *   <li>Rarity is the weight on its own scale: 10 COMMON, 5 UNCOMMON, 2 RARE, 1 VERY_RARE --
 *       the same numbers vanilla's four rarities carry, so no judgment call hides in here.</li>
 *   <li>Supported and primary items coincide on all thirty-seven (one tag each, verified by
 *       reading the JSONs), so {@code canEnchant} and {@code canApplyAtEnchantingTable} ask
 *       the same tag. The tags themselves ship in the jar and load on both legs.</li>
 *   <li>Availability mirrors {@code data/minecraft/tags/enchantment/}: everything sits in
 *       {@code in_enchanting_table}, so everything is discoverable; only {@code steel_reflex}
 *       sits in {@code tradeable}. There is no treasure file, so nothing is treasure-only.</li>
 * </ul>
 *
 * <p>Category is DIGGER for the tool tags (axe, hoe, shovel) and WEAPON for the rest. It feeds
 * only the default {@code canEnchant}, which is overridden anyway -- the tag is the real
 * answer, the category keeps the bookkeeping honest. (μ_μ)</p>
 */
public final class CombatEnchantRegistry {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS,
                    com.cortes.combat.CortesCombat.MODID);

    /** One row per JSON: id, max level, min base/step, max base/step, rarity, category, tag. */
    private record Entry(String id, int maxLevel, int minBase, int minStep, int maxBase, int maxStep,
                         Enchantment.Rarity rarity, EnchantmentCategory category, String tag) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("steel_reflex", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/sabre"),
            new Entry("audacity", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/ropera"),
            new Entry("adrenalina", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/dagger"),
            new Entry("rhythm_of_iron", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/warhammer"),
            new Entry("riposte", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:enchantable/sword"),
            new Entry("dry_powder", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/arcabuz"),
            new Entry("punta_fina", 2, 15, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/ropera"),
            new Entry("filo_astillado", 2, 15, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.DIGGER,
                    "#cortescombat:enchantable/axe"),
            new Entry("apunalar", 2, 15, 20, 45, 20,
                    Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/dagger"),
            new Entry("sanguijuela", 2, 15, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/dagger"),
            new Entry("aventurero", 5, 10, 12, 60, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:enchantable/sword"),
            new Entry("culata", 3, 15, 15, 50, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/arcabuz"),
            new Entry("estocada", 2, 15, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/ropera"),
            new Entry("segundo_aliento", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:enchantable/sword"),
            new Entry("guardia_firme", 1, 15, 0, 40, 0,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:enchantable/sword"),
            new Entry("cicatrizador", 4, 15, 12, 55, 15,
                    Enchantment.Rarity.RARE, EnchantmentCategory.DIGGER,
                    "#cortescombat:enchantable/hoe"),
            new Entry("destripamiento", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/halberd"),
            new Entry("quebranto", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:enchantable/heavy"),
            new Entry("desvio_vampirico", 4, 20, 15, 60, 20,
                    Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/sabre"),
            new Entry("mecha_corta", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/arcabuz"),
            new Entry("barredor", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/halberd"),
            new Entry("astilladora", 1, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.DIGGER,
                    "#cortescombat:enchantable/axe"),
            new Entry("mella", 4, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.DIGGER,
                    "#cortescombat:enchantable/axe"),
            new Entry("segunda_pasada", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/halberd"),
            new Entry("santa_muerte", 1, 18, 20, 45, 20,
                    Enchantment.Rarity.RARE, EnchantmentCategory.DIGGER,
                    "#cortescombat:enchantable/hoe"),
            new Entry("inercia", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/warhammer"),
            new Entry("ensarte", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:enchantable/reach"),
            new Entry("paliza", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.DIGGER,
                    "#cortescombat:profile/shovel"),
            new Entry("retirada", 1, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/ropera"),
            new Entry("sentencia", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/warhammer"),
            new Entry("trabazon", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/warhammer"),
            new Entry("gripa", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.COMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/dagger"),
            new Entry("corte_gemelo", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/sabre"),
            new Entry("capacitor", 2, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/sabre"),
            new Entry("pala_antimaterial", 1, 25, 20, 50, 20,
                    Enchantment.Rarity.VERY_RARE, EnchantmentCategory.DIGGER,
                    "#cortescombat:enchantable/shovel"),
            new Entry("rey_de_las_moscas", 1, 18, 20, 45, 20,
                    Enchantment.Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/arcabuz"),
            new Entry("mungus", 1, 25, 20, 50, 20,
                    Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON,
                    "#cortescombat:profile/arcabuz"));

    static {
        for (Entry entry : ENTRIES) {
            ENCHANTMENTS.register(entry.id(), () -> build(entry));
        }
    }

    private CombatEnchantRegistry() {
    }

    private static Enchantment build(Entry entry) {
        // The tag arrives as "#namespace:path", the one form the JSONs use; parsing it here
        // keeps the table readable instead of splitting namespace and path per row. (￣ε￣＠)
        String raw = entry.tag().substring(1);
        int colon = raw.indexOf(':');
        TagKey<Item> supported = TagKey.create(Registries.ITEM,
                new ResourceLocation(raw.substring(0, colon), raw.substring(colon + 1)));
        return new Enchantment(entry.rarity(), entry.category(),
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}) {
            @Override
            public int getMaxLevel() {
                return entry.maxLevel();
            }

            @Override
            public int getMinCost(int level) {
                return entry.minBase() + entry.minStep() * (level - 1);
            }

            @Override
            public int getMaxCost(int level) {
                return entry.maxBase() + entry.maxStep() * (level - 1);
            }

            @Override
            public boolean canEnchant(ItemStack stack) {
                return stack.is(supported);
            }

            @Override
            public boolean canApplyAtEnchantingTable(ItemStack stack) {
                return stack.is(supported);
            }

            @Override
            public boolean isDiscoverable() {
                // Everything sits in in_enchanting_table on the other leg. (⌒‿⌒)
                return true;
            }

            @Override
            public boolean isTradeable() {
                // Only steel_reflex sits in tradeable over there. (μ_μ)
                return entry.id().equals("steel_reflex");
            }

            @Override
            public boolean isTreasureOnly() {
                return false;
            }
        };
    }
}
