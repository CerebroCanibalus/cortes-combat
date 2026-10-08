package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.gear.CopperGear;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModArmor;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.registry.ModTools;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A model per item, generated only for the ones that do not already exist.
 *
 * <p>Written as a plain {@link DataProvider} rather than by extending vanilla's
 * {@code ModelProvider}, because that class's {@code run} is concrete and generates every model in
 * the game: there is no seam to add thirty of our own through, and the only way in would be to
 * copy it. Thirty small JSON files are the cheaper answer.</p>
 *
 * <p><b>Twenty-four of the thirty are generated here and six are not.</b> The halberd models were
 * drawn in Blockbench with real per-hand display transforms, and datagen's existing-file check
 * skips anything already on disk, so asking for all thirty is safe and regenerating a hand-drawn
 * file never happens. This class still asks for all thirty on purpose: the skip is what makes the
 * list here a statement of intent rather than a list of what is missing.</p>
 *
 * <p>Why vanilla's {@code item/handheld} parent is the right default for a weapon nobody has tuned:
 * it is exactly the pose vanilla draws, and the viewmodel framework treats a missing profile as
 * "draw it exactly as vanilla does". Same decision in two places. A viewmodel that is subtly wrong
 * is worse than none, because a player cannot tell that a motion was meant to be there.</p>
 */
public class CortesModelProvider implements DataProvider {

    private final PackOutput output;

    private final ExistingFileHelper existing;

    /**
     * What a "model file" is, spelled out.
     *
     * <p>{@code ExistingFileHelper.ResourceType} is a concrete class with no constants in
     * 1.21.1 -- it is a (pack type, suffix, prefix) triple and nothing is predefined, so
     * {@code ResourceType.MODEL} does not exist. The three values are what a model is: client
     * resources, the {@code .json} suffix, under {@code models/item}.</p>
     */
    private static final ExistingFileHelper.ResourceType MODEL_FILE =
            new ExistingFileHelper.ResourceType(
                    PackType.CLIENT_RESOURCES, ".json", "models/item");

    /**
 * A hand-drawn model a whole family shares, if one exists.
 *
 * <p><b>This replaced six files that were six copies of one shape.</b> The halberds each had their
 * own model, identical except for {@code layer0}, and a human was keeping them in sync: the set
 * was drawn before copper was a material, so it had a {@code wooden_alabarda.json} pointing at a
 * texture nobody drew and no {@code copper_alabarda.json} at all. The copper halberd then fell
 * through to the two-line generic and rendered at 1x while its five sisters rendered at 1.5x in
 * the GUI and 2x in third person -- and nothing failed, because nothing was wrong as far as the
 * game was concerned.</p>
 *
 * <p>So the shape lives in {@code models/weapon/alabarda.json} and each item model is three lines
 * naming a texture. Six files become one, and a material added to {@code WeaponMaterial} gets a
 * correct model without anyone remembering that models are also hand-written.</p>
 */
private ResourceLocation familyBase(WeaponFamily family) {
    // Only the halberd, and by name rather than by a flag on the enum: the base is a drawn asset,
    // and an enum that claimed one existed for a shape nobody had drawn would point at a file that
    // is not there.
    if (family == WeaponFamily.ALABARDA) {
        return ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "weapon/alabarda");
    }
    return null;
}

/** Whether the file behind {@link #familyBase} is actually on disk. */
private boolean handDrawnBase(WeaponFamily family) {
    ResourceLocation base = familyBase(family);
    if (base == null) {
        return false;
    }
    return Files.isRegularFile(handDrawnRoot()
            .resolve(base.getNamespace())
            .resolve("models")
            .resolve(base.getPath() + ".json"));
}

/**
 * The parent an item model points at.
 *
 * <p>Vanilla's {@code item/handheld} is the right default for a weapon nobody has tuned: it is
 * exactly the pose vanilla draws, and the viewmodel framework treats a missing profile as "draw it
 * exactly as vanilla does". Same decision in two places. A viewmodel that is subtly wrong is worse
 * than none, because a player cannot tell that a motion was meant to be there.</p>
 */
private String parentFor(WeaponFamily family, boolean handheld) {
    if (handheld && handDrawnBase(family)) {
        return familyBase(family).toString();
    }
    return handheld ? "minecraft:item/handheld" : "minecraft:item/generated";
}

/**
 * Whether a designer already drew this model by hand.
 *
 * <p>Asks the filesystem instead of {@code ExistingFileHelper} on purpose, and the reason is
 * written up at the call site: the helper's own methods answer a different question, and both
 * answers were wrong in a way that produced no error at all.</p>
 *
 * <p>Rooted at the <em>generated output folder's parent</em> rather than at
 * {@code src/main/resources}: the datagen run's working directory is the project root for an
 * IDE launch and something else under Gradle, so a hard-coded relative path silently fails
 * to resolve and every model comes back hand-drawn. Walking up from the output folder is the
 * one anchor both launch paths agree on.</p>
 *
 * <p>Not that it matters for correctness whether the hand-drawn one would have won:
 * {@code src/generated} comes after {@code src/main} on the resource path, so a generated
 * model <em>does</em> win, and skipping it is the whole fix.</p>
 */
private boolean handDrawn(ResourceLocation id) {
    return Files.isRegularFile(handDrawnRoot()
                .resolve(id.getNamespace())
                .resolve("models/item")
                .resolve(id.getPath() + ".json"));
    }

    /**
     * Walks up from {@code src/generated/resources} to {@code src/main/resources}.
     *
     * <p>The output folder is the one path this class is handed and the one that is always right;
     * everything above it is two levels up and a fixed suffix.</p>
     */
    private java.nio.file.Path handDrawnRoot() {
        java.nio.file.Path folder = output.getOutputFolder().toAbsolutePath();
        // .../src/generated/resources  ->  .../src/main/resources/assets
        java.nio.file.Path src = folder.getParent() == null ? folder : folder.getParent();
        java.nio.file.Path project = src == null || src.getParent() == null
                ? src : src.getParent();
        return project.resolve("main").resolve("resources").resolve("assets");
    }

    /**
     * Takes the existing-file helper, and that is the whole reason this provider does not go
     * through {@code event.createProvider(..)}.
     *
     * <p>The six halberd models carry per-hand display transforms drawn in Blockbench and live
     * in {@code src/main/resources}. Vanilla's own {@code ModelProvider} routes around existing
     * files for exactly this reason: the generated folder is a <em>second copy</em>, not a merge,
     * and {@code build.gradle} puts {@code src/generated} after {@code src/main} on the resource
     * path, so a generated model silently wins over a hand-drawn one with the same name.</p>
     *
     * <p>Without the check the first datagen run replaced six models that cost a designer an
     * afternoon with the two-line generic, and nothing said so: the run succeeded, the textures
     * were right, and the halberds simply looked flat in game.</p>
     */
    public CortesModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        this.output = output;
        this.existing = existingFileHelper;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cached) {
        // Sized from the catalogue rather than a literal 32, so a family gaining or losing a tier
        // cannot leave the array too short and write an ArrayIndexOutOfBounds at datagen.
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                futures.add(write(cached, family,
                        ModItems.weapon(family.itemName(material)).get(), true));
            }
        }
        // Flat, not handheld: an ingot is not a thing you swing, and the handheld template's
        // display transforms exist to make a held item lie at a believable angle.
        futures.add(write(cached, null, ModItems.COPPER_NUGGET.get(), false));

        // **The copper set: five tools and four armour pieces, every one an ordinary vanilla
        // shape.** The tools are handheld and the armour is flat, the same distinction the nugget
        // makes. Nothing here needs a hand-drawn model -- a copper axe is drawn exactly like an
        // iron one and the only thing that differs is the texture it names -- so the two-line
        // generic is the right model and not a fallback.
        for (var tool : ModTools.tools()) {
            futures.add(write(cached, null, tool.get(), true));
        }
        for (var piece : ModArmor.pieces()) {
            futures.add(write(cached, null, piece.get(), false));
        }

        // The arcabuz asks for nothing. Its model is a hand drawn Blockbench file with eight
        // elements, five groups and its own display transforms, it names its texture with a
        // numbered key instead of layer0, and its texture is a five frame animated strip rather
        // than a 16x16 icon. There is nothing this provider could write for it that would not be
        // worse than the file that already exists.
        //
        // It is also not in the catalogue and not in textures/item, so nothing downstream is
        // expecting a file here -- which is why `ModelDeclarationTest` grew a test for the
        // arcabuz specifically instead of reading it off WeaponFamily.
        futures.add(write(cached, null, ModItems.FIRESTONE.get(), false,
                // **The copper nugget's texture, deliberately.**
                // **Its own texture, which now exists.**
                //
                // This was the copper nugget's, deliberately, back when the firestone had no art:
                // a placeholder that exists beats the missing-model cube. The playtest then reported
                // the firestone showing the nugget sprite, which is the placeholder having done
                // exactly its job for exactly as long as it was needed.
                //
                // Still named explicitly rather than left to the item's own path: `write` grew that
                // overload for this call, and dropping the argument now would delete the reason it
                // exists.
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "item/firestone")));

        // **And the block, which is three files rather than one and none of them is an item
        // model of the shape `write` produces.** A block's inventory sprite is not a flat texture:
        // it is the block model, drawn in three dimensions, and `minecraft:item/generated` would
        // show the sixteen pixel face of a cube as if it were a sprite. So the block model, its
        // blockstate and the item model that parents it are written here, and the item model's
        // parent is the block model -- one shape, named once.
        //
        // The block model's texture is deliberately not checked against the atlas here: that is a
        // question about a block texture, and the model-declaration test answers it where the
        // block models live.
        futures.add(writeBlock(cached));
        futures.add(writeBlockState(cached));
        futures.add(writeBlockItem(cached));

        // **And the dead star, which is an ordinary flat item.** Registered ahead of its use so the
        // texture the designer drew has a model -- an unclaimed PNG in textures/item is what
        // `WeaponTexturesTest` calls an orphan, and it is right to.
        futures.add(write(cached, null, ModItems.DEAD_STAR.get(), false));

        // **And the nether reactor, which is four files: two base models, an overlay, and a
        // blockstate that combines them.** The two base models are `cube_column` -- one texture for
        // the four sides and one for the top and bottom -- because the reactor's core is on its
        // sides and its cap is shared. The overlay is a hand-shaped cube with only its four side
        // faces, inflated a hair so it does not z-fight the base it sits on.
        futures.add(writeReactorModel(cached, "nether_reactor", "nether_reactor",
                "nether_reactor_top"));
        futures.add(writeReactorModel(cached, "nether_reactor_empty", "nether_reactor_empty",
                "nether_reactor_top"));
        // **The star's own pair.** Same two shapes as the normal core -- a cube_column base and a
        // four-face bloom -- because the textures are the same sixteen pixels and the same drawing;
        // only the art changes. The blockstate swaps the pair on `star`, so both pairs exist and one
        // of each is unreachable by construction of the `when` clauses.
        futures.add(writeReactorModel(cached, "nether_reactor_star", "nether_reactor_star",
                "nether_reactor_top"));
        futures.add(writeReactorOverlay(cached, "nether_reactor_e", "nether_reactor_e"));
        futures.add(writeReactorOverlay(cached, "nether_reactor_star_e", "nether_reactor_star_e"));
        futures.add(writeReactorBlockState(cached));
        futures.add(writeReactorItem(cached));

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * {@code models/block/nether_reactor[_empty].json}: {@code cube_column} with the core on the
     * sides and one texture on the top and bottom.
     *
     * <p>One model per charge state, because the design wants the core to read as lit or dead and
     * that is two different side textures. The cap is the same on both, so it is named twice and
     * drawn once.</p>
     */
    private CompletableFuture<?> writeReactorModel(CachedOutput cached, String name, String side,
                                                   String end) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/cube_column");
        JsonObject textures = new JsonObject();
        textures.addProperty("side", CortesCombat.MODID + ":block/" + side);
        textures.addProperty("end", CortesCombat.MODID + ":block/" + end);
        json.add("textures", textures);

        return DataProvider.saveStable(cached, json,
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block")
                        .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, name)));
    }

    /**
     * {@code models/block/nether_reactor_e.json}: the emissive overlay, four side faces only.
     *
     * <p><b>Four faces, spelled out, because a cube would put the glow on the cap too.</b> The
     * texture is a centred bloom meant to sit over the side core; a {@code cube_all} or
     * {@code cube_column} would paint it on the top and bottom, where there is no core to glow. An
     * explicit element lists exactly the faces wanted and omits the rest.</p>
     *
     * <p><b>Inflated by a tenth of a pixel, which is what stops the z-fight.</b> The overlay is
     * coplanar with the base side face; without a nudge the two fight for the same depth and the
     * glow flickers. A tenth of a model unit is about six thousandths of a block -- invisible as a
     * size, sufficient as a depth.</p>
     *
     * <p>{@code cullface} on each face so the overlay is culled exactly when the base face behind it
     * is: a solid neighbour hides both, and the overlay is not drawn into a wall.</p>
     */
    private CompletableFuture<?> writeReactorOverlay(CachedOutput cached, String name,
                                                     String texture) {
        JsonObject face = new JsonObject();
        // A four-entry UV and the same texture key on all four sides.
        com.google.gson.JsonArray uv = new com.google.gson.JsonArray();
        uv.add(0);
        uv.add(0);
        uv.add(16);
        uv.add(16);

        JsonObject textures = new JsonObject();
        textures.addProperty("particle", CortesCombat.MODID + ":block/" + texture);
        textures.addProperty("glow", CortesCombat.MODID + ":block/" + texture);

        JsonObject faces = new JsonObject();
        for (String direction : new String[]{"north", "south", "east", "west"}) {
            JsonObject entry = new JsonObject();
            entry.add("uv", uv);
            entry.addProperty("texture", "#glow");
            entry.addProperty("cullface", direction);
            faces.add(direction, entry);
        }

        JsonObject element = new JsonObject();
        com.google.gson.JsonArray from = new com.google.gson.JsonArray();
        com.google.gson.JsonArray to = new com.google.gson.JsonArray();
        for (int i = 0; i < 3; i++) {
            from.add(-0.1D);
            to.add(16.1D);
        }
        element.add("from", from);
        element.add("to", to);
        element.add("faces", faces);

        com.google.gson.JsonArray elements = new com.google.gson.JsonArray();
        elements.add(element);

        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/block");
        json.addProperty("render_type", "minecraft:translucent");
        json.add("textures", textures);
        json.add("elements", elements);

        return DataProvider.saveStable(cached, json,
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block")
                        .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, name)));
    }

    /**
     * {@code blockstates/nether_reactor.json}: a multipart of the base models plus the blooms.
     *
     * <p><b>Multipart rather than four variants, because the overlay is orthogonal to the base.</b>
     * The base is chosen by {@code charged} and the bloom is added by {@code active}, so the two
     * properties combine into three rendered shapes instead of four combinations written out.</p>
     *
     * <p><b>And AND is real, read out of {@code Selector.Deserializer.getCondition} rather than
     * remembered:</b> a {@code when} object with more than one key builds an {@code AndCondition}
     * (and a lone {@code "AND"} or {@code "OR"} key takes an array). That is what lets
     * {@code star} <em>replace</em> the normal base instead of being stacked on it -- two full cubes
     * in one cell are a depth fight, not a texture -- while the star's bloom is picked by
     * {@code active AND star} and the normal one by {@code active AND NOT star}. Five parts: the
     * empty core, the charged core, the star core, the normal bloom, the star bloom.</p>
     */
    private CompletableFuture<?> writeReactorBlockState(CachedOutput cached) {
        JsonObject emptyWhen = new JsonObject();
        emptyWhen.addProperty("charged", "false");
        emptyWhen.addProperty("star", "false");
        JsonObject emptyApply = new JsonObject();
        emptyApply.addProperty("model", CortesCombat.MODID + ":block/nether_reactor_empty");

        JsonObject chargedWhen = new JsonObject();
        chargedWhen.addProperty("charged", "true");
        chargedWhen.addProperty("star", "false");
        JsonObject chargedApply = new JsonObject();
        chargedApply.addProperty("model", CortesCombat.MODID + ":block/nether_reactor");

        JsonObject starWhen = new JsonObject();
        starWhen.addProperty("star", "true");
        JsonObject starApply = new JsonObject();
        starApply.addProperty("model", CortesCombat.MODID + ":block/nether_reactor_star");

        JsonObject activeWhen = new JsonObject();
        activeWhen.addProperty("active", "true");
        activeWhen.addProperty("star", "false");
        JsonObject activeApply = new JsonObject();
        activeApply.addProperty("model", CortesCombat.MODID + ":block/nether_reactor_e");

        JsonObject starActiveWhen = new JsonObject();
        starActiveWhen.addProperty("active", "true");
        starActiveWhen.addProperty("star", "true");
        JsonObject starActiveApply = new JsonObject();
        starActiveApply.addProperty("model", CortesCombat.MODID + ":block/nether_reactor_star_e");

        com.google.gson.JsonArray multipart = new com.google.gson.JsonArray();
        multipart.add(part(emptyWhen, emptyApply));
        multipart.add(part(chargedWhen, chargedApply));
        multipart.add(part(starWhen, starApply));
        multipart.add(part(activeWhen, activeApply));
        multipart.add(part(starActiveWhen, starActiveApply));

        JsonObject json = new JsonObject();
        json.add("multipart", multipart);

        return DataProvider.saveStable(cached, json,
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates")
                        .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                                "nether_reactor")));
    }

    private static JsonObject part(JsonObject when, JsonObject apply) {
        JsonObject part = new JsonObject();
        part.add("when", when);
        part.add("apply", apply);
        return part;
    }

    /**
     * {@code models/item/nether_reactor.json}: the block model, so the inventory sprite is the cube.
     *
     * <p>Same as the firestone block: {@code item/generated} would draw the flat face of a cube as
     * if it were an icon. <b>The parent is the EMPTY base model, not the charged one</b>: an item
     * sitting in a chest or a hotbar has no charge, so drawing the lit core would tell the player
     * that the reactor in their hand is running when it is not.</p>
     */
    private CompletableFuture<?> writeReactorItem(CachedOutput cached) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", CortesCombat.MODID + ":block/nether_reactor_empty");

        return DataProvider.saveStable(cached, json,
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/item")
                        .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                                "nether_reactor")));
    }

    /**
     * {@code models/block/firestone_block.json}: a full cube, all six faces one texture.
     *
     * <p>A plain {@code minecraft:block/cube_all} and not a hand-drawn shape, because a block of
     * powder is a cube. The texture is named here and only here -- the blockstate and the item
     * model both point at this model, so a change of texture is a change in one file.</p>
     */
    private CompletableFuture<?> writeBlock(CachedOutput cached) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", CortesCombat.MODID + ":block/firestone_block");
        json.add("textures", textures);

        return DataProvider.saveStable(cached, json,
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block")
                        .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                                "firestone_block")));
    }

    /**
     * {@code blockstates/firestone_block.json}: one state, one model.
     *
     * <p><b>An empty variant key, spelled out.</b> A block with a single model and no properties
     * gets the key {@code ""}, and a blockstate that names a model which does not exist renders the
     * missing-model cube with the log saying it in one line. The key is a JSON object member that
     * is the empty string, which is legal and is what vanilla does.</p>
     */
    private CompletableFuture<?> writeBlockState(CachedOutput cached) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", CortesCombat.MODID + ":block/firestone_block");
        JsonObject variants = new JsonObject();
        variants.add("", variant);
        JsonObject json = new JsonObject();
        json.add("variants", variants);

        return DataProvider.saveStable(cached, json,
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates")
                        .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                                "firestone_block")));
    }

    /**
     * {@code models/item/firestone_block.json}: a parent and nothing else.
     *
     * <p>The parent is the block model, which is what makes the inventory sprite the three
     * dimensional cube. Naming a texture here instead would flatten it, and naming
     * {@code minecraft:item/generated} -- the default every item in this set gets -- would do the
     * same. One line, and it is the difference between a block in the hand and a picture of one.</p>
     */
    private CompletableFuture<?> writeBlockItem(CachedOutput cached) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", CortesCombat.MODID + ":block/firestone_block");

        return DataProvider.saveStable(cached, json,
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/item")
                        .json(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                                "firestone_block")));
    }

    private CompletableFuture<?> write(CachedOutput cached, WeaponFamily family, Item item,
                                       boolean handheld) {
        return write(cached, family, item, handheld, null);
    }

    /**
     * The same write, with the texture spelled out.
     *
     * <p>An overload rather than a parameter on the existing one because the default case is
     * thirty-one items and the exception is one, and a parameter that is null in thirty-one of
     * thirty-two call sites is a parameter nobody reads. The null means "name it after the item",
     * which is what every weapon and the nugget want.</p>
     *
     * @param texture the texture to name as layer0, or null to name it after the item's own id
     */
    private CompletableFuture<?> write(CachedOutput cached, WeaponFamily family, Item item,
                                       boolean handheld, ResourceLocation texture) {
        // The registry key, not the description id.
        //
        // getDescriptionId() returns "item.cortescombat.iron_ropera", which is a translation key
        // and not a model location. Stripping the "item." prefix does not rescue it: what is left
        // is a path with dots in it, so the model was written to
        // assets/minecraft/models/item/item.cortescombat.iron_ropera.json -- wrong namespace, wrong
        // folder, wrong name -- and nothing referenced it. The symptom is the missing-model cube
        // with datagen reporting success.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        // Whether a hand-drawn model already owns this exact name.
        //
        // Two other APIs were wrong here and both failed silently in opposite directions:
        //
        //   existing.exists(id, MODEL_FILE) is true for anything vanilla already ships, because
        //   it searches Mojang's sources too. "minecraft:item/handheld", the parent every model
        //   here uses, is one of those -- so it reported every item as hand-drawn and generated
        //   nothing at all. Thirty-two weapons, zero models, datagen reporting success.
        //
        //   existing.trackGenerated(..) returns void. It registers, it does not query.
        //
        // The question that is actually being asked -- "is there a file at
        // src/main/resources/assets/<ns>/models/item/<name>.json" -- is answered by asking it.
        if (handDrawn(id)) {
            return CompletableFuture.completedFuture(null);
        }

        JsonObject json = new JsonObject();
        json.addProperty("parent", parentFor(family, handheld));
        JsonObject textures = new JsonObject();
        // The texture is named after the item, so "cortescombat:item/iron_ropera" resolves to the
        // file the designer actually drew. Same string as the model name, by construction --
        // unless the caller named one, which is the firestone's case and the only one.
        textures.addProperty("layer0", texture != null
                ? texture.toString()
                : id.getNamespace() + ":item/" + id.getPath());
        json.add("textures", textures);

        Path path = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/item")
                .json(id);
        return DataProvider.saveStable(cached, json, path);
    }

    @Override
    public String getName() {
        return "Cortés Combat Models";
    }

    /**
     * Every item name this provider writes a model for, the two copper materials included.
     *
     * <p>Delegated to {@link WeaponFamily} rather than rebuilt here. It used to be a copy in this
     * class, and the copy could not even be read from a unit test: a static on a class that
     * implements {@code DataProvider} drags {@code DataProvider} into the test's classpath, which
     * has no Minecraft in it, so {@code compileTestJava} failed before a single assertion ran.
     * One list, below the Minecraft line.</p>
     */
    public static Set<String> itemNames() {
        Set<String> names = new LinkedHashSet<>(WeaponFamily.allItemNames());
        names.add("copper_nugget");
        // The firestone, because this provider writes a model for it. **Not the arcabuz**, and
        // that asymmetry is the point: the arcabuz's model is a hand drawn file on disk and this
        // provider skips anything already there, so listing it here would be a claim about a file
        // it never writes.
        names.add("firestone");
        // **And the copper set, which this provider writes a model for.** Five tools and four
        // armour pieces, every one a plain shape: the tools handheld, the armour flat.
        names.addAll(CopperGear.ALL_NAMES);
        // The two block-less additions since: the dead star and the nether reactor's block item.
        // Both have a model this provider writes, and listing them here keeps the method an honest
        // statement of what it draws.
        names.add("dead_star");
        names.add("nether_reactor");
        return names;
    }
}
