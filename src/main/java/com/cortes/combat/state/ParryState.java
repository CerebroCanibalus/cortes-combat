package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Sabre parry: how long the window is open, and how long until the next one is allowed.
 *
 * <p>Reflects only the single hit that landed inside the window. There is no accumulated
 * buffer and no stored damage waiting to be released.</p>
 *
 * <p><b>Half a second, and it cannot be held.</b> The window is twelve ticks and pressing again
 * inside it does nothing, so the parry is a thing you commit to rather than a stance you can keep
 * up. A window that refreshed on every press would be a free block with a five second lockout
 * somewhere behind it, and the cost of the parry would be the thing you stop paying attention to.
 * This is the same rule as the guard's: a state that outlives the button that made it is a state
 * the player did not ask for.</p>
 *
 * <p>The five second cooldown is the other half of the price. Between the two of them, plus the
 * slowdown the window costs, the sabre's parry is the riskiest thing the weapon does -- which is
 * the point, because the sabre's reach is the shortest in the game and a defensive option it could
 * lean on forever would be a strictly better ropera.</p>
 */
public record ParryState(int activeTicks, int cooldownTicks) {

    /** Twelve ticks: 0.6 seconds, per the decision that the parry cannot be held open. */
    public static final int WINDOW_TICKS = 12;

    /** 5 seconds, per DESIGN.md. */
    public static final int COOLDOWN_TICKS = 100;

    public static final ParryState READY = new ParryState(0, 0);
    public static final Codec<ParryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("active_ticks").forGetter(ParryState::activeTicks),
            Codec.INT.fieldOf("cooldown_ticks").forGetter(ParryState::cooldownTicks)
    ).apply(instance, ParryState::new));

    public boolean isReady() {
        return cooldownTicks <= 0;
    }

    public boolean isParrying() {
        return activeTicks > 0;
    }

    /**
     * Whether a press right now would do anything.
     *
     * <p>Both halves matter and they are different halves. A ready parry with the window already
     * open is not a second parry: refusing it is what stops the window from being a stance.</p>
     */
    public boolean canActivate() {
        return isReady() && !isParrying();
    }

    /**
     * Opens the window, at a cooldown the weapon's own enchantment may have shortened.
     *
     * <p><b>The level goes in, and not the ticks.</b> A caller that passed the cooldown would be a
     * caller that can forget the enchantment -- and the whole rule would live at the call site,
     * where no test can reach it. Taking the level means level zero is the base cooldown by
     * construction, and {@link com.cortes.combat.enchant.CombatEnchantRules#parryCooldown} is the
     * only place the number is decided. The window is untouched either way: what the enchantment
     * buys is frequency, not duration.</p>
     */
    public ParryState activate(int enchantLevel) {
        return new ParryState(WINDOW_TICKS,
                com.cortes.combat.enchant.CombatEnchantRules.parryCooldown(COOLDOWN_TICKS,
                        enchantLevel));
    }

    /**
     * A hit landed inside the window: the parry is spent, the cooldown is not restarted.
     *
     * <p>One parry per press. Leaving the window open after a success would let one press parry
     * every hit in the next twelve ticks, and "returns the damage of the parried hit" stops being
     * a skill and becomes a stance.</p>
     */
    public ParryState consume() {
        return new ParryState(0, cooldownTicks);
    }

    public ParryState tick() {
        return new ParryState(Math.max(0, activeTicks - 1), Math.max(0, cooldownTicks - 1));
    }

    /** One tick of the parry, and whether the client has to hear about it. */
    public record Step(ParryState state, boolean tellClient) {

        /** What a caller writes to the attachment, and what it sends to the player. */
        public static Step of(ParryState state, boolean tellClient) {
            return new Step(state, tellClient);
        }
    }

    /**
     * The tick, and the "does the client need to know" decision, in one place.
     *
     * <p><b>This method exists because the caller got it wrong first.</b> The ticker asked
     * "is the window still open?" and returned early when it was not, on the reasonable-looking
     * grounds that a closed window is nothing to report. That guard was also swallowing the
     * countdown: the state sits at {@code (0, 100)} for the whole lockout, so the early return
     * fired every tick of those five seconds, the cooldown never fell, and the sabre could be
     * parried <b>once per session</b>. The playtest found it in a minute.</p>
     *
     * <p>The two questions -- does the number move, and does the client need telling -- are
     * different questions that came to look like one, and answering them together at the call site
     * is what let a shortcut through. {@code ParryState#tick} is correct and was never wrong; the
     * thing that was wrong was the question being asked of it, and putting the question here is
     * the only way a test can reach it.</p>
     */
    public Step step() {
        if (isReady() && !isParrying()) {
            // Nothing running. Returning the same state rather than an equal new one is not a
            // detail: the caller writes this to an attachment, and a record that compares equal is
            // the difference between a quiet tick and a write.
            return Step.of(this, false);
        }
        ParryState next = tick();
        // Told on the tick the window closes, and never again. A stream of a number nobody plots
        // is a packet per player per tick for the length of a press.
        return Step.of(next, isParrying() && !next.isParrying());
    }
}
