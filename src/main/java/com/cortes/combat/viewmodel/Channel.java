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
    BREAK,

    /**
     * How far the parry has come up, 0..1.
     *
     * <p>Its own channel rather than a second reader of {@link #GUARD}, and the reason is that the
     * two are not the same motion. The guard is a stance: it goes up because the player is holding
     * something up and it comes down when they let go. The parry is a commitment: the blade goes
     * out on its own, the player is slowed to a crawl while it is there, and it comes back when
     * the half second is over whether or not anything hit it. Sharing a channel would have meant
     * the sabre's blade rising at guard speed and sitting there like a shield.</p>
     *
     * <p>The sabre is the only weapon that reads it, and the only one that can.</p>
     */
    PARRY,

    /**
     * How far the ropera's lunge has gone, 0..1.
     *
     * <p>Its own channel and not a bigger strike, because the two are not the same motion wearing
     * different numbers. The strike is the wrist; the lunge is the body going forward, and it
     * finishes with the point <em>above</em> where the strike finishes level. Reusing
     * {@link #STRIKE} would have meant one number deciding both how far the blade goes and where
     * it points, and those two are exactly what makes a lunge read as a lunge.</p>
     *
     * <p>The ropera is the only weapon that reads it, and the only one that can.</p>
     */
    LUNGE,

    /**
     * How far the halberd's wide sweep has gone, 0..1.
     *
     * <p><b>Its own channel, and not a bigger {@link #STRIKE}, because the sweep crosses the screen
     * and a cut does not.</b> A thrust goes forward and returns along the same line: one
     * displacement with a peak in the middle. A sweep goes one way, crosses in front of the player,
     * and finishes on the other side, which is a signed yaw and a roll at the same time and cannot
     * be written as an amplitude toward zero without the blade arriving at the centre on its way
     * past.</p>
     *
     * <p>A one-shot bump like {@link #STRIKE} and {@link #RECOVER}, so it rests at zero and needs
     * no undo track. The weapon that reads it is the one that has it, and the read is a gate rather
     * than a branch: see {@link ViewModelProfile#reads}.</p>
     *
     * <p>It is the first channel a <em>special</em> owns. The three before it all hang off the
     * attack button, and a special that reused one of those channels would have been drawn as a
     * swing the player did not throw.</p>
     */
    SWEEP,

        /**
     * A blade that starts above and comes down: 1 to 0 across the swing, and never back up.
     *
     * <p><b>The first channel that only ever goes down, and it exists because a bump cannot express
     * a chop.</b> Every other motion here is {@code amplitude * curve} over a value that rises and
     * falls, and the playtest's complaint about the normal hit was exactly that: it starts from
     * below and goes up. A bump scaled by a negative pitch leaves the arm at rest on the first
     * frame of the swing, lifts it through the middle and brings it back -- so what the player sees
     * is a stab upward, and the second half of it is the return rather than the cut.</p>
     *
     * <p><b>A chop is one-directional.</b> The arm is already up when the swing starts, because the
     * charge raised it, and the whole motion is that height being given back to the ground. One
     * number, falling, over the same {@code strikeTicks}: at its first frame the weapon is at the
     * wind-up's own height, at its last it is at rest, and it never goes anywhere else.</p>
     *
     * <p><b>Which is why the wind-up and the chop can share a height without a jump.</b> The charge
     * is at full when the button comes up and the chop starts at one, so the hand-off has no step in
     * it. That continuity is the whole reason a charged swing reads as one motion instead of a
     * wind-up followed by a different animation -- and it is the second "it goes up when I let go"
     * the playtest reported, which was the bump answering a question a chop cannot be asked.</p>
     *
     * <p>Not every weapon reads it: the ropera and the dagger are a thrust, and their version of the
     * swing was reported as excellent, so they keep {@link #STRIKE} and this stays at zero.</p>
     */
    CHOP,
/**
     * Whether the weapon is loaded and ready, 0 or 1, as a <em>pose</em> and not a motion.
     *
     * <p><b>The first channel that is a state rather than a movement.</b> Everything else here is
     * "how much of this motion is happening", and a state cannot be that: a gun that is loaded is
     * not <em>doing</em> anything, it is sitting there differently.</p>
     *
     * <p>It works anyway because a profile is a sum of {@code amplitude × curve(channel)}, so a
     * channel that is 1 while loaded and 0 while not moves the weapon from its resting pose to the
     * loaded one, and the resting pose is the idle one. The weapon's flintlock comes down and the
     * profile says so by having a track here and no track anywhere else.</p>
     *
     * <p><b>Which is why IDLE needed no channel at all.</b> It is the profile's {@code rest}, and the
     * resting pose is by definition the one with nothing happening.</p>
     */
    HELD,

    /**
     * The reload, a one-shot bump over half a second.
     *
     * <p>A motion like {@link #STRIKE}, on the shape of the {@link #BREAK} bump: out and back in
     * half a second, resting at zero the rest of the time so it needs no undo track.</p>
     */
    RELOAD,

    /**
     * The fuse burning and the shot going off, 0..1 across the whole shot.
     *
     * <p><b>Not a bump.</b> Every other channel in this enum returns to zero, and this one only
     * reaches zero when the shot is over -- so it is a ramp that is held for as long as the fuse
     * is lit, the same shape as {@link #GUARD} and {@link #LUNGE}. That is what makes a long fuse
     * possible at all: a bump would be over before the player could see it start.</p>
     *
     * <p>Its length is the server's {@code FUSE_TICKS} and nothing else, so the recoil and the
     * visible burn are the same number and cannot drift apart.</p>
     */
    SHOOT;

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
            case PARRY -> in.parry();
            case LUNGE -> in.lunge();
            case SWEEP -> in.sweep();
            case CHOP -> in.chop();
            case HELD -> in.held();
            case RELOAD -> in.reload();
            case SHOOT -> in.shoot();
        };
    }
}
