package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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

        // **And the copper set, which is neither a weapon nor an ingredient.** Five tools and four
        // armour pieces, all with a texture on disk and none of them in WeaponFamily -- so without
        // this line the audit would call every one of them an orphan. Read from CopperGear rather
        // than typed, because that is the same list the two registries build their items from.
        names.addAll(com.cortes.combat.core.gear.CopperGear.ALL_NAMES);

        // **And NOT the arcabuz, which looks like it belongs here and does not.**
        //
        // It is registered by hand like the firestone, so the catalogue cannot see it, and it has a
        // hand-drawn PNG -- so the obvious move was to add it and be done. That put a 64x320
        // hand-drawn model into an audit written about icons: "four families at 16x16 and the
        // halberd at 32x32, and a third size means somebody rescaled one texture and not its
        // siblings."
        //
        // The arcabuz's own texture is not an icon and never was. It is `arcabuz_fuse_0`, the top
        // frame of a five frame strip, under `textures/item/arcabuz_fuse/` -- below the top level,
        // so this test already skips it for the reason it skips a subfolder. The strip that lives
        // beside it is the fuse, drawn by a model that is only selected while a fuse is lit.
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
            // **The arcabuz is not in here, and that is the correct answer, not an exemption.**
            //
            // This audit is about the thirty catalogue weapons: four families at 16x16 and the
            // halberd at 32x32, and a third size means somebody rescaled one texture and not its
            // siblings. The arcabuz is a hand-drawn 64x320 model and its texture is the top frame of
            // the fuse strip, under `textures/item/arcabuz_fuse/` -- below the top level of this
            // folder, so `everyItemName()` never reaches it and nothing has to be excluded here.
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
            // **A PNG here must be drawn by a MODEL, and not necessarily by an ITEM.**
            //
            // The rule used to be "a top-level PNG here is an item icon", which held until the
            // arcabuz needed a flat sprite for the inventory: a two block musket drawn as eight 3D
            // elements is a grey rectangle in a sixteen pixel slot, so `arcabuz_icon.png` became its
            // own model file and no item of that name exists. The playtest asked for the sprite and
            // this test then failed on the very file that answered it.
            //
            // So the question moves one level down, to the one the atlas actually cares about:
            // does some model name this texture? A texture nothing references is the real defect --
            // it is stitched into the atlas, sixteen bytes a pixel, for nothing.
            //
            // **And the fuse frames still pass for the reason they always did:** they live under
            // `arcabuz_fuse/`, which is a directory rather than a top-level file, so `isRegularFile`
            // is false and this loop never sees it. That distinction is not a detail -- the first
            // version of this test failed on the subfolder with "texture arcabuz_fuse.png has no
            // item", naming neither what it found nor what it wanted.
            List<String> drawn = texturesNamedByModels();
            List<String> orphans = new ArrayList<>();
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString().replace(".png", ""))
                    .filter(name -> !drawn.contains("cortescombat:item/" + name))
                    .forEach(orphans::add);

            assertTrue(orphans.isEmpty(),
                    "these sit in textures/item/, which the atlas stitches, and no model file "
                            + "names them, so they are paid for and never drawn: " + orphans);
        } catch (Exception e) {
            fail("could not list " + TEXTURES.toAbsolutePath() + ": " + e.getMessage());
        }
    }

    /**
     * Where the models live, in both places they live.
     *
     * <p><b>Two roots, and this was a bug in the original version of this test.</b> It asked "is
     * there an item of this name?" and compared against the catalogue, so it never had to read a
     * model file and never noticed that thirty of the thirty two models are written by datagen into
     * {@code src/generated} while only eight are hand drawn in {@code src/main}. Rewriting the
     * question as "does a model name this texture?" forced the paths to be read, and the first run
     * reported all thirty two textures as orphans -- because it was looking in one directory and
     * the answers were in the other.</p>
     *
     * <p>The game loads both, in the order its run config states: generated first, then the ones
     * under {@code src/main} as overrides. So the test has to read both, or it is asking about half
     * the mod.</p>
     *
     * <p><b>And under both namespaces.</b> The third root is the one that surprised me: the copper
     * ingot has no item of its own -- {@code ModItems} says so at length, it is a reskin of
     * vanilla's -- so its model lives at {@code assets/minecraft/models/item/copper_ingot.json} and
     * its texture at ours. Reading only {@code assets/cortescombat} reported it as an orphan on the
     * first run of the corrected test, which is what a hand written path list does: it encodes the
     * layout you expected rather than the one there is.</p>
     */
    private static final List<java.nio.file.Path> MODEL_ROOTS = List.of(
            java.nio.file.Path.of("src", "main", "resources", "assets", "cortescombat", "models"),
            java.nio.file.Path.of("src", "main", "resources", "assets", "minecraft", "models"),
            java.nio.file.Path.of("src", "generated", "resources", "assets", "cortescombat",
                    "models"));

    /**
     * Every texture path any model in this mod names, in the {@code namespace:path} form.
     *
     * <p><b>Text and not a JSON parser</b>, for the rule the whole project follows: {@code src/test}
     * reaches nothing that belongs to Minecraft, and a JSON library would either come from there or
     * be a new dependency. It looks for one shape and that is enough.
     *
     * <p><b>It reads bare paths too</b> -- {@code "item/x"} as well as {@code "cortescombat:item/x"} --
     * because a model may write either. A model that writes a bare path is naming a vanilla texture
     * unless it is under our namespace, so those come back as {@code minecraft:item/x} and are not
     * mistaken for one of ours.
     */
    private static List<String> texturesNamedByModels() {
        List<String> drawn = new ArrayList<>();
        for (java.nio.file.Path root : MODEL_ROOTS) {
            if (!Files.isDirectory(root)) {
                fail("no models at " + root.toAbsolutePath() + ", so this test is asserting "
                        + "nothing about anything");
                return drawn;
            }
            try (var models = Files.walk(root)) {
                models.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    try {
                        String json = Files.readString(p, StandardCharsets.UTF_8);
                        java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("\"([a-z0-9_.-]+:[a-z0-9_/.-]+|[a-z0-9_]+/[a-z0-9_/.-]+)\"")
                                .matcher(json);
                        while (m.find()) {
                            String value = m.group(1);
                            drawn.add(value.contains(":") ? value : "minecraft:" + value);
                        }
                    } catch (java.io.IOException e) {
                        fail("could not read " + p + ": " + e.getMessage());
                    }
                });
            } catch (java.io.IOException e) {
                fail("could not walk " + root.toAbsolutePath() + ": " + e.getMessage());
            }
        }
        return drawn;
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
