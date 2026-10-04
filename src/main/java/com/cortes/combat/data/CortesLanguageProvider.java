package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModCreativeTabs;
import com.cortes.combat.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Two language files: {@code es_es}, which is the language the design is written in, and
 * {@code en_us}, which is the language Minecraft runs in by default.
 *
 * <h2>Why there are two, and why it was one for months</h2>
 *
 * <p>This class used to generate only Spanish, and the reason written in its comment was that a
 * missing {@code en_us} entry falls back to the key -- so an English client would show
 * {@code item.cortescombat.iron_ropera} in the hotbar, "which is worse than a rough
 * translation".</p>
 *
 * <p>That reasoning stops one step short of where it needed to go. A missing {@code en_us} does
 * not make a rough translation look worse; it makes the mod show its own internals to <b>every
 * player who did not open the options menu and find the language</b>, and {@code en_us} is what
 * Minecraft is in until somebody changes it. Shipping a Spanish mod without Spanish being the
 * default is not a translation decision, it is a broken build.</p>
 *
 * <p>There was also a second rule in here saying English should come from a translator rather than
 * from this class. <b>Nobody asked for that rule</b> -- it was written here as if it were a
 * decision of the project's, and then the next reader treated it as one. A rule nobody chose is
 * worse than no rule, because it survives: it looks like a wall and there is no one behind it.</p>
 *
 * <h2>Two providers, not one provider with a switch inside</h2>
 *
 * <p>Each instance writes one file, so the two languages are two independent methods rather than
 * one with thirty {@code if (locale.equals("es_es"))}. They cannot half-generate each other, and
 * a string added to the Spanish side and forgotten on the English side is a missing line rather
 * than a line in the wrong language.</p>
 *
 * <p>{@code getName()} is not overridden because {@code LanguageProvider} already puts the locale
 * in it: {@code "Languages: es_es for mod: cortescombat"}. Two instances do not collide, which
 * was worth reading rather than assuming -- a collision would be the "Duplicate provider" error
 * this project has already paid once.</p>
 */
public class CortesLanguageProvider extends LanguageProvider {

    private final boolean spanish;

    public CortesLanguageProvider(PackOutput output, String locale) {
        super(output, CortesCombat.MODID, locale);
        // The one place the two files are told apart. Every string below branches on this, so a
        // third language is a new constructor argument and two more methods, not a refactor of
        // thirty conditionals.
        this.spanish = "es_es".equals(locale);
    }

    @Override
    public void addTranslations() {
        // Names come from the catalogue rather than from here, so a name a player reads in the
        // hotbar and the string a test asserts are the same two calls. The language difference is
        // the family and the material's own accessor, because English puts the material in front
        // ("Iron Ropera") and Spanish puts it behind with a "de".
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                addItem(ModItems.weapon(family.itemName(material)),
                        spanish ? family.displayName(material) : family.displayNameEn(material));
            }
        }
        addItem(ModItems.COPPER_NUGGET, spanish ? "Pepita de Cobre" : "Copper Nugget");

        // **Three names are identical in both files on purpose.** Ropera, Arcabuz and Shotel are
        // the project's own, and WeaponFamily's comment says why. "Firestone" is a fourth: it is
        // an English word already, and DESIGN.md calls the item that way, so there is no Spanish
        // version of it to choose.
        addItem(ModItems.ARCABUZ, "Arcabuz");
        addItem(ModItems.FIRESTONE, "Firestone");

        if (spanish) {
            addSpanish();
        } else {
            addEnglish();
        }
    }

    /**
     * The Spanish strings, and the ones a player reads in this project's own language.
     *
     * <p>The recipe book description is one line rather than thirty: thirty entries that differ
     * only in the item name are not thirty translations, they are one translation typed thirty
     * times, and a translator asked to do that eventually leaves one of them in the other
     * language.</p>
     */
    private void addSpanish() {
        add("advancements.cortescombat.weapon", "Hecho a mano.");
        add("advancements.cortescombat.arcabuz",
                "Una caña, un mechero, un dispensador y madera. La Firestone es lo caro.");

        // %s and nothing else. TranslatableContents rejects every other format at render time with
        // "Unsupported format", so "%.1f" here would be a line that does not appear rather than a
        // line that is formatted. The numbers arrive pre-rounded as strings.
        add("tooltip.cortescombat.damage", "%s de daño");
        add("tooltip.cortescombat.charged_damage", "%s de daño cargado");
        add("tooltip.cortescombat.attack_speed", "%s golpes por segundo");
        add("tooltip.cortescombat.range", "%s de alcance");
        add("tooltip.cortescombat.durability", "Durabilidad: %s");

        add("tooltip.cortescombat.size.very_small", "Muy pequeño");
        add("tooltip.cortescombat.size.small", "Pequeño");
        add("tooltip.cortescombat.size.medium", "Medio");
        add("tooltip.cortescombat.size.wide", "Ancho");
        add("tooltip.cortescombat.size.very_wide", "Muy ancho");
        add("tooltip.cortescombat.size.maximum", "Máximo");

        add("tooltip.cortescombat.special.multi_hit",
                "Click derecho: golpea a todo lo que hay en el cono");
        add("tooltip.cortescombat.special.parry",
                "Click derecho: parry, devuelve el golpe que le pegues");
        add("tooltip.cortescombat.special.slam",
                "Click derecho: golpe en el suelo");
        add("tooltip.cortescombat.special.shove",
                "Click derecho: empujón corto");
        add("tooltip.cortescombat.special.lunge",
                "Click derecho: embestida hacia adelante");
        add("tooltip.cortescombat.special.sweep",
                "Click derecho: barrido por el suelo");
        add("tooltip.cortescombat.special.throw",
                "Click derecho: lanzamiento");
        add("tooltip.cortescombat.special.fusee",
                "Click derecho: gasta una Firestone, y un segundo después quemo todo el cono");

        // By key and not by holder: there is no add(TagKey) overload for a creative tab and the
        // holder is not a TagKey, so the key is spelled out. It is the translation key, which is
        // also what the tab's title() looks up at runtime -- so these two cannot disagree.
        add(ModCreativeTabs.COMBAT_KEY, "Cortés Combat");
    }

    /**
     * The English strings, and the three weapon names are deliberately <b>not</b> in here.
     *
     * <p>"Iron Ropera", "Iron Alabarda" and "Iron Arcabuz" read in English and still name the
     * same weapons, because those three are the project's iconic names and translating them would
     * give the two languages two different sets of weapons. "Iron Dagger" and "Iron War Hammer"
     * <em>are</em> translated, because those two are descriptive rather than named. "Iron
     * Shotel" is the shotel.</p>
     */
    private void addEnglish() {
        add("advancements.cortescombat.weapon", "Made by hand.");
        add("advancements.cortescombat.arcabuz",
                "A bamboo, a firer, a dispenser and some wood. The Firestone is the expensive part.");

        add("tooltip.cortescombat.damage", "%s Damage");
        add("tooltip.cortescombat.charged_damage", "%s Charged Damage");
        add("tooltip.cortescombat.attack_speed", "%s Attacks per Second");
        add("tooltip.cortescombat.range", "%s Reach");
        add("tooltip.cortescombat.durability", "Durability: %s");

        add("tooltip.cortescombat.size.very_small", "Very Small");
        add("tooltip.cortescombat.size.small", "Small");
        add("tooltip.cortescombat.size.medium", "Medium");
        add("tooltip.cortescombat.size.wide", "Wide");
        add("tooltip.cortescombat.size.very_wide", "Very Wide");
        add("tooltip.cortescombat.size.maximum", "Maximum");

        add("tooltip.cortescombat.special.multi_hit",
                "Right Click: hits everything in the cone");
        add("tooltip.cortescombat.special.parry",
                "Right Click: parry, returns the hit you take");
        add("tooltip.cortescombat.special.slam", "Right Click: ground slam");
        add("tooltip.cortescombat.special.shove", "Right Click: short shove");
        add("tooltip.cortescombat.special.lunge", "Right Click: forward lunge");
        add("tooltip.cortescombat.special.sweep", "Right Click: ground sweep");
        add("tooltip.cortescombat.special.throw", "Right Click: throw");
        add("tooltip.cortescombat.special.fusee",
                "Right Click: spends a Firestone, and a second later burns the whole cone");

        add(ModCreativeTabs.COMBAT_KEY, "Cortés Combat");
    }
}