package com.cortes.combat.pose;

import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.Curve;
import com.cortes.combat.viewmodel.ViewModelProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponPoseViewsTest {

    private static WeaponPose poseOf(WeaponPose.Motion... tracks) {
        return new WeaponPose("test:pose", WeaponPose.Axes.FIRST_PERSON, Optional.empty(),
                new WeaponPose.Transform(0.01, 0, 0, 0, 0, 0),
                List.of(tracks), Optional.empty());
    }

    private static WeaponPose.Motion motion(String channel, String curve) {
        return new WeaponPose.Motion(channel, curve,
                new WeaponPose.Transform(0, 0.1, 0, 8.0, 0, 0),
                WeaponPose.Transform.ZERO, 0.0, "LINEAR");
    }

    /** La traducción conserva canal, curva y amplitud número por número. */
    @Test
    @DisplayName("tracks translate channel curve and amplitude unchanged")
    void tracksTranslateUnchanged() {
        ViewModelProfile profile = WeaponPoseViews.toProfile(poseOf(motion("WINDUP", "LINEAR")));
        assertEquals(1, profile.tracks().size());
        assertEquals(Channel.WINDUP, profile.tracks().get(0).channel());
        assertEquals(Curve.LINEAR, profile.tracks().get(0).curve());
        assertEquals(0.1, profile.tracks().get(0).amplitude().y(), 1e-9);
        assertEquals(8.0, profile.tracks().get(0).amplitude().pitch(), 1e-9);
        assertEquals(0.01, profile.rest().x(), 1e-9, "el rest viaja con los tracks");
    }

    /** El temblor viaja con sus tres campos. */
    @Test
    @DisplayName("tremor travels with all three fields")
    void tremorTravels() {
        WeaponPose.Motion shaking = new WeaponPose.Motion("CHOP", "SMOOTH",
                new WeaponPose.Transform(0, 0, 0, -96.0, 0, 0),
                new WeaponPose.Transform(0, 0, 0.012, 2.0, -1.2, 1.0), 17.0, "EXPO");
        ViewModelProfile profile = WeaponPoseViews.toProfile(poseOf(shaking));
        assertEquals(Channel.CHOP, profile.tracks().get(0).channel());
        assertEquals(0.012, profile.tracks().get(0).tremor().z(), 1e-9);
        assertEquals(17.0F, profile.tracks().get(0).tremorHz());
        assertEquals(Curve.EXPO, profile.tracks().get(0).tremorWeight());
    }

    /** Canal o curva fuera del enum: error nombrando la palabra, nunca un default. */
    @Test
    @DisplayName("unknown channel or curve fails naming the word")
    void unknownNamesFail() {
        IllegalArgumentException channel = assertThrows(IllegalArgumentException.class,
                () -> WeaponPoseViews.toProfile(poseOf(motion("VOLAR", "LINEAR"))));
        assertTrue(channel.getMessage().contains("VOLAR"));

        IllegalArgumentException curve = assertThrows(IllegalArgumentException.class,
                () -> WeaponPoseViews.toProfile(poseOf(motion("WINDUP", "SERPENTINA"))));
        assertTrue(curve.getMessage().contains("SERPENTINA"));
    }
}
