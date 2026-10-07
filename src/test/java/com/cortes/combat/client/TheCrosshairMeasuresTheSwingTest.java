package com.cortes.combat.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the cone the player is shown is the cone the swing uses.
 *
 * <h2>The report this exists for</h2>
 *
 * <p>The playtest said the maza's air bonus "no lo noto" -- and the bonus was working. The reach
 * really did grow by a fifth on the server and in the targeting, so nothing was broken in the sense
 * a test can see. What was missing was that <b>every display site measured the item's cone with no
 * notion of a holder</b>: the brackets drew three point four blocks while the swing reached four
 * point oh eight, so the player was told they were out of range and the hit connected anyway.</p>
 *
 * <p>That is the one disagreement this project exists to prevent, and the reason it survived a
 * green build is worth writing down: a marker that lies is not a bug report about reach, it is a
 * bug report about <em>not noticing a feature</em>. Nothing crashed and no number was wrong -- the
 * HUD was just describing a different weapon from the one in the player's hand.</p>
 *
 * <h2>Why this reads the source</h2>
 *
 * <p>Because the four sites are client render code: {@code CombatAim} is ticked by the client,
 * the two layers are {@code LayeredDraw.Layer}s and cannot be loaded in a bare JVM at all, and
 * {@code ConeDebugRenderer} is a renderer. The rule they share is one call, and the only place it
 * can be checked without a client is here. Brittle by design; the alternative is invisible.</p>
 */
class TheCrosshairMeasuresTheSwingTest {

    /**
     * Every place a cone is drawn, measured or reported to the player.
     *
     * <p>Written out rather than globbed, so adding a fifth display site is a decision somebody
     * makes here rather than a file that quietly escapes the rule.</p>
     */
    private static final List<String> DISPLAY_SITES = List.of(
            "CombatAim.java",
            "CooldownBracketLayer.java",
            "ConeDebugRenderer.java",
            "HudGeometryDump.java");

    @Test
    @DisplayName("every display site asks MaceAir for its cone, so the air bonus is shown")
    void theDisplayAsksTheSameQuestion() {
        List<String> blind = new ArrayList<>();
        for (String file : DISPLAY_SITES) {
            if (!source(file).contains("MaceAir.displayedFor")) {
                blind.add(file);
            }
        }
        assertTrue(blind.isEmpty(),
                "**estos sitios miden el cono del item y no el del jugador: " + blind + ".** El "
                        + "bonus aereo de la maza escala el alcance en el servidor y en el targeting, "
                        + "asi que la espada pega mas lejos y el jugador no se entera: el crosshair "
                        + "sigue dibujando el cono de tierra. La correccion es una llamada, y sin "
                        + "esto el sintoma se reporta como 'no lo noto' y no como un bug");
    }

    @Test
    @DisplayName("and none of them measures the item's cone directly")
    void noneMeasuresTheItem() {
        List<String> direct = new ArrayList<>();
        for (String file : DISPLAY_SITES) {
            if (source(file).contains(".displayedReach()")) {
                direct.add(file);
            }
        }
        assertTrue(direct.isEmpty(),
                "**`displayedReach()` no sabe de quien lo sostiene: " + direct + ".** Es correcto "
                        + "para 30 de las 31 armas, porque su cono es una propiedad del item, y es "
                        + "exactamente el error para la maza en el aire, que es la primera arma cuyo "
                        + "cono es una propiedad del JUGADOR. El camino es MaceAir.displayedFor");
    }

    private static String source(String file) {
        Path path = Path.of("src", "main", "java", "com", "cortes", "combat", "client", file);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path + ": " + e.getMessage(), e);
        }
    }
}
