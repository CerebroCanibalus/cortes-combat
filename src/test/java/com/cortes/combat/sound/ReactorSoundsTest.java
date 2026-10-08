package com.cortes.combat.sound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the reactor's six sound names exist as events, and point at the files that were recorded.
 *
 * <p><b>This is the seam check for the spectacle phase.</b> The constants in {@link ReactorSounds}
 * are strings; a typo in one is silence at the exact moment the reactor is supposed to roar, with no
 * error anywhere. The two ends are read from disk: the event key in {@code sounds.json} and the
 * {@code .ogg} beside it. If either is missing this says which constant is wrong.</p>
 */
class ReactorSoundsTest {

    private static final Path SOUNDS_JSON =
            Path.of("src", "main", "resources", "assets", "cortescombat", "sounds.json");
    private static final Path SOUND_DIR =
            Path.of("src", "main", "resources", "assets", "cortescombat", "sounds", "block");

    private static String bare(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static String eventKey(String id) {
        String path = bare(id);
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("every reactor sound is declared and the file behind it exists")
    void everyReactorSoundResolves() throws IOException {
        String json = Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8);
        for (String id : new String[]{
                ReactorSounds.UP,
                ReactorSounds.OFF,
                ReactorSounds.LOOP_ON,
                ReactorSounds.OVERHEAT_FIRESTONE,
                ReactorSounds.OVERHEAT_NETHERSTAR,
                ReactorSounds.NUCLEAR_EXPLOSION}) {
            assertTrue(json.contains("\"" + eventKey(id) + "\""),
                    id + " is not an event in sounds.json, so it resolves to silence");
            assertTrue(json.contains("\"name\": \"cortescombat:block/" + eventKey(id) + "\""),
                    id + " is declared but points at something other than the block recording");
            assertTrue(Files.isRegularFile(SOUND_DIR.resolve(eventKey(id) + ".ogg")),
                    id + " has no file at sounds/block/" + eventKey(id) + ".ogg");
        }
    }
}
