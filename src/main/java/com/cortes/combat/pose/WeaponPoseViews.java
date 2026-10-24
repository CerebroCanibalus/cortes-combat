package com.cortes.combat.pose;

import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.Curve;
import com.cortes.combat.viewmodel.Offset;
import com.cortes.combat.viewmodel.Track;
import com.cortes.combat.viewmodel.ViewModelProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link WeaponPose} file compiled to the viewmodel the game already draws.
 *
 * <p>The two formats say the same thing in different words: a pose file's track is a channel,
 * a curve and amplitudes in metres and degrees, and a {@link Track} is a channel, a curve and
 * amplitudes in the viewmodel's own units -- which are also metres and degrees, because the
 * file format was written from the viewmodel's frame. The translation is therefore a change
 * of spelling, and a change of spelling is pure, which is why it lives here with tests instead
 * of in the client. (⌒‿⌒)</p>
 *
 * <p>What does <em>not</em> translate, and why each absence is a decision:</p>
 * <ul>
 *   <li>The id and the axes: the id is the file's address, and the axes declare whose frame
 *       the numbers are in. The loader reads them; the profile does not need them twice.</li>
 *   <li>The parts: pivots for Blockbench-separated pieces, which only the arcabuz has. A pack
 *       weapon is one rigid item, so there is nowhere to pivot.</li>
 *   <li>The body: aim and reload offsets for the third-person arm. The body's motion stays
 *       profile-driven (gates on the same channels the tracks below open), because a body that
 *       followed a different file than the hand is the sabre-parry bug with the names changed.
 *       </li>
 *   <li>{@code ownsSwing}: a file that took over the vanilla swing would take over the arm
 *       without telling the body. Profiles that own the swing stay hand-written.</li>
 * </ul>
 */
public final class WeaponPoseViews {

    private WeaponPoseViews() {
    }

    /**
     * The viewmodel profile this pose file describes.
     *
     * @param pose a loaded, validated pose file
     * @return the profile to evaluate per frame, with the file's rest and tracks
     * @throws IllegalArgumentException naming the channel or curve, if the file was
     *                                  hand-built past the codec (the codec rejects unknown
     *                                  names at load; this refuses to guess)
     */
    public static ViewModelProfile toProfile(WeaponPose pose) {
        List<Track> tracks = new ArrayList<>(pose.tracks().size());
        for (WeaponPose.Motion motion : pose.tracks()) {
            tracks.add(toTrack(pose.id(), motion));
        }
        WeaponPose.Transform rest = pose.rest();
        return new ViewModelProfile(
                new Offset(rest.x(), rest.y(), rest.z(), rest.pitch(), rest.yaw(), rest.roll()),
                tracks, false);
    }

    private static Track toTrack(String id, WeaponPose.Motion motion) {
        Channel channel;
        try {
            channel = Channel.valueOf(motion.channel());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    id + " :: canal desconocido '" + motion.channel() + "'", unknown);
        }
        Curve curve;
        try {
            curve = Curve.valueOf(motion.curve());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    id + " :: curva desconocida '" + motion.curve() + "'", unknown);
        }
        Curve weight;
        try {
            weight = Curve.valueOf(motion.tremorWeight());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    id + " :: peso de temblor desconocido '" + motion.tremorWeight() + "'",
                    unknown);
        }
        WeaponPose.Transform shake = motion.tremor();
        return new Track(channel, curve, toOffset(motion.amplitude()),
                toOffset(shake), (float) motion.tremorHz(), weight);
    }

    private static Offset toOffset(WeaponPose.Transform transform) {
        return new Offset(transform.x(), transform.y(), transform.z(),
                transform.pitch(), transform.yaw(), transform.roll());
    }
}
