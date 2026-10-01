package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.CortesWeaponItem;
import com.cortes.combat.item.WeaponFamily;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every item this mod adds, built from the catalogue rather than written out.
 *
 * <p>Thirty weapons plus two copper materials, and the thirty are <b>not</b> thirty
 * registrations. Each one is a (family, material) pair read out of {@link WeaponFamily}, so the
 * count is a consequence of the design rather than a number someone typed and then had to keep
 * true. Change a family and the item set changes; forget to change one and
 * {@code WeaponCatalogueTest} says so before a player does.</p>
 *
 * <p>The two copper materials are the only items here that are not weapons. They exist because the
 * design decided on its own copper set (DESIGN.md 5bis) rather than adopting Mojang's, and
 * because the ropera's crafting grid names a copper nugget as its second ingredient -- the
 * "copper tip" the design was missing a piece for.</p>
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CortesCombat.MODID);

    /**
     * The copper ingot is <b>vanilla's</b>, with our texture on it.
     *
     * <p>There was a {@code cortescombat:copper_ingot} here first, and it was wrong twice over.
     * It put two copper ingots in the world, so every tag, recipe and creative tab had to pick one
     * and a player holding the other was holding an item nothing could use. And it was a second
     * economy rather than a reskin, which is not what a copper tier is.</p>
     *
     * <p>So there is no item here at all. {@code assets/minecraft/models/item/copper_ingot.json}
     * points vanilla's item at our texture, and every reference in the mod reads
     * {@code Items.COPPER_INGOT}. A mod can ship a file under the vanilla namespace because the
     * resource packs stack and the mod's is applied after the game's, which is the only way to
     * reskin an item that is not ours.</p>
     *
     * <p>The texture itself is unchanged and still lives at
     * {@code assets/cortescombat/textures/item/copper_ingot.png}.</p>
     */
    /**
     * The ropera's second ingredient on every metal tier.
     *
     * <p>A nugget rather than an ingot, and that is not a saving: the grid has exactly one such
     * cell, and nine nuggets next to the ingot is the recipe Mojang uses for its own copper gear.
     * It is also what makes the ropera the cheapest metal weapon in the set, which is the price of
     * the narrowest cone in the game.</p>
     *
     * <p><b>Vanilla has no copper nugget.</b> {@code Items} in 1.21.1 has gold and iron nuggets and
     * no copper equivalent, and the vanilla jar ships no copper nugget model or texture either. So
     * this one really is an item we register, and it is the only reason the ropera's tip is a
     * nugget: it is the only metal tier whose tip material had to be invented.</p>
     */
    public static final DeferredItem<Item> COPPER_NUGGET =
            ITEMS.registerSimpleItem("copper_nugget", new Item.Properties());

    private static final Map<String, DeferredItem<? extends Item>> WEAPONS = new LinkedHashMap<>();

    static {
        // Built in family order then material order, so the registry listing a developer reads is
        // in the shape of the catalogue rather than in the shape of a HashMap.
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                registerWeapon(family, material);
            }
        }
    }

    private static void registerWeapon(WeaponFamily family, WeaponMaterial material) {
        String name = family.itemName(material);
        DeferredItem<CortesWeaponItem> item = ITEMS.registerItem(name,
                properties -> CortesWeaponItem.of(family, material));
        WEAPONS.put(name, item);
    }

    /**
     * The items that carry a weapon profile, in catalogue order.
     *
     * <p>Exposed as a list rather than as the map so that a caller iterating it -- the creative
     * tab, the datagen, {@code /cc weapons} -- cannot reorder it by touching the map.</p>
     */
    public static List<DeferredItem<? extends Item>> weapons() {
        return List.copyOf(WEAPONS.values());
    }

    public static DeferredItem<? extends Item> weapon(String name) {
        DeferredItem<? extends Item> item = WEAPONS.get(name);
        if (item == null) {
            throw new IllegalArgumentException("no weapon called " + name + ", have " + WEAPONS.keySet());
        }
        return item;
    }

    /** The icon for the creative tab: the family's stone version, or the first one it has. */
    public static ItemStack tabIcon() {
        WeaponFamily first = WeaponFamily.values()[0];
        WeaponMaterial material = first.existsIn(WeaponMaterial.STONE)
                ? WeaponMaterial.STONE
                : first.materialsInOrder().iterator().next();
        // Cast to ItemLike explicitly: a DeferredItem is both an ItemLike and a Holder<Item>, so
        // new ItemStack(holders.get(...)) is ambiguous and the compiler says so.
        ItemLike like = WEAPONS.get(first.itemName(material));
        return new ItemStack(like);
    }

    private ModItems() {
    }
}
