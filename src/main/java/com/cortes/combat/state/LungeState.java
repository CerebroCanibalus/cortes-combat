package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A lunge in progress: which way it is going and how far into it the player is.
 *
 * <p>An attachment rather than a static, for the reason this project has one rule about mutable
 * gameplay state: a static would be one lunge for the whole server, and with two players in a
 * world the second one to press would overwrite the first's direction. It would also survive death
 * and world change, which is the bug the button state already had once.</p>
 *
 * <p><b>The direction is stored rather than re-read from where the player is looking.</b> A lunge
 * is a commitment to a direction, and reading the camera every tick turned it into a flight the
 * player could steer mid-dash -- which is not a lunge, it is a double jump nobody asked for.</p>
 *
 * <p>Pure, so the direction's arithmetic is testable without a world.</p>
 */
public record LungeState(double directionX, double directionZ, int tick) {

    public static final LungeState IDLE = new LungeState(0.0, 0.0,
            com.cortes.combat.special.LungeDamage.MAX_TICKS);

    public static final Codec<LungeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("direction_x").forGetter(LungeState::directionX),
            Codec.DOUBLE.fieldOf("direction_z").forGetter(LungeState::directionZ),
            Codec.INT.fieldOf("tick").forGetter(LungeState::tick)
    ).apply(instance, LungeState::new));

    /**
     * Whether this lunge still has steps left, for the tier that started it.
     *
     * <p>Asks the tier rather than comparing against one constant. A netherite lunge is thirty
     * steps and a wooden one is ten, so a single ceiling would end the good one after a third of
     * its distance -- and the player would see a lunge that stops for no reason, which is the
     * complaint that started all of this.</p>
     */
    public boolean running(int materialRank) {
        return tick < com.cortes.combat.special.LungeDamage.ticksFor(materialRank);
    }

    /** The same question without a tier, for the places that only know there is a state at all. */
    public boolean running() {
        return tick < com.cortes.combat.special.LungeDamage.MAX_TICKS;
    }

    /**
     * The direction, normalised, as two components.
     *
     * <p><b>Two doubles rather than a {@code Vec3}, so the arithmetic is testable.</b> A bare JVM
     * cannot load {@code Vec3}, which meant the one rule that decides which way a lunge goes -- and
     * which had already been wrong once -- had no test at all.</p>
     *
     * <p>Normalised on read rather than on write, so a state restored from storage or copied onto
     * another entity is still usable. The degenerate case returns straight ahead rather than
     * dividing by zero: a zero direction is not a state the game produces, but it is one a
     * corrupted attachment could carry, and a division by zero on the movement tick takes the
     * player with it.</p>
     */
    public double[] direction() {
        double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length < 1.0E-6) {
            return new double[]{0.0, 1.0};
        }
        return new double[]{directionX / length, directionZ / length};
    }

    /** The same thing as a {@code Vec3}, for the caller that is about to move an entity. */
    public net.minecraft.world.phys.Vec3 asVec3() {
        double[] unit = direction();
        return new net.minecraft.world.phys.Vec3(unit[0], 0.0, unit[1]);
    }

    /** The next state of the same lunge, one step further in. */
    public LungeState advance() {
        return new LungeState(directionX, directionZ, tick + 1);
    }

    /** How many steps are left for this tier, zero once the lunge is over. */
    public int remaining(int materialRank) {
        return com.cortes.combat.special.LungeDamage.remaining(materialRank, tick);
    }
}
