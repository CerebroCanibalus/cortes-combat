package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A lunge in progress, as the server measures it.
 *
 * <p>An attachment rather than a static, for the reason this project has one rule about mutable
 * gameplay state: a static would be one lunge for the whole server, and with two players in a
 * world the second one to press would overwrite the first's. It would also survive death and world
 * change, which is the bug the button state already had once.</p>
 *
 * <h2>Why this records where the player went instead of where they were aiming</h2>
 *
 * <p>It used to store the direction of the press and how many steps were owed, and it kept the
 * direction because "a lunge is a commitment" -- reading the camera every tick had turned it into a
 * flight. Under one impulse that reasoning is obsolete: the impulse is the commitment, and the
 * engine gives the player air control exactly as it does after every other knockback, so a stored
 * direction would only be a second guess at what the player is already doing.</p>
 *
 * <p>What the server actually needs is the opposite of a direction: <b>where the player went.</b>
 * The client sends its position every tick in the ordinary movement report, so the server has it
 * for free and can compare it against where the lunge began. That is the whole basis of the
 * authority model here -- the server does not ask the client for permission to move, it looks at
 * where the client said it moved and judges that.</p>
 *
 * <h2>Why the material is stored</h2>
 *
 * <p>Because the ceiling it is checked against is a property of the lunge, not of whatever the
 * player is holding fourteen ticks later. Reading the current item would mean a dash could be cut
 * short by a weapon swap -- the player changed to a wooden ropera halfway and the tolerance
 * collapsed under them -- and that is the same class of bug as a number outliving the thing that
 * justified it, only running the other way.</p>
 *
 * <p>Pure, so the measurement is testable without a world.</p>
 */
public record LungeState(int tick, double startX, double startZ, double lastX, double lastZ,
                         int materialRank) {

    public static final LungeState IDLE = new LungeState(
            com.cortes.combat.special.LungeDamage.WINDOW_TICKS,
            0.0, 0.0, 0.0, 0.0, 0);

    public static final Codec<LungeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("tick").forGetter(LungeState::tick),
            Codec.DOUBLE.fieldOf("start_x").forGetter(LungeState::startX),
            Codec.DOUBLE.fieldOf("start_z").forGetter(LungeState::startZ),
            Codec.DOUBLE.fieldOf("last_x").forGetter(LungeState::lastX),
            Codec.DOUBLE.fieldOf("last_z").forGetter(LungeState::lastZ),
            Codec.INT.fieldOf("material_rank").forGetter(LungeState::materialRank)
    ).apply(instance, LungeState::new));

    /**
     * Whether the damage and invulnerability window is still open.
     *
     * <p>Twenty ticks, and that is the end of it whatever the player is doing. Vanilla's riptide
     * behaves the same way -- {@code startAutoSpinAttack(20, ...)} -- so a dash into a wall and a
     * dash across a field stop being the same move, and the number the player has to learn is
     * one.</p>
     */
    public boolean running() {
        return tick < com.cortes.combat.special.LungeDamage.WINDOW_TICKS;
    }

    /**
     * How far the player has got from where the lunge began.
     *
     * <p>Delegated rather than open-coded, because this is the rule that decides whether a lunge is
     * honoured, and a second copy of that arithmetic in another file is how the two of them end up
     * disagreeing about what a lunge was.</p>
     */
    public double travelled(double x, double z) {
        return com.cortes.combat.special.LungeDamage.travelled(startX, startZ, x, z);
    }

    /**
     * Whether this lunge is still one the server will honour, measured against the material that
     * opened it.
     */
    public boolean legal(double travelled) {
        return com.cortes.combat.special.LungeDamage.legal(materialRank, travelled);
    }

    /**
     * The same state one tick further on, with the player's new position.
     *
     * <p>{@code lastX/lastZ} exist for the duration test and nothing else. The hit volume is a cube
     * around the player now, so the geometry needs no history -- but "has the player stopped moving"
     * does, and it is the difference between a dash as long as its immunity and one twice as long.</p>
     */
    public LungeState advance(double x, double z) {
        return new LungeState(tick + 1, startX, startZ, x, z, materialRank);
    }
}
