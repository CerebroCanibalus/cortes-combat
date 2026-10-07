package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That {@link WeaponKinds} draws the line where the design says it does, and that something asks.
 *
 * <h2>Why every profile is asserted by name</h2>
 *
 * <p>Because "a set of profiles fights" is only a fact if you know which ones, and a set that
 * grows by accident is a set nobody decided.</p>
 */
class WeaponKindsTest {

    /**
     * The whole set, by name.
     *
     * <p><b>The two absences are the interesting part.</b> {@code NONE} is what every unrecognised
     * item resolves to, so if it were in the set a block would swing; {@code PICKAXE} is the one
     * deliberate exclusion, and it is a decision rather than an oversight.</p>
     */
    @Test
    @DisplayName("the profiles that fight, named one by one")
    void theProfilesThatFight() {
        for (WeaponProfile fights : new WeaponProfile[]{
                WeaponProfile.UNARMED, WeaponProfile.SWORD, WeaponProfile.AXE,
                WeaponProfile.SHOVEL, WeaponProfile.MACE, WeaponProfile.TRIDENT,
                WeaponProfile.ROPERA, WeaponProfile.HALBERD, WeaponProfile.WARHAMMER,
                WeaponProfile.DAGGER, WeaponProfile.SABRE, WeaponProfile.ARCABUZ}) {
            assertTrue(WeaponKinds.fights(fights), fights + " is meant to fight");
        }

        for (WeaponProfile notAWeapon : new WeaponProfile[]{
                WeaponProfile.NONE, WeaponProfile.PICKAXE}) {
            assertFalse(WeaponKinds.fights(notAWeapon),
                    notAWeapon + " does not fight. **NONE is what an unrecognised item resolves "
                            + "to**, so a block or a potion with it would otherwise swing; and the "
                            + "pickaxe is a deliberate exclusion, not an oversight");
        }
    }

    /**
     * Everything the project declared has an opinion, and both ways of asking agree.
     *
     * <p><b>Walks the enum</b>, the same lesson as {@code LanguageFilesTest}. A hand-written list
     * of the same thirteen is a second thing to forget: the day someone adds a weapon and does not
     * add it here, nothing fails &mdash; the weapon simply does not fight, and the report is "the
     * new sword does nothing".</p>
     */
    @Test
    @DisplayName("every profile has an answer, and the two ways of asking agree")
    void everyProfileHasAnAnswer() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            assertEquals(WeaponKinds.combatProfiles().contains(profile), WeaponKinds.fights(profile),
                    "the set and the question disagree for " + profile);
        }
        assertEquals(13, WeaponKinds.combatProfiles().size(),
                "thirteen fight. **The hoe is the one that was added late**: its "
                        + "profile, all four table rows and the tag existed, and it was still not "
                        + "in this list -- so a profile can be complete everywhere else and still "
                        + "not fight. The named test above needs the new name in it");
    }

    /** Null is an answer, not a crash. */
    @Test
    @DisplayName("no profile at all is not a weapon")
    void noProfileIsNotAWeapon() {
        assertFalse(WeaponKinds.fights(null),
                "an item that resolved to nothing is not a weapon. Without this the caller would "
                        + "have to null-check at every use, and one forgotten check is an item that "
                        + "swings because a component was missing");
    }

    /**
     * The set handed out is a copy, not the one that decides.
     *
     * <p><b>Returning the mutable set would make this a second place where the line can be
     * moved</b>, one that no test reads and that nobody remembers.</p>
     */
    @Test
    @DisplayName("the set handed out is a copy, not the one that decides")
    void theSetHandedOutIsACopy() {
        var handed = WeaponKinds.combatProfiles();

        assertFalse(handed.contains(WeaponProfile.PICKAXE),
                "sanity: the pickaxe is not in it to begin with");
        assertEquals(13, handed.size(), "and the copy has the same thirteen");

        // **The first version of this asserted `handed.add(...) == false || true`, which is true
        // whatever happens.** I wrote it because asserting that a set is immutable sounded like it
        // needed no thought. It needed one: the answer is that it throws.
        assertThrows(UnsupportedOperationException.class,
                () -> handed.add(WeaponProfile.PICKAXE),
                "adding to the copy throws, so it is not the set this class decides with");
    }

    /**
     * The set is actually asked, at the front of the press.
     *
     * <p><b>This file can be perfect and the gate can still be gone.</b> {@code press()} takes a
     * {@code Minecraft}, so none of the behaviour above is reachable from a test, and deleting one
     * line from it compiles, keeps every assertion green, and puts a block back in combat. That is
     * the shape this project has paid for three times: a component in place and nothing drawing
     * it.</p>
     *
     * <p>So it reads the source, like the other wiring tests. Brittle by design: the two ways of
     * breaking are a false positive when the file is reformatted and an invisible bug when it is
     * not checked, and the first one is cheap.</p>
     */
    @Test
    @DisplayName("the press actually asks, and it asks first")
    void thePressAsksTheQuestion() {
        String press = readSource(Path.of("src", "main", "java", "com", "cortes", "combat",
                "client", "SwingInputHandler.java"));

        assertTrue(press.contains("WeaponKinds.fights("),
                "**press() has to ask it.** Everything below the question is about whether there "
                        + "is a target in the cone; nothing else asks what is in the hand, so "
                        + "without this a block and a potion swing and hit at three blocks");
        assertTrue(press.contains("no es un arma"),
                "and it has to say so in the debug line, because /cc input prints the gate that "
                        + "refused a press, and a refusal with no name is the one report nobody "
                        + "can use");

        // **And before the screen check**, which is an order that looks arbitrary.
        int asks = press.indexOf("WeaponKinds.fights(");
        int screen = press.indexOf("if (mc.screen != null) {");
        assertTrue(asks > 0 && screen > asks,
                "the question comes before the screen check. A screen swallows the click either "
                        + "way, so the order decides nothing about the game -- it decides which "
                        + "line the debug print names, and that one is the more useful of the two "
                        + "when both are true");
    }

    /**
     * The tooltip asks the same question the click asks.
     *
     * <p><b>The second door, and it was the wrong one for two rounds.</b> {@code CombatTooltipHandler}
     * gated on {@code profile() != NONE}, which was the same question until the rework decided it
     * is not: a pickaxe has a profile, so it passed and printed a reach and a cone for a profile
     * that no longer swings. The player reads a number off an item and believes it, so this is the
     * door where the mistake is visible rather than latent.</p>
     *
     * <p>Asserted as wiring, because {@code showsTooltip} is private and the class is a
     * subscriber. What matters is that the two call sites agree, and the way to say that without
     * reaching into a client class is to look for the one question in both.</p>
     */
    @Test
    @DisplayName("the tooltip gate asks it too, not whether the profile is NONE")
    void theTooltipAsksTheSameQuestion() {
        String tooltip = readSource(Path.of("src", "main", "java", "com", "cortes", "combat",
                "client", "CombatTooltipHandler.java"));

        assertTrue(tooltip.contains("WeaponKinds.fights("),
                "**showsTooltip has to ask fights()**. `profile() != NONE` is a different question "
                        + "and the difference is exactly one item: the pickaxe. It has a profile, "
                        + "so it passed, and it printed a reach and a cone for a tool that no "
                        + "longer swings -- and the player reads a number off an item and believes it");
        assertFalse(tooltip.contains("profile() != WeaponProfile.NONE"),
                "and the old question must be gone. Both at once would be two doors that answer "
                        + "differently, which is the shape this file already caught twice");
    }

    private static String readSource(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}