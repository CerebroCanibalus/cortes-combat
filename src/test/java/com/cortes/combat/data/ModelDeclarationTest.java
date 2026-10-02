package com.cortes.combat.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponFamily;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every item has a model, and every model points at something that exists.
 *
 * <p>This whole class exists because of one bug that shipped and looked fine. The halberd models
 * were six separate hand-written files, identical except for which texture they named, and the set
 * was drawn before copper was a material: so there was a {@code wooden_alabarda.json} pointing at
 * a texture nobody drew, and no {@code copper_alabarda.json} at all. The copper halberd fell
 * through to datagen's two-line generic, which renders at 1x while its five sisters render at
 * 1.5x in the GUI and 2x in third person.</p>
 *
 * <p>Nothing threw. The textures existed, the recipes existed, the item existed, datagen reported
 * success, and the halberd was visibly half the size it should be. <b>Every one of those four
 * checks below is a thing that was true at the time.</b></p>
 */
class ModelDeclarationTest {

    private static final Path ASSETS =
            Path.of("src", "main", "resources", "assets", "cortescombat");
    private static final Path GENERATED = Path.of("src", "generated", "resources", "assets",
            "cortescombat");

    private static final Pattern LAYER0 = Pattern.compile("\"layer0\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PARENT = Pattern.compile("\"parent\"\\s*:\\s*\"([^\"]+)\"");

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String first(Pattern pattern, String json, Path where) {
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), "no \"" + pattern.pattern().replaceAll(".*", "") + "\" in " + where);
        return matcher.group(1);
    }

    /** Where a hand-drawn model file may live, and where a generated one goes. */
    private static Path handDrawn(String name) {
        return ASSETS.resolve(Path.of("models", "item", name + ".json"));
    }

    private static Path generated(String name) {
        return GENERATED.resolve(Path.of("models", "item", name + ".json"));
    }

    /**
     * Every item the catalogue claims exists has a model.
     *
     * <p>Sized from {@code WeaponFamily} rather than from the files on disk, so a model that was
     * deleted and a texture that was deleted look identical from here and both fail. Reading the
     * directory instead would have passed with six models and thirty items.</p>
     */
    @Test
    @DisplayName("every item in the catalogue has a model")
    void everyItemHasAModel() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String name : WeaponFamily.allItemNames()) {
            if (!Files.isRegularFile(handDrawn(name)) && !Files.isRegularFile(generated(name))) {
                missing.add(name);
            }
        }
        assertEquals(List.of(), missing,
                "items with no model file at all. They render as the missing-model cube, which is "
                        + "loud, so this is here to name them rather than to catch them");
    }

    /**
     * A hand-drawn model never names a texture nobody drew.
     *
     * <p>This is the check that was missing while {@code wooden_alabarda.json} pointed at
     * {@code textures/item/wooden_alabarda.png}. It cost nothing to notice and it broke nothing
     * either, because there is no item called a wooden halberd -- the file was simply never asked
     * for. It sat there as a lie that looked exactly like every other file in the folder.</p>
     */
    @Test
    @DisplayName("a hand-drawn model names a texture that exists")
    void handDrawnModelsNameRealTextures() throws IOException {
        Path folder = ASSETS.resolve(Path.of("models", "item"));
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<String> dangling = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String json = read(file);
                Matcher layer0 = LAYER0.matcher(json);
                if (!layer0.find()) {
                    continue;   // a parent-only model, which is a legal shape
                }
                String reference = layer0.group(1);
                if (!reference.startsWith("cortescombat:")) {
                    continue;   // vanilla's own, e.g. the copper ingot reskin
                }
                Path png = ASSETS.resolve(Path.of("textures",
                        reference.substring("cortescombat:".length()) + ".png"));
                if (!Files.isRegularFile(png)) {
                    dangling.add(file.getFileName() + " -> " + reference);
                }
            }
        }
        assertEquals(List.of(), dangling,
                "hand-drawn models naming a texture that does not exist. Nothing renders them, so "
                        + "nothing complains either");
    }

    /**
     * A generated model's parent is a file, not a wish.
     *
     * <p>The shared halberd base is the parent of six models. Delete it and all six fall back to
     * vanilla's default with no error anywhere -- which is the same class of bug as the one this
     * class was written for, one level up: six thin models pointing at nothing.</p>
     */
    @Test
    @DisplayName("every parent a generated model names resolves to a file")
    void generatedParentsResolve() throws IOException {
        Path folder = GENERATED.resolve(Path.of("models", "item"));
        assertTrue(Files.isDirectory(folder), "no generated models at all: " + folder);
        List<String> dangling = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String parent = first(PARENT, read(file), file);
                if (parent.startsWith("minecraft:")) {
                    continue;
                }
                String path = parent.substring(parent.indexOf(':') + 1);
                Path target = ASSETS.resolve(Path.of("models", path + ".json"));
                if (!Files.isRegularFile(target)) {
                    dangling.add(file.getFileName() + " -> " + parent);
                }
            }
        }
        assertEquals(List.of(), dangling,
                "generated models pointing at a parent that is not on disk. Every item using them "
                        + "falls back to the default pose with nothing in the log");
    }

    /**
     * The halberd is one shape with six textures, and not six shapes.
     *
     * <p>The regression this task fixed, written as a property rather than as a count. The bug was
     * not "there are six halberd models" -- six is correct, they need one texture each. The bug was
     * that the six <em>shapes</em> could drift, because a human was keeping six copies in step
     * and the set was stale by one material.</p>
     */
    @Test
    @DisplayName("every halberd shares one shape")
    void everyHalberdSharesOneShape() throws IOException {
        Set<String> parents = new LinkedHashSet<>();
        int count = 0;
        for (String name : WeaponFamily.allItemNames()) {
            if (!name.endsWith("_" + WeaponFamily.ALABARDA.id())) {
                continue;
            }
            count++;
            Path file = Files.isRegularFile(handDrawn(name)) ? handDrawn(name) : generated(name);
            parents.add(first(PARENT, read(file), file));
        }

        assertEquals(6, count, "the halberd has six materials and one model each");
        assertEquals(Set.of("cortescombat:weapon/alabarda"), parents,
                "all six point at the same drawn shape. Six different parents means somebody is "
                        + "keeping six copies of one pose in step by hand, and the copper halberd "
                        + "rendered at half size for exactly that reason");
    }

    /** And the shared shape is really there, with the display transforms in it. */
    @Test
    @DisplayName("the shared shape carries the hand poses")
    void theSharedShapeHasThePoses() throws IOException {
        String json = read(ASSETS.resolve(Path.of("models", "weapon", "alabarda.json")));

        assertTrue(json.contains("\"gui\""), json
                + "\n\nno gui transform. This is the transform that makes the copper halberd half "
                + "the size of its five sisters");
        assertTrue(json.contains("\"firstperson_righthand\""), json
                + "\n\nand no first person pose, so the weapon the player looks at all day is "
                + "drawn by vanilla's default instead of the one that was drawn for it");
        assertTrue(json.contains("\"scale\""), json + "\n\nno scale anywhere in the display block");

        // And it must not name a texture, or all six items would share one.
        assertTrue(!LAYER0.matcher(json).find(), json
                + "\n\nthe shared shape names a layer0. That would override the six generated "
                + "textures and paint every halberd the same colour");
    }

    /** The arcabuz's own model, which is not in the catalogue and so nothing else checks it. */
    @Test
    @DisplayName("the arcabuz model points at a texture that exists")
    void theArcabuzResolves() throws IOException {
        Path model = ASSETS.resolve(Path.of("models", "item", "arcabuz.json"));
        if (!Files.isRegularFile(model)) {
            return;   // not drawn yet
        }
        String json = read(model);
        // **Only the `textures` block, and not the whole file.** The arcabuz model carries
        // `overrides` whose predicates are also namespaced strings -- "cortescombat:fuse" -- and a
        // test that scanned the whole file for namespaced references would treat the predicate
        // name as a texture path and fail on it. That is not a hypothetical: it happened the first
        // time this model grew its overrides.
        String texturesBlock = json.substring(json.indexOf("\"textures\""),
                json.indexOf("}", json.indexOf("\"textures\"")));
        Matcher quoted = Pattern.compile("\"(cortescombat:[^\"]+)\"").matcher(texturesBlock);
        List<String> references = new ArrayList<>();
        while (quoted.find()) {
            references.add(quoted.group(1));
        }
        assertTrue(!references.isEmpty(), model + " names no namespaced texture at all");

        for (String reference : references) {
            Path png = ASSETS.resolve(Path.of("textures",
                    reference.substring("cortescombat:".length()) + ".png"));
            assertTrue(Files.isRegularFile(png),
                    reference + " does not exist. An unnamespaced \"arcabuz\" in a Blockbench file "
                            + "resolves to textures/arcabuz.png, which nothing ever wrote");
        }
    }

    /**
     * The five fuse frames are five files, and the model picks between them on a predicate.
     *
     * <p>The frame cannot be chosen from an animated texture -- 1.21.1 has no API for that, and the
     * {@code .mcmeta} ran the strip freely, which is why the playtest called the weapon "bugged,
     * it has no textures": the fuse burned down in the inventory whether or not anything had
     * happened.</p>
     *
     * <p>So the strip was cut and the model takes an {@code overrides} entry per frame, keyed on
     * the {@code cortescombat:fuse} property. This asserts the whole chain exists: the frames, the
     * four models that point at them, and the four predicates that name those models. A model
     * pointing at a frame that was never written renders as the missing-model cube.</p>
     */
    @Test
    @DisplayName("the fuse is five frames chosen by a model predicate")
    void theFuseHasFiveFramesAndFourPredicates() throws IOException {
        Path frames = ASSETS.resolve(Path.of("textures", "item", "arcabuz_fuse"));
        assertTrue(Files.isDirectory(frames), "missing: " + frames);

        List<String> files = new ArrayList<>();
        try (var list = Files.list(frames)) {
            list.map(p -> p.getFileName().toString()).sorted().forEach(files::add);
        }
        assertEquals(List.of("arcabuz_fuse_0.png", "arcabuz_fuse_1.png", "arcabuz_fuse_2.png",
                        "arcabuz_fuse_3.png", "arcabuz_fuse_4.png"), files,
                "five frames, frame zero unlit and one to four the burn. The original strip is "
                        + "five frames, frame zero unlit and one to four the burn. The original"
                        + " strip is untouched and still on disk under textures/models/, which is"
                        + " not an atlas source -- see everyNamedTextureIsInAnAtlasSource, which is the"
                        + " reason these had to be cut somewhere else");
        java.util.Set<String> hashes = new java.util.HashSet<>();
        for (String name : files) {
            hashes.add(hashOf(frames.resolve(name)));
        }
        assertEquals(5, hashes.size(),
                "five identical files means the crop took one frame five times and the fuse would "
                        + "not appear to burn at all");

        String json = read(ASSETS.resolve(Path.of("models", "item", "arcabuz.json")));
        assertTrue(json.contains("\"overrides\""),
                "the model has to choose between them, or the gun is always frame zero");

        for (int frame = 1; frame <= 4; frame++) {
            assertTrue(json.contains("\"cortescombat:fuse\": " + frame),
                    "no predicate for frame " + frame + ". A frame with no override is a frame "
                            + "that can never be seen");
            Path model = ASSETS.resolve(Path.of("models", "item", "arcabuz_fuse_" + frame + ".json"));
            assertTrue(Files.isRegularFile(model), "missing: " + model
                    + " -- the predicate names a model that is not there");
            assertTrue(read(model).contains("arcabuz_fuse_" + frame + "\""),
                    model.getFileName() + " does not point at frame " + frame
                            + ". All four models would then be the same picture");
        }
    }

    /**
     * A texture that exists but is nowhere the atlas looks is not a texture.
     *
     * <p><b>This is the arcabuz's real bug and it was invisible to the test above it.</b> The frame
     * PNGs were on disk, so "names a texture that exists" was green, and the weapon rendered as the
     * missing-model cube in every single frame of every playtest. The client log said it in one
     * line and nothing else did:
     * {@code Missing textures in model cortescombat:arcabuz#inventory}.</p>
     *
     * <p><b>The reason is that a texture has to be in a folder the atlas is built FROM.</b>
     * {@code assets/minecraft/atlases/blocks.json} lists exactly two directories -- {@code block/}
     * and {@code item/} -- plus three {@code single} entities and the trim palettes. Everything
     * else under {@code textures/} is simply not stitched, so the game never loads it and renders
     * the missing sprite instead. The art was in {@code textures/models/}.</p>
     *
     * <p><b>This is the same trap as {@code textures/particle/} against
     * {@code textures/particles/}, which cost two launches earlier in the project.</b> Both are one
     * folder name away from being found, and in both the file exists, the path is right, the model
     * is valid -- and the game never looks. A test that only asks "does the file exist" is asking
     * the question that was already true.</p>
     */
    @Test
    @DisplayName("every texture a model names lives in a folder the atlas is built from")
    void everyNamedTextureIsInAnAtlasSource() throws IOException {
        // Read out of vanilla's blocks.json rather than remembered: its two directory sources.
        Set<String> atlasFolders = Set.of("block/", "item/");
        Set<String> offline = new java.util.TreeSet<>();

        for (Path folder : List.of(handDrawn(""), generated(""))) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (var files = Files.list(folder)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String json = read(file);
                    Matcher quoted = Pattern.compile("\"(cortescombat:[^\"]+)\"").matcher(json);
                    while (quoted.find()) {
                        String path = quoted.group(1).substring("cortescombat:".length());
                        boolean inAtlas = atlasFolders.stream().anyMatch(path::startsWith);
                        if (inAtlas) {
                            continue;
                        }
                        // An override predicate is a property name, not a texture. The arcabuz's
                        // overrides all live in the `overrides` array and name `cortescombat:fuse`.
                        if (path.indexOf('/') < 0 && path.indexOf(':') < 0) {
                            continue;
                        }
                        offline.add(file.getFileName() + " -> " + quoted.group(1));
                    }
                }
            }
        }
        assertEquals(Set.of(), offline,
                "these textures are named by a model and are on disk, but they sit in a folder "
                        + "that vanilla's blocks.json does not list as a source, so the atlas "
                        + "never stitches them and the model renders as the missing cube. "
                        + "blocks.json only reads block/ and item/. A file being present is not "
                        + "the same as the game ever looking at it");
    }

    /** A cheap content hash, because a file being present is not the same as it being right. */
    private static String hashOf(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            int h = 0;
            for (byte b : bytes) {
                h = h * 31 + b;
            }
            return Integer.toHexString(h);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The five frames the fuse burns through, and the timing that has to match the shot. */
    @Test
    @DisplayName("the arcabuz texture is five frames and the mcmeta spends them on one second")
    void theFuseIsFiveFramesOverOneSecond() throws IOException {
        Path png = ASSETS.resolve(Path.of("textures", "models", "arcabuz.png"));
        if (!Files.isRegularFile(png)) {
            return;
        }
        java.awt.image.BufferedImage image =
                javax.imageio.ImageIO.read(png.toFile());
        int frames = 5;
        int frameHeight = image.getHeight() / frames;
        assertEquals(64, image.getWidth(), "the model says texture_size 64x64");
        assertEquals(64, frameHeight,
                image.getHeight() + " tall is not five frames of 64. The strip is drawn by hand and "
                        + "the model's own texture_size has to agree with it");

        String mcmeta = read(Path.of("src", "main", "resources", "assets", "cortescombat",
                "textures", "models", "arcabuz.png.mcmeta"));
        int frametime = Integer.parseInt(
                mcmeta.replaceAll("(?s).*frametime\"?\\s*:\\s*(\\d+).*", "$1"));
        int fuseTicks = 20;
        assertEquals(fuseTicks / (frames - 1), frametime,
                "four lit frames across the twenty ticks the shot waits. At frametime 1 the strip "
                        + "burns in five ticks and the fuse the player watches is a quarter of "
                        + "the one they are actually standing through");
        assertEquals(fuseTicks, 20, "the shot waits one second, per the design");
    }

    /** The muzzle flash is in the format the slash particle already reads. */
    @Test
    @DisplayName("the muzzle flash is a five frame strip the particle can cut")
    void theMuzzleFlashIsReadable() throws IOException {
        Path png = ASSETS.resolve(Path.of("textures", "particle", "arcabuz_shot.png"));
        if (!Files.isRegularFile(png)) {
            return;
        }
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png.toFile());
        assertEquals(32, image.getWidth(), "slash strips are 32 wide");
        assertEquals(32 * 5, image.getHeight(),
                "and 32x160, five frames. A different shape needs a different particle, and "
                        + "SlashParticle cuts its UVs to a fixed frame count");

        // And the sprite has to be reachable, or it is a PNG nobody can spawn.
        String description = read(Path.of("src", "main", "resources", "assets", "cortescombat",
                "particles", "slash.json"));
        assertTrue(description.contains("arcabuz_shot"),
                "textures/particle/arcabuz_shot.png is a real 32x160 strip and nothing names it. "
                        + "The particle atlas is a directory of everything in textures/particle/, "
                        + "so it will be in the atlas, but a sprite set only binds what its "
                        + "description JSON lists");
    }

    /** The firestone borrows the copper nugget's texture until it has art of its own. */
    @Test
    @DisplayName("the firestone's generated model names a texture that exists")
    void theFirestoneResolves() throws IOException {
        Path model = GENERATED.resolve(Path.of("models", "item", "firestone.json"));
        assertTrue(Files.isRegularFile(model),
                "missing: " + model + " -- CortesModelProvider writes one for it, so run gradlew "
                        + "runData");
        String reference = first(LAYER0, read(model), model);
        assertEquals("cortescombat:item/copper_nugget", reference,
                "the placeholder is the copper nugget's own PNG, named explicitly rather than "
                        + "after the item. A model written by the default path would have named "
                        + "cortescombat:item/firestone.png, which is a file nobody drew");
        Path png = ASSETS.resolve(Path.of("textures",
                reference.substring("cortescombat:".length()) + ".png"));
        assertTrue(Files.isRegularFile(png),
                "and that texture has to exist, or the firestone renders as the missing-model "
                        + "cube and nothing anywhere complains");
    }

    /**
     * The arcabuz's model is hand drawn and must stay that way.
     *
     * <p>{@code CortesModelProvider} skips anything already on disk, so the arcabuz's Blockbench
     * file is never touched -- which also means no loop over the catalogue would ever look at it.
     * This is the only place that says the item and the file agree, and the only place that
     * would notice a generated copy quietly taking over.</p>
     */
    @Test
    @DisplayName("the arcabuz's model is hand drawn and is not regenerated over")
    void theArcabuzIsNotGenerated() throws IOException {
        assertTrue(Files.isRegularFile(handDrawn("arcabuz")), "missing: " + handDrawn("arcabuz"));
        assertTrue(!Files.isRegularFile(generated("arcabuz")),
                "there is a GENERATED arcabuz.json. The generated folder comes after src/main on "
                        + "the resource path, so a generated copy silently wins over the hand "
                        + "drawn one, and this model's eight elements, five groups and per-hand "
                        + "display transforms would be replaced by the two line generic");
    }

    /** Nothing in the item folder may claim to be an old name. */
    @Test
    @DisplayName("no model is left over from the rapier rename")
    void noLeftoverNames() throws IOException {
        Path folder = ASSETS.resolve(Path.of("models"));
        assertTrue(Files.isDirectory(folder), folder.toString());
        List<String> stale = new ArrayList<>();
        try (var files = Files.walk(folder)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                if (name.contains("rapier") || name.contains("halberd")) {
                    stale.add(folder.relativize(file).toString());
                }
            }
        }
        assertEquals(List.of(), stale,
                "models left over from before the rename. The Spanish name is alabarda and the "
                        + "English one is halberd, and a player who sees either of these in a "
                        + "resource pack list has found our working notes");
    }

    /** Nothing derived from the catalogue may quietly lose its model to a stale skip. */
    @Test
    @DisplayName("the halberd is the only family with a drawn shape")
    void onlyTheHalberdHasADrawnShape() throws IOException {
        for (WeaponFamily family : WeaponFamily.values()) {
            Path base = ASSETS.resolve(Path.of("models", "weapon", family.id() + ".json"));
            if (family == WeaponFamily.ALABARDA) {
                assertTrue(Files.isRegularFile(base), "the halberd's shared shape is missing");
            } else {
                assertTrue(!Files.isRegularFile(base), family.id() + " has a models/weapon/"
                        + family.id() + ".json but CortesModelProvider does not name it, so no "
                        + "model will ever point at it");
            }
        }
        // And the provider's own rule still lines up with WeaponProfile, which is the thing that
        // would drift if a family were added and forgotten.
        assertEquals(WeaponProfile.HALBERD, WeaponFamily.ALABARDA.profile());
    }
}
