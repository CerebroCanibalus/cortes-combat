package com.cortes.combat.core;

/**
 * What just happened to the player, as far as the HUD is concerned.
 *
 * <p>Only the events that change how the crosshair is drawn. It is a closed list on purpose:
 * every value here is a colour and a size change, and a HUD that reacts to everything stops
 * meaning anything.</p>
 *
 * <p>There is no "missed". A swing at nothing already costs the cooldown and shows up in the
 * brackets, and a fourth flash for whiffs would train the player to ignore the real ones.</p>
 *
 * <p>{@link #tintTicks()} lives here rather than in the drawing code because how long a colour
 * lasts is a property of the event, not of the widget that happens to show it. The crosshair
 * and the cooldown brackets read the same number, which is what keeps the two from disagreeing
 * about when the red is over.</p>
 */
public enum CombatFeedback {

    /** A landed hit. The crosshair grows and shakes. */
    HIT(6),

    /** A landed hit that killed. Same growth, louder colour, and it means more. */
    KILL(12),

    /** A hit successfully blocked. No growth: nothing was thrown at you. */
    GUARDED(8);

    private final int tintTicks;

    CombatFeedback(int tintTicks) {
        this.tintTicks = tintTicks;
    }

    /** Ticks the colour stays on screen. Long enough to see, short enough not to smear. */
    public int tintTicks() {
        return tintTicks;
    }

    /**
     * 24 bit RGB, no alpha. The drawing code owns alpha, because alpha means something
     * different in each place: a flat tint on the crosshair, a gradient on the brackets.
     */
    public int rgb() {
        return switch (this) {
            // Dark red. A hit should read as weight, not as an alarm.
            case HIT -> 0x8C1A14;
            // Vibrant blood. The kill is the one event allowed to be loud.
            case KILL -> 0xE11B1B;
            // Blue grey. Cold, and deliberately not blue: a hit blocked is not a reward.
            case GUARDED -> 0x8296AD;
        };
    }

    public static CombatFeedback byId(int id) {
        CombatFeedback[] values = values();
        return id >= 0 && id < values.length ? values[id] : HIT;
    }
}
