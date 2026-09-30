package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One row of a viewmodel profile: a channel, a curve, an amplitude, and optionally a tremble.
 *
 * <p>This is the whole abstraction of the library. A profile is a list of these and nothing
 * else, which is what makes a new weapon a matter of writing data rather than code, and what
 * keeps a profile reviewable as a list of "when charged, move here this much".</p>
 *
 * <p>The displacement is {@code amplitude × curve(channel)}, which is the whole formula. Because
 * every curve is normalised to {@code f(0) == 0}, a track contributes nothing at rest without
 * anyone having to write that down, so a profile that has four tracks and a player standing
 * still produces exactly the vanilla pose.</p>
 *
 * <p>The tremble is a second, optional displacement: {@code tremor × noise}, scaled by
 * {@link #tremorWeight} applied to the channel, so a weapon can shake only near the top of its
 * charge without a branch. The weight is a plain curve on the channel, not a threshold, because
 * a threshold is a discontinuity the player sees as a snap.</p>
 *
 * <p>Pure: {@link #evaluate} touches no game state and allocates nothing, so a profile's
 * behaviour is a unit test away.</p>
 */
public record Track(Channel channel, Curve curve, Offset amplitude,
                    Offset tremor, float tremorHz, Curve tremorWeight) {

    /** A plain displacement with no tremble. */
    public static Track of(Channel channel, Curve curve, Offset amplitude) {
        return new Track(channel, curve, amplitude, Offset.ZERO, 0.0F, Curve.LINEAR);
    }

    /** A displacement that also trembles, shaking harder the further along the channel is. */
    public static Track shaking(Channel channel, Curve curve, Offset amplitude,
                                Offset tremor, float tremorHz, Curve tremorWeight) {
        return new Track(channel, curve, amplitude, tremor, tremorHz, tremorWeight);
    }

    public static final Codec<Track> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Channel.CODEC.fieldOf("channel").forGetter(Track::channel),
            Curve.CODEC.fieldOf("curve").forGetter(Track::curve),
            Offset.CODEC.fieldOf("amplitude").forGetter(Track::amplitude),
            Offset.CODEC.optionalFieldOf("tremor", Offset.ZERO).forGetter(Track::tremor),
            Codec.FLOAT.optionalFieldOf("tremor_hz", 0.0F).forGetter(Track::tremorHz),
            Curve.CODEC.optionalFieldOf("tremor_weight", Curve.LINEAR).forGetter(Track::tremorWeight)
    ).apply(instance, Track::new));

    /**
     * This track's contribution to one frame.
     *
     * <p>No allocation: {@link Offset} is summed by hand into two locals rather than by chaining
     * {@code plus}, because this runs for every track of every held item on every frame and the
     * garbage it would produce is the one cost a viewmodel can never afford.</p>
     */
    public Offset evaluate(ViewModelInput in) {
        float t = channel.read(in);
        float shaped = curve.apply(t);
        double x = amplitude.x() * shaped;
        double y = amplitude.y() * shaped;
        double z = amplitude.z() * shaped;
        double pitch = amplitude.pitch() * shaped;
        double yaw = amplitude.yaw() * shaped;
        double roll = amplitude.roll() * shaped;

        if (!tremor.isZero() && tremorHz > 0.0F) {
            // Cubed, not thresholded. The tremble has to be invisible at the start of the
            // charge and unmissable at the top, and a curve gives that for free while a
            // threshold gives a visible moment where the shaking switches on.
            float weight = tremorWeight.apply(t);
            if (weight != 0.0F) {
                float time = in.time();
                int seed = in.seed();
                x += tremor.x() * ViewModelNoise.axis(seed, time, tremorHz, 0) * weight;
                y += tremor.y() * ViewModelNoise.axis(seed, time, tremorHz, 1) * weight;
                z += tremor.z() * ViewModelNoise.axis(seed, time, tremorHz, 2) * weight;
                pitch += tremor.pitch() * ViewModelNoise.axis(seed, time, tremorHz, 3) * weight;
                yaw += tremor.yaw() * ViewModelNoise.axis(seed, time, tremorHz, 4) * weight;
                roll += tremor.roll() * ViewModelNoise.axis(seed, time, tremorHz, 5) * weight;
            }
        }

        return new Offset(x, y, z, pitch, yaw, roll);
    }
}
