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

    /** How much of the hard cooldown has elapsed, 0..1, where 1 is ready to swing. */
    COOLDOWN,

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
            case COOLDOWN -> in.cooldown();
            case HOLD -> in.holding() ? 1.0F : 0.0F;
        };
    }
}
