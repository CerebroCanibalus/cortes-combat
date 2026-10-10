package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Warhammer escalation: how many consecutive hits landed, and when the last one was.
 *
 * <p>By design there is no reset on taking damage. The only thing that breaks the streak is
 * the 4s window running out, which makes the warhammer a weapon of commitment rather than a
 * weapon that works against whoever stands still.</p>
 *
 * <h2>Why the window is a field and not just the constant</h2>
 *
 * <p><b>Ritmo de Hierro lengthens it, and the length belongs to the streak and not to the
 * weapon.</b> The window is decided when the streak's first hit lands and then travels with the
 * state: reading it back off the item every tick would let a player start a streak with the
 * enchanted hammer and swap to an unenchanted one mid-chain, and the streak would collapse on a
 * rule it was never started under. It is the same rule the lunge follows with its material, and
 * for the same reason -- <b>what a legal action was judged by has to be what it is still judged
 * by.</b></p>
 */
public record StreakState(int hits, int ticksSinceLastHit, int windowTicks) {

    /** 4 seconds, per DESIGN.md. The window a warhammer has with no enchantment on it. */
    public static final int WINDOW_TICKS = 80;

    public static final StreakState EMPTY = new StreakState(0, WINDOW_TICKS);

    public static final Codec<StreakState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("hits").forGetter(StreakState::hits),
            Codec.INT.fieldOf("ticks_since_last_hit").forGetter(StreakState::ticksSinceLastHit),
            // Defaulted rather than required: a streak written before this field existed was a
            // streak at the base window, and a missing field has to read as exactly that instead
            // of failing the load of a whole player.
            Codec.INT.optionalFieldOf("window_ticks", WINDOW_TICKS)
                    .forGetter(StreakState::windowTicks)
    ).apply(instance, StreakState::new));

    /** A streak at the base window: the one a warhammer with no enchantment runs. */
    public StreakState(int hits, int ticksSinceLastHit) {
        this(hits, ticksSinceLastHit, WINDOW_TICKS);
    }

    public boolean isActive() {
        return hits > 0 && ticksSinceLastHit < windowTicks;
    }

    /**
     * One more hit, and the window it will be judged by from here on.
     *
     * <p>The window is re-read on every hit rather than kept from the first one: a player who
     * enchants the hammer mid-fight is holding a different weapon, and the chain should be worth
     * what the weapon in their hand is worth. What it is <em>not</em> is re-read from the item on
     * every tick -- see the class note.</p>
     */
    public StreakState onHit(int windowTicks) {
        return new StreakState(hits + 1, 0, windowTicks);
    }

    public StreakState tick() {
        if (hits == 0) {
            return this;
        }
        int since = ticksSinceLastHit + 1;
        return since >= windowTicks ? EMPTY : new StreakState(hits, since, windowTicks);
    }

    /**
     * The pitch this streak's next hit sounds at.
     *
     * <p><b>The escalation you can hear, which the escalation you can read was not.</b> The
     * multiplier climbs silently and without a cap, so a player who lands eight consecutive hits
     * has no way to know the streak exists until something dies -- and the one thing the design
     * cannot afford is a reward the player cannot notice.</p>
     */
    public float stackPitch() {
        return stackPitchFor(hits);
    }

    /**
     * Half a tone to two, one note block step at a time.
     *
     * <p><b>Semitones, and not a linear slide, because a slide stops being a scale.</b> Between 0.5
     * and 2.0 a linear rise spends most of its travel in the top half of the range, where the ear
     * cannot resolve small steps, so the early hits sound like nothing happened. A semitone ladder
     * is the interval the ear is built to notice, and it is what makes the second hit read as
     * higher than the first rather than as different.</p>
     *
     * <p><b>The range is vanilla's, and that is why the numbers are exactly these.</b>
     * {@code NoteBlock#getPitchFromNote} is {@code 2^((note - 12) / 12)}, so note 0 is 0.5 and
     * note 24 is 2.0 -- the two ends of the instrument's own two-octave sweep. A pitch range taken
     * from that slider sounds like something the player has already heard a thousand times.</p>
     *
     * <p><b>Nine hits, three semitones each, and the middle lands on exactly 1.0.</b> The ladder
     * divides evenly into the two octaves, so the halfway point is the untouched pitch rather than
     * a stretch of it. Nine is also reachable: at the warhammer's nineteen ticks a swing that is
     * a little over eight seconds long, and the escalation is uncapped so the ceiling is a real
     * place to arrive rather than a decoration nobody gets to.</p>
     *
     * <p>Anything past the ninth hit holds at the top instead of running off the end. The ladder is
     * a readout, and a readout that keeps climbing stops being one.</p>
     */
    public static float stackPitchFor(int hits) {
        int step = Math.max(1, FULL_STACK_HITS - 1);
        int clamped = Math.max(1, Math.min(hits, FULL_STACK_HITS));
        int note = (clamped - 1) * SEMITONES_ACROSS / step;
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    /** Hits it takes to walk the whole ladder, first landed hit included. */
    public static final int FULL_STACK_HITS = 9;

    /** Two octaves, which is the distance between {@code 0.5} and {@code 2.0}. */
    static final int SEMITONES_ACROSS = 24;
}
