package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a track adds up, and that a track at rest adds nothing.
 *
 * <p>The second one is the invariant the whole composition model stands on. Profiles are a plain
 * sum, so a track that is not exactly zero at rest quietly moves the item away from the vanilla
 * pose for as long as the player is standing still, and there is no way to tell a weapon that
 * drifts from a weapon that is being drawn on purpose.</p>
 */
class TrackTest {

    private static final float EXACT = 1.0E-6F;

    private static final Offset SOME_MOVEMENT =
            new Offset(0.1, -0.2, 0.3, -12.0, 5.0, 2.0);

    @ParameterizedTest
    @EnumSource(Channel.class)
    @DisplayName("at rest every channel is zero, so every track is zero")
    void zeroAtRest(Channel channel) {
        ViewModelInput atRest = ViewModelInput.RESTING;
        for (Curve curve : Curve.values()) {
            Offset offset = Track.of(channel, curve, SOME_MOVEMENT).evaluate(atRest);
            assertTrue(offset.isZero(), channel + "/" + curve + " should not move a resting item");
        }
    }

    @ParameterizedTest
    @EnumSource(Channel.class)
    @DisplayName("a trembling track at rest is zero too, weight included")
    void trembleIsSilentAtRest(Channel channel) {
        // The tremor weight is a curve on the channel, so at rest it is zero and the whole track
        // is zero. This is the test that catches a track written with a non-zero weight floor,
        // which would shake a weapon that is sitting in a player's hand.
        ViewModelInput atRest = ViewModelInput.RESTING;
        Offset tremor = new Offset(0.0, 0.0, 0.02, 3.0, 0.0, 1.0);
        for (Curve curve : Curve.values()) {
            for (Curve weight : Curve.values()) {
                Track track = Track.shaking(channel, curve, Offset.ZERO, tremor, 14.0F, weight);
                assertTrue(track.evaluate(atRest).isZero(),
                        channel + "/" + curve + " with weight " + weight
                                + " should not tremble at rest");
            }
        }
    }

    @Test
    @DisplayName("a track at full channel reaches exactly its amplitude")
    void fullChannelDeliversTheAmplitude() {
        Offset offset = Track.of(Channel.WINDUP, Curve.SMOOTH, SOME_MOVEMENT)
                .evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 0.0F, 1));
        assertEquals(SOME_MOVEMENT.x(), offset.x(), EXACT);
        assertEquals(SOME_MOVEMENT.y(), offset.y(), EXACT);
        assertEquals(SOME_MOVEMENT.z(), offset.z(), EXACT);
        assertEquals(SOME_MOVEMENT.pitch(), offset.pitch(), EXACT);
        assertEquals(SOME_MOVEMENT.yaw(), offset.yaw(), EXACT);
        assertEquals(SOME_MOVEMENT.roll(), offset.roll(), EXACT);
    }

    @Test
    @DisplayName("SMOOTH rises monotonically, so the wind-up never jumps back")
    void windUpIsMonotonic() {
        Track track = Track.of(Channel.WINDUP, Curve.SMOOTH, new Offset(0, 0, -0.2, 0, 0, 0));
        float previous = Float.NEGATIVE_INFINITY;
        for (int i = 0; i <= 100; i++) {
            float t = i / 100.0F;
            float depth = (float) -track.evaluate(new ViewModelInput(t, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 0.0F, 1)).z();
            assertTrue(depth >= previous - EXACT,
                    "the wind-up went backwards at t=" + t + ": " + depth + " after " + previous);
            previous = depth;
        }
    }

    @Test
    @DisplayName("two tracks on different channels do not cancel each other by accident")
    void channelsAreIndependent() {
        Track windUp = Track.of(Channel.WINDUP, Curve.SMOOTH, new Offset(0, 0, -0.2, 0, 0, 0));
        Track strike = Track.of(Channel.STRIKE, Curve.EXPO, new Offset(0, 0, 0.3, 0, 0, 0));
        ViewModelInput both = new ViewModelInput(1.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 0.0F, 1);
        // Both active at once: the sum, not one winning.
        assertEquals(0.1F, windUp.evaluate(both).z() + strike.evaluate(both).z(), EXACT);
    }

    @Test
    @DisplayName("a tremble is the same every time it is evaluated at the same instant")
    void trembleIsDeterministic() {
        Track track = Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                new Offset(0, 0, 0.02, 3.0, 0, 1.0), 14.0F, Curve.SMOOTH);
        ViewModelInput frame = new ViewModelInput(1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 1.2345F, 7);
        assertEquals(track.evaluate(frame).z(), track.evaluate(frame).z(), 0.0,
                "the same frame evaluated twice must give the same tremble");
    }

    @Test
    @DisplayName("the tremble is different across swings, or a repeat charge looks like a loop")
    void trembleChangesWithTheSeed() {
        Track track = Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                new Offset(0, 0, 0.02, 3.0, 0, 1.0), 14.0F, Curve.SMOOTH);
        float first = (float) track.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 1.2345F, 1)).z();
        float second = (float) track.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 1.2345F, 2)).z();
        assertFalse(first == second, "two swings at the same instant should not shake identically");
    }

    @Test
    @DisplayName("a profile with no tracks moves nothing and reports itself idle")
    void emptyProfileIsInert() {
        assertTrue(ViewModelProfile.NONE.isEmpty());
        assertTrue(ViewModelProfile.NONE.idleFor(ViewModelInput.RESTING));
        assertTrue(ViewModelProfile.NONE.evaluate(ViewModelInput.RESTING).isZero());
    }

    @Test
    @DisplayName("a profile that would move the item says so instead of claiming to be idle")
    void activeProfileIsNotIdle() {
        ViewModelProfile profile = ViewModelProfile.of(
                Track.of(Channel.WINDUP, Curve.SMOOTH, SOME_MOVEMENT));
        assertTrue(profile.idleFor(ViewModelInput.RESTING));
        assertFalse(profile.idleFor(new ViewModelInput(0.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 0.0F, 1)));
    }
}
