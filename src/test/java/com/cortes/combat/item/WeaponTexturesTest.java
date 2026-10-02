package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * That every texture the catalogue promises is on disk.
 *
 * <p>This is the cheapest test in the project and it covers the most common way the thirty items
 * go wrong. A missing texture has no symptom until the client loads: the item renders as the
 * missing-model cube, in the hotbar, in game, and datagen itself reports success. It is a
 * "datagen passed and the thing is still broken", which is the exact shape of bug this project's
 * notes keep coming back to.</p>
 *
 * <p>It also pins the <em>path</em>. The textures used to live in a prototype's
 * {@code textures/items/sprites/weapons/}, which is not a path Minecraft looks in at all, and
 * thirty files sat there looking finished. A test that only asked "does a file with this name
 * exist somewhere" would have passed; this one asks for the exact file the game will load.</p>
 */
class WeaponTexturesTest {

    private static final Path TEXTURES =
            Path.of("src", "main", "resources", "assets", "cortescombat", "textures", "item");

    /** Where the game actually looks. Anything else is a file nobody will ever open. */
    private static final String GAMING_PATH = "textures/item";

    /**
     * Every name the catalogue claims, plus the two copper materials.
     *
     * <p>Read from {@link WeaponFamily} and not from the datagen provider on purpose: the provider
     * implements {@code DataProvider}, so naming it here drags {@code DataProvider} into a test
     * classpath that has no Minecraft in it, and the test stops compiling before it can fail
     * anything. Any list a test needs has to live below the Minecraft line.</p>
     */
    static Stream<String> everyItemName() {
        var names = new java.util.LinkedHashSet<>(WeaponFamily.allItemNames());
        names.add("copper_ingot");
        names.add("copper_nugget");
        // **The firestone, which is the one item here that is not a weapon.**
        //
        // It has a texture on disk and it is the whole price of an arcabuz shot, and this test
        // caught it as an orphan: the catalogue below is built from `WeaponFamily`, which is a list
        // of the thirty catalogue weapons, so anything registered outside a family is invisible to
        // it. A texture that nothing claims is exactly the state this whole file exists to prevent,
        // and the fix is to name it here rather than to loosen the test.
        names.add("firestone");
        return names.stream();
    }

    @ParameterizedTest
    @MethodSource("everyItemName")
    @DisplayName("the texture exists where Minecraft looks for it")
    void textureExists(String name) throws Exception {
        Path texture = TEXTURES.resolve(name + ".png");
        if (!Files.isRegularFile(texture)) {
            fail("missing texture " + texture.toAbsolutePath()
                    + " -- the game loads it from assets/cortescombat/" + GAMING_PATH
                    + "/" + name + ".png and shows the missing-model cube without it");
        }
        assertTrue(Files.size(texture) > 0, name + ".png is empty");
    }

    @Test
    @DisplayName("every tier of one family is drawn at the same size")
    void oneSizePerFamily() throws Exception {
        // This test was first written as "every texture is 16x16" and it failed on the six
        // halberds, which are 32x32. The art was right and the test was wrong: a polearm is a long
        // object and a sixteen pixel square cannot hold one, so the halberds get a taller canvas
        // on purpose and Minecraft scales the model either way.
        //
        // The invariant worth having is the narrower one: one size per family. A single tier
        // drawn at a different size from its siblings is invisible in a file listing and obvious
        // in the hotbar, where one item is twice the height of the five next to it.
        for (WeaponFamily family : WeaponFamily.values()) {
            String expected = null;
            for (WeaponMaterial material : family.materialsInOrder()) {
                var image = javax.imageio.ImageIO.read(
                        TEXTURES.resolve(family.itemName(material) + ".png").toFile());
                String size = image.getWidth() + "x" + image.getHeight();
                if (expected == null) {
                    expected = size;
                } else {
                    assertTrue(expected.equals(size),
                            family.id() + " mixes canvas sizes: " + material.id() + " is " + size
                                    + " and the family settled on " + expected);
                }
            }
            assertTrue(expected != null, family.id() + " has no materials at all");
        }
    }

    @Test
    @DisplayName("the whole set uses only two canvas sizes, and the long weapons own the big one")
    void onlyTwoSizesInTheWholeSet() throws Exception {
        // Four of the five families are 16x16 and the halberd is 32x32. A third size appearing
        // means somebody rescaled one texture and not its siblings, which is the mistake above
        // wearing a different hat.
        var sizes = new java.util.TreeSet<String>();
        everyItemName().forEach(name -> {
            try {
                var image = javax.imageio.ImageIO.read(TEXTURES.resolve(name + ".png").toFile());
                sizes.add(image.getWidth() + "x" + image.getHeight());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(sizes.equals(java.util.Set.of("16x16", "32x32")),
                "the set uses " + sizes + " and should use only 16x16 and 32x32");
    }

    @Test
    @DisplayName("the catalogue and the textures folder agree, in both directions")
    void noOrphanTextures() {
        var expected = everyItemName().collect(java.util.stream.Collectors.toSet());

        try (var files = Files.list(TEXTURES)) {
            // **Regular `.png` files only, and not every entry.**
            //
            // `textures/item/` is an atlas source directory, so it is now also where the arcabuz's
            // five fuse frames live -- under an `arcabuz_fuse/` subfolder. A subfolder is not an
            // item icon, and this test failed on it the first time with "texture arcabuz_fuse.png
            // has no item", which names neither what it found nor what it wanted.
            //
            // The distinction is the whole test: a top-level PNG here is an icon and must have an
            // item, and anything below the top level belongs to a model that is not an icon.
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString().replace(".png", ""))
                    .filter(name -> !expected.contains(name))
                    .forEach(name -> fail("texture " + name + ".png has no item: the catalogue says "
                            + expected.size() + " items and this is not one of them"));
        } catch (Exception e) {
            fail("could not list " + TEXTURES.toAbsolutePath() + ": " + e.getMessage());
        }
    }

    @Test
    @DisplayName("no texture is named after the weapon the ropera used to be called")
    void nothingIsNamedHalberd() {
        // The rename from rapier to ropera was thirty renames plus one leftover, and the leftover
        // was the stone one: it was a 32x32 prototype that had never been replaced. A name that
        // still says halberd is either that file or a recipe pointing at it.
        try (var files = Files.list(TEXTURES)) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.contains("halberd"))
                    .forEach(name -> fail(name + " still carries the old name"));
        } catch (Exception e) {
            fail("could not list " + TEXTURES.toAbsolutePath() + ": " + e.getMessage());
        }
    }

    @Test
    @DisplayName("every family is represented by a texture at every tier it claims")
    void everyClaimedCombinationExists() {
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                String name = family.itemName(material);
                assertTrue(Files.isRegularFile(TEXTURES.resolve(name + ".png")),
                        "the catalogue claims " + name + " and there is no texture for it");
            }
        }
    }
}
