package com.cortes.combat.core.weapon;

import java.util.Locale;

/**
 * How wide a weapon's cone is, as a size instead of as a number.
 *
 * <p><b>The number a tooltip can print and the number a player can compare are different
 * numbers.</b> Reach in blocks is comparable -- "3.5" means something you can walk -- but a cone
 * of 45 degrees does not, because nobody has an intuition for 45 degrees and nobody measures it
 * while playing. So the tooltip prints the angle as the weapon's apparent size, which is the
 * comparison a player is actually making when they hold two weapons up.</p>
 *
 * <h2>Where the band edges come from</h2>
 *
 * <p><b>From the gaps in {@code WeaponTables}, not from round numbers.</b> The horizontal angles
 * in the game are 12, 15, 25, 28, 30, 35, 40, 45, 55 and 75. Those cluster into six groups and
 * the empty space between them is where a boundary belongs:</p>
 *
 * <pre>
 *   12  15   |  25  28   |  30  35   |  40  45   |  55   |  75
 *   very small|  small   |  medium   |   wide    | very   | max
 *        ^20          ^29        ^37.5        ^50       ^65
 * </pre>
 *
 * <p>Every edge is the midpoint of a real gap. A boundary at 30 would put the warhammer and the
 * sword in different bands while leaving the mace -- the same thirty degrees -- on the other side
 * of nothing, which is the shape of a band that was drawn on a ruler instead of read off the
 * table.</p>
 *
 * <h2>Why these five words and not six invented ones</h2>
 *
 * <p>Four of the six are DESIGN.md's own vocabulary, for the five families whose cone column it
 * spells out: the ropera is "muy pequeño", the warhammer "medio", the halberd "muy ancho", the
 * sabre "máximo". {@code ConeSizeTest} asserts those four land where the design says, so this
 * enum and the document that asked for it cannot drift apart. The two this table needed and the
 * design had no word for are "pequeño" and "ancho", which is where the sword, the axe and the
 * dagger fall.</p>
 */
public enum ConeSize {

    VERY_SMALL("very_small"),
    SMALL("small"),
    MEDIUM("medium"),
    WIDE("wide"),
    VERY_WIDE("very_wide"),
    MAXIMUM("maximum");

    /**
     * The upper bound of the five narrowest bands, in degrees. The last one is implicit: anything
     * above {@link #VERY_WIDE_UNTIL} is {@link #MAXIMUM}.
     *
     * <p>Twenty is the midpoint between fifteen and twenty five. The ropera's twelve and the
     * trident's fifteen are the only things below it, and both of them are meant to be the
     * hardest thing in the game to land on.</p>
     */
    private static final float VERY_SMALL_UNTIL = 20.0F;

    /** Midpoint of twenty eight and thirty. */
    private static final float SMALL_UNTIL = 29.0F;

    /**
     * Midpoint of thirty five and forty.
     *
     * <p>The widest band in the enum, and the one that earns it: four profiles sit at thirty or
     * thirty five and none anywhere else between thirty and thirty five, so this boundary has to
     * live in the space above them rather than in the middle of the cluster.</p>
     */
    private static final float MEDIUM_UNTIL = 37.5F;

    /** Midpoint of forty five and fifty five. */
    private static final float WIDE_UNTIL = 50.0F;

    /** Midpoint of fifty five and seventy five. */
    private static final float VERY_WIDE_UNTIL = 65.0F;

    private final String id;

    ConeSize(String id) {
        this.id = id;
    }

    /** The translation key suffix, so the tooltip is one format string and six strings. */
    public String id() {
        return id;
    }

    /**
     * The size band a horizontal angle falls in.
     *
     * <p>Ascending if-chains rather than a lookup table: there are six of them, they are checked
     * once per tooltip and not per frame, and an ordered chain puts the boundaries in the order
     * they were derived, next to the numbers they came from. A table would hide the ordering in
     * a {@code HashMap} that has to be trusted to be in it.</p>
     *
     * <p>A negative or NaN angle lands in {@link #VERY_SMALL} rather than throwing. The angle
     * comes off a data component a foreign mod can write, and a tooltip that crashes the render
     * thread over a malformed number is a far worse outcome than a weapon that claims to be
     * small.</p>
     */
    public static ConeSize forAngle(float horizontalDegrees) {
        if (Float.isNaN(horizontalDegrees) || horizontalDegrees < VERY_SMALL_UNTIL) {
            return VERY_SMALL;
        }
        if (horizontalDegrees < SMALL_UNTIL) {
            return SMALL;
        }
        if (horizontalDegrees < MEDIUM_UNTIL) {
            return MEDIUM;
        }
        if (horizontalDegrees < WIDE_UNTIL) {
            return WIDE;
        }
        if (horizontalDegrees < VERY_WIDE_UNTIL) {
            return VERY_WIDE;
        }
        return MAXIMUM;
    }

    /** Parses an id back, for the override config and for a test that walks the enum. */
    public static ConeSize byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (ConeSize size : values()) {
            if (size.id.equals(key)) {
                return size;
            }
        }
        throw new IllegalArgumentException("Unknown cone size: " + id);
    }
}