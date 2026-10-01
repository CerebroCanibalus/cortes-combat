package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModCreativeTabs;
import com.cortes.combat.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Thirty item names and a tab title, generated from the catalogue.
 *
 * <p>Names live on the enums rather than here, and in Spanish, because that is the language the
 * design and the designer's own notes are written in. The generated file is the only place a
 * translation can change them; the string a player reads by default comes out of the same {@code
 * displayName()} call that a test and a command would read, so there is no second answer.</p>
 *
 * <p>English is not generated. A missing {@code en_us} entry falls back to the key, so an
 * English client would show {@code item.cortescombat.iron_ropera} in the hotbar, which is worse
 * than a rough translation. If English is wanted later it should be a real
 * {@code en_us.json} written by a translator, not a switch in this class.</p>
 */
public class CortesLanguageProvider extends LanguageProvider {

    public CortesLanguageProvider(PackOutput output) {
        super(output, CortesCombat.MODID, "es_es");
    }

    @Override
    public void addTranslations() {
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                addItem(ModItems.weapon(family.itemName(material)),
                        family.displayName(material));
            }
        }
        addItem(ModItems.COPPER_NUGGET, "Pepita de Cobre");

        // The recipe book description, shared by all thirty advancements.
        //
        // One line rather than thirty because thirty entries that differ only in the item name are
        // not thirty translations, they are one translation typed thirty times, and a translator
        // asked to do that will eventually leave one of them in English. The name is the title,
        // which the advancement already reads off the item.
        add("advancements.cortescombat.weapon", "Hecho a mano.");
        // By key, not by holder: there is no add(TagKey) overload for a creative tab and the
        // holder is not a TagKey, so the key is spelled out. It is the translation key, which is
        // also what the tab's title() looks up at runtime -- so these two cannot disagree.
        add(ModCreativeTabs.COMBAT_KEY, "Cortés Combat");
    }
}
