package com.cortes.combat.viewmodel;

/**
 * Everything a viewmodel profile gets to look at for one frame.
 *
 * <p>One immutable snapshot, built once per frame and then read by every track. That is the
 * performance story of the whole library: a profile is not a function that queries the game, it
 * is a function of this record. Evaluating a profile touches no Minecraft state, allocates
 * nothing, and cannot be surprised by a value changing halfway through the sum.</p>
 *
 * <p>Four of the five channels are 0..1 ratios and mean "how much of this motion is happening
 * right now". The fifth, {@link #recover}, is the one that is zero for part of the motion and
 * non-zero for the rest, because a cut that goes out and comes back needs two shapes rather than
 * one.</p>
 *
 * <p>{@link #time} is the only field that is not a ratio, and it exists so that a tremble can be
 * a function of the clock instead of a function of random numbers.</p>
 *
 * <p>Pure, so a profile can be evaluated in a unit test with nothing else loaded.</p>
 */
public record ViewModelInput(float windup, float strike, float recover, float cooldown,
                             boolean holding, float time, int seed) {

    /** At rest: no charge, no swing, no cooldown running, guard down. */
    public static final ViewModelInput RESTING =
            new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 0);

    /** One line to build a frame for a test that does not care about the recovery. */
    public static ViewModelInput of(float windup, float strike) {
        return new ViewModelInput(windup, strike, 0.0F, 0.0F, false, 0.0F, 1);
    }

    public float read(Channel channel) {
        return channel.read(this);
    }
}
