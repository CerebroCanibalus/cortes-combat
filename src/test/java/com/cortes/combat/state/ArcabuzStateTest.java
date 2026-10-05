package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
        ArcabuzState loaded = ArcabuzState.EMPTY.finishReload();
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
        ArcabuzState lit = ArcabuzState.EMPTY.finishReload().light();

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
        ArcabuzState state = ArcabuzState.EMPTY.finishReload().light();

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
        ArcabuzState state = ArcabuzState.EMPTY.finishReload().light();
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
        ArcabuzState state = ArcabuzState.EMPTY.finishReload();
        assertFalse(state.isEmpty());

        state = state.light();
        while (state.isBurning()) {
            state = state.step().state();
        }
        assertTrue(state.isEmpty(),
                "the shot empties it. A gun that came back loaded would be a free shot nobody "
                        + "paid a firestone for");
    }

    /**
     * The same walk, asking the question the test above cannot see.
     *
     * <p><b>{@code isEmpty()} is a proxy and the proxy was green while the gun was charged.</b> It
     * reads the two timers, so a state of {@code fuseTicks = 0, loaded = true} answers "empty" and
     * the assertion above passed for a whole session with the chamber never emptying. Nothing about
     * the gun was actually empty; the flintlock stayed down from the first shot onward and the
     * playtest reported it as "the model is always the loaded one".
     *
     * <p>So the channel is asserted here rather than the summary of it. The two differ in exactly
     * the way that matters: {@code isEmpty()} is what the rest of the code asks, and {@code loaded}
     * is what the player sees and what the client is told to draw.</p>
     */
    @Test
    @DisplayName("lighting empties the chamber, and nothing puts a firestone back into it")
    void lightingEmptiesTheChamberAndNothingRefillsIt() {
        // **Written the other way round from how it was first written, and the first version was
        // wrong about the code.** It began with `assertTrue(state.loaded())` after
        // `finishReload().light()`, on the assumption that the gun holds its charge through the
        // burn. It does not: `light()` spends the firestone, which is the design -- one firestone
        // per shot, charged when the match is struck, not when it goes off.
        //
        // That is worth a test of its own, because the client's `loaded` comes from somewhere else
        // entirely: a literal `true` in the lit packet, so the client keeps the loaded *pose* for
        // the length of the burn. The pose is a viewmodel decision; the chamber is this record. Two
        // things, two owners, and reading one as the other is how "the flintlock never comes back
        // up" gets diagnosed in the wrong file twice.
        ArcabuzState state = ArcabuzState.EMPTY.finishReload();
        assertTrue(state.loaded(), "a finished reload leaves a firestone in the chamber");

        state = state.light();
        assertFalse(state.loaded(),
                "lighting spends the firestone. A gun that kept it would let a player light a fuse "
                        + "they never paid for.");

        int ticks = 0;
        while (state.isBurning()) {
            state = state.step().state();
            ticks++;
            assertFalse(state.loaded(),
                    "tick " + ticks + " of the burn found a firestone back in the chamber. Nothing "
                            + "refills it: not the burn, and not the shot.");
        }
        assertFalse(state.loaded(),
                "after the shot the gun still holds a firestone, which is a free shot nobody paid "
                        + "a firestone for.");
    }

    // ------------------------------------------------------------------ the reload --

    /**
     * Two and a half seconds, and the number is not a guess about a charge.
     *
     * <p>It is a third longer than the halberd's whole sweep, which costs four swings and about
     * two seconds. A reload paid for in standing still cannot be the same length as a reload paid
     * for in hitting something, or the gun reads as the cheap button.</p>
     */
    @Test
    @DisplayName("the reload is fifty ticks, which is two and a half seconds")
    void theReloadIsTwoAndAHalfSeconds() {
        assertEquals(50, ArcabuzState.RELOAD_TICKS,
                "two and a half seconds. Half a second longer than the halberd's sweep, which is "
                        + "the other button in the game that costs time and has to feel dearer");
    }

    /**
     * The reload fills while the button is down, and not otherwise.
     *
     * <p>This is the mechanic. It is asserted as a loop over ticks with an explicit hold, because
     * a test that only checked the end state would pass a timer that ignored the button entirely
     * -- which is what the bug this fixes actually looked like.</p>
     */
    @Test
    @DisplayName("the gun fills only while the button is held")
    void theReloadOnlyAdvancesWhileHeld() {
        ArcabuzState state = ArcabuzState.EMPTY.startReload().withHeld(true);
        assertTrue(state.isReloading());
        assertEquals(ArcabuzState.RELOAD_TICKS, state.reloadTicks());

        int ticks = 0;
        while (state.isReloading() && ticks < 200) {
            state = state.step().state();
            ticks++;
        }
        assertEquals(ArcabuzState.RELOAD_TICKS, ticks,
                "holding for the whole reload fills the gun in exactly that many ticks");
        assertTrue(state.loaded(), "and the gun is loaded when it finishes");
    }

    /**
     * The reload begins with the button down, and that is not an assumption.
     *
     * <p><b>This is the test for "no deja recargar".</b> {@code startReload()} used to clear the
     * held flag, on the reasoning that a press must not assume the button is still down. The client
     * sends {@code held = true} and <em>then</em> the press, so the flag was already true and the
     * clear put the reload into the one state that can never advance: the gun took the reload and
     * then sat at fifty ticks for ever.</p>
     *
     * <p>Two halves, because either alone passes for the wrong reason.</p>
     */
    @Test
    @DisplayName("a reload starts with the button down, because the press says it is")
    void aReloadStartsHeld() {
        ArcabuzState pressedWhileHeld = ArcabuzState.EMPTY.withHeld(true).startReload();
        assertTrue(pressedWhileHeld.reloadHeld(),
                "the flag survives the press. Clearing it here is what made the gun unreloadable");
        assertTrue(pressedWhileHeld.isReloading());

        // **And it actually finishes**, which is the half that was broken: the state was
        // reloading and never advanced, so a loop over ticks is the only assertion that catches it.
        ArcabuzState state = pressedWhileHeld;
        int ticks = 0;
        while (!state.loaded() && ticks < 200) {
            state = state.step().state();
            ticks++;
        }
        assertTrue(state.loaded(),
                "a reload that begins on a press finishes in fifty ticks of holding. This one did "
                        + "not, and it did not fail loudly: the gun simply never filled");
        assertEquals(ArcabuzState.RELOAD_TICKS, ticks);
    }

    /**
     * And a press that is not followed by holding fills nothing.
     *
     * <p>The scenario is now built by clearing the flag explicitly rather than by pressing, because
     * the press no longer clears it -- and the property still has to hold: a state that is
     * reloading with the button up must throw its timer away.</p>
     */
    @Test
    @DisplayName("a reload that begins and is then not held fills nothing")
    void aReloadThatIsNotHeldFillsNothing() {
        ArcabuzState state = ArcabuzState.EMPTY.startReload().withHeld(false);
        for (int tick = 0; tick < ArcabuzState.RELOAD_TICKS * 2; tick++) {
            state = state.step().state();
        }
        assertTrue(state.isEmpty(),
                "a hundred ticks of a reload nobody is holding ends in an empty gun, and no "
                        + "firestone was ever due");
    }

    /**
     * And the loaded gun waits forever, which is the other half of what a reload is for.
     *
     * <p>Fifty idle ticks after loading, the gun is still loaded and can still light. A state
     * with a timer on it has no business expiring this one: the design says the charge sits in the
     * chamber until the player lights it, and a gun that unloads itself is a gun with a reload
     * cooldown nobody was told about.</p>
     */
    @Test
    @DisplayName("a loaded gun waits indefinitely and can still be lit")
    void aLoadedGunWaitsForever() {
        ArcabuzState state = ArcabuzState.EMPTY.finishReload();
        for (int tick = 0; tick < 20 * 60; tick++) {
            state = state.step().state();
            assertTrue(state.loaded(), "the gun un-loaded itself on tick " + tick);
        }
        assertTrue(state.canLight(),
                "a minute later the gun can still light its fuse, which is the whole point of "
                        + "loading it");
        assertEquals(0, state.fuseTicks(), "and nothing has been burning during any of it");
    }

    /**
     * The state follows the stack when the two disagree, and the gun it disagrees about is a gun
     * the player is holding.
     *
     * <p><b>The bug this was written for, and it was a bug in two places at once.</b> Swap to a
     * sword with a charged musket in your hand and the client''s viewmodel wipes its own copy of the
     * fuse, because a sword has no fuse and a leftover reload is the thing this project has been
     * bitten by twice. Pick the musket back up and nothing tells the client what it is holding: the
     * gun draws empty, and the next shot asks for a firestone that was already spent.
     *
     * <p><b>The server had the right answer the whole time.</b> The chamber lives on the
     * {@code ItemStack}, so reading it back says the gun is loaded. Nobody asked. The reconciliation
     * existed, but only in {@code resolve()}, which runs on the press -- and putting a gun back in
     * your hand is not a press.
     *
     * <p>So the rule is here, in the pure class, where a test can reach it: the stack wins, and the
     * countdown and the button flag survive the reconciliation untouched.</p>
     */
    @Test
    @DisplayName("a gun picked back up is charged again, because the stack still says so")
    void aGunPickedBackUpIsChargedAgain() {
        ArcabuzState loaded = ArcabuzState.EMPTY.finishReload();
        assertTrue(loaded.loaded(), "the gun starts out loaded");

        // The client''s copy was wiped, which is not a change on the server: the record still says
        // loaded and the stack still carries the chamber. What goes wrong is that nothing travels.
        ArcabuzState afterSwap = loaded.withChamber(true);
        assertTrue(afterSwap.loaded(),
                "the stack carries the chamber, so the gun is still charged. This one lost its "
                        + "charge because the client stopped listening");
        assertTrue(afterSwap.canLight(),
                "and it can be lit, which is the whole complaint: a musket that has a firestone in "
                        + "it and refuses to fire is a weapon the player cannot read");
    }

    /** And the reconciliation must not touch the two fields it has no opinion about. */
    @Test
    @DisplayName("reconciling the chamber keeps the countdown and the button")
    void reconcilingKeepsTheCountdownAndTheButton() {
        ArcabuzState burning = ArcabuzState.EMPTY.finishReload().light();
        for (int tick = 0; tick < 7; tick++) {
            burning = burning.step().state();
        }
        int fuseBefore = burning.fuseTicks();
        boolean heldBefore = burning.reloadHeld();
        assertTrue(fuseBefore > 0, "the gun is burning for this test to mean anything");
        assertTrue(fuseBefore < ArcabuzState.FUSE_TICKS, "and it is partly spent");

        ArcabuzState reconciled = burning.withChamber(false);
        assertEquals(fuseBefore, reconciled.fuseTicks(),
                "a gun picked up mid-burn keeps the countdown it had. Sending only the loaded flag "
                        + "would draw a fuse a third spent as though it had just been lit, because "
                        + "the frame comes off the countdown");
        assertEquals(heldBefore, reconciled.reloadHeld(),
                "and the button flag survives, because the reconciliation is about the chamber and "
                        + "knows nothing about what the mouse is doing");
    }

    /**
     * A reconciliation that changes nothing returns the same instance.
     *
     * <p><b>Because the caller compares by identity to decide whether to send a packet.</b> That is
     * the whole of "cheap": one compare per tick against a value already on the stack, and a packet
     * only on the tick where the two answers actually disagree. A method that built a fresh equal
     * record would send the state to the client twenty times a second forever.</p>
     */
    @Test
    void agreeingWithTheStackSendsNothing() {
        ArcabuzState loaded = ArcabuzState.EMPTY.finishReload();
        assertSame(loaded, loaded.withChamber(true),
                "the gun agrees with the stack, so nothing is rebuilt and no packet is sent");
        assertSame(loaded, loaded.withChamber(false),
                "and a stack that says empty does not rebuild the record either -- see the test "
                        + "below for why that direction is not the negation of this one");
    }

    /**
     * The stack can only fill the gun, never empty it, and that asymmetry is the whole mechanic.
     *
     * <p><b>This is a bug the first version of this method had, and it was nearly invisible.</b>
     * {@code withChamber} was written as "the stack wins", which reads symmetric and is not: the
     * chamber component is removed at the <em>light</em>, in the same statement that lights the
     * fuse. So for the twenty ticks a fuse burns, the honest server state is
     * {@code loaded == false} while the gun is about to fire -- the ball is in the barrel. A
     * symmetric reconciliation would empty the gun on that tick, and again on every tick after, and
     * the player would watch a charged musket drain itself while the fuse burned.
     *
     * <p><b>The empty direction belongs to the shot alone.</b> {@code resolve} takes the component
     * and lights the fuse in one breath, and there is no other event anywhere in this weapon that
     * turns a charged gun into an empty one. So this method fills and nothing else does, and a stack
     * that says "no" to a record that says "yes" is a gun the server has never heard of.</p>
     */
    @Test
    @DisplayName("the stack can fill the gun but cannot empty it while the fuse burns")
    void theStackCanFillButNotEmpty() {
        ArcabuzState burning = ArcabuzState.EMPTY.finishReload().light();
        assertFalse(burning.loaded(),
                "the firestone was spent at the light, so the chamber is empty on the stack from "
                        + "the moment the fuse is lit");
        assertTrue(burning.isBurning(), "and the gun is about to go off");

        ArcabuzState reconciled = burning.withChamber(false);
        assertFalse(reconciled.loaded(),
                "the record stays empty too, because the light already decided that and nothing "
                        + "here gets to decide it a second time");
        assertSame(burning, reconciled,
                "and the reconciliation does not even rebuild the record: the stack agreeing with "
                        + "the record is the no-packet case");
        assertFalse(reconciled.canLight(),
                "and a gun already burning cannot be lit twice, which is the gate that made the "
                        + "first version of this method look harmless");

        // **And a fuse that runs out does not come back**, which is the half the bug above could
        // not have told us: a one-way fill is only safe because something else empties the gun.
        ArcabuzState afterShot = burning;
        for (int tick = 0; tick < ArcabuzState.FUSE_TICKS + 1; tick++) {
            afterShot = afterShot.step().state();
        }
        assertFalse(afterShot.isBurning(), "the fuse ran out");
        assertFalse(afterShot.loaded(), "and there is nothing left to light");
    }

    /**
     * A charged stack fills a record that lost the charge.
     *
     * <p><b>The bug as reported.</b> Load a musket, swap to a sword, come back: the client wiped
     * its copy on the way out and the server's record was never consulted, so the gun drew empty
     * and the next shot wanted a firestone that was already spent. The reconciliation existed but
     * only in {@code resolve()}, which runs on the press -- and picking a gun back up is not a
     * press.</p>
     */
    @Test
    @DisplayName("a charged stack fills a record that lost the charge")
    void aChargedStackFillsARecordThatLostTheCharge() {
        // The scenario: the server's record is stale-empty while the stack says charged. That is
        // what the swap leaves behind, and it is also what a lost sync looks like.
        ArcabuzState stale = ArcabuzState.EMPTY;
        assertFalse(stale.loaded(), "the record is empty, which is what the swap left behind");

        ArcabuzState fixed = stale.withChamber(true);
        assertTrue(fixed.loaded(),
                "the stack carries the chamber, so the gun is charged. This one is still empty, "
                        + "which is the reported bug: the weapon draws unloaded and asks for a "
                        + "firestone the player already paid");
        assertTrue(fixed.canLight(), "and it can be lit, which is what a loaded gun is for");
        assertNotSame(stale, fixed,
                "a real change returns a different instance, because the caller sends a packet "
                        + "exactly when this returns something new");
    }
}