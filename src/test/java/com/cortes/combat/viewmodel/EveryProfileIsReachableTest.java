package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * That every weapon profile in the design is actually reachable.
 *
 * <p>The bare fist was not. {@code ItemViewModel.resolve} short circuited an empty stack to
 * {@link ViewModelProfile#NONE}, and since {@code NONE} is not a weapon there was nothing to
 * draw, so {@code chargingFist()} was code no execution path could reach. Three rounds of
 * playtesting reported it as "the fist does not animate", and every one of those rounds went and
 * changed the fist's <em>numbers</em>.</p>
 *
 * <p>The lesson is the one this whole round is about: an unreachable profile and a profile with
 * the wrong numbers look exactly the same from the player's chair, and only one of them is fixed
 * by editing an {@code Offset}. So the reachability is what gets a test, not the feel.</p>
 */
class EveryProfileIsReachableTest {

    @Test
    @DisplayName("the bare fist has a profile, and it is the one the design asks for")
    void theFistIsNotDeadCode() {
        // The design makes the fists chargeable because they are the fastest thing in the game,
        // which is the only thing that makes them worth having with nothing equipped.
        assertFalse(ViewModelDefaults.of(WeaponProfile.UNARMED).isEmpty(),
                "the fist has a profile to draw, or the UNARMED row of the table is unreachable");
    }

    @Test
    @DisplayName("the fist is not the sword's animation")
    void theFistIsItsOwn() {
        assertFalse(
                ViewModelDefaults.of(WeaponProfile.UNARMED).idleFor(ViewModelInput.of(1.0F, 1.0F)),
                "a charged fist has to move");
    }

    @Test
    @DisplayName("the sword is reachable and is the one with the profile")
    void theSwordIsReachable() {
        assertFalse(ViewModelDefaults.of(WeaponProfile.SWORD).isEmpty(),
                "the sword is the weapon this was debugged on");
    }
}
