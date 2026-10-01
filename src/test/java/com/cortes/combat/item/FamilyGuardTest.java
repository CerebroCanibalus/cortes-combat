package com.cortes.combat.item;

import com.cortes.combat.core.weapon.GuardProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the five families can guard, and why the answer is not the Java class.
 *
 * <p>All thirty items are {@code SwordItem}s, because that is how a mod gets vanilla's own
 * {@code createAttributes} and therefore the right damage and speed per tier. And
 * {@code GuardStats} used to answer "can this guard" with {@code instanceof SwordItem}. Which
 * meant all thirty guarded, including the two whose right button is a special, and nothing
 * arbitrated between the two meanings of one button.</p>
 *
 * <p>These are the numbers the design actually decided, pinned so that a family cannot quietly
 * gain a guard by changing which vanilla class it borrows attributes from.</p>
 */
class FamilyGuardTest {

    @Test
    @DisplayName("none of the five new families guards")
    void noneOfThemGuard() {
        // All five, the ropera included. It was left guarding here on the reasoning that it
        // has no special and is the sword-adjacent one, and the playtest settled it. The
        // guard is a shield or a sword and none of the five is either.
        for (WeaponFamily family : WeaponFamily.values()) {
            assertFalse(family.guard().canGuard(), family + " must not guard");
            assertEquals(GuardProfile.NONE, family.guard(),
                    family + " should be exactly NONE rather than a profile with no points");
        }
    }

    @Test
    @DisplayName("the ones that cannot guard are worth zero, not a negative or a half")
    void noGuardIsNoGuard() {
        for (WeaponFamily family : EnumSet.of(WeaponFamily.DAGA, WeaponFamily.ALABARDA,
                WeaponFamily.MARTILLO, WeaponFamily.SHOTEL)) {
            assertEquals(GuardProfile.NONE, family.guard(),
                    family + " should be exactly NONE");
            assertEquals(0, family.guard().safePoints(),
                    family + " should have no points at all");
        }
    }

    @Test
    @DisplayName("the ropera is not a sword on this axis")
    void theRoperaIsNotASword() {
        // There was a test here asserting the ropera guarded with the sword's one point. It passed,
        // and the playtest reported "the ropera can guard, which it should not" the same session.
        // It was not a wrong test: it was a wrong decision that had been written down and then
        // pinned, which is what a test is for, and it does not know that.
        assertFalse(WeaponFamily.ROPERA.guard().canGuard(),
                "the ropera is a polearm, not a sword");
        assertNotEquals(GuardProfile.SWORD, WeaponFamily.ROPERA.guard());
    }

    @Test
    @DisplayName("no family is left undecided")
    void everyFamilyHasAnAnswer() {
        // The failure mode this exists to stop is a family added later with no decision made about
        // it, inheriting whatever the Java class happens to imply.
        Set<WeaponFamily> decided = EnumSet.allOf(WeaponFamily.class);
        for (WeaponFamily family : decided) {
            assertTrue(family.guard() == GuardProfile.SWORD
                            || family.guard() == GuardProfile.NONE,
                    family + " resolves to an unexpected guard profile: " + family.guard());
        }
        assertEquals(5, decided.size(), "the catalogue grew and this test should notice");
    }

    @Test
    @DisplayName("the two families with a right button special are among the ones that do not guard")
    void specialAndGuardNeverOverlap() {
        // The rule the button arbitration rests on: if an item's right button means something, the
        // guard cannot also live there. If a family ever gains a special while guarding, this is
        // the test that says so.
        for (WeaponFamily family : WeaponFamily.values()) {
            if (family.special() != com.cortes.combat.core.weapon.SpecialType.NONE) {
                assertFalse(family.guard().canGuard(),
                        family + " has a right click special and must not also guard");
            }
        }
    }
}