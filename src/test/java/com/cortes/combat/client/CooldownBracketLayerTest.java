package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far the cooldown brackets sit for a given cone.
 *
 * <p>Pure arithmetic over a cone angle, so it runs without the game even though the widget that
 * uses it is a GUI layer. The failure this guards against is not a crash: it is a widget that
 * quietly stops telling the player anything, which is exactly the kind of bug a test has to be
 * told about by a human looking at the screen.</p>
 */
class CooldownBracketLayerTest {

    private static final float SWORD = 35.0F;
    private static final float DAGGER = 45.0F;
    private static final float AXE = 45.0F;
    private static final float SABRE = 75.0F;
    private static final float RAPIER = 12.0F;

    @Test
    @DisplayName("a wider cone always draws wider")
    void monotonic() {
        // The one invariant the whole widget rests on. If this ever inverts, the indicator is
        // actively lying: a sabre would look tighter than a dagger.
        int previous = 0;
        for (float angle = 0.0F; angle <= 90.0F; angle += 0.5F) {
            int span = ConeGauge.halfSpan(angle);
            assertTrue(span >= previous,
                    "span shrank going from " + (angle - 0.5F) + " to " + angle
                            + " degrees: " + previous + " -> " + span);
            previous = span;
        }
    }

    @Test
    @DisplayName("angles outside the window are clamped, not extrapolated")
    void clamped() {
        assertEquals(ConeGauge.halfSpan(0.0F), ConeGauge.halfSpan(-40.0F),
                "a negative angle must clamp to the floor, not draw off the bracket");
        assertEquals(ConeGauge.halfSpan(180.0F), ConeGauge.halfSpan(200.0F),
                "an absurd angle must clamp to the ceiling rather than leaving the screen");
    }

    @Test
    @DisplayName("the common weapons are told apart from each other")
    void commonWeaponsAreDistinct() {
        // The band that matters. Sword, axe and pickaxe live within a few degrees of each
        // other, and a linear spread put all of them on the same span, so the four weapons a
        // player actually carries drew identically.
        int sword = ConeGauge.halfSpan(SWORD);
        int dagger = ConeGauge.halfSpan(DAGGER);
        assertTrue(dagger - sword >= 4,
                "a 45 degree cone must be visibly wider than a 35 degree one, was "
                        + sword + " vs " + dagger);
    }

    @Test
    @DisplayName("the extremes use the full range")
    void usesTheRange() {
        assertEquals(22, ConeGauge.halfSpan(10.0F), "narrowest cone, floor");
        assertEquals(62, ConeGauge.halfSpan(80.0F), "widest cone, ceiling");
    }

    @Test
    @DisplayName("the rapier is the tightest and the sabre the widest, as designed")
    void extremesMatchTheDesign() {
        int rapier = ConeGauge.halfSpan(RAPIER);
        int sabre = ConeGauge.halfSpan(SABRE);
        assertTrue(rapier < ConeGauge.halfSpan(SWORD),
                "the rapier has a 12 degree cone and must draw tighter than a 35 degree sword");
        assertTrue(sabre > ConeGauge.halfSpan(AXE),
                "the sabre has a 75 degree cone and must draw wider than a 45 degree axe");
    }

    @Test
    @DisplayName("the brackets never sit on top of the crosshair")
    void clearsTheCrosshair() {
        // The crosshair is 15px and grows on a hit. If a bracket can land inside the crosshair's
        // own silhouette the two merge into an unreadable blob at exactly the moment the player
        // is trying to aim.
        for (float angle = 0.0F; angle <= 90.0F; angle += 1.0F) {
            int span = ConeGauge.halfSpan(angle);
            assertTrue(span > 15,
                    "a bracket at " + span + "px is inside the 15px crosshair, cone " + angle);
        }
    }
}
