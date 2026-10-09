package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod's own profile tags: the path, the wiring, and the file on disk.
 *
 * <h2>Why this file is three tests and not one</h2>
 *
 * <p>Because there are three separate ways for a profile to end up unselectable, and only one
 * of them is a compile error:</p>
 *
 * <ol>
 *   <li><b>A rename.</b> {@code tagPath()} moves, the generated file moves, nothing fails, and
 *       a pack writing the old path writes a tag nothing reads. A test that can reach the
 *       naming is the only thing that catches it, which is the whole reason the path lives on
 *       the pure enum instead of inside the class that turns it into a key.</li>
 *   <li><b>A missing file.</b> A profile added to the enum with no tag behind it is a mould with
 *       no door, and the failure is silent for exactly as long as nobody tries to select it.
 *       Walking the enum against the filesystem turns that into a red build.</li>
 *   <li><b>The read order.</b> If the vanilla tags ever go back to being asked first, the mod's
 *       own items are classified by a namespace we do not own again -- which is the bug this
 *       round closed, and it closes without a single test noticing, because everything still
 *       compiles and every item still resolves.</li>
 * </ol>
 *
 * <p>So the naming is asserted directly, the files are walked, and the source of the resolver is
 * read. All three in one file because they are one fact: <b>these tags are ours and they are
 * the door.</b></p>
 */
class WeaponProfileTagsTest {

    /** The folder the generator writes into, relative to the project root. */
    private static final Path PROFILE_TAGS = Path.of("src", "generated", "resources", "data",
            "cortescombat", "tags", "item", "profile");

    @Test
    @DisplayName("the path is profile/<id>, and that is the public name")
    void thePathIsThePublicName() {
        // The exact prefix, not a prefix check: this string is what a datapack writes into and
        // what the resolver reads, so a rename has to break a test rather than a pack.
        assertEquals("profile/halberd", WeaponProfile.HALBERD.tagPath());
        assertEquals("profile/none", WeaponProfile.NONE.tagPath());
        assertEquals("profile/arcabuz", WeaponProfile.ARCABUZ.tagPath());
    }

    /**
     * Every profile has a path, all fourteen are distinct, and none of them is malformed.
     *
     * <p><b>Walks the enum rather than listing it</b>, which is the lesson {@code
     * LanguageFilesTest} taught: a hand-written list of the same fourteen is a second thing to
     * forget, and the day someone adds a fifteenth the tests stay green while the new mould has
     * no tag.</p>
     */
    @Test
    @DisplayName("every profile has a path, and no two share one")
    void everyProfileHasAPath() {
        Set<String> seen = new HashSet<>();

        for (WeaponProfile profile : WeaponProfile.values()) {
            String path = profile.tagPath();
            assertTrue(seen.add(path),
                    "two profiles claim the tag " + path + ", so one of them cannot be selected");
            assertTrue(path.startsWith("profile/"),
                    profile + " is outside the profile folder: " + path);
            assertEquals("profile/" + profile.id(), path,
                    profile + "'s path is built from its id, or the file and the key disagree");
            assertFalse(path.contains("//"), "a double slash is a path nothing resolves: " + path);
        }

        // Fourteen today. The number is asserted because it is the whole point of walking: a
        // fifteenth profile that somehow produced no new path would show up here as fourteen.
        assertEquals(WeaponProfile.values().length, seen.size(),
                "one path per profile, no exceptions");
    }

    /**
     * The file for every profile exists, and the four with no member are empty on purpose.
     *
     * <p><b>This is the guard that makes the tag a door rather than a name.</b> The provider
     * registers a tag per profile value, so a profile added to the enum without a file is a
     * mould that exists in the code and has nothing to select it. It fails here instead.</p>
     */
    @Test
    @DisplayName("every profile has a tag file on disk, and the empty ones are empty on purpose")
    void everyProfileHasAFile() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            Path file = PROFILE_TAGS.resolve(profile.id() + ".json");
            assertTrue(Files.isRegularFile(file),
                    "no tag file for " + profile + " at " + file + ". The mod owns a mould it "
                            + "cannot select, and nothing else in the build will say so");
            assertTrue(read(file).contains("\"values\""),
                    profile + "'s file has no values array, so it is not a tag file at all");
        }

        // The four with no item of ours. **A deliberate empty, and the opposite of the
        // `stone_tool_materials` bug** in the same provider, where calling `tag(...)` for a tag
        // nothing filled was the mistake. Here the empty file is the specification: it is the
        // path somebody writes into later, and an empty tag is a tag that exists.
        for (WeaponProfile empty : new WeaponProfile[]{
                WeaponProfile.NONE, WeaponProfile.UNARMED, WeaponProfile.MACE,
                WeaponProfile.TRIDENT}) {
            assertFalse(read(PROFILE_TAGS.resolve(empty.id() + ".json"))
                            .contains("cortescombat:"),
                    "the " + empty + " tag was supposed to have no member of ours");
        }

        // And the ones that do. These two are the load-bearing members: the copper tools carry
        // no component, so if they fell out of the tag they would resolve to NONE and stop
        // fighting -- which is the bug this round closed, arriving by the back door.
        assertTrue(read(PROFILE_TAGS.resolve("sword.json")).contains("cortescombat:copper_sword"),
                "the copper sword is classified by this tag and nothing else");
        assertTrue(read(PROFILE_TAGS.resolve("ropera.json")).contains("cortescombat:iron_ropera"),
                "the catalogue's weapons are the safety net behind the component");
    }

    /**
     * Ours is asked before vanilla's, and vanilla's is still asked.
     *
     * <p>Source read, the same way {@code WeaponKindsTest} reads {@code SwingInputHandler},
     * because {@code deriveProfile} takes an {@code ItemStack} and a test cannot build one. The
     * order is the fact: if the loop over {@code VANILLA_TAGS} came back in front, every one of
     * the above stays green and the mod's own items go back to being classified in a namespace
     * we do not own.</p>
     */
    @Test
    @DisplayName("the resolver asks ours first, and still asks vanilla's")
    void oursIsAskedFirst() {
        String stats = readSource(Path.of("src", "main", "java", "com", "cortes", "combat",
                "attack", "WeaponStats.java"));

        int ours = stats.indexOf("CORTES_PROFILE_TAGS.entrySet()");
        int vanilla = stats.indexOf("VANILLA_TAGS.entrySet()");

        assertTrue(ours > 0, "deriveProfile has to walk the mod's own tags");
        assertTrue(vanilla > ours,
                "**vanilla's tags are the fallback, not the classification.** Asked first, the "
                        + "mod's own items are decided in a namespace we do not own -- which is "
                        + "what this round closed");
        assertTrue(stats.contains("VANILLA_TAGS.put(itemTag(\"swords\")"),
                "and the compat layer is still there. Dropping it would send every weapon of "
                        + "every mod we have never heard of back to vanilla with no message");
    }

    /**
     * The generator walks the enum instead of listing it.
     *
     * <p>A hand-written list of fourteen {@code tag(...)} calls is a second thing to forget, and
     * the failure is invisible for exactly as long as nobody selects the missing mould.</p>
     */
    @Test
    @DisplayName("the generator walks the profiles rather than listing them")
    void theGeneratorWalksTheEnum() {
        String provider = readSource(Path.of("src", "main", "java", "com", "cortes", "combat",
                "data", "CortesItemTagProvider.java"));

        assertTrue(provider.contains("for (WeaponProfile profile : WeaponProfile.values())"),
                "one tag per profile, from the enum, or a new profile ships with no door");
        assertTrue(provider.contains("WeaponProfileTags.profileTag(profile)"),
                "and the key comes from the pure half, not from a path written here a second "
                        + "time");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    private static String readSource(Path file) {
        return read(file);
    }
}
