package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Everything a viewmodel profile gets to look at for one frame.
 *
 * <p>One immutable snapshot, built once per frame and then read by every track. That is the
 * performance story of the whole library: a profile is not a function that queries the game, it
 * is a function of this record. Evaluating a profile touches no Minecraft state, allocates
 * nothing, and cannot be surprised by a value changing halfway through the sum.</p>
 *
 * <p>Every channel is a 0..1 ratio except the one-shots with a shape of their own:
 * {@link #strike} is the cut and {@link #recover} is the weight that follows it. They are two
 * separate beats because the playtest showed that one channel for both averages them into a
 * single shove.</p>
 *
 * <p>{@link #holding} is the attack button, {@link #guard} is the stance, {@link #parry} is the
 * sabre's half second, {@link #lunge} is the ropera's step and {@link #sweep} is the halberd's arc.
 * They are different states that used to be confused for each other, and keeping them apart is
 * what stops a punch from raising a shield.</p>
 *
 * <p><b>{@link #strike} and {@link #chop} are two different swings and the difference is the
 * shape.</b> The strike is a bump -- up and back -- which suits a thrust, and the playtest called
 * the ropera's and the dagger's version excellent. The chop only ever falls, because the normal
 * hit was reported as "it starts from below and goes up" and "when I let go of the charge the hit
 * goes up as well". A bump cannot be made to fall; that needs its own number.</p>
 *
 * <p><b>The last three are the arcabuz's.</b> {@link #held} is a pose rather than a movement, which
 * is why the idle state needed no channel at all: it is the resting pose. {@link #reload} is a bump
 * over half a second. {@link #shoot} is a ramp that is held for as long as the fuse burns, because
 * a bump would be over before the player saw it start.</p>
 *
 * <p>{@link #time} is the only field that is not a ratio, and it exists so that a tremble can
 * be a function of the clock instead of a function of random numbers.</p>
 *
 * <p>Pure, so a profile can be evaluated in a unit test with nothing else loaded.</p>
 */
public record ViewModelInput(float windup, float strike, float recover,
                             float cooldown, float guard, float guardBreak,
                             float parry, float lunge, float sweep, float chop,
                             float held, float reload, float shoot,
                             boolean holding, float time, int seed) {

    /** At rest: nothing happening, no cooldown running, guard down, blade up, feet planted. */
    public static final ViewModelInput RESTING =
            new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 0);

    /** One line to build a frame for a test that does not care about the follow-through. */
    public static ViewModelInput of(float windup, float strike) {
        return new ViewModelInput(windup, strike, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /**
     * The same frame with a firestone in the pan, for a test that only cares about a loaded gun.
     *
     * <p><b>Built by hand rather than by ticking a motion.</b> The arcabuz's loaded posture is the
     * one thing {@code ViewModelMotion} ramps, so driving the motion to arrive at a loaded frame
     * would test the ramp and not the posture. This builds the frame directly, which is what a test
     * about <em>where the weapon points</em> needs and what a test about the ramp does not.</p>
     */
    public static ViewModelInput loaded(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, howFar, 0.0F, 0.0F, false, 0.0F, 0);
    }

    /** The same frame with the guard up, for a test that only cares about a stance. */
    public static ViewModelInput guarding(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, howFar, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame with the guard broken, for a test that only cares about the collapse. */
    public static ViewModelInput broken(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, howFar,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame with the blade out, for a test that only cares about the parry. */
    public static ViewModelInput parrying(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                howFar, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame in the follow-through, for a test that only cares about the weight. */
    public static ViewModelInput recovering(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, howFar, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame at the top of a lunge, for a test that only cares about the step. */
    public static ViewModelInput lunging(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, howFar, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame mid-sweep, for a test that only cares about the arc. */
    public static ViewModelInput sweeping(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, howFar, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /**
     * The same frame part way through a chop, for a test that only cares about the fall.
     *
     * <p>The chop falls from one to zero, so a test asking for {@code 0.5F} is asking for the arm
     * halfway down and not for a swing half over. That is the difference the whole channel exists
     * for and it is why it gets its own factory instead of going through {@link #of}.</p>
     */
    public static ViewModelInput chopping(float howHigh) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, howHigh, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame with the weapon loaded: the arcabuz between shots, waiting for a fuse. */
    public static ViewModelInput loaded() {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame at the bottom of a reload. */
    public static ViewModelInput reloading(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, howFar, 0.0F, false, 0.0F, 1);
    }

    /** The same frame part way through the fuse. */
    public static ViewModelInput shooting(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, howFar, false, 0.0F, 1);
    }

    public float read(Channel channel) {
        return channel.read(this);
    }
}