package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * The viewmodel of one weapon: a list of tracks and nothing else.
 *
 * <p>The split this class exists to enforce is between the <b>machinery</b>, which is this whole
 * package and is written once, and the <b>profiles</b>, which are data. The step that adds the
 * next twenty-nine weapons is a row in a table, not a class, and that is only true because
 * nothing about how a profile is evaluated lives here.</p>
 *
 * <p>There is no timeline, no keyframe list and no state machine, and that is a deliberate limit
 * rather than an unfinished feature. A timeline is the right tool for an attack with six
 * distinct beats; it is the wrong tool for "the item is drawn back in proportion to how charged
 * you are", which is what almost every motion in this game actually is. Adding timelines later
 * is possible without changing this class, because a timeline would just be another channel.</p>
 *
 * <h2>Cost</h2>
 *
 * <p>One {@link ViewModelInput} per frame, then a pass over a list of four-element records
 * doing a handful of multiplies each. No allocation, no map lookup, no branch on which weapon is
 * held. The only thing that happens per frame in the game code is reading four floats that were
 * already being tracked for the HUD, and the profile itself is resolved when the held item
 * changes rather than per frame.</p>
 *
 * <p>Pure, so a profile can be evaluated in a unit test with nothing else on the classpath.</p>
 */
public record ViewModelProfile(List<Track> tracks, boolean ownsSwing) {

    public static final ViewModelProfile NONE = new ViewModelProfile(List.of(), false);

    /**
     * Whether this profile replaces the vanilla arm swing rather than sitting on top of it.
     *
     * <p>True for a weapon whose profile already contains the whole strike. Left false, the
     * vanilla swing still plays underneath, which is what a weapon with no profile of its own
     * must get.</p>
     */
    public static final Codec<Boolean> OWN_SWING_CODEC = Codec.BOOL;

    public static final Codec<ViewModelProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Track.CODEC.listOf().fieldOf("tracks").forGetter(ViewModelProfile::tracks),
            Codec.BOOL.optionalFieldOf("owns_swing", false).forGetter(ViewModelProfile::ownsSwing)
    ).apply(instance, ViewModelProfile::new));

    public ViewModelProfile {
        tracks = List.copyOf(tracks);
    }

    public static ViewModelProfile of(Track... tracks) {
        return new ViewModelProfile(List.of(tracks), false);
    }

    public static ViewModelProfile owningSwing(Track... tracks) {
        return new ViewModelProfile(List.of(tracks), true);
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    /**
     * The total displacement for one frame, in item units.
     *
     * <p>Allocates one {@link Offset}. It could return a mutable accumulator instead and this
     * would be allocation-free, and that was the version written first: the profile is evaluated
     * once per frame, so one small short-lived record is a few bytes of nursery garbage that
     * dies before the next frame's allocation, which is not a cost worth an accumulator object
     * and a mutable field to reason about.</p>
     */
    public Offset evaluate(ViewModelInput in) {
        Offset total = Offset.ZERO;
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
     */
    public boolean idleFor(ViewModelInput in) {
        for (Track track : tracks) {
            if (!track.evaluate(in).isZero()) {
                return false;
            }
        }
        return true;
    }
}
