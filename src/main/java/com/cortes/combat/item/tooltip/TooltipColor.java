package com.cortes.combat.item.tooltip;

/**
 * The colour a tooltip line is drawn in, as data.
 *
 * <p><b>A pure enum and not a {@code ChatFormatting}, for the reason every other decision in this
 * package is pure:</b> {@code ChatFormatting} is a Minecraft type, so a {@code TooltipLine} that
 * carried one could not be built in a bare JVM and "which line is which colour" would stop being a
 * question a test can answer. The layer that turns these into components maps them, once.</p>
 *
 * <p><b>Colour carries meaning here and it is not decoration.</b> Seven lines of identical grey is
 * a wall: the player scanning for "how hard does this hit" reads all seven to find one, every time.
 * Colouring the damage red and the reach aqua is the same information delivered in a glance, and it
 * is what vanilla does with its own lines -- green for enchantments, blue for the category, red for
 * a curse.</p>
 */
public enum TooltipColor {

    /** The swing's damage, in vanilla's own red. */
    DAMAGE,

    /** A fully charged swing, one shade darker so the two read as a pair and not as one line twice. */
    CHARGED,

    /** Attacks per second, yellow. */
    SPEED,

    /** Reach, aqua -- the colour vanilla uses for a positive attribute. */
    RANGE,

    /** The cone size, grey: it qualifies the reach above it rather than being a number of its own. */
    SIZE,

    /** The right button, gold, because it is the line that describes a choice. */
    SPECIAL,

    /** Durability, green when healthy and red when it is nearly gone. */
    DURABILITY,

    /**
     * The category, <b>blue, which is vanilla's own colour for this line.</b>
     *
     * <p>Copied deliberately: a player has seen "Food" and "Music Disc" in blue since they started
     * playing, so the line reads as the same kind of fact without being told.</p>
     */
    CATEGORY,

    /** A delta or a caveat -- "+3 from Strength". Dim, because it is an aside. */
    NOTE,

    /** Durability that is nearly spent. */
    DURABILITY_LOW;
}
