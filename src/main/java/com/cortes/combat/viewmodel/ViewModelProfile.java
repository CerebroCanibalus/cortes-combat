package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * The viewmodel of one weapon: a resting pose, a list of tracks, and a flag.
 *
 * <p>The split this class exists to enforce is between the <b>machinery</b>, which is this whole
 * package and is written once, and the <b>profiles</b>, which are data. The step that adds the
 * next twenty-nine weapons is a row in a table, not a class, and that is only true because
 * nothing about how a profile is evaluated lives here.</p>
 *
 * <h2>The resting pose is the one thing that is not a channel</h2>
 *
 * <p>Every channel rests at zero and that is the invariant the sum of tracks rests on: zero
 * means "this track contributes nothing", and it has to mean that for all of them at once. A
 * weapon that is <b>held</b> differently therefore cannot express itself through a channel, and
 * the ropera is the case that proved it.</p>
 *
 * <p>Its art is drawn with the blade pointing up, so at rest it was a lowered guard and every
 * thrust had to spend ninety degrees of rotation to become level. The playtest called it "casi
 * como un dash", and it was right: a quarter of a second of ninety degrees is a movement the arm
 * makes, not a weapon the player uses. Held level, the same thrust spends about thirty. The
 * rotation is still in the motion -- it has to be, a point that never moves is a stab with no
 * follow-through -- but thirty degrees over ten ticks is a thrust and ninety is a lunge the
 * player did not order.</p>
 *
 * <p><b>It is a field and not a channel on purpose.</b> A channel that rested at one would need a
 * special case in every track that reads it, and "what is the resting value of this channel"
 * would stop having one answer. A field on the profile is outside that question entirely: it is
 * a property of the weapon, the same on every frame, and it is read once in the sum.</p>
 *
 * <p>There is no timeline, no keyframe list and no state machine, and that is a deliberate limit
 * rather than an unfinished feature. A timeline is the right tool for an attack with six distinct
 * beats; it is the wrong tool for "the item is drawn back in proportion to how charged you are",
 * which is what almost every motion in this game actually is. Adding timelines later is possible
 * without changing this class, because a timeline would just be another channel.</p>
 *
 * <p>Pure, so a profile can be evaluated in a unit test with nothing else on the classpath.</p>
 */
public record ViewModelProfile(Offset rest, List<Track> tracks, boolean ownsSwing) {

    public static final ViewModelProfile NONE = new ViewModelProfile(Offset.ZERO, List.of(), false);

    /**
     * Whether this profile replaces the vanilla arm swing rather than sitting on top of it.
     *
     * <p>True for a weapon whose profile already contains the whole strike. Left false, the
     * vanilla swing still plays underneath, which is what a weapon with no profile of its own
     * must get.</p>
     */
    public static final Codec<Boolean> OWN_SWING_CODEC = Codec.BOOL;

    public static final Codec<ViewModelProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Offset.CODEC.optionalFieldOf("rest", Offset.ZERO).forGetter(ViewModelProfile::rest),
            Track.CODEC.listOf().fieldOf("tracks").forGetter(ViewModelProfile::tracks),
            Codec.BOOL.optionalFieldOf("owns_swing", false).forGetter(ViewModelProfile::ownsSwing)
    ).apply(instance, ViewModelProfile::new));

    public ViewModelProfile {
        tracks = List.copyOf(tracks);
    }

    /** A profile with the vanilla resting pose, which is what most weapons want. */
    public static ViewModelProfile of(Track... tracks) {
        return new ViewModelProfile(Offset.ZERO, List.of(tracks), false);
    }

    public static ViewModelProfile owningSwing(Track... tracks) {
        return new ViewModelProfile(Offset.ZERO, List.of(tracks), true);
    }

    /** The same, for a weapon that is held differently at rest and not only in motion. */
    public static ViewModelProfile heldAs(Offset rest, boolean ownsSwing, Track... tracks) {
        return new ViewModelProfile(rest, List.of(tracks), ownsSwing);
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    /**
     * Whether this profile has a track on that channel at all.
     *
     * <p><b>Which channels a weapon is allowed to be fed.</b> The channels are global -- there is
     * one {@code PARRY}, one {@code GUARD} -- and the input that drives them is the player's
     * state, which is not tied to the item in their hand. Without this question, a sabre's parry
     * state keeps driving the weapon you switched to: the playtest parried, picked up a sword, and
     * the sword played the sabre's lockout for the five seconds it had left, because the lockout
     * rides {@code BREAK} and the sword reads {@code BREAK} for its guard.</p>
     *
     * <p>Answering it here rather than with an item check at the call site is deliberate. "Is
     * this the sabre" is a name; "does this profile have anything to do with the parry" is the
     * question, it stays true if the sabre ever gets a second item, and it is a property of the
     * profile that a test can read.</p>
     */
    public boolean reads(Channel channel) {
        for (Track track : tracks) {
            if (track.channel() == channel) {
                return true;
            }
        }
        return false;
    }

    /**
     * The total displacement for one frame, in item units.
     *
     * <p>Allocates one {@link Offset}. It could return a mutable accumulator instead and this
     * would be allocation-free, and that was the version written first: the profile is evaluated
     * once per frame, so one small short-lived record is a few bytes of nursery garbage that
     * dies before the next frame's allocation, which is not a cost worth an accumulator object
     * and a mutable field to reason about.</p>
     *
     * <p>Starts at the resting pose rather than at zero, so a weapon that is held differently
     * reaches that difference in one addition instead of asking every track to carry a constant.
     */
    public Offset evaluate(ViewModelInput in) {
        Offset total = rest;
        for (Track track : tracks) {
            total = total.plus(track.evaluate(in));
        }
        return total;
    }

    /**
     * Whether this profile would do nothing for the given frame, so the pose can be left alone.
     *
     * <p>The check that keeps a player standing still with a sword from paying for a viewmodel
     * system at all. Worth the exact comparison: an offset built out of zeroes really is
     * bit-for-bit zero here, because every track multiplies by a shaped zero.</p>
     *
     * <p><b>A resting pose counts as doing something.</b> It is a pose, it is the same on every
     * frame, and skipping it would put the weapon back into the vanilla grip -- which is the exact
     * "a viewmodel subtly wrong is worse than none" case this project has already paid for once
     * with the two profiles that were written and never rendered.</p>
     */
    public boolean idleFor(ViewModelInput in) {
        for (Track track : tracks) {
            if (!track.evaluate(in).isZero()) {
                return false;
            }
        }
        return rest.isZero();
    }
}
