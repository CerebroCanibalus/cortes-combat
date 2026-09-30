package com.cortes.combat.viewmodel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A displacement in the viewmodel's own space, in the units vanilla uses for item transforms.
 *
 * <p>Positions are item units, where a held item is roughly 1 across, and angles are degrees,
 * because that is what {@code Axis.rotationDegrees} takes and what every number in a hand-coded
 * vanilla transform is written in. Keeping the units vanilla's means a value copied out of a
 * decompiled {@code ItemInHandRenderer} lands in a profile and works.</p>
 *
 * <p>Additive by construction, and that is the whole composition model: a profile is a sum of
 * offsets and nothing ever replaces the pose vanilla built. Two tracks that both push the item
 * back add up, which is what lets "wind up" and "tremble" and "strike" be three independent
 * rows of data instead of one function with three branches.</p>
 *
 * <p>Pure. Applying an offset to a pose is the one job that needs {@code PoseStack}, and it
 * lives in {@link ViewModelRenderer} alone.</p>
 */
public record Offset(double x, double y, double z, double pitch, double yaw, double roll) {

    public static final Offset ZERO = new Offset(0, 0, 0, 0, 0, 0);

    public static final Codec<Offset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Offset::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Offset::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Offset::z),
            Codec.DOUBLE.fieldOf("pitch").forGetter(Offset::pitch),
            Codec.DOUBLE.fieldOf("yaw").forGetter(Offset::yaw),
            Codec.DOUBLE.fieldOf("roll").forGetter(Offset::roll)
    ).apply(instance, Offset::new));

    public Offset plus(Offset other) {
        return new Offset(
                x + other.x, y + other.y, z + other.z,
                pitch + other.pitch, yaw + other.yaw, roll + other.roll);
    }

    public Offset times(float factor) {
        return new Offset(
                x * factor, y * factor, z * factor,
                pitch * factor, yaw * factor, roll * factor);
    }

    /** Whether this offset would move nothing at all, so the pose can be skipped entirely. */
    public boolean isZero() {
        return x == 0.0 && y == 0.0 && z == 0.0
                && pitch == 0.0 && yaw == 0.0 && roll == 0.0;
    }
}
