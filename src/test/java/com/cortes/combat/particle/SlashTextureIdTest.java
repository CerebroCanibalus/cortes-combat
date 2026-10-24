package com.cortes.combat.particle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SlashTextureIdTest {

    /** La forma del template: path de archivo a sprite id. */
    @Test
    @DisplayName("file path loses the atlas prefix and the extension")
    void filePathBecomesSpriteId() {
        assertEquals("mymod:slash/chef",
                SlashTextureId.resolve("mymod:textures/particle/slash/chef.png"));
    }

    /** Un id ya armado pasa intacto. */
    @Test
    @DisplayName("a full id passes through untouched")
    void fullIdPassesThrough() {
        assertEquals("mymod:slash/chef", SlashTextureId.resolve("mymod:slash/chef"));
    }

    /** Un nombre pelado es de los nuestros. */
    @Test
    @DisplayName("a bare name lands in our namespace")
    void bareNameLandsHome() {
        assertEquals("cortescombat:sword_hit", SlashTextureId.resolve("sword_hit"));
    }

    /** Nada escrito = nada custom: el que llama cae al sprite del perfil. */
    @Test
    @DisplayName("null and blank mean no custom sprite")
    void emptyMeansNothingCustom() {
        assertNull(SlashTextureId.resolve(null));
        assertNull(SlashTextureId.resolve("   "));
    }
}
