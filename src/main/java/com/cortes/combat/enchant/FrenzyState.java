package com.cortes.combat.enchant;

/**
 * The frenzy's two numbers, as one value and therefore as one attachment.
 *
 * <h2>Why a record and not two ints</h2>
 *
 * <p><b>A stack without the tick it was written at is not a state</b> -- it is a number that can
 * never go down, because nothing knows how long it has been sitting there. The pair is always read
 * together and always written together, so it is one thing, and the project's rule is that all
 * mutable combat state lives in an attachment rather than in a static field: a static would leak
 * the same frenzy across two players on a dedicated server, and across a logout and a login.</p>
 *
 * <p>Not serialised and not synced: the frenzy is a live tempo, not a fact about a world, and the
 * bonus itself is an attribute, which the game already syncs to the client for free.</p>
 */
public record FrenzyState(int stacks, int lastTick) {

    public static final FrenzyState EMPTY = new FrenzyState(0, 0);

    /** One block broken, and the window reset to now. */
    public FrenzyState broken(int now, int ceiling) {
        int grown = Math.min(stacks + 1, ceiling);
        return new FrenzyState(grown, now);
    }

    /**
     * The state as of {@code now}, with staleness resolved here rather than at the call site.
     *
     * <p>Stale reads as {@link #EMPTY} instead of returning the old value, because a stack whose
     * window has closed must not still be paying: the ceiling that only resets on the next block
     * is a ceiling that never resets for a player who stopped digging.</p>
     */
    public FrenzyState asOf(int now) {
        return GoldEnchantRules.frenzyExpired(lastTick, now) ? EMPTY : this;
    }
}
