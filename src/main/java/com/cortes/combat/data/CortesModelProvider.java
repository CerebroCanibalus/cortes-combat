package com.cortes.combat.data;

import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModItems;
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
                futures.add(write(cached, ModItems.weapon(family.itemName(material)).get(), true));
            }
        }
        // Flat, not handheld: an ingot is not a thing you swing, and the handheld template's
        // display transforms exist to make a held item lie at a believable angle.
        futures.add(write(cached, ModItems.COPPER_NUGGET.get(), false));

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> write(CachedOutput cached, Item item, boolean handheld) {
        // The registry key, not the description id.
        //
        // getDescriptionId() returns "item.cortescombat.iron_ropera", which is a translation key
        // and not a model location. Stripping the "item." prefix does not rescue it: what is left
        // is a path with dots in it, so the model was written to
        // assets/minecraft/models/item/item.cortescombat.iron_ropera.json -- wrong namespace, wrong
        // folder, wrong name -- and nothing referenced it. The symptom is the missing-model cube
        // with datagen reporting success.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        // Whether a hand-drawn model already owns this name.
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
        json.addProperty("parent", handheld
                ? "minecraft:item/handheld"
                : "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        // The texture is named after the item, so "cortescombat:item/iron_ropera" resolves to the
        // file the designer actually drew. Same string as the model name, by construction.
        textures.addProperty("layer0", id.getNamespace() + ":item/" + id.getPath());
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
        return names;
    }
}
