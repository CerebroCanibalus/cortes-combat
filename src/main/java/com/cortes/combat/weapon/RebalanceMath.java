package com.cortes.combat.weapon;

/**
 * The two one-line deltas the vanilla-tool rebalances apply, with nowhere to drift to.
 *
 * <p>Both legs read them: the 1.21.1 handlers through {@code SwordRebalance.reduced} and
 * {@code HoeRebalance.raised} (which delegate here so their tested names keep working),
 * and the 1.20.1 twins directly. One formula in one file, so a balance change cannot land
 * on one leg and miss the other. (⌒‿⌒)</p>
 */
public final class RebalanceMath {

    private RebalanceMath() {
    }

    /** The sword's point. One, and it is one: the design says one. */
    public static final float SWORD_REMOVED = 1.0F;

    /** The hoe's raise. One and a half: the design's number. */
    public static final float HOE_ADDED = 1.5F;

    /**
     * One point off, floored at zero: a negative attack damage would turn a weapon into a
     * healer with a swing animation.
     */
    public static float swordReduced(float offset) {
        return (float) Math.max(0.0, offset - SWORD_REMOVED);
    }

    /**
     * One and a half points on: adding to a negative baseline is the point, so there is no
     * floor -- the player's own one is still underneath it.
     */
    public static float hoeRaised(float offset) {
        return offset + HOE_ADDED;
    }
}
