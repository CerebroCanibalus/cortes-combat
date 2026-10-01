package com.cortes.combat.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rules the playtest changed, in a bare JVM.
 *
 * <p>Neither one is about geometry and neither one is about points, which is why they get their
 * own class instead of a few more methods in {@link GuardRulesTest}. Both came from the same
 * playtest round, and both are the kind of rule that looks like a one-liner right up until it
 * is wrong in a way that only shows on screen.</p>
 *
 * <p>The regen one exists because the bug was in a <em>caller</em>. Every test on
 * {@code GuardState} was green while a staggered guard was quietly refilling itself, because the
 * state class was right and the argument it was handed was not. Component tests cannot see that,
 * so the decision had to move somewhere a test could reach it.</p>
 */
class GuardOrderingTest {

    /**
     * The four cells, all of them, because the bug was one of them being flipped.
     *
     * <p>The two that were wrong at the same time are the interesting pair, and they are wrong in
     * opposite directions, which is what made it read as a reasonable expression: raised used to
     * refill and staggered used not to.</p>
     */
    @Nested
    @DisplayName("the refill pauses while the guard is raised or broken")
    class RegenPause {

        @Test
        @DisplayName("a raised guard does not refill")
        void raisedPauses() {
            assertTrue(GuardRules.regenPaused(true, false));
        }

        @Test
        @DisplayName("a broken guard does not refill")
        void staggeredPauses() {
            // The playtest. "The points keep regenerating while the guard is broken" -- and the
            // expression feeding this flag said the opposite while reading like agreement.
            assertTrue(GuardRules.regenPaused(false, true));
        }

        @Test
        @DisplayName("a down, unbroken guard does refill")
        void neitherResumes() {
            assertFalse(GuardRules.regenPaused(false, false));
        }

        @Test
        @DisplayName("both at once still just pauses")
        void bothPause() {
            assertTrue(GuardRules.regenPaused(true, true));
        }
    }

    @Nested
    @DisplayName("you cover first, you eat after")
    class EatingAndGuarding {

        @Test
        @DisplayName("the first press is allowed, which is what lets the two start together")
        void firstPressIsAllowed() {
            // The whole point of the ordering rule. On a single press the guard resolves
            // immediately and the food takes 32 ticks, so the guard is always up first without
            // anything having to arbitrate between them.
            assertTrue(GuardRules.mayRaiseGuard(true, false));
        }

        @Test
        @DisplayName("a second press while already chewing does not raise the guard")
        void noRaiseMidBite() {
            assertFalse(GuardRules.mayRaiseGuard(true, true));
        }

        @Test
        @DisplayName("no guard item means no guard, eating or not")
        void noItemNoGuard() {
            assertFalse(GuardRules.mayRaiseGuard(false, false));
            assertFalse(GuardRules.mayRaiseGuard(false, true));
        }
    }

    @Nested
    @DisplayName("a broken guard may not eat your dinner")
    class CancellingTheItemUse {

        @Test
        @DisplayName("a raised shield is stopped when the guard goes down")
        void shieldIsStopped() {
            // The case the check exists for: the block use is predicted on the client and the
            // server cannot take it back, so the client has to drop it itself or the shield
            // stays up on screen for as long as the button is held.
            assertTrue(GuardRules.mayCancelItemUse(true, true));
        }

        @Test
        @DisplayName("a meal is never stopped, even with a shield in the other hand")
        void mealSurvives() {
            // The regression, in one assertion. The old scope was "the hands hold something
            // that can guard", which a shield in the off hand satisfies while you are halfway
            // through a steak in the main hand -- so breaking the guard cancelled the dinner.
            assertFalse(GuardRules.mayCancelItemUse(true, false));
        }

        @Test
        @DisplayName("nothing in use means nothing to stop")
        void nothingInUse() {
            assertFalse(GuardRules.mayCancelItemUse(false, true));
            assertFalse(GuardRules.mayCancelItemUse(false, false));
        }
    }

    @Nested
    @DisplayName("the shield does not go up without points")
    class StartingTheItemUse {

        @Test
        @DisplayName("an empty guard means vanilla may not raise the shield")
        void emptyGuardRefusesTheUse() {
            // The playtest. The shield went up on screen with nothing left, which is vanilla
            // doing it and us not stopping it: right-clicking starts an item use, the server
            // runs it, and the flag syncs.
            assertFalse(GuardRules.mayStartItemUse(true, false));
        }

        @Test
        @DisplayName("a guard with points raises exactly as it did before")
        void readyGuardAllowsTheUse() {
            // This one is load-bearing in the other direction. The block decision hangs off
            // LivingShieldBlockEvent, which only fires while the vanilla use is running, so
            // refusing it unconditionally would stop the guard blocking at all.
            assertTrue(GuardRules.mayStartItemUse(true, true));
        }

        @Test
        @DisplayName("bread in the main hand with a shield in the other one still gets eaten")
        void foodIsNotTheGuardsProblem() {
            // The right-clicked hand is bread, so this is not the guard's use to refuse. The
            // first argument is the whole reason there are two.
            assertTrue(GuardRules.mayStartItemUse(false, false));
        }

        @Test
        @DisplayName("a bow with a shield in the off hand is still a bow")
        void otherItemsAreUntouched() {
            assertTrue(GuardRules.mayStartItemUse(false, true));
        }
    }
}
