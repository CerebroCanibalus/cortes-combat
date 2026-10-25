package com.cortes.combat.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cortes.combat.core.weapon.ConeSize;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.core.weapon.WeaponMaterial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the two language files say the same things.
 *
 * <p><b>This class exists because the mod once shipped with one.</b> Only {@code es_es} was
 * generated, on the reasoning that a missing {@code en_us} entry falls back to the key "which is
 * worse than a rough translation". It is {@code en_us} that Minecraft runs in until a player opens
 * the options menu, so the result was not a rough translation -- it was every item in the game
 * displaying {@code item.cortescombat.iron_ropera}, and nobody noticed for months because a
 * tooltip is only read when somebody opens it.</p>
 *
 * <p>So the invariant is not "the English is good". It is <b>the two files carry the same keys</b>,
 * and three assertions walk the enums rather than a hardcoded list, which is what makes a missing
 * key a test failure instead of a player's screenshot: add a {@code SpecialType} and this test
 * says which two languages are now missing a line.</p>
 */
class LanguageFilesTest {

    private static final Path LANG =
            Path.of("src", "generated", "resources", "assets", "cortescombat", "lang");

    private static final String ES = "es_es";
    private static final String EN = "en_us";

    /** Matches {@code "some.key":} in a language file. Enough for a file we generate ourselves. */
    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    private static String read(String locale) {
        Path path = LANG.resolve(locale + ".json");
        assertTrue(Files.isRegularFile(path), "missing: " + path + " -- a player running in "
                + locale + " would see a translation key instead of a name. Run gradlew runData");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static Set<String> keys(String json) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = KEY.matcher(json);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String value(String json, String key) {
        Matcher matcher = KEY.matcher(json);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) {
                int colon = json.indexOf(':', matcher.end() - 1);
                int close = json.indexOf('\n', colon);
                return json.substring(colon + 1, close < 0 ? json.length() : close)
                        .replaceAll("[,\"}]", "").trim();
            }
        }
        return null;
    }

    /**
     * The two files carry exactly the same keys.
     *
     * <p>Bidirectional on purpose, and that is the whole test: a key in English that is missing in
     * Spanish is a Spanish player reading a key, and it is just as broken as the case that was
     * found. Asserting only one direction would have passed the file that shipped.</p>
     */
    @Test
    @DisplayName("both language files carry exactly the same keys")
    void theTwoFilesAgreeOnKeys() {
        Set<String> spanish = keys(read(ES));
        Set<String> english = keys(read(EN));

        Set<String> missingInEnglish = new LinkedHashSet<>(spanish);
        missingInEnglish.removeAll(english);
        Set<String> missingInSpanish = new LinkedHashSet<>(english);
        missingInSpanish.removeAll(spanish);

        assertEquals(Set.of(), missingInEnglish,
                "these keys exist in es_es and not in en_us. A player running in English reads "
                        + "the key itself");
        assertEquals(Set.of(), missingInSpanish,
                "these keys exist in en_us and not in es_es, which is this project's own language");
        assertTrue(spanish.size() >= 50,
                "the file shrank to " + spanish.size() + " keys; the catalogue alone is 33");
    }

    /**
     * Every cone size band has a line in both languages.
     *
     * <p>Walking the enum rather than a list, so {@link ConeSize} gaining a seventh band fails
     * here instead of printing the identifier on screen -- which is what a missing key does, since
     * a size line is a bare key with no argument.</p>
     */
    @Test
    @DisplayName("every cone size band is named in both languages")
    void everyConeSizeIsNamed() {
        for (ConeSize size : ConeSize.values()) {
            for (String locale : new String[]{ES, EN}) {
                String key = "tooltip.cortescombat.size." + size.id();
                assertTrue(read(locale).contains("\"" + key + "\""),
                        locale + " has no line for the band " + size.id() + ", and the tooltip "
                                + "prints the band's identifier verbatim when it has none");
            }
        }
    }

    /** Every right button that exists has a line in both languages. */
    @Test
    @DisplayName("every special is described in both languages")
    void everySpecialIsDescribed() {
        int described = 0;
        for (SpecialType special : SpecialType.values()) {
            if (!special.isPresent()) {
                continue;
            }
            described++;
            for (String locale : new String[]{ES, EN}) {
                String key = "tooltip.cortescombat.special." + special.id();
                assertTrue(read(locale).contains("\"" + key + "\""),
                        locale + " has no line for " + special.id() + ", so a weapon with that "
                                + "right button shows its key instead of saying what the button does");
            }
        }
        assertEquals(8, described, "the set of specials with a right button changed");
    }

    /**
     * The three iconic names survive in English, and the other two do not.
     *
     * <p>Both halves, because either alone passes for the wrong reason. Asserting only that
     * "Ropera" is still there would be satisfied by an English file that never translated
     * anything; asserting only that "Dagger" is there would be satisfied by a translation that
     * renamed all six and lost the three names the project decided to keep.</p>
     */
    @Test
    @DisplayName("three names are kept in English and two are translated")
    void theNameSplitIsTheOneThatWasDecided() {
        String english = read(EN);

        // Kept, and asserted as "contains" rather than "equals": the material prefix is in front,
        // so the full value is "Iron Ropera".
        assertTrue(english.contains("\"Iron Ropera\"")
                        && english.contains("\"Diamond Alabarda\""),
                "the ropera and the alabarda are the iconic names and stay in English");
        assertTrue(value(english, "item.cortescombat.arcabuz").equals("Arcabuz"),
                "and the arcabuz is its own name in both files");
        assertTrue(value(english, "item.cortescombat.firestone").equals("Firestone"),
                "and the firestone is an English word already, so it is identical in both");

        // Translated: these two are descriptive rather than named, and the English word is a
        // better name than a literal translation would be.
        assertTrue(english.contains("\"Iron Dagger\""), "the daga is a dagger in English");
        assertTrue(english.contains("\"Diamond War Hammer\""),
                "the martillo de guerra is a war hammer in English");

        // **And the shotel is not translated, which is the one that looks like a bug.**
        //
        // Both halves are exact rather than "contains", because the two files differ in word
        // order and not in the name: an assertion of `equals("Shotel")` failed here for a reason
        // worth writing down -- the value is "Shotel de Hierro", because the material is part of
        // the name and the test had forgotten it. Comparing the bare family name would have
        // passed for the wrong reason.
        assertEquals("Iron Shotel", value(english, "item.cortescombat.iron_shotel"),
                "the shotel is a Muslim term whose translation is a transliteration, and a "
                        + "transliteration of a Latin word is the word. The English file says Shotel");
        assertEquals("Shotel de Hierro", value(read(ES), "item.cortescombat.iron_shotel"),
                "and the Spanish file says the same name with its own word order");
    }

    /**
     * English puts the material in front, Spanish behind it.
     *
     * <p>The assertion that would have caught a name assembled by rearranging the Spanish one:
     * "Ropera de Iron" is a plausible-looking bug that no compiler and no reader of the enum would
     * notice, because both halves are spelled correctly.</p>
     */
    @Test
    @DisplayName("the English name puts the material first and the Spanish one behind")
    void theWordOrderIsTheLanguagesOwn() {
        WeaponFamily ropera = WeaponFamily.ROPERA;
        WeaponMaterial iron = WeaponMaterial.IRON;

        assertEquals("Ropera de Hierro", ropera.displayName(iron),
                "Spanish puts the material behind a de");
        assertEquals("Iron Ropera", ropera.displayNameEn(iron),
                "English puts the material in front, because it is an adjective and not a noun "
                        + "with a preposition");
        assertTrue(!ropera.displayNameEn(iron).contains(" de "),
                "an English name with a Spanish de in it is a translation rather than a name");
    }

    /** No language file may name an item that does not exist, or a recipe one that is not made. */
    @Test
    @DisplayName("every weapon in the catalogue is named in both languages")
    void everyWeaponIsNamed() {
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                String key = "item.cortescombat." + family.itemName(material);
                for (String locale : new String[]{ES, EN}) {
                    assertTrue(read(locale).contains("\"" + key + "\""),
                            locale + " has no name for " + key + ", so that weapon shows its key "
                                    + "in the hotbar");
                }
            }
        }
    }

    /**
     * And every family carries its own advancement line, one per family, in both languages.
     *
     * <p>The thirty used to share "Hecho a mano.", which is the same as saying nothing thirty
     * times. The recipe provider now derives the key from the enum
     * ({@code advancements.cortescombat.familia.<id>}), so a family added to
     * {@link WeaponFamily} without a story line in a language file lands here as a failure
     * naming the language and the key, instead of showing a raw key inside a toast.</p>
     */
    @Test
    @DisplayName("every family has its own advancement story line in both languages")
    void everyFamilyHasItsAdvancementLine() {
        for (WeaponFamily family : WeaponFamily.values()) {
            String key = "advancements.cortescombat.familia." + family.id();
            for (String locale : new String[]{ES, EN}) {
                assertTrue(read(locale).contains("\"" + key + "\""),
                        locale + " has no " + key + " -- the family's advancement would show "
                                + "the raw key in its toast");
            }
        }
    }

    /**
     * And the strings that go with the numbers exist in both.
     *
     * <p>The five format lines, listed by hand because they are five fixed strings and not an
     * enum: an accidental sixth would show up as a key on screen and nowhere else.</p>
     */
    @Test
    @DisplayName("every fixed tooltip line exists in both languages")
    void theFixedLinesExist() {
        for (String locale : new String[]{ES, EN}) {
            String json = read(locale);
            for (String key : new String[]{
                    "tooltip.cortescombat.damage",
                    "tooltip.cortescombat.charged_damage",
                    "tooltip.cortescombat.attack_speed",
                    "tooltip.cortescombat.range",
                    "tooltip.cortescombat.durability",
                    "advancements.cortescombat.weapon",
                    "advancements.cortescombat.arcabuz"}) {
                assertTrue(json.contains("\"" + key + "\""), locale + " has no " + key);
            }
        }
    }
}