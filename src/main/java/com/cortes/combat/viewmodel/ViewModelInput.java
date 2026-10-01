package com.cortes.combat.viewmodel;

/**
 * Everything a viewmodel profile gets to look at for one frame.
 *
 * <p>One immutable snapshot, built once per frame and then read by every track. That is the
 * performance story of the whole library: a profile is not a function that queries the game, it
 * is a function of this record. Evaluating a profile touches no Minecraft state, allocates
 * nothing, and cannot be surprised by a value changing halfway through the sum.</p>
 *
 * <p>Every channel is a 0..1 ratio except two one-shots with a shape of their own:
 * {@link #strike} is the cut and {@link #recover} is the weight that follows it. They are two
 * separate beats because the playtest showed that one channel for both averages them into a
 * single shove.</p>
 *
 * <p>{@link #holding} is the attack button, {@link #guard} is the stance, {@link #parry} is the
 * sabre's half second and {@link #lunge} is the ropera's step. They are different states that
 * used to be confused for each other, and keeping them apart is what stops a punch from raising
 * a shield.</p>
 *
 * <p>{@link #time} is the only field that is not a ratio, and it exists so that a tremble can
 * be a function of the clock instead of a function of random numbers.</p>
 *
 * <p>Pure, so a profile can be evaluated in a unit test with nothing else loaded.</p>
 */
public record ViewModelInput(float windup, float strike, float recover,
                             float cooldown, float guard, float guardBreak,
                             float parry, float lunge,
                             boolean holding, float time, int seed) {

    /** At rest: nothing happening, no cooldown running, guard down, blade up, feet planted. */
    public static final ViewModelInput RESTING =
            new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 0);

    /** One line to build a frame for a test that does not care about the follow-through. */
    public static ViewModelInput of(float windup, float strike) {
        return new ViewModelInput(windup, strike, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame with the guard up, for a test that only cares about a stance. */
    public static ViewModelInput guarding(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, howFar, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame with the guard broken, for a test that only cares about the collapse. */
    public static ViewModelInput broken(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, howFar, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame with the blade out, for a test that only cares about the parry. */
    public static ViewModelInput parrying(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, howFar, 0.0F, false, 0.0F, 1);
    }

    /** The same frame in the follow-through, for a test that only cares about the weight. */
    public static ViewModelInput recovering(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, howFar, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
    }

    /** The same frame at the top of a lunge, for a test that only cares about the step. */
    public static ViewModelInput lunging(float howFar) {
        return new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, howFar, false, 0.0F, 1);
    }

    public float read(Channel channel) {
        return channel.read(this);
    }
}
