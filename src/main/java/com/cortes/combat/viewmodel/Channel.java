package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;

/**
 * What a viewmodel track reads to decide how far along it is.
 *
 * <p>A closed list, not an open-ended key/value map, and the reason is the same one that made
 * {@code CombatFeedback} a closed list: a channel set that anything can add to stops being a
 * vocabulary. If a new motion needs a new input, the input belongs here as a named channel and
 * the profile gets to use it, rather than every profile inventing its own.</p>
 *
 * <p>All four are 0..1 and all four mean the same thing: <em>how much of this motion is
 * happening right now</em>. None of them is a clock, none of them counts, and none of them is
 * ever negative. A track multiplies its amplitude by its curve of that number, so {@code 0}
 * always means "this track contributes nothing" and the resting pose is reachable from every
 * channel without a special case.</p>
 *
 * <p>That uniformity is the invariant the whole composition model rests on, and it is why
 * {@link #COOLDOWN} counts the cooldown that is <em>running</em> rather than the one that has
 * finished. A weapon at rest has a ready cooldown, so "ready" would have to be the resting
 * value of a channel, and a channel whose resting value is one is a channel whose tracks need a
 * special case. A profile that moves the item when the player is standing still is a bug that
 * only shows up as a weapon that slowly drifts off the vanilla pose.</p>
 */
public enum Channel {

    /**
     * How far the item is drawn back before the strike, 0..1.
     *
     * <p>While the attack button is held this is the charge. After the release it decays to
     * zero over the strike, which is what lets the wind-up and the follow-through be one
     * continuous motion instead of a snap: the item is still back at the moment of release and
     * travels forward out of that position, rather than being teleported to rest and then
     * pushed forward from there.</p>
     */
    WINDUP,

    /**
     * The strike itself, a one-shot bump: up, then back to zero.
     *
     * <p>Returning to zero is what makes a follow-through work. A channel that stayed at 1 would
     * leave the item permanently displaced when the motion ended, so every profile would need
     * its own "and now undo it" track. Shaping the bump with the track's curve gives the timing
     * control without that.</p>
     */
    STRIKE,

    /**
     * How much of the hard cooldown is still running, 0..1, where 0 is ready to swing.
     *
     * <p>Counting the cooldown rather than the readiness, so that zero is the resting value like
     * every other channel. See the class comment: a channel that rests at one is a channel that
     * needs a special case, and the whole point of a sum of tracks is that it does not.</p>
     */
    COOLDOWN,

    /**
     * The recovery, one-shot, peaking in the second half of the strike.
     *
     * <p>This channel exists because a single bump cannot express a slash. The cut has to go
     * forward hard and then come back down and towards the player, and those are two motions
     * with a peak between them, so putting both on {@link #STRIKE} would average them into one
     * shove that goes out and comes straight back.</p>
     *
     * <p>It is zero for the first half of the strike and ramps after it, so a track on it
     * contributes nothing while the cut is still travelling. A track that starts where another
     * one stops is how a follow-through is written without a timeline.</p>
     */
    RECOVER,

    /**
     * Whether the guard is up, 0 or 1.
     *
     * <p>Not strictly 0..1 in the general case, but it only ever holds 0 or 1 here, and
     * {@link Track#smoothed} exists for the guards that want to see it as a ramp.</p>
     */
    HOLD;

    public static final Codec<Channel> CODEC = Codec.STRING.xmap(Channel::valueOf, Enum::name);

    /** Read this channel out of a frame's inputs. */
    public float read(ViewModelInput in) {
        return switch (this) {
            case WINDUP -> in.windup();
            case STRIKE -> in.strike();
            case RECOVER -> in.recover();
            case COOLDOWN -> in.cooldown();
            case HOLD -> in.holding() ? 1.0F : 0.0F;
        };
    }
}
