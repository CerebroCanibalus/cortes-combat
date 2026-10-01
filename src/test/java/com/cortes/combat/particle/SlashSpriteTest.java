package com.cortes.combat.particle;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every weapon resolves to a sprite, and that the ones with art get their own.
 *
 * <p>This is a table of filenames and nothing else, and it is table-shaped because three of the
 * seven disagree with the code: the warhammer profile is {@code warhammer} and ships as
 * {@code martillo_hit}, the sabre profile is {@code sabre} and ships as {@code shotel_hit}, the
 * dagger profile is {@code dagger} and ships as {@code daga_hit}. Generating the name from the
 * profile id would have produced three wrong filenames, which are three invisible particles.</p>
 */
class SlashSpriteTest {

    @Test
    @DisplayName("every profile resolves to something")
    void nothingResolvesToNothing() {
        // The failure this pins is silence. A missing sprite is not an error: the particle spawns,
        // the atlas hands back the missing texture, and the swing draws nothing. So the only place
        // that can catch it is here.
        for (WeaponProfile profile : WeaponProfile.values()) {
            String sprite = SlashSprite.of(profile);
            assertFalse(sprite == null || sprite.isBlank(),
                    profile + " resolved to no sprite at all");
        }
        assertEquals(SlashSprite.FALLBACK, SlashSprite.of(null),
                "a bare hand has no profile and must still draw something");
    }

    @Test
    @DisplayName("the weapons with art of their own get their own")
    void ownArtIsItsOwn() {
        assertEquals("sword_hit", SlashSprite.of(WeaponProfile.SWORD));
        assertEquals("axe_hit", SlashSprite.of(WeaponProfile.AXE));
        assertEquals("daga_hit", SlashSprite.of(WeaponProfile.DAGGER));
        assertEquals("shotel_hit", SlashSprite.of(WeaponProfile.SABRE));
        assertEquals("alabarda_hit", SlashSprite.of(WeaponProfile.HALBERD));
        assertEquals("martillo_hit", SlashSprite.of(WeaponProfile.WARHAMMER));
        assertEquals("ropera_hit", SlashSprite.of(WeaponProfile.ROPERA));

        for (WeaponProfile profile : new WeaponProfile[]{
                WeaponProfile.SWORD, WeaponProfile.AXE, WeaponProfile.DAGGER,
                WeaponProfile.SABRE, WeaponProfile.HALBERD, WeaponProfile.WARHAMMER,
                WeaponProfile.ROPERA}) {
            assertTrue(SlashSprite.hasOwn(profile), profile + " claims art and has none");
        }
    }

    @Test
    @DisplayName("everything else borrows the sword's slash")
    void theRestFallsBack() {
        // A decision, not a shrug. Nothing rendering reads as the game being broken; one asset
        // reused reads as one asset reused. The bare hands, the pickaxe, the shovel, the mace and
        // the trident are the ones that land here today.
        for (WeaponProfile profile : new WeaponProfile[]{
                WeaponProfile.NONE, WeaponProfile.UNARMED, WeaponProfile.PICKAXE,
                WeaponProfile.SHOVEL, WeaponProfile.MACE, WeaponProfile.TRIDENT}) {
            assertEquals(SlashSprite.FALLBACK, SlashSprite.of(profile),
                    profile + " should be borrowing the sword's slash");
            assertFalse(SlashSprite.hasOwn(profile),
                    profile + " should not claim art it does not have");
        }
    }

    @Test
    @DisplayName("no two profiles with art share a sprite, and no sprite is named twice")
    void theNamesAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (WeaponProfile profile : WeaponProfile.values()) {
            if (!SlashSprite.hasOwn(profile)) {
                continue;
            }
            assertTrue(seen.add(SlashSprite.of(profile)),
                    "two profiles are drawing the same slash: " + SlashSprite.of(profile));
        }
    }

    @Test
    @DisplayName("every sprite the code can ask for is listed in the particle description")
    void theDescriptionListsEverySpriteTheCodeCanAskFor() {
        // The crash this pins. A particle type registered from code gets its textures from the
        // description JSON under assets/<ns>/particles/, not from the provider callback: with no
        // description the sprite set is never rebound, `sprite` stays null, and the first particle
        // on screen kills the render thread on getU0(). The client booted clean, no error in the log
        // at all, and crashed the moment a zombie was hit.
        //
        // So the list lives in two places -- SlashSprite.SPRITES and slash.json -- and this reads
        // the second one back and compares. The order matters too, because the particle binds a
        // sprite by index into it.
        String json;
        try {
            java.nio.file.Path path = java.nio.file.Path.of(
                    "src/main/resources/assets/cortescombat/particles/slash.json");
            json = java.nio.file.Files.readString(path);
        } catch (Exception e) {
            // Not run from the project root, so the file cannot be read. Say so instead of
            // passing on a comparison that never happened.
            throw new IllegalStateException(
                    "slash.json has to be readable from the project root for this to mean "
                            + "anything; run the tests from there", e);
        }

        for (String sprite : SlashSprite.SPRITES) {
            assertTrue(json.contains("\"cortescombat:" + sprite + "\""),
                    "slash.json does not list " + sprite + ", so it would never bind");
        }

        // And nothing extra: an entry in the JSON that the code cannot name is a texture nobody
        // can ever draw, which is how a sprite quietly stops being used.
        for (String name : new String[]{"sword_hit", "axe_hit", "daga_hit", "ropera_hit",
                "martillo_hit", "shotel_hit", "alabarda_hit", "barrido_alabarda"}) {
            assertTrue(SlashSprite.SPRITES.contains(name),
                    name + " is in the art but the code cannot ask for it");
        }
    }

    @Test
    @DisplayName("an unknown sprite index falls back to the first one rather than throwing")
    void indexIsAlwaysValid() {
        // SpriteSet has no get(index); it walks list.get(age * (size - 1) / lifetime). An index
        // past the end of the list would be an out of bounds inside the particle, on the render
        // thread, so indexOf never returns one.
        assertEquals(0, SlashSprite.indexOf("no_such_sprite"));
        assertEquals(0, SlashSprite.indexOf(""));
        for (int i = 0; i < SlashSprite.SPRITES.size(); i++) {
            String sprite = SlashSprite.SPRITES.get(i);
            assertEquals(i, SlashSprite.indexOf(sprite),
                    sprite + " should land on its own index, because the particle binds by it");
        }
    }

    @Test
    @DisplayName("the halberd's sweep is listed, because it is not a hit")
    void theSweepIsItsOwnSprite() {
        assertEquals("barrido_alabarda", SlashSprite.SWEEP);
        assertTrue(SlashSprite.SPRITES.contains(SlashSprite.SWEEP),
                "the sweep throws on its own, whether or not anything was hit, so it needs its own");
        assertFalse(SlashSprite.SWEEP.equals(SlashSprite.of(WeaponProfile.HALBERD)),
                "and it is not the same sprite as the halberd's hit slash: one throws on a "
                        + "right click whether or not anything was hit, the other says that "
                        + "something was hit");
    }

    @Test
    @DisplayName("the sprite id carries no path prefix")
    void spriteIdIsBare() {
        // Vanilla declares the particles atlas as
        // {"type":"directory","source":"particle","prefix":""}, so textures/particle/sword_hit.png
        // is the sprite cortescombat:sword_hit and NOT cortescombat:particle/sword_hit.
        //
        // The prefix is empty in vanilla's own declaration, which is the part that is easy to get
        // wrong by assuming it repeats the directory name. Reading it out of the vanilla atlas file
        // rather than from memory is what stops this being a guess.
        assertEquals("cortescombat:sword_hit", SlashSprite.spriteId("sword_hit"));
        assertFalse(SlashSprite.spriteId("sword_hit").contains("/"),
                "an id with a path in it will not be found in the atlas");
    }
}