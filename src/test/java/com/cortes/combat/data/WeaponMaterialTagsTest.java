package com.cortes.combat.data;

import com.cortes.combat.core.weapon.WeaponMaterial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every material's recipe tag is a tag something actually publishes.
 *
 * <h2>The bug this file exists for</h2>
 *
 * <p>{@code WeaponMaterial#repairTag} names a tag per tier, and the recipes, the advancements and
 * the anvil all read it as {@code minecraft:<name>}. <b>1.21.1 ships one of the seven.</b> The
 * vanilla client jar carries {@code data/minecraft/tags/item/stone_tool_materials.json} and no
 * other {@code *_tool_materials} file, and {@code Tiers} confirms it: iron, gold, diamond and
 * netherite repair with plain items. So the other six resolved to empty sets, and <b>only the five
 * stone weapons could be crafted</b> -- with {@code Loaded 1290 recipes} printed and no error
 * anywhere.</p>
 *
 * <h2>Why it reads the filesystem and not the provider</h2>
 *
 * <p>Because the provider is the thing that could be wrong. Asking it whether it wrote a tag is the
 * same as asking the author whether they did their job; asking the disk is asking the artefact. The
 * generated tag files are a resource {@code srcDir} of the mod and are what ships in the jar, so
 * their absence is the exact failure being guarded.</p>
 *
 * <p>The one tag vanilla supplies is pinned below rather than special-cased by name, so a second
 * exception is a deliberate edit to a constant and not a line buried in a loop.</p>
 */
class WeaponMaterialTagsTest {

    /**
     * The one tool-material tag 1.21.1 ships itself.
     *
     * <p>Read out of {@code minecraft_1.21.1_client.jar} rather than assumed, and pinned here
     * because it is the whole exception: every other tier's vanilla repair ingredient is a plain
     * item, so every other tag has to be written by our provider.</p>
     */
    private static final Set<String> VANILLA_SHIPPED = Set.of("stone_tool_materials");

    /** Where the tag provider writes, which is the same tree the jar ships. */
    private static final Path ITEM_TAGS =
            Path.of("src", "generated", "resources", "data", "minecraft", "tags", "item");

    /**
     * Every material's tag is on disk with something in it.
     *
     * <p><b>Two failures in one loop, and both were live.</b> A missing file is the tag nothing
     * publishes, which is the recipe that cannot be made; an empty {@code values} list is the same
     * thing written out. The second is not hypothetical: the first version of the provider called
     * {@code tag(...)} before its switch, so it wrote an empty
     * {@code minecraft:stone_tool_materials.json} -- a second definition of a tag vanilla already
     * fills.</p>
     */
    @Test
    @DisplayName("every material's recipe tag is generated, and is not empty")
    void everyMaterialTagExists() throws IOException {
        for (WeaponMaterial material : WeaponMaterial.values()) {
            String tag = material.repairTag();
            if (VANILLA_SHIPPED.contains(tag)) {
                continue;
            }

            Path file = ITEM_TAGS.resolve(tag + ".json");
            assertTrue(Files.exists(file),
                    "**" + tag + " is not generated, and a recipe whose ingredient is a tag nothing "
                            + "publishes can never be crafted.** It resolves to an empty set: the "
                            + "recipe parses, 'Loaded N recipes' prints, and the weapon never "
                            + "appears. Only the five stone weapons were craftable before this tag "
                            + "was written. Expected file: " + file);

            String json = Files.readString(file, StandardCharsets.UTF_8).replaceAll("\\s+", "");
            assertFalse(json.contains("\"values\":[]"),
                    "**an empty tag is the same as no tag**, and it is worse to read: the file "
                            + "exists, so a check that only asks 'is it there' passes. " + file);
            assertTrue(json.contains("\"values\":["),
                    "a tag file with no values key is not a tag: " + file);
        }
    }

    /**
     * And the exception is exactly one, named.
     *
     * <p>Pins the constant so a second material quietly leaning on a vanilla tag is an edit someone
     * made on purpose rather than a line that appeared. {@code stone_tool_materials} is the only
     * vanilla tool-material tag, verified against the client jar.</p>
     */
    @Test
    @DisplayName("exactly one material leans on a vanilla tag, and it is stone")
    void exactlyOneVanillaTag() {
        assertEquals(1, VANILLA_SHIPPED.size(),
                "a second vanilla tag would need its own line here and its own proof that it "
                        + "exists; the jar says there is one");
        assertTrue(VANILLA_SHIPPED.contains(WeaponMaterial.STONE.repairTag()),
                "stone is the one tag vanilla writes (VanillaItemTagsProvider:207), and it is the "
                        + "reason the stone weapons were the only ones that ever crafted");
    }

    /**
     * The tags the provider writes cover the materials, and no material is left to a tag that does
     * not exist.
     *
     * <p>The counterpart of the loop above from the other side: it asserts that the set of
     * generated {@code *_tool_materials} files equals the set the materials ask for, minus the one
     * vanilla supplies. If a material is added and the provider is not told, this fails on the
     * count instead of on a craft that silently does nothing.</p>
     */
    @Test
    @DisplayName("the generated tags are exactly the ones the materials ask for")
    void theGeneratedSetMatchesTheMaterials() throws IOException {
        Set<String> expected = new HashSet<>();
        for (WeaponMaterial material : WeaponMaterial.values()) {
            if (!VANILLA_SHIPPED.contains(material.repairTag())) {
                expected.add(material.repairTag());
            }
        }

        Set<String> onDisk = new HashSet<>();
        try (var files = Files.list(ITEM_TAGS)) {
            files.filter(p -> p.getFileName().toString().endsWith("_tool_materials.json"))
                    .forEach(p -> onDisk.add(p.getFileName().toString()
                            .replace("_tool_materials.json", "_tool_materials")));
        }

        assertEquals(expected, onDisk,
                "**the set of generated material tags and the set the recipes read have to be the "
                        + "same set.** A tag generated but never read is dead weight; a tag read "
                        + "but never generated is an uncraftable weapon with no symptom");
    }
}
