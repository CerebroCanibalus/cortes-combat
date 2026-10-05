package com.cortes.combat.pose;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelInput;
import com.cortes.combat.viewmodel.ViewModelProfile;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the sable's JSON and its Java profile are the same numbers, and stay that way.
 *
 * <h2>Why this file exists before anything uses the format</h2>
 *
 * <p><b>A copied number is a second truth.</b> The whole reason for the pose format is that an
 * animation is currently four files and a number written in the wrong one fails on screen three
 * playtests later. Writing the sable down as JSON *without this test* would add a fifth file and
 * keep every problem: two places to edit, one of them invisible to the build, and no way to tell
 * which one a tuning session changed.</p>
 *
 * <p>So the JSON is not authoritative yet and does not pretend to be. It is a <em>transcribed</em>
 * copy, and this file is what makes the transcription safe: it walks both sides and fails on the
 * first number that has drifted.</p>
 *
 * <h2>What happens when they do drift</h2>
 *
 * <p>Nothing automatic. This test going red means somebody tuned the Java and did not update the
 * JSON, or the other way round, and the fix is to decide which one is the intention &mdash; which
 * is a playtest question, not a build question.</p>
 */
class SabrePoseMatchesJavaTest {

    private static final Path POSE =
            Path.of("src", "main", "resources", "data", "cortescombat", "poses", "shotel.json");

    /** The pose directory, walked so a new file is checked the day it is added. */
    private static final Path POSES =
            Path.of("src", "main", "resources", "data", "cortescombat", "poses");

    private static WeaponPose sable() {
        assertTrue(Files.isRegularFile(POSE),
                "no pose at " + POSE.toAbsolutePath() + ", so there is nothing to compare and "
                        + "this file would pass for the wrong reason");
        String json = read(POSE);
        var parsed = WeaponPose.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(json));
        return parsed.getOrThrow(error -> new AssertionError("the sable pose did not load: " + error));
    }

    /**
     * The track's declared amplitude, in the format's own type.
     *
     * <p><b>And that is the whole point of this method.</b> {@code Track#evaluate} answers
     * "where is the weapon <em>now</em>", which includes the tremble, and the tremble is noise
     * seeded per strike &mdash; so evaluating a track and reading the numbers back is a way of
     * never quite getting the amplitude. The first version of this test did exactly that and
     * reported a zero amplitude in Java where the file said {@code -0.04}, because it had asked a
     * question the code does not answer.</p>
     *
     * <p>A sixth field to convert into a sixth field is boring, and it is here because the format
     * and the renderer have different types on purpose: {@code Offset} is what the renderer
     * consumes and {@code Transform} is what a file declares.</p>
     */
    private static WeaponPose.Transform amplitudeOf(com.cortes.combat.viewmodel.Track track) {
        var o = track.amplitude();
        return new WeaponPose.Transform(o.x(), o.y(), o.z(), o.pitch(), o.yaw(), o.roll());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * A frame with <b>one</b> channel at one and every other at zero.
     *
     * <p>Named per channel because {@code ViewModelInput} has one builder each, and the default of
     * "open them all" is what made the first version of this file compare a sum of five tracks
     * against a single one.</p>
     */
    private static ViewModelInput onlyThisChannel(Channel channel) {
        ViewModelInput rest = ViewModelInput.RESTING;
        return switch (channel) {
            case WINDUP -> ViewModelInput.of(1.0F, 0.0F);
            case STRIKE -> ViewModelInput.of(0.0F, 1.0F);
            case RECOVER -> rest.recovering(1.0F);
            case GUARD -> rest.guarding(1.0F);
            case BREAK -> rest.broken(1.0F);
            case PARRY -> rest.parrying(1.0F);
            case LUNGE -> rest.lunging(1.0F);
            case SWEEP -> rest.sweeping(1.0F);
            case CHOP -> rest.chopping(1.0F);
            case HELD -> rest.loaded(1.0F);
            case RELOAD -> rest.reloading(1.0F);
            case SHOOT -> rest.shooting(1.0F);
            case COOLDOWN, HOLD -> rest;
        };
    }

    /**
     * The five tracks, one for one, with the same numbers.
     *
     * <p><b>A tolerance of 1e-9 on numbers that were written in two places.</b> These are literals
     * on both sides, so anything above zero means a typo, and the tolerance exists only because
     * they arrive as JSON doubles rather than as the source text.</p>
     */
    @Test
    @DisplayName("the five tracks say the same thing in the JSON and in Java")
    void theFiveTracksSayTheSameThing() {
        WeaponPose pose = sable();
        ViewModelProfile java = ViewModelDefaults.of(com.cortes.combat.core.weapon.WeaponProfile.SABRE);

        assertEquals(java.tracks().size(), pose.tracks().size(),
                "the sabre has five tracks in Java. The JSON has a different number, so one of "
                        + "them gained or lost a track and the other is now a stale copy");

        // **Two tracks can share a channel**, so this is by position and not by channel name. The
        // sabre has two WINDUP tracks: the cock of the arm, and the tremble layered on it, and
        // matching them by channel alone would compare the tremble against the cock and pass on
        // both if the numbers were swapped.
        assertEquals(java.tracks().size(), pose.tracks().size(),
                "the sabre has six tracks in Java: five motions and one tremble. The JSON has a "
                        + "different number, so one of them gained or lost a track and the other "
                        + "is now a stale copy");

        for (int i = 0; i < java.tracks().size(); i++) {
            var track = java.tracks().get(i);
            WeaponPose.Motion mine = pose.tracks().get(i);

            assertEquals(track.channel().name(), mine.channel(),
                    "track " + i + " is on " + track.channel() + " in Java and " + mine.channel()
                            + " in the JSON, so the two files are not describing the same weapon");
            assertEquals(track.curve().name(), mine.curve(),
                    track.channel() + " is " + track.curve() + " in Java and " + mine.curve()
                            + " in the JSON. A curve is the shape of the motion, so a change here "
                            + "is a change to how long the weapon stays out and how it comes back");

            // **One channel open, and a track that trembles is compared on its own amplitude.**
            //
            // Two fixes, and the second one is the interesting one. The first opened every channel
            // at once, so the answer belonged to a sum of five tracks rather than to any of them --
            // which is what made the sabre's two WINDUP tracks look like a disagreement.
            //
            // The second is that {@code Track#evaluate} adds its tremble, and the tremble is noise
            // seeded per strike. So a track's <em>amplitude</em> cannot be read back out of its
            // evaluated result: a zero amplitude on the trembling track still produces -0.0014 into
            // the screen, because that is the whole job of a tremble. `amplitude()` is the field,
            // and the field is what the JSON has to match.
            WeaponPose.Transform fromJava = amplitudeOf(track);
            WeaponPose.Transform a = mine.amplitude();

            assertEquals(fromJava.x(), a.x(), 1.0E-9,
                    track.channel() + " moved the weapon " + fromJava.x() + " sideways in Java and "
                            + a.x() + " in the JSON");
            assertEquals(fromJava.y(), a.y(), 1.0E-9,
                    track.channel() + " moved it " + fromJava.y() + " up in Java and " + a.y()
                            + " in the JSON");
            assertEquals(fromJava.z(), a.z(), 1.0E-9,
                    track.channel() + " pushed it " + fromJava.z() + " into the screen in Java and "
                            + a.z() + " in the JSON");
            assertEquals(fromJava.pitch(), a.pitch(), 1.0E-9,
                    track.channel() + " tilted it " + fromJava.pitch() + " degrees in Java and "
                            + a.pitch() + " in the JSON. **This is the number that has a sign, and a "
                            + "sign that differs here is the bug that pointed the arcabuz at the sky**");
            assertEquals(fromJava.yaw(), a.yaw(), 1.0E-9,
                    track.channel() + " turned it " + fromJava.yaw() + " in Java and " + a.yaw()
                            + " in the JSON");
            assertEquals(fromJava.roll(), a.roll(), 1.0E-9,
                    track.channel() + " rolled it " + fromJava.roll() + " in Java and " + a.roll()
                            + " in the JSON");

            // **And the three tremor numbers**, which is the whole reason this test existed: the
            // format was written with three fields per track and the sabre turned out to need six.
            assertEquals(track.tremor().x(), mine.tremor().x(), 1.0E-9,
                    "track " + i + " trembles " + track.tremor().x() + " sideways in Java and "
                            + mine.tremor().x() + " in the JSON");
            assertEquals(track.tremor().y(), mine.tremor().y(), 1.0E-9,
                    "track " + i + " trembles " + track.tremor().y() + " up in Java and "
                            + mine.tremor().y() + " in the JSON");
            assertEquals(track.tremor().z(), mine.tremor().z(), 1.0E-9,
                    "track " + i + " trembles " + track.tremor().z() + " into the screen in Java "
                            + "and " + mine.tremor().z() + " in the JSON");
            assertEquals(track.tremor().pitch(), mine.tremor().pitch(), 1.0E-9,
                    "track " + i + " shakes its pitch by " + track.tremor().pitch() + " degrees in "
                            + "Java and " + mine.tremor().pitch() + " in the JSON");
            assertEquals(track.tremor().yaw(), mine.tremor().yaw(), 1.0E-9,
                    "track " + i + " shakes its yaw by " + track.tremor().yaw() + " in Java and "
                            + mine.tremor().yaw() + " in the JSON");
            assertEquals(track.tremor().roll(), mine.tremor().roll(), 1.0E-9,
                    "track " + i + " shakes its roll by " + track.tremor().roll() + " in Java and "
                            + mine.tremor().roll() + " in the JSON");
            assertEquals(track.tremorHz(), mine.tremorHz(), 1.0E-9,
                    "track " + i + " trembles at " + track.tremorHz() + " hertz in Java and "
                            + mine.tremorHz() + " in the JSON. **A tremble with no frequency is "
                            + "silence**, so this number decides whether the weapon shakes at all");
            assertEquals(track.tremorWeight().name(), mine.tremorWeight(),
                    "track " + i + " weights its tremble with " + track.tremorWeight() + " in Java "
                            + "and " + mine.tremorWeight() + " in the JSON, which is what makes it a "
                            + "suspicion at half charge and unmistakable at full");
        }
    }

    /** The resting pose, which for the sabre is vanilla's: `owningSwing` declares none. */
    @Test
    @DisplayName("the sabre rests on the vanilla pose, and the JSON says so with zeros")
    void theSabreRestsOnTheVanillaPose() {
        ViewModelProfile java =
                ViewModelDefaults.of(com.cortes.combat.core.weapon.WeaponProfile.SABRE);
        assertEquals(com.cortes.combat.viewmodel.Offset.ZERO, java.rest(),
                "the sabre's profile is `owningSwing` with no rest, which is a fact the JSON has "
                        + "to be able to express: a weapon held exactly as vanilla holds it");

        assertTrue(sable().rest().isZero(),
                "and the JSON declares six zeroes, not an absent rest. `rest` missing means the "
                        + "vanilla pose and `rest` zero means the same pose, and the second one is "
                        + "what a reader of the file can see");
    }

    /**
     * The body pose, and here the Java side is two constants rather than a profile.
     *
     * <p><b>Read off {@code BodyPoses#parrying}, not invented.</b> The arm pitch is -96 and the
     * roll -22, both with their reason written down in that file &mdash; the pitch from
     * {@code PARRY_ARM_PITCH} and the roll from the same place the strike's roll came from, which
     * cites vanilla's own swing.</p>
     */
    @Test
    @DisplayName("the body pose is the one BodyPoses writes, transcribed")
    void theBodyPoseIsTheOneBodyPosesWrites() {
        var parry = com.cortes.combat.thirdperson.BodyPoses.parrying(1.0F);

        assertEquals(parry.rightArmPitch(), (float) sable().body().orElseThrow().aim().pitch(), 1.0E-6,
                "PARRY_ARM_PITCH is -96 and the JSON has to say the same. This is the arm going up "
                        + "across the body on the parry, and a sign error here is a guard the "
                        + "playtest already reported as backwards");
        assertEquals(parry.rightArmRoll(), (float) sable().body().orElseThrow().aim().roll(), 1.0E-6,
                "and the roll, which BodyPoses takes from vanilla's own swing direction");
    }

    /**
     * Every pose file that exists parses, and every track in it names something real.
     *
     * <p><b>So a file added tomorrow is checked today.</b> The list of files is walked rather than
     * written out, because a hand-written list is a second thing to forget &mdash; the same lesson
     * as {@code LanguageFilesTest} walking the enums.</p>
     */
    @Test
    @DisplayName("every pose file on disk loads, and every track in it names a real channel")
    void everyPoseFileLoads() throws IOException {
        assertTrue(Files.isDirectory(POSES),
                "no pose directory at " + POSES.toAbsolutePath() + ", so this test passes for the "
                        + "wrong reason");

        List<String> loaded = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> found = Files.list(POSES)) {
            found.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
        }
        assertFalse(files.isEmpty(), "no pose files at all, which is the state this file was "
                + "written to end");

        for (Path file : files) {
            var parsed = WeaponPose.CODEC.parse(JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString(read(file)));
            WeaponPose pose = parsed.getOrThrow(error -> new AssertionError(
                    file.getFileName() + " did not load: " + error));

            assertTrue(pose.tracks().stream().allMatch(m ->
                            WeaponPose.Motion.CHANNELS.contains(m.channel())),
                    file.getFileName() + " names a track on a channel that does not exist");
            assertTrue(pose.tracks().stream().allMatch(m ->
                            WeaponPose.Motion.CURVES.contains(m.curve())),
                    file.getFileName() + " names a curve that does not exist");
            loaded.add(file.getFileName().toString());
        }
        assertTrue(loaded.contains("shotel.json"),
                "and the sabre's is among them, because it is the one the format was proven on");
    }

    /**
     * A pose file has no parts yet, and that is a fact about the art rather than about the format.
     *
     * <p><b>Stated so it cannot be mistaken for an oversight.</b> {@code parts} exists and the
     * convention test covers its axes, but the sabre's model is {@code item/handheld} with a flat
     * texture: there is no drawn geometry to hang a pivot on. The arcabuz is the one weapon with a
     * Blockbench model and separate elements, so it is the first one that can use this.</p>
     */
    @Test
    @DisplayName("the sabre has no parts, and that is the model and not the format")
    void theSabreHasNoParts() {
        assertTrue(sable().parts().isEmpty(),
                "the sabre's model is item/handheld over a flat texture. There is no geometry to "
                        + "declare a pivot on, so `parts` is empty and that is correct. The first "
                        + "weapon that uses it is the arcabuz, whose model has named elements");
    }
}
