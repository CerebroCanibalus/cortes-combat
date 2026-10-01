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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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
 *   <li><b>{@code #minecraft:copper_tool_materials}</b> gets our copper ingot. It is our own
 *       material in a vanilla's tag, which is the point of shipping a copper set: every
 *       vanilla-shaped consumer that accepts iron now accepts ours.</li>
 *   <li><b>{@code #cortescombat:ropera_tips}</b> is the ropera's tip, the nuggets by metal. A
 *       separate tag rather than a special case in the recipe, so a datapack can add its own
 *       metal to it.</li>
 *   <li><b>{@code #minecraft:swords}</b> gets all thirty, so anything asking "is this a blade" gets
 *       a truthful answer. Our own derivation reads this tag, so it is also the safety net behind
 *       the explicit profile component.</li>
 * </ul>
 */
public class CortesItemTagProvider extends IntrinsicHolderTagsProvider<Item> {

    /** The tag the ropera's metal tip reads. Mirrors {@code WeaponFamily.ROPERA_TIP_TAG}. */
    public static final TagKey<Item> ROPERA_TIPS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "ropera_tips"));

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
     * <p>Passing {@link CortesCombat#MODID} also names the output directory, which is why the
     * generated tag files land under {@code data/cortescombat/tags} rather than overwriting
     * vanilla's.</p>
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
        nuggetsAreTips();
        everyWeaponIsABlade();
    }


    /**
     * The nuggets into the ropera's tip tag.
     *
     * <p>Only our copper exists today, since we are the only mod with a copper tier at 1.21.1.
     * The tag is written anyway: changing the recipe from a named item to a tag later would
     * invalidate every recipe a player had already learned.</p>
     */
    private void nuggetsAreTips() {
        tag(ROPERA_TIPS).add(ModItems.COPPER_NUGGET.get());
    }

    private void everyWeaponIsABlade() {
        var swords = tag(CortesTier.swordsTag());
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                swords.add(ModItems.weapon(family.itemName(material)).get());
            }
        }
    }
}
