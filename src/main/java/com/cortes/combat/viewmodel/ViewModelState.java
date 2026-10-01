package com.cortes.combat.viewmodel;

/**
 * One word for what the weapon is doing, derived from the channels.
 *
 * <h2>This is not a state machine, and the difference is the point</h2>
 *
 * <p>The playtest asked whether the animations should get one. The answer here is no, and the
 * reason is not taste.</p>
 *
 * <p>A state machine says <em>exactly one</em> of idle, attacking, guarding, broken is true, and
 * then every weapon has to be written for every state and every state has to be written for
 * every weapon. Six states is fifteen transitions to decide what happens, and the answer to each
 * one is a per-weapon number. Adding a weapon becomes adding fifteen numbers instead of adding
 * a profile.</p>
 *
 * <p>Channels are the opposite shape: a profile is a list of
 * {@code amplitude × curve(channel)} terms, every channel rests at zero, and a weapon says
 * <em>"when a guard is up, do this"</em> without knowing that attacking also exists. Six channels
 * is six terms, and two of them can be non-zero at once with no rule anywhere saying they cannot.
 * That is why the guard can raise while a swing is recovering and neither profile needed to be
 * told.</p>
 *
 * <p>So what did the failure actually teach? Not that the architecture was wrong. It taught that
 * <b>a channel's length has to be chosen for the motion, not borrowed from another system's
 * clock</b>: the collapse was timed by the item cooldown, which is five seconds, and a
 * five-second marker is not a marker. No state machine would have caught that; the state machine
 * would have had the same bug with an extra layer.</p>
 *
 * <h2>What this class is for anyway</h2>
 *
 * <p>Debuggability. Five numbers in {@code /cc viewmodel} is five things to read and correlate,
 * and the question a playtest report actually asks is one question. This is a pure function of
 * the frame, so it cannot disagree with what is drawn, and there is nothing to keep in step.</p>
 */
public enum ViewModelState {

    /** Something is happening and it is not one of the named states below. The fallback. */
    BUSY,
    /** A guard was just knocked down. Highest priority: it is the shortest and the loudest. */
    GUARD_BROKEN,
    /** An attack in flight: the cut, its follow-through, or the charge leading into it. */
    ATTACKING,
    /** The stance is up. */
    GUARDING,
    /** Nothing, but the weapon is waiting out its cooldown. */
    COOLING_DOWN,
    /** Nothing at all. The item is exactly at the vanilla pose. */
    RESTING;

    /**
     * @param input the frame, already interpolated
     */
    public static ViewModelState of(ViewModelInput input) {
        if (input.guardBreak() > 0.0F) {
            return GUARD_BROKEN;
        }
        if (input.strike() > 0.0F || input.recover() > 0.0F || input.windup() > 0.0F) {
            return ATTACKING;
        }
        if (input.guard() > 0.0F) {
            return GUARDING;
        }
        if (input.cooldown() > 0.0F) {
            return COOLING_DOWN;
        }
        return RESTING;
    }

    /** Lower case for the chat line, which is Spanish-flavoured but should not shout. */
    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
