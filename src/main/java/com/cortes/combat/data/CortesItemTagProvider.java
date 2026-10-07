package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.CortesTier;
import com.cortes.combat.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * The tags the recipes depend on.
 *
 * <p>They exist because the recipes read tags rather than named items, and a recipe that reads a
 * tag nothing defines is a recipe a player can never craft. So the members are generated here,
 * beside the recipes that need them, and the two cannot drift apart.</p>
 *
 * <ul>
 *   <li><b>{@code #minecraft:<tier>_tool_materials}</b> for six of the seven tiers. 1.21.1 ships
 *       exactly <em>one</em> of these -- {@code stone_tool_materials} -- and the other six do not
 *       exist, so this provider writes them. See {@link #materialTags()} for the whole story; the
 *       short version is that a recipe reading a tag nothing publishes is a recipe nobody can
 *       craft, and nothing anywhere says so.</li>
 *   <li><b>{@code #minecraft:swords}</b> gets all thirty, so anything asking "is this a blade" gets
 *       a truthful answer. Our own derivation reads this tag, so it is also the safety net behind
 *       the explicit profile component.</li>
 * </ul>
 */
public class CortesItemTagProvider extends IntrinsicHolderTagsProvider<Item> {

    /**
     * The five-argument constructor, and the mod id is the reason.
     *
     * <p>Vanilla already registers a tags provider for {@code minecraft:item}, and NeoForge's
     * duplicate check rejects a second one whose name collides -- which the three-argument
     * constructor does, because it defaults the mod id to {@code "vanilla"}. The symptom is an
     * IllegalStateException during {@code runData} that reads
     * {@code "Duplicate provider: Tags for minecraft:item mod id vanilla"} and names neither this
     * mod nor this provider, so it is worth the comment that it does not mean what it says.</p>
     *
     * <p>The mod id is the provider's <em>name</em>, and it is not what decides where a tag file
     * lands: the output directory follows the {@code TagKey}'s namespace, so a
     * {@code minecraft:swords} tag is written to {@code data/minecraft/tags/item/swords.json} and a
     * {@code cortescombat:...} tag to {@code data/cortescombat/tags/item/...}. Both are correct --
     * tags from several packs merge -- but it is worth writing down because it is not what the
     * parameter looks like it does.</p>
     */
    public CortesItemTagProvider(PackOutput output,
                                 CompletableFuture<HolderLookup.Provider> registries,
                                 ExistingFileHelper existingFileHelper) {
        // The provider needs a Function<Item, ResourceKey<Item>> -- the registry *key*, which is
        // what a tag file names an entry by. getKey returns a ResourceLocation instead, and
        // getResourceKey returns the Optional, so orElseThrow is what unwraps it.
        super(output, Registries.ITEM, registries,
                item -> BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow(),
                CortesCombat.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        materialTags();
        everyWeaponIsABlade();
    }

    private void everyWeaponIsABlade() {
        var swords = tag(CortesTier.swordsTag());
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                swords.add(ModItems.weapon(family.itemName(material)).get());
            }
        }
    }

    /**
     * The six tool-material tags 1.21.1 does not ship, written here so the recipes have one to read.
     *
     * <h2>The bug, and why it is the same shape as the mace and the trident</h2>
     *
     * <p>{@link WeaponMaterial#repairTag()} names a tag per tier -- {@code iron_tool_materials},
     * {@code diamond_tool_materials} and so on -- and <b>three</b> separate consumers resolve it as
     * {@code minecraft:<name>}: the recipe's body ingredient, the advancement's criterion, and the
     * anvil repair ingredient in {@link CortesTier#getRepairIngredient()}.</p>
     *
     * <p><b>1.21.1 ships exactly one of the seven.</b> The vanilla client jar carries
     * {@code data/minecraft/tags/item/stone_tool_materials.json} and no other
     * {@code *_tool_materials} file at all, and {@code Tiers} agrees from the other side: wood
     * repairs with {@code ItemTags.PLANKS}, stone with {@code STONE_TOOL_MATERIALS}, and iron,
     * gold, diamond and netherite with <b>plain items</b> -- {@code IRON_INGOT},
     * {@code GOLD_INGOT}, {@code DIAMOND}, {@code NETHERITE_INGOT}. There is no
     * {@code iron_tool_materials} for a recipe to read, and there never was.</p>
     *
     * <p>A {@code TagKey} for a path nothing publishes does not throw and does not warn: it
     * resolves to an empty set. The recipe parses, the advancement parses,
     * {@code Loaded 1290 recipes} is printed, and the weapon cannot be made. <b>Only the five stone
     * weapons were craftable</b>, and only because stone is the one tag vanilla actually writes.
     * This is the same failure as the {@code #maces} / {@code #tridents} round that cost the mace
     * and the trident their profiles, found in a third place.</p>
     *
     * <h2>Why the {@code minecraft} namespace and not ours</h2>
     *
     * <p>Because DESIGN.md 5bis names it, and the reason survives the version change.
     * {@link WeaponMaterial} says "when the version that brings copper age arrives this enum loses
     * one line and the recipes keep working, because they read the material tag rather than a named
     * item". A tag we own would be a tag we keep forever; writing into {@code minecraft:} means the
     * day Mojang ships {@code copper_tool_materials} the two files merge and this line becomes
     * redundant, which is the outcome the design was aiming at. Tags from several packs merge
     * additively, so a new tag in vanilla's namespace is legal and is how a mod extends one.</p>
     *
     * <p><b>{@code stone_tool_materials} is deliberately not written.</b> Vanilla ships it with
     * cobblestone, blackstone and cobbled deepslate, and re-declaring a tag that already works
     * would only add a second place for its contents to be wrong.</p>
     */
    private void materialTags() {
        for (WeaponMaterial material : WeaponMaterial.values()) {
            // **`tag(...)` is called inside each arm and not once before the switch.** Calling it
            // unconditionally registers the tag even when nothing is added, so the STONE arm below
            // wrote an empty `minecraft:stone_tool_materials.json` -- a second, empty definition of
            // a tag vanilla already fills. It merges harmlessly, and it is exactly the kind of file
            // that later reads as "we own this tag" when we do not.
            switch (material) {
                case WOODEN -> tag(CortesTier.repairTag(material)).addTag(ItemTags.PLANKS);
                case COPPER -> tag(CortesTier.repairTag(material)).add(Items.COPPER_INGOT);
                case IRON -> tag(CortesTier.repairTag(material)).add(Items.IRON_INGOT);
                case GOLDEN -> tag(CortesTier.repairTag(material)).add(Items.GOLD_INGOT);
                case DIAMOND -> tag(CortesTier.repairTag(material)).add(Items.DIAMOND);
                case NETHERITE -> tag(CortesTier.repairTag(material)).add(Items.NETHERITE_INGOT);
                // Vanilla's own, and the one tag the recipes could already reach. Not touched.
                case STONE -> { }
            }
        }
    }
}
