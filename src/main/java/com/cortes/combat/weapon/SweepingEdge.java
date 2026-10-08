package com.cortes.combat.weapon;

/**
 * Sweeping Edge, reworked from an area effect into a crowd charge.
 *
 * <h2>Why the vanilla identity had to go</h2>
 *
 * <p>In 1.21.1 Sweeping Edge multiplies the damage of the sweep that a sword performs when it kills
 * the attack's charge -- and it is <b>dead in this mod</b>, because the sweep is ours and only the
 * halberd and the hoe have it. An enchantment that modifies a swing the weapon will never make is a
 * line on an item that does nothing, which is the same shape as the sable's dead {@code BREAK} track
 * and the shovel's copied special.</p>
 *
 * <p>It also stopped being wanted on its own terms: the design already has two area weapons, and a
 * third source of "hits everything in the arc" would be a third number to learn for a motion nobody
 * asked for twice.</p>
 *
 * <h2>The new identity: a crowd charge</h2>
 *
 * <p><b>It still hits one thing.</b> What the level buys is damage against the thing you aimed at,
 * and that damage grows with how many other bodies are packed around it. So the enchantment rewards
 * walking into a crowd and picking one target out of it -- the opposite of an area weapon, which
 * rewards the crowd itself. A sword with Sweeping Edge III is a crowd's worst enemy precisely
 * because it refuses to be distracted by it.</p>
 *
 * <p>Pure on purpose: the level and the count are both plain integers, so the formula can be walked
 * in a bare JVM. The half that needs a world -- counting the bodies around a target -- lives in
 * {@code CombatEngine} and only produces the second argument.</p>
 */
public final class SweepingEdge {

    /**
     * How much damage one enemy packed around the target adds, per enchantment level.
     *
     * <p><b>Half a point, and it is a first number to validate in playtest.</b> It is deliberately
     * small: the enchantment's whole promise is that it is worth more the more bodies are around,
     * and a bonus that is large per enemy would make the level of the enchantment secondary to the
     * size of the crowd, which is not something the player can control. At half a point per body
     * per level, level I against four packed enemies is two extra damage -- a real swing without
     * being a different weapon.</p>
     */
    public static final float DAMAGE_PER_ENEMY_PER_LEVEL = 0.5F;

    /**
     * How far around the target another body has to be to count as part of the crowd, in blocks.
     *
     * <p>Measured from the target's own bounding box, so a wide mob counts bodies a little further
     * out and a narrow one does not. Three blocks is a room's corner, not a battlefield: the point
     * is "packed together", and vanilla's own sweep box is tighter than this, around one block.
     * Three is the smallest number that reliably catches the second and third mob of a spawn group.</p>
     */
    public static final double CROWD_RADIUS = 3.0;

    private SweepingEdge() {
    }

    /**
     * The flat damage the enchantment adds to a hit on the aimed target.
     *
     * @param level   the Sweeping Edge level on the weapon, zero when it has none
     * @param enemies how many other living things sit inside {@link #CROWD_RADIUS} of the target
     * @return the bonus, never negative, and zero for a weapon without the enchantment or a target
     *         standing alone
     */
    public static float bonus(int level, int enemies) {
        if (level <= 0 || enemies <= 0) {
            return 0.0F;
        }
        return DAMAGE_PER_ENEMY_PER_LEVEL * level * enemies;
    }
}
