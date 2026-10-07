package com.cortes.combat.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every folder we write into is one the game reads.
 *
 * <h2>The bug this exists for, and it is the worst one this project has shipped</h2>
 *
 * <p>Thirty-four recipes and thirty-four advancements were generated into
 * {@code data/cortescombat/recipes/} and {@code data/cortescombat/advancements/}. <b>1.21 renamed
 * every datapack folder to the singular</b> -- the vanilla jar carries {@code recipe/} and
 * {@code advancement/} and the plural of neither -- so the loader never looked at one of them. Not
 * a single recipe or advancement this mod has ever written has been read by the game.</p>
 *
 * <p>And it was invisible in the way this project keeps re-learning: <b>the file is in the jar, it
 * parses, and the game never looks.</b> The log said {@code Loaded 1290 recipes} and
 * {@code Loaded 1399 advancements}, which read as proof everything had loaded -- and those are
 * <em>exactly</em> vanilla's own counts, because ours were never in the list. The number was the
 * evidence and it was read as the opposite.</p>
 *
 * <h2>Why the list below is a copy, and why that is the right copy</h2>
 *
 * <p>It is every top-level folder under {@code data/minecraft/} in
 * {@code minecraft_1.21.1_client.jar}, read out of it rather than remembered. It is a
 * <em>contract</em> with the version -- the names the loader answers to -- and the failure mode of
 * it going stale is a test that fails on a version bump, which is where someone should look. The
 * alternative, reading the jar from the test, would pin a Gradle cache path instead of a fact.</p>
 */
class DataFolderNamesTest {

    /** Every {@code data/minecraft/<folder>/} in the 1.21.1 client jar, verbatim. */
    private static final Set<String> READ_BY_THE_GAME = Set.of(
            "advancement", "banner_pattern", "chat_type", "damage_type", "datapacks",
            "dimension_type", "enchantment", "enchantment_provider", "jukebox_song", "loot_table",
            "painting_variant", "recipe", "structure", "tags", "trim_material", "trim_pattern",
            "wolf_variant", "worldgen");

    private static final Path OUR_DATA =
            Path.of("src", "generated", "resources", "data", "cortescombat");

    /**
     * No folder name we generate is one the loader ignores.
     *
     * <p>Walks the generated tree, not a written list, so a new provider writing into a new folder
     * is checked without anyone remembering to add it here.</p>
     */
    @Test
    @DisplayName("no folder we write is a name the loader ignores")
    void everyFolderIsReadByTheGame() throws IOException {
        List<Path> dirs;
        try (var stream = Files.list(OUR_DATA)) {
            dirs = stream.filter(Files::isDirectory).toList();
        }

        assertTrue(!dirs.isEmpty(), "we have to write something under data/cortescombat, or this "
                + "test is about nothing");
        for (Path dir : dirs) {
            String name = dir.getFileName().toString();
            assertTrue(READ_BY_THE_GAME.contains(name),
                    "**`" + name + "` is not a folder 1.21.1 reads.** The list above is every "
                            + "top-level folder under `data/minecraft/` in the client jar. 1.21 "
                            + "renamed the plural ones -- recipes to recipe, advancements to "
                            + "advancement, loot_tables to loot_table -- and a folder the loader "
                            + "does not read is a silent nothing: the file is in the jar, it parses, "
                            + "and the game never looks. Thirty-four recipes and thirty-four "
                            + "advancements shipped that way and `Loaded 1290 recipes` was vanilla's "
                            + "own count, which is why it read as proof they had loaded");
        }
    }

    /**
     * And the two we do use are spelled the way the game spells them, with no plural left behind.
     *
     * <p>The first half would catch a rename; the second catches the stale folder, which is the
     * half that actually bit -- the files were there, in a name the loader skips.</p>
     */
    @Test
    @DisplayName("our recipes and advancements are in `recipe` and `advancement`, and no plural is left")
    void theFoldersWeUseArePresentAndSingular() {
        assertTrue(Files.isDirectory(OUR_DATA.resolve("recipe")),
                "our recipes live in `recipe`, singular, or they do not load at all");
        assertTrue(Files.isDirectory(OUR_DATA.resolve("advancement")),
                "and our advancements in `advancement`, singular -- without them a recipe never "
                        + "enters the book even when the recipe itself is fine");
        assertTrue(!Files.exists(OUR_DATA.resolve("recipes")),
                "**and there must be no `recipes` left.** A stale plural folder is the exact bug: "
                        + "files in the jar, nothing loaded, no error anywhere");
        assertTrue(!Files.exists(OUR_DATA.resolve("advancements")),
                "nor `advancements`");
    }
}
