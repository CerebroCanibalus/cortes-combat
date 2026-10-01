package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;

/**
 * What a profile can hook into. One per kind of motion, never two for the same idea.
 *
 * <p>Every channel <b>rests at zero</b> and that is not a convention, it is the invariant the
 * whole design rests on. A profile is a sum of tracks, each one {@code amplitude × curve(channel)},
 * and every curve starts at zero. So a channel that rested at one would need a special case in
 * every track that reads it, and "what is the resting value of this channel" would stop being a
 * question with one answer. Four of the five here are 0..1 ratios meaning <em>how much of this
 * motion is happening right now</em>; the two one-shots are bumps with a shape of their own.</p>
 *
 * <p>None of them is a clock. A channel says how much is happening; a {@link Track} says when it
 * started and how it eases. Keeping those two apart is what lets the same channel serve a
 * weapon that cuts in six ticks and one that cuts in twelve without the profile knowing.</p>
 */
public enum Channel {

    /**
     * How far back the weapon is cocked, 0..1, where 1 is a full charge.
     *
     * <p>While the button is down this is the charge. Once the swing resolves it becomes the
     * unwind, and the two are the same number read at a different time, so a blade leaves the
     * cocked position it was in rather than teleporting from wherever the charge left off.</p>
     */
    WINDUP,

    /**
     * The cut, a one-shot bump on <b>vanilla's own swing curve</b>: fast out, slow back, and
     * back at rest by the end of the swing.
     *
     * <p>The shape is {@code sin(sqrt(f) * PI)}, read off {@code applyItemArmAttackTransform} in
     * the 1.21.1 jar. The square root is the point and not a detail: it makes the motion leave
     * far faster than it returns, and that asymmetry is what reads as an arm swinging rather than
     * as a machine.</p>
     *
     * <p>Ending at zero is not a nicety. A channel that stayed at one would leave the item
     * displaced when the motion ended, so every profile would need its own "and now undo it"
     * track and the two would have to stay in agreement forever.</p>
     *
     * <p>There was a second channel here, {@code THRUST}, that rose and <b>held</b>. It was
     * added to fix "the cut has no weight" and it was the wrong diagnosis in the right coat: what
     * actually made the cut read as a shove was a translation applied in the wrong frame, and a
     * channel that holds leaves the blade turned when the swing ends, so the item snapped to the
     * vanilla pose on the last frame. Two bumps where one belongs is a way of saying "I did not
     * know which shape this was".</p>
     */
    STRIKE,

    /**
     * The weight, a one-shot bump over the tail of the swing: the motion that goes
     * <b>down and back to where the hand is</b>.
     *
     * <p>Separate from the cut on purpose, and the playtest is what asked for it. A cut that
     * goes out and comes back is two displacements with a peak between them, so putting both on
     * one channel averages them into a single shove that leaves and returns along the same line,
     * and that was called "no weight" three rounds running. It is a different beat with a
     * different direction, so it gets its own channel and starts only once the cut has arrived.</p>
     */
    RECOVER,

    /**
     * How much of the hard cooldown is still running, 0..1, where 0 is ready to swing.
     *
     * <p>Counting the cooldown rather than the readiness, so that zero is the resting value like
     * every other channel here. A channel that rests at one is a channel that needs a special
     * case, and the whole point of a sum of tracks is that it does not.</p>
     */
    COOLDOWN,

    /**
     * Whether the attack button is down, 0 or 1.
     *
     * <p>Deliberately not the guard, which is {@link #GUARD} and has its own channel. This one
     * was documented as the guard for a long time and was never the guard; a channel named for
     * one thing and wired to another is the sort of thing that costs an afternoon.</p>
     */
    HOLD,

    /**
     * How far the guard has come up, 0..1.
     *
     * <p>A ramp and not a flag, because the guard goes up and comes down over a few ticks and a
     * flag would put the whole pose on one frame. The rise and the fall are different lengths on
     * purpose: bringing a weapon up to guard is a decision and dropping it is a reflex, and the
     * same duration for both reads as a UI toggle rather than as a stance.</p>
     *
     * <p>It is a channel and not a state field for the same reason every other channel here is:
     * a profile is a sum of {@code amplitude × curve(channel)} terms, and giving the guard its
     * own channel means every weapon decides what its guard looks like and none of them has to
     * know how long the stance takes.</p>
     */
    GUARD,

    /**
     * How far through a broken guard the collapse is, 0..1.
     *
     * <p>A one-shot bump, and it is a separate channel from {@link #GUARD} for the same reason
     * {@link #RECOVER} is separate from {@link #STRIKE}: the stance going up and the stance being
     * knocked down are two motions in different directions, and putting them on one channel
     * averages them into a single movement that reads as neither.</p>
     *
     * <p>Its length is the vanilla item cooldown, not a tick count, so it cannot outlast or
     * outlast-not the stagger it is animating. It rests at zero when there is no break, like every
     * channel here, so a weapon with no break track contributes nothing at rest.</p>
     */
    BREAK;

    public static final Codec<Channel> CODEC = Codec.STRING.xmap(Channel::valueOf, Enum::name);

    /** Read this channel out of a frame's inputs. */
    public float read(ViewModelInput in) {
        return switch (this) {
            case WINDUP -> in.windup();
            case STRIKE -> in.strike();
            case RECOVER -> in.recover();
            case COOLDOWN -> in.cooldown();
            case HOLD -> in.holding() ? 1.0F : 0.0F;
            case GUARD -> in.guard();
            case BREAK -> in.guardBreak();
        };
    }
}
