package com.cortes.combat.core.weapon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The category line: the taxonomy, its boundaries, and that both languages name it.
 *
 * <h2>Why the boundaries and not the weapons</h2>
 *
 * <p>A test that asserted "the halberd is long" would pass forever while the boundary drifted past
 * it. The interesting assertions are the edges -- five blocks and not three point six, nineteen
 * ticks and not eighteen, three blocks exactly -- because those are the numbers somebody will move
 * and the ones that decide which weapon changes category when they do.</p>
 *
 * <h2>And why the languages are checked here</h2>
 *
 * <p>Because a category with no name prints its identifier: "tooltip.cortescombat.category.ranged"
 * on screen, in every language, and the enum compiles and the line is emitted and nothing says so.
 * It is the same shape as the six cone bands, and it is checked the same way -- by counting the key
 * in the provider, once per language, so a key added to Spanish and forgotten in English fails
 * rather than shipping half-translated.</p>
 */
class WeaponCategoryTest {

    @Test
    @DisplayName("ranged beats everything, because the arcabuz is short and ranged at once")
    void rangedWins() {
        // The arcabuz's bayonet is two point six blocks, which on reach alone is a short weapon --
        // and it is not one. The right button's own cone is the thing that decides, and it decides
        // first, because a gun is a gun.
        assertEquals(WeaponCategory.RANGED,
                WeaponCategory.forWeapon(WeaponProfile.ARCABUZ, new ReachProfile(2.6F, 46.0F, 35.0F),
                        99, true));
    }

    @Test
    @DisplayName("long starts at five blocks, and the espada is on the other side of it")
    void longStartsAtFive() {
        assertEquals(WeaponCategory.LONG,
                WeaponCategory.forWeapon(WeaponProfile.HALBERD, new ReachProfile(5.0F, 60.0F, 35.0F),
                        20, false),
                "**exactly five is long.** The boundary is inclusive, so the halberd's own reach "
                        + "lands on the long side and not one step under it");
        assertEquals(WeaponCategory.MEDIUM,
                WeaponCategory.forWeapon(WeaponProfile.SWORD, new ReachProfile(3.6F, 35.0F, 30.0F),
                        10, false),
                "and the espada, at three point six, is a medium weapon -- which is the decision "
                        + "the boundary makes: the sword reaches further than anything else in "
                        + "vanilla and is still not a long weapon here");
    }

    @Test
    @DisplayName("heavy is slow AND long, because the pala is slower than the warhammer")
    void heavyNeedsBoth() {
        ReachProfile mid = new ReachProfile(3.4F, 36.0F, 36.0F);
        assertEquals(WeaponCategory.HEAVY,
                WeaponCategory.forWeapon(WeaponProfile.WARHAMMER, mid, WeaponCategory.HEAVY_TICKS,
                        false),
                "**exactly nineteen is heavy**, which is the warhammer's own interval");
        assertEquals(WeaponCategory.MEDIUM,
                WeaponCategory.forWeapon(WeaponProfile.WARHAMMER, mid, WeaponCategory.HEAVY_TICKS - 1,
                        false),
                "and eighteen is not, or the boundary is a different number than it says");

        // **The case that made this two questions instead of one.** Vanilla's shovel swings every
        // twenty ticks, which is slower than the warhammer -- so an interval-only rule called the
        // pala heavy and the warhammer medium, backwards in both directions at once.
        assertEquals(WeaponCategory.MEDIUM,
                WeaponCategory.forWeapon(WeaponProfile.SHOVEL, new ReachProfile(3.0F, 35.0F, 30.0F),
                        20, false),
                "**the pala is slower than the martillo and is not a heavy weapon.** It is a tool "
                        + "that fights; the warhammer is a weapon that happens to be slow");
    }

    @Test
    @DisplayName("short is under three blocks, and the pala at three is not")
    void shortEndsAtThree() {
        assertEquals(WeaponCategory.SHORT,
                WeaponCategory.forWeapon(WeaponProfile.SABRE, new ReachProfile(2.25F, 75.0F, 25.0F),
                        8, false),
                "the sabre is the shortest weapon in the game and the one the category exists for");
        assertEquals(WeaponCategory.MEDIUM,
                WeaponCategory.forWeapon(WeaponProfile.SHOVEL, new ReachProfile(3.0F, 35.0F, 30.0F),
                        20, false),
                "**and three blocks exactly is medium.** The pala's own reach is the boundary, so "
                        + "the shortest of the ordinary weapons is the first of the medium ones");
    }

    @Test
    @DisplayName("the fists are their own category, and every profile answers something")
    void theFistsAndTheWholeTable() {
        assertEquals(WeaponCategory.NATURAL,
                WeaponCategory.forWeapon(WeaponProfile.UNARMED, new ReachProfile(2.5F, 28.0F, 22.0F),
                        4, false),
                "the fists have no item and are still a weapon in this design");

        for (WeaponProfile profile : WeaponProfile.values()) {
            ReachProfile reach = com.cortes.combat.item.WeaponTables.reach(profile);
            assertNotNull(WeaponCategory.forWeapon(profile, reach, 20, false),
                    profile + " has no category, so its line would print nothing");
        }
    }

    @Test
    @DisplayName("both languages name every category, or the key shows on screen")
    void bothLanguagesNameEveryCategory() throws IOException {
        Path provider = Path.of("src", "main", "java", "com", "cortes", "combat", "data",
                "CortesLanguageProvider.java");
        String source = Files.readString(provider, StandardCharsets.UTF_8);

        for (WeaponCategory category : WeaponCategory.values()) {
            String key = "tooltip.cortescombat.category." + category.id();
            int count = source.split(Pattern.quote(key), -1).length - 1;
            assertEquals(2, count,
                    "**" + key + " aparece " + count + " veces y tienen que ser dos**, una por "
                            + "idioma. Con una sola, el idioma que falta imprime el identificador "
                            + "en pantalla: la linea se emite, el enum compila y nada avisa");
        }
    }
}
