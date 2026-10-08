package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One tab, holding everything this mod adds.
 *
 * <p>Not five tabs per weapon family and not a tab per material. The catalogue is thirty items of
 * five shapes and seven tiers, and a player looking for "the fast one" is asking a question about
 * <em>shape</em>, so the families are grouped in progression order inside one tab and the ordering
 * does the teaching: the dagger is last because it is the beginner's weapon even though it is
 * cheap, and the warhammer sits next to the ropera because both of them are expensive.</p>
 *
 * <p>A tab per family would mean six clicks to compare two weapons, and a tab per tier would put
 * five unrelated shapes on one page. One tab, catalogue order, is the answer that costs the
 * player nothing.</p>
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CortesCombat.MODID);

    /**
     * The tab's translation key, spelled out once.
     *
     * <p>The provider needs the key and the tab needs the title, and if the two were written
     * separately a typo would produce a tab labelled {@code itemGroup.cortescombat.combta}. One
     * constant, and the provider reads it.</p>
     */
    public static final String COMBAT_KEY = "itemGroup." + CortesCombat.MODID + ".combat";

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMBAT =
            TABS.register("combat", () -> CreativeModeTab.builder()
                    .title(Component.translatable(COMBAT_KEY))
                    .icon(ModItems::tabIcon)
                    .displayItems((parameters, output) -> {
                        ModItems.weapons().forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.ARCABUZ.get());
                        // The two things that are not weapons, after all of them that are, and in
                        // the order a player needs them: the gun, then what to put in it. The
                        // tab teaches the progression for the catalogue and the dependency for the
                        // arcabuz, which is the same thing it was already doing.
                        output.accept(ModItems.FIRESTONE.get());
                        // **And the block made of nine of them, right after the nine.** The tab is
                        // ordered by dependency -- the gun, then what it eats, then what those
                        // stack into -- so a player reading top to bottom meets the firestone, its
                        // recipe ingredient, and the block in the order they are learned.
                        output.accept(ModBlocks.FIRESTONE_BLOCK_ITEM.get());
                        // **And the reactor, the thing those firestones are for.** It sits after
                        // the block made of nine of them because the progression reads the same
                        // way: the powder, the block, and then the machine that burns it.
                        output.accept(ModBlocks.NETHER_REACTOR_ITEM.get());
                        output.accept(ModItems.DEAD_STAR.get());
                        output.accept(ModItems.COPPER_NUGGET.get());
                        // **The copper gear, last, because it is the one thing here that is not a
                        // weapon.** The tab's order is the progression: thirty weapons, the gun,
                        // what to put in it, and then the set a player makes out of the metal they
                        // have been digging up all along.
                        ModTools.tools().forEach(item -> output.accept(item.get()));
                        ModArmor.pieces().forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
