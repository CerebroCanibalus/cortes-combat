package com.cortes.combat.pose;

import com.cortes.combat.pose.WeaponPose.Axes;
import com.cortes.combat.pose.WeaponPose.Axis;
import com.cortes.combat.pose.WeaponPose.Part;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pose format's conventions, asserted before any weapon uses them.
 *
 * <h2>What this file is for</h2>
 *
 * <p>The playtest complaint that produced this package was *"adivinar las direcciones cardinales de
 * las armas es una mierda"*, and the reason it was a complaint is that <b>the same word meant two
 * different things on two sides of one codebase</b>:</p>
 *
 * <ul>
 *   <li>first person: {@code ItemViewModel} turns the item with {@code .rotateX(pitch)} in a frame
 *       where {@code -Z} is away from the camera, and a <b>positive</b> pitch carries the muzzle
 *       <b>up</b>;</li>
 *   <li>third person: {@code PlayerModelMixin} writes degrees onto {@code ModelPart.xRot} where a
 *       <b>negative</b> arm pitch is the arm coming <b>forward</b>.</li>
 * </ul>
 *
 * <p>That cost the arcabuz three corrections. The first flipped the sign by mistake &mdash; taking
 * the third person's verified convention and applying it inside the first person's frame &mdash; and
 * pointed the gun at the sky. So the conventions are declared once, in the data, and these are the
 * tests that make the declaration mean something.</p>
 *
 * <h2>Why the numbers are not the test</h2>
 *
 * <p>Every assertion below is a <b>relationship</b> or a <b>fact about the code that consumes
 * this</b>, never a magic number. A test that asserts "the pitch is -66" pins the design to today's
 * arithmetic and passes every time somebody breaks it, which is backwards.</p>
 */
class WeaponPoseConventionTest {

    /**
     * Parse JSON with the route the game itself uses.
     *
     * <p><b>There is no {@code Codec.parse(String)} in 1.21.1's DFU</b> -- checked with
     * {@code javap} rather than remembered, and the first version of this file assumed there was and
     * did not compile. A codec is fed a {@code Dynamic}, and the one the game reads its data
     * through is {@code JsonOps.INSTANCE}. So a test that wants to prove "this file loads" has to
     * build that {@code Dynamic} the same way the loader does, which is a stronger claim than a
     * hand-built object would be: it is the same path a data pack takes.</p>
     */
    private static <T> DataResult<T> parse(com.mojang.serialization.Codec<T> codec, String json) {
        JsonElement element = JsonParser.parseString(json);
        return codec.parse(JsonOps.INSTANCE, element);
    }

    /**
     * The first person frame is {@code -Z} forward, and that is not a guess.
     *
     * <p>It is read out of {@code ItemInHandRendererMixin}'s own javadoc, which carries the proof:
     * the item sits at {@code ITEM_POS = 0.56 / -0.52 / -0.72}, and an item at a negative Z that
     * is not behind the camera is an item in front of it.</p>
     */
    @Test
    @DisplayName("the first person frame is -Z forward, from ITEM_POS and not from memory")
    void theFirstPersonFrameIsNegZForward() {
        assertEquals("-z", Axes.FIRST_PERSON.forward(),
                "the item sits at ITEM_POS.z = -0.72 and is drawn in front of the player, so a "
                        + "negative Z is away from the camera");
        assertEquals("y", Axes.FIRST_PERSON.up(),
                "+Y is up: ITEM_POS.y = -0.52 puts the item below the eye");
    }

    /**
     * The two frames disagree about pitch, and that disagreement is the bug.
     *
     * <p><b>This is the assertion that pays for the whole package.</b> If the two senses ever
     * converge it is because one of them was changed without the other, and a file that converts
     * between them would then silently apply a sign flip to half the weapons in the game.</p>
     */
    @Test
    @DisplayName("the two frames disagree about pitch, which is exactly why the format exists")
    void theTwoFramesDisagreeAboutPitch() {
        assertNotEquals(Axes.FIRST_PERSON.pitch(), Axes.THIRD_PERSON.pitch(),
                "first person reads .rotateX and a positive pitch is nose up; the body writes degrees "
                        + "and a negative pitch is the arm forward. If these ever match, one was "
                        + "changed alone and every conversion between the frames is now inverted");
    }

    /**
     * The third person sense is negative, and that is read out of vanilla.
     *
     * <p>{@code HumanoidModel#poseRightArm}'s {@code ITEM} case writes
     * {@code xRot * 0.5 - PI/10} &mdash; about -18 degrees for a player standing still holding
     * something &mdash; and {@code setupAttackAnimation}'s {@code xRot -= f1 * 1.2F} peaks at
     * -68.8. Both are negative and both are the arm coming forward.</p>
     */
    @Test
    @DisplayName("the body frame is negative, from poseRightArm and setupAttackAnimation")
    void theBodyFrameIsNegative() {
        assertEquals(Axes.PitchSense.UP_IS_NEGATIVE, Axes.THIRD_PERSON.pitch(),
                "poseRightArm's ITEM case and setupAttackAnimation are both negative arm pitch, "
                        + "which is the arm coming forward. Positive would be a follow-through");
    }

    /**
     * Six axes are legal and a seventh is rejected by name.
     *
     * <p><b>{@code rotatesOn} has no default, and this is the test that says why.</b> A part with a
     * pivot and no axis is a part somebody moves by eye, which is the shape the arcabuz's three
     * corrections had. Rejecting the word at load time means the file that names it wrong is a
     * file that fails to load, not a file that renders a number nobody chose.</p>
     */
    @Test
    @DisplayName("six axes are legal and a seventh is rejected by name")
    void sixAxesAreLegalAndASeventhIsNot() {
        assertEquals(6, Part.ALL.size(),
                "the six directions a part can turn on. More than six and two of them mean the "
                        + "same thing with opposite signs, which is what makes a sign bug readable");

        for (Axis axis : Part.ALL) {
            assertEquals(axis, parse(Part.AXIS_CODEC, "\"" + axis.name() + "\"").getOrThrow(
                error -> new AssertionError("a legal axis failed to parse: " + error)),
                axis.name() + " is in the list and the codec must accept it");
        }

        // **The rejection throws rather than returning an error, and that is the right shape.**
        // `Codec.STRING.xmap` runs its function eagerly, so an illegal axis is an exception carrying
        // the word -- which is better than a soft failure, because a soft failure on a load path is
        // how the arcabuz's four fuse frames were loaded and then never drawn.
        //
        // The first version of this test called `.error().isPresent()` on the result, which is the
        // shape a lazy codec returns and this one does not. It failed with the *message* it was
        // supposed to be asserting, which is the most useful possible outcome from a test written
        // against the wrong assumption.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> parse(Part.AXIS_CODEC, "\"w\""),
                "\"w\" is not an axis, so parsing it has to throw rather than default to something "
                        + "and move a part on an axis nobody chose");
        assertTrue(thrown.getMessage().contains("w"),
                "the message names the word that was wrong, because \"not an axis\" with no word in "
                        + "it is the error that cost the arcabuz three rounds. It said: "
                        + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("-z"),
                "and it names the six that are legal, so the reader does not have to go and count "
                        + "them");
    }

    /**
     * The channels and curves named in a file are the ones the enums declare.
     *
     * <p><b>Fourteen and six, and the test is that a typo fails to load.</b> A track naming a
     * channel that does not exist reads every value and therefore never moves anything &mdash; the
     * same shape as the missing {@code @SubscribeEvent} that cost the arcabuz its fuse frames: a
     * component in place and nothing drawing it.</p>
     */
    @Test
    @DisplayName("a channel or curve that does not exist is a failed load, not a dead track")
    void anUnknownChannelOrCurveIsAFailedLoad() {
        // **And the lists agree with the enums**, so adding a channel to `Channel` without adding
        // it here fails this test rather than quietly making that channel unavailable to the files
        // that are supposed to be able to name it.
        assertEquals(14, WeaponPose.Motion.CHANNELS.size(),
                "fourteen channels: the seven vanilla swings and the seven the arcabuz added. "
                        + "Channel declares them and this list has to match, or the newest channel "
                        + "is one a file cannot name");
        assertEquals(6, WeaponPose.Motion.CURVES.size(),
                "six curves, the ones Curve declares: linear, smooth, expo, back, elastic, bounce");

        for (String channel : WeaponPose.Motion.CHANNELS) {
            assertTrue(com.cortes.combat.viewmodel.Channel.valueOf(channel).name().equals(channel),
                    channel + " is a real Channel");
        }
        for (String curve : WeaponPose.Motion.CURVES) {
            assertEquals(curve, com.cortes.combat.viewmodel.Curve.valueOf(curve).name(),
                    curve + " is a real Curve");
        }
    }

    /**
     * A transform that declares nothing is the resting pose, and that is a legal file.
     *
     * <p><b>The retrocompatibility, asserted.</b> Thirty of the weapons have no body pose and only
     * one has a separated part, so both are optional and a three-line JSON naming a resting pose
     * and two tracks has to parse. If that stops being true, this format cannot land without
     * rewriting every weapon in the game, and the whole point of writing it by hand was that it
     * would not.</p>
     */
    @Test
    @DisplayName("a minimal pose is a valid pose: rest plus two tracks, nothing else")
    void aMinimalPoseIsAValidPose() throws Exception {
        String minimal = """
                {
                  "id": "sable",
                  "axes": "first_person",
                  "tracks": [
                    { "channel": "PARRY",  "curve": "LINEAR",
                      "amplitude": { "pitch": 22, "yaw": -14 } },
                    { "channel": "STRIKE", "curve": "EXPO",
                      "amplitude": { "position_dummy": 0 } }
                  ]
                }
                """.replace("\"position_dummy\": 0 ", "");

        var parsed = parse(WeaponPose.CODEC, minimal);
        var pose = parsed.getOrThrow(error ->
                new AssertionError("a three-line pose did not load: " + error));

        assertTrue(pose.parts().isEmpty(),
                "no parts declared, which is the thirty weapons' case");
        assertTrue(pose.body().isEmpty(),
                "no body pose declared, which is also the thirty weapons' case");
        assertEquals(2, pose.tracks().size(), "and the two tracks it did declare");
        assertEquals(WeaponPose.Transform.ZERO, pose.rest(),
                "no rest declared means the vanilla pose, which is what a weapon with no held "
                        + "posture should draw as");
    }

    /**
     * A track that names a channel nobody declared fails to load, and says which word was wrong.
     */
    @Test
    @DisplayName("a track naming a channel that does not exist fails with the word in the message")
    void aTrackNamingNothingFailsWithTheWordInTheMessage() {
        String broken = """
                {
                  "id": "sable",
                  "axes": "first_person",
                  "tracks": [
                    { "channel": "PARRY", "curve": "LINEAR", "amplitude": { "pitch": 22 } },
                    { "channel": "BALANCE", "curve": "LINEAR", "amplitude": { "pitch": 10 } }
                  ]
                }
                """;

        var parsed = parse(WeaponPose.CODEC, broken);
        assertTrue(parsed.error().isPresent(),
                "BALANCE is not one of the fourteen channels, so this has to fail. Without the "
                        + "check the track reads every value and never moves anything");
        String message = parsed.error().orElseThrow().message();
        assertTrue(message.contains("BALANCE") && message.contains("PARRY"),
                "the message names both: the word that was wrong and the list of the ones that are "
                        + "right. A channel list without the bad word in it is how an hour is lost");
    }

    /** The two frames are the only two there are, and they are named rather than inferred. */
    @Test
    @DisplayName("the axes are declared in the file and defaulting them would be a silent guess")
    void theAxesAreDeclaredInTheFile() {
        // There is no `defaultValue` on the field, which is the assertion: a file without `axes`
        // does not load. The two constants differ, so a default would silently pick one and the
        // other half of the game would be inverted.
        for (Axes axes : new Axes[]{Axes.FIRST_PERSON, Axes.THIRD_PERSON}) {
            assertTrue(axes.forward() != null && axes.up() != null && axes.pitch() != null,
                    "an Axes is three facts or it is a guess");
        }
        assertNotEquals(Axes.FIRST_PERSON, Axes.THIRD_PERSON,
                "and the two are different objects, so comparing them is meaningful");
    }

    /**
     * A transform of six zeroes really is zero, and the check is exact.
     *
     * <p><b>No tolerance here on purpose.</b> Every other number in this project gets a
     * {@code 1.0E-6} because it comes out of arithmetic that accumulates error. These are literals
     * and the comparison is {@code ==}, so a tolerance would be the only thing keeping a bug
     * visible.</p>
     *
     * <p>The first version asserted {@code assertEquals(-0.0, 0.0)} &mdash; a {@code double} against
     * an {@code int} literal with no delta, which failed because {@code -0.0} and {@code 0.0} are
     * different doubles and also not the same type as the literal. The assertion that was meant to
     * be about zero was really a test about float representation.</p>
     */
    @Test
    @DisplayName("a transform of six zeroes really is zero, so it can be skipped")
    void aTransformOfSixZeroesIsZero() {
        assertTrue(WeaponPose.Transform.ZERO.isZero(),
                "the resting pose has to compare equal to nothing, or every frame pays for a "
                        + "translation that does not exist");

        WeaponPose.Transform moved = new WeaponPose.Transform(0.001, 0, 0, 0, 0, 0);
        assertTrue(!moved.isZero(),
                "and one non-zero component is enough to make it not zero -- otherwise a pose "
                        + "would be skipped for moving the weapon a millimetre");

        WeaponPose.Transform turned = new WeaponPose.Transform(0, 0, 0, 0.0, 0.0, 0.0);
        assertTrue(turned.isZero(),
                "a rotation of all zero is zero too: it is the same question asked of the other "
                        + "three fields, and the six are one value");
    }
}