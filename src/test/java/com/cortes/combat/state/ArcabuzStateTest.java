package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arcabuz's fuse, and the one rule it exists to enforce.
 *
 * <p><b>A second press while the fuse burns must do nothing, and must not restart it.</b> That
 * is the same rule the parry made and the same one the ropera's discarded charge broke, and every
 * one of those three bugs was the same shape: a state that outlives the button that made it, and
 * a caller that asked the state a question it should have answered itself.</p>
 *
 * <p>So the rule lives in {@link ArcabuzState#canLight()} and this is what proves it.</p>
 */
class ArcabuzStateTest {

    @Test
    @DisplayName("a gun picked up off the ground is empty and wants a firestone")
    void emptyIsEmpty() {
        ArcabuzState state = ArcabuzState.EMPTY;
        assertFalse(state.isBurning());
        assertFalse(state.loaded());
        assertFalse(state.canLight(), "an empty gun cannot light a fuse");
        assertTrue(state.canReload(), "and it can be loaded");
        assertTrue(state.isEmpty());
    }

    /**
     * One second, exactly, and the number the texture's five frames are spent on.
     */
    @Test
    @DisplayName("the fuse is twenty ticks, which is one whole second")
    void theFuseIsOneSecond() {
        assertEquals(20, ArcabuzState.FUSE_TICKS,
                "one second is twenty ticks and the design says a second. This is also the number "
                        + "arcabuz.png.mcmeta spends its four lit frames on, so the two have to be "
                        + "the same number or the fuse the player watches is not the fuse they "
                        + "are standing through");
    }

    @Test
    @DisplayName("loading spends nothing but the firestone, and lighting spends it")
    void theTwoPressesAreDifferent() {
        ArcabuzState loaded = ArcabuzState.EMPTY.reload();
        assertTrue(loaded.loaded());
        assertFalse(loaded.isBurning());
        assertTrue(loaded.canLight());
        assertFalse(loaded.canReload(), "a loaded gun cannot be loaded again");

        ArcabuzState lit = loaded.light();
        assertFalse(lit.loaded(), "the firestone is spent AT THE LIGHT, not at the shot, so a gun "
                + "with a live fuse cannot be topped up behind the player's back");
        assertTrue(lit.isBurning());
        assertEquals(ArcabuzState.FUSE_TICKS, lit.fuseTicks());
    }

    /**
     * The whole test, and the bug it exists for.
     *
     * <p>Twelve ticks in, the state is asked again whether it can light. It cannot, and -- this
     * is the half that matters -- the countdown is exactly where it was. A rule that only refused
     * without that half would be a machine that delays itself forever, and the player would be
     * holding the right button watching a fuse that never reaches the end.</p>
     */
    @Test
    @DisplayName("a second press mid-fuse is refused and does not restart the countdown")
    void aSecondPressDoesNothing() {
        ArcabuzState lit = ArcabuzState.EMPTY.reload().light();

        ArcabuzState state = lit;
        for (int tick = 0; tick < 12; tick++) {
            state = state.step().state();
        }
        assertEquals(8, state.fuseTicks(), "twelve ticks of twenty gone");

        assertFalse(state.canLight(), "the second press is the bug, not the refusal");
        assertFalse(state.canReload(), "and it does not get to reload either: a gun with a live "
                + "fuse and a fresh charge would be a gun that fires twice off one firestone");

        // And the refused press changed nothing at all. This is the assertion the design's
        // sentence is really about: "no reinicia y no dispara".
        ArcabuzState after = state.step().state();
        assertEquals(7, after.fuseTicks(),
                "the countdown carried on from where it was. A refused press that reset the fuse "
                        + "to twenty would be an arcabuz that never fires while the button is held");
    }

    /** And the refusal is total: however many presses arrive, it fires on time. */
    @Test
    @DisplayName("spamming the button still fires on the twentieth tick and not before")
    void spammingStillFiresOnTime() {
        ArcabuzState state = ArcabuzState.EMPTY.reload().light();

        int fired = 0;
        for (int tick = 1; tick <= ArcabuzState.FUSE_TICKS; tick++) {
            ArcabuzState.Step step = state.step();
            state = step.state();
            if (step.fireNow()) {
                fired++;
                assertEquals(ArcabuzState.FUSE_TICKS, tick,
                        "the shot resolves on the twentieth tick, not on " + tick);
            }
            // A press arriving every single tick, which is the worst case and the one that would
            // catch a state that restarted itself.
            if (state.canLight()) {
                state = state.light();
            }
        }
        assertEquals(1, fired, "exactly one shot out of twenty presses");
        assertFalse(state.isBurning());
        assertFalse(state.loaded());
    }

    /**
     * The parry's lesson, applied: the countdown and the packet are two different questions.
     *
     * <p>The parry's ticker used to ask "is the window open" and return early when it was not,
     * which was true of the packet and silently also taken as true of the countdown -- so the
     * five second lockout never ran and the sabre could be parried once per session. This is the
     * same shape, and {@link ArcabuzState#step()} is the same answer: the tick always happens,
     * and only the <em>report</em> is conditional.</p>
     */
    @Test
    @DisplayName("an unlit fuse is not ticked and is not reported, and returns itself")
    void anUnlitFuseIsQuiet() {
        ArcabuzState.Step idle = ArcabuzState.EMPTY.step();
        assertFalse(idle.fireNow());
        assertFalse(idle.tellClient());
        assertSame(ArcabuzState.EMPTY, idle.state(),
                "the same instance rather than an equal new one. The caller writes this to an "
                        + "attachment, and a record that compares equal is the difference between "
                        + "a quiet tick and a write");
    }

    /**
     * Exactly two reports per shot: the light and the shot. Not twenty.
     */
    @Test
    @DisplayName("only the two transitions are reported")
    void onlyTransitionsAreReported() {
        ArcabuzState state = ArcabuzState.EMPTY.reload().light();
        int reports = 0;
        for (int tick = 0; tick < ArcabuzState.FUSE_TICKS; tick++) {
            ArcabuzState.Step step = state.step();
            state = step.state();
            if (step.tellClient()) {
                reports++;
            }
        }
        assertEquals(1, reports,
                "the client is told once per shot, when the fuse goes off. Twenty packets a shot "
                        + "to carry a number nobody plots is twenty packets a player for the length "
                        + "of a second");
    }

    /** The loaded gun and the burning one are different states and the state says which. */
    @Test
    @DisplayName("a gun that is neither loaded nor burning is the empty one")
    void reloadThenShootEndsEmpty() {
        ArcabuzState state = ArcabuzState.EMPTY.reload();
        assertFalse(state.isEmpty());

        state = state.light();
        while (state.isBurning()) {
            state = state.step().state();
        }
        assertTrue(state.isEmpty(),
                "the shot empties it. A gun that came back loaded would be a free shot nobody "
                        + "paid a firestone for");
    }
}
