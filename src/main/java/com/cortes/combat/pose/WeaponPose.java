package com.cortes.combat.pose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;


/**
 * One weapon's pose, declared in one file: what the first person viewmodel does, what the body does,
 * and where each moving part pivots.
 *
 * <h2>Why this class exists at all</h2>
 *
 * <p>Because a weapon's animation is currently <b>four files</b> -- a {@code ViewModelProfile} in
 * {@code ViewModelDefaults}, the channels it reads, {@code ViewModelMotion}'s clock, and the
 * constants in {@code BodyPoses} -- and a number written in the wrong one does not fail the build.
 * It fails on screen, three playtests later.</p>
 *
 * <p>This is the one file that holds all of it, and the reason it can be is {@link Axes}: the sign
 * conventions stop being tribal knowledge.</p>
 *
 * <h2>The two conventions, and why there are two</h2>
 *
 * <p><b>First person is {@code ItemViewModel}'s frame:</b> the item sits at
 * {@code ITEM_POS = (0.56, -0.52, -0.72)}, so {@code -Z} is away from the camera, and
 * {@code apply()} turns it with {@code .rotateX(pitch)}. A positive {@code rotateX} carries the muzzle
 * up, so <b>positive pitch is nose up</b>.</p>
 *
 * <p><b>Third person is {@code PlayerModelMixin}'s frame:</b> the pose is in degrees and the mixin
 * converts, and the convention is the opposite for the same word -- {@code HumanoidModel}'s
 * {@code ITEM} case writes {@code xRot * 0.5 - PI/10}, which is negative, and a negative arm pitch is
 * the arm coming <em>forward</em>.</p>
 *
 * <p><b>So "pitch" meant two different things on two sides of one codebase</b>, and it cost the
 * arcabuz three corrections -- the first of which flipped the sign by mistake, using the third
 * person's verified convention inside the first person's frame, and pointed the gun at the sky.</p>
 *
 * <p>{@link Axes} is declared once, in the file, and this class is the only place that knows both
 * sides. A JSON that omits it is rejected rather than defaulted: a default here is a silent guess,
 * and a silent guess on a sign is the exact bug that happened three times.</p>
 *
 * <h2>Units, and there is only one</h2>
 *
 * <p><b>Everything in this file is metres and degrees.</b> A {@link Part}'s pivot is a pose-space
 * point, not a model-space one, so the file that converts a Blockbench export divides by sixteen
 * once and the answer is written down here where an animation can read it.</p>
 *
 * <p>Having the model's own 0-16 grid inside the same file as a pose in metres is the trap this
 * removes: one number, one scale, and a pivot and a translation next to each other are directly
 * comparable -- which is what makes "the mecha is a hand's width ahead of the grip" checkable.</p>
 *
 * <h2>What is optional, and why that is the retrocompatibility</h2>
 *
 * <p>{@code body} and {@code parts} are both {@link Optional}. Thirty of the weapons have no body
 * pose today and only the arcabuz has a separated part, so a three-line JSON naming a resting pose
 * and two tracks is a <b>valid</b> file. That is what lets this land without rewriting thirty
 * weapons: they keep their Java profiles until somebody moves one, and each move is a single file
 * instead of four.</p>
 */
public record WeaponPose(String id,
                         Axes axes,
                         Optional<Parts> parts,
                         Transform rest,
                         List<Motion> tracks,
                         Optional<Body> body) {

    /** Every part of a weapon, by the name an animation refers to it by. */
    public record Parts(java.util.Map<String, Part> byName) {

        public static Parts empty() {
            return new Parts(java.util.Map.of());
        }

        public static final Codec<Part> PART_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.DOUBLE.fieldOf("pivot_x").forGetter(Part::pivotX),
                        Codec.DOUBLE.fieldOf("pivot_y").forGetter(Part::pivotY),
                        Codec.DOUBLE.fieldOf("pivot_z").forGetter(Part::pivotZ),
                        // **No default.** A part with no axis is a part somebody moves by eye, which
                        // is the bug this class was written to prevent.
                        Part.AXIS_CODEC.fieldOf("rotates_on").forGetter(Part::rotatesOn)
                ).apply(instance, Part::new));

        /**
         * A map keyed by the name, which DFU cannot build for us.
         *
         * <p>Written by hand because a map's entry order is not a record's field order and no shape
         * of {@code RecordCodecBuilder} produces one key per map entry.</p>
         */
        public static final Codec<Parts> CODEC =
                Codec.unboundedMap(Codec.STRING, PART_CODEC).xmap(Parts::new, Parts::byName);
    }

    /**
     * A named axis of rotation, written the way an animation thinks about it.
     *
     * <p><b>Declared before {@link Part} because {@code Part} names it in a field type.</b> A
     * nested record is only in scope from where it is written down, so a {@code Part} above an
     * {@code Axis} is a file that does not compile &mdash; and the fix is to move the declaration,
     * not to qualify the name twice.</p>
     */
    public record Axis(String name) {
    }

    /**
     * One moving part of a weapon: where it pivots and which axis it turns on.
     *
     * <p><b>{@code rotatesOn} is not optional and there is no default.</b> A pivot without an axis
     * is a part somebody is going to move "by eye", and the whole reason this class exists is that
     * three corrections of the arcabuz came from exactly that.</p>
     *
     * <p>Six values are legal, and they are written the way an animation thinks about them rather
     * than the way a matrix does: {@code +x -x +y -y +z -z}, resolved against {@link Axes} so that
     * a file which says {@code -z} means "away from the camera" in first person regardless of which
     * axis the maths ends up calling forward.</p>
     */
    public record Part(double pivotX, double pivotY, double pivotZ, Axis rotatesOn) {

        public static final Axis X = new Axis("x");
        public static final Axis NEG_X = new Axis("-x");
        public static final Axis Y = new Axis("y");
        public static final Axis NEG_Y = new Axis("-y");
        public static final Axis Z = new Axis("z");
        public static final Axis NEG_Z = new Axis("-z");

        /** The six legal axes. A string outside this list is a rejected file, not a default. */
        public static final List<Axis> ALL = List.of(X, NEG_X, Y, NEG_Y, Z, NEG_Z);

        public static final Codec<Axis> AXIS_CODEC = Codec.STRING.xmap(
                name -> {
                    for (Axis axis : ALL) {
                        if (axis.name().equals(name)) {
                            return axis;
                        }
                    }
                    throw new IllegalArgumentException("not an axis: " + name
                            + ". The six are " + ALL.stream().map(Axis::name).toList());
                },
                Axis::name);
    }

    /**
     * Where forward is, where up is, and what a positive pitch means.
     *
     * <p><b>Declared, and not derived.</b> Both frames are wrong about something and neither is
     * derivable from the other without reading two different classes, which is how the convention
     * gets lost in the first place.</p>
     */
    public record Axes(String forward, String up, PitchSense pitch) {

        public static final String FORWARD_NEG_Z = "-z";
        public static final String UP_Y = "y";

        /** What a positive pitch does to the weapon's nose. */
        public enum PitchSense {
            /** The first person frame: {@code .rotateX} carries the muzzle up. */
            UP_IS_POSITIVE,
            /** The body frame: a negative arm pitch is the arm coming forward. */
            UP_IS_NEGATIVE
        }

        /**
         * The frame {@code ItemViewModel} draws in, read out of its own code.
         *
         * <p>{@code ItemInHandRendererMixin}'s javadoc carries the proof: the item sits at
         * {@code ITEM_POS = 0.56 / -0.52 / -0.72}, and an item at a negative Z that is not behind
         * the camera is an item in front of it.</p>
         */
        public static final Axes FIRST_PERSON =
                new Axes(FORWARD_NEG_Z, UP_Y, PitchSense.UP_IS_POSITIVE);

        /** The frame {@code PlayerModelMixin} writes into, from {@code poseRightArm}'s {@code ITEM} case. */
        public static final Axes THIRD_PERSON =
                new Axes("-z", UP_Y, PitchSense.UP_IS_NEGATIVE);
    }

    /**
     * A displacement and a turn, in metres and degrees.
     *
     * <p>Degrees rather than radians because the file is read by people, and {@code BodyPoses} has
     * been in degrees from the start for the same reason. The mixin converts once, on the way in,
     * and a file that stored radians would be unreadable and would make a sign error invisible.</p>
     */
    public record Transform(double x, double y, double z,
                            double pitch, double yaw, double roll) {

        public static final Transform ZERO = new Transform(0, 0, 0, 0, 0, 0);

        public boolean isZero() {
            return x == 0.0 && y == 0.0 && z == 0.0
                    && pitch == 0.0 && yaw == 0.0 && roll == 0.0;
        }
    }

    /**
     * One track: a channel, a curve, and how far this weapon goes while that channel is open.
     *
     * <p>Names the channel and the curve as strings so a typo is a <b>failed load with a message
     * naming the word</b>, rather than a track that reads every value and therefore never moves
     * anything. That is the same shape as the missing {@code @SubscribeEvent} that cost the arcabuz
     * its fuse frames, and the reason it is checked rather than defaulted.</p>
     *
     * <p><b>The three tremor fields are the reason this record has six numbers and not three.</b>
     * They were missing when the format was written and the sabre's JSON caught it on the first run:
     * Java has six tracks and the transcribed file had five, because {@code Track#shaking} is a
     * <em>different kind</em> of track &mdash; a deterministic tremble layered on the motion, seeded
     * per strike &mdash; and a format with only a channel and an amplitude has no way to say one.
     *
     * <p>A guard that compares a JSON against Java found it on the day it was written, which is the
     * entire argument for having written one.</p>
     */
    public record Motion(String channel, String curve, Transform amplitude,
                         Transform tremor, double tremorHz, String tremorWeight) {

        /** The fourteen channels {@code Channel} declares. */
        public static final List<String> CHANNELS = List.of(
                "WINDUP", "STRIKE", "RECOVER", "COOLDOWN", "HOLD", "GUARD", "BREAK", "PARRY",
                "LUNGE", "SWEEP", "CHOP", "HELD", "RELOAD", "SHOOT");

        /** The six curves {@code Curve} declares. */
        public static final List<String> CURVES =
                List.of("LINEAR", "SMOOTH", "EXPO", "BACK", "ELASTIC", "BOUNCE");

        /**
         * Whether this track trembles at all.
         *
         * <p><b>And the answer is not "the amplitude is non-zero"</b>, because a tremble with no
         * frequency is silence: the sabre's is {@code (0, 0, 0.012)} with a pitch of 2, and asking
         * only about the amplitude gets both halves wrong &mdash; it says a track that only pitches
         * has no tremble, and a track that only has a frequency reads as having none.</p>
         */
        public boolean hasTremor() {
            return !tremor.isZero() || tremorHz != 0.0 || !"LINEAR".equals(tremorWeight);
        }

        /** Named {@code CODEC} because {@code Motion#CODEC} shadows nothing here but reads badly. */
        public static final Codec<Motion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                // **Both are validated against the enum names rather than defaulted.** A track
                // naming a channel that does not exist reads every value and therefore never moves
                // anything, which is the same shape as the missing `@SubscribeEvent` that cost the
                // arcabuz its fuse frames: a component in place and nothing drawing it.
                Codec.STRING.validate(name -> CHANNELS.contains(name)
                                ? com.mojang.serialization.DataResult.success(name)
                                : com.mojang.serialization.DataResult.error(() -> "not a channel: "
                                        + name + ". The fourteen are " + CHANNELS))
                        .fieldOf("channel").forGetter(Motion::channel),
                Codec.STRING.validate(name -> CURVES.contains(name)
                                ? com.mojang.serialization.DataResult.success(name)
                                : com.mojang.serialization.DataResult.error(() -> "not a curve: "
                                        + name + ". The six are " + CURVES))
                        .fieldOf("curve").forGetter(Motion::curve),
                TRANSFORM_CODEC.fieldOf("amplitude").forGetter(Motion::amplitude),
                // **All three optional and all three defaulting to "no tremble."** They are the
                // common case -- four of the sabre's six tracks have none -- and a file that wants
                // to say "this track shakes" has to write all three, because a tremble with no
                // frequency is silence and a frequency with no weight is a constant offset.
                TRANSFORM_CODEC.optionalFieldOf("tremor", Transform.ZERO).forGetter(Motion::tremor),
                Codec.DOUBLE.optionalFieldOf("tremor_hz", 0.0D).forGetter(Motion::tremorHz),
                Codec.STRING.validate(name -> CURVES.contains(name)
                                ? com.mojang.serialization.DataResult.success(name)
                                : com.mojang.serialization.DataResult.error(() -> "not a curve: "
                                        + name + ". The six are " + CURVES))
                        .optionalFieldOf("tremor_weight", "LINEAR").forGetter(Motion::tremorWeight)
        ).apply(instance, Motion::new));
    }

    /**
     * What the weapon does to the body carrying it.
     *
     * <p>Degrees, in the body's own convention, and <b>added</b> to whatever vanilla's walk cycle
     * put there -- the same thing every pose in {@code BodyPoses} does. The exception is the guard,
     * which replaces the weapon arm's pitch because {@code poseBlockingArm} has already moved it,
     * and that exception stays in {@code PlayerModelMixin} rather than becoming a field here.</p>
     */
    public record Body(Transform aim, Transform reload) {
    }

    public static final Codec<Transform> TRANSFORM_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(Transform::x),
                    Codec.DOUBLE.optionalFieldOf("y", 0.0D).forGetter(Transform::y),
                    Codec.DOUBLE.optionalFieldOf("z", 0.0D).forGetter(Transform::z),
                    Codec.DOUBLE.optionalFieldOf("pitch", 0.0D).forGetter(Transform::pitch),
                    Codec.DOUBLE.optionalFieldOf("yaw", 0.0D).forGetter(Transform::yaw),
                    Codec.DOUBLE.optionalFieldOf("roll", 0.0D).forGetter(Transform::roll)
            ).apply(instance, Transform::new));

    public static final Codec<Body> BODY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TRANSFORM_CODEC.fieldOf("aim").forGetter(Body::aim),
            TRANSFORM_CODEC.fieldOf("reload").forGetter(Body::reload)
    ).apply(instance, Body::new));


    public static final Codec<WeaponPose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(WeaponPose::id),
            Codec.STRING.xmap(
                            name -> "third_person".equals(name) ? Axes.THIRD_PERSON : Axes.FIRST_PERSON,
                            axes -> axes == Axes.FIRST_PERSON ? "first_person" : "third_person")
                    .fieldOf("axes").forGetter(WeaponPose::axes),
            Parts.CODEC.optionalFieldOf("parts").forGetter(WeaponPose::parts),
            TRANSFORM_CODEC.optionalFieldOf("rest", Transform.ZERO).forGetter(WeaponPose::rest),
            Motion.CODEC.listOf().optionalFieldOf("tracks", List.of()).forGetter(WeaponPose::tracks),
            BODY_CODEC.optionalFieldOf("body").forGetter(WeaponPose::body)
    ).apply(instance, WeaponPose::new));

    public WeaponPose {
        tracks = List.copyOf(tracks);
    }

    /** The part called {@code name}, or empty. An animation that asks for a missing part gets nothing. */
    public java.util.Optional<Part> part(String name) {
        return parts.map(p -> p.byName().get(name));
    }
}