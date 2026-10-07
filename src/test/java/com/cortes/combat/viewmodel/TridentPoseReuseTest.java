package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the trident really is the ropera's motion, and that its own two numbers are its own.
 *
 * <h2>Why the reuse is asserted instead of described</h2>
 *
 * <p>Because "it reuses the ropera's strike" written in a comment is a promise, and a promise about
 * a number is worth nothing the day somebody retunes the ropera: the trident would keep the old
 * figures and the two would silently diverge. <b>These assert the numbers are identical</b>, so
 * the reuse is a fact and a retune has to change both.</p>
 *
 * <p>The same test also pins the two numbers that <em>are</em> the trident's, because a profile
 * that copies everything is a second ropera wearing a different name.</p>
 */
class TridentPoseReuseTest {

    private static final ViewModelProfile ROPERA = ViewModelDefaults.of(WeaponProfile.ROPERA);
    private static final ViewModelProfile TRIDENT = ViewModelDefaults.of(WeaponProfile.TRIDENT);

    /**
     * The thrust and the follow-through are the ropera's, to the decimal.
     *
     * <p>Asserted through {@code evaluate} at the same inputs rather than by reading the tracks,
     * because the tracks are private and a copy of their internals is a second thing to forget.
     * The question is "what does the hand do", and that is what this asks.</p>
     */
    @Test
    @DisplayName("the thrust and the recovery are the ropera's, to the decimal")
    void theThrustIsTheRoperas() {
        // Three points along the cut, so this is the motion and not three coincident frames.
        //
        // **Compared as a delta from each profile's own resting pose.** The first version compared
        // the offsets outright and failed at zero on the first float, because at zero what
        // `evaluate` returns *is* the rest -- and the two rests are deliberately different, the
        // ropera being the one weapon in this project that is held differently. "Uses the same
        // motion" is about the travel, not about where the weapon starts, and a comparison that
        // could not tell those apart was a comparison of the wrong thing.
        Offset roperaRest = ROPERA.evaluate(ViewModelInput.RESTING);
        Offset tridentRest = TRIDENT.evaluate(ViewModelInput.RESTING);
        for (float at : new float[]{0.0F, 0.35F, 1.0F}) {
            Offset ropera = minus(ROPERA.evaluate(ViewModelInput.of(0.0F, at)), roperaRest);
            Offset trident = minus(TRIDENT.evaluate(ViewModelInput.of(0.0F, at)), tridentRest);
            assertEquals(ropera.x(), trident.x(), 1.0E-6F, "x at " + at);
            assertEquals(ropera.y(), trident.y(), 1.0E-6F, "y at " + at);
            assertEquals(ropera.z(), trident.z(), 1.0E-6F, "z at " + at);
            assertEquals(ropera.pitch(), trident.pitch(), 1.0E-6F, "pitch at " + at);
            assertEquals(ropera.yaw(), trident.yaw(), 1.0E-6F, "yaw at " + at);
            assertEquals(ropera.roll(), trident.roll(), 1.0E-6F, "roll at " + at);
        }
        // And it is not a trivial identity: the travel has to be non-zero, or the whole test would
        // pass on two empty profiles.
        assertFalse(ROPERA.evaluate(ViewModelInput.of(0.0F, 1.0F)).z()
                        == ROPERA.evaluate(ViewModelInput.RESTING).z(),
                "sanity: the ropera's thrust moves at all");
        assertEquals(minus(ROPERA.evaluate(ViewModelInput.recovering(0.5F)), roperaRest),
                minus(TRIDENT.evaluate(ViewModelInput.recovering(0.5F)), tridentRest),
                "and the follow-through is the ropera's too, as a delta");
    }

    private static Offset minus(Offset a, Offset b) {
        return new Offset(a.x() - b.x(), a.y() - b.y(), a.z() - b.z(),
                a.pitch() - b.pitch(), a.yaw() - b.yaw(), a.roll() - b.roll());
    }

    /**
     * The cock is its own, and it is deeper.
     *
     * <p><b>This is the number the weapon exists on.</b> A shaft five and a half blocks long, given
     * twenty five ticks before it is thrown, is a thing that goes <em>back</em>: the spear retreats
     * almost three times as far as a rapier's does, and that distance is what the player's eye
     * reads as "this one is loaded".</p>
     *
     * <p>Asserted as a relationship rather than as a figure, so that retuning both together does
     * not fail &mdash; and so that retuning one of them does.</p>
     */
    @Test
    @DisplayName("the cock is its own and it goes back further than a rapier's")
    void theCockIsItsOwn() {
        Offset ropera = ROPERA.evaluate(ViewModelInput.of(1.0F, 0.0F));
        Offset trident = TRIDENT.evaluate(ViewModelInput.of(1.0F, 0.0F));

        assertTrue(trident.z() > ropera.z() * 2.0F,
                "**a full charge pulls the trident back more than twice as far as the ropera.** "
                        + "ropera " + ropera.z() + ", trident " + trident.z() + ". This is the whole "
                        + "reason the weapon can be told apart by its charge, and it is the number "
                        + "to look at in a playtest. The rest is asserted in its own test below, "
                        + "because it is a different rule and not part of the cock");
    }

    /**
     * It rests where vanilla draws it, and that is not a detail.
     *
     * <p><b>The first version of this profile had a rest pose at -28 and every test in the project
     * passed.</b> A rest pose is a correction for art that was drawn wrong for the weapon's job,
     * and vanilla's trident art is drawn the way a trident is held. Rotating it puts the point at
     * the floor on the first frame of every idle, which is the kind of bug that has no test until
     * somebody looks at the screen.</p>
     */
    @Test
    @DisplayName("the trident rests at zero, like every vanilla item that is not the ropera")
    void theRestIsVanillas() {
        assertTrue(TRIDENT.evaluate(ViewModelInput.RESTING).isZero(),
                "**nothing moves it at rest.** The ropera is the only weapon in this project with a "
                        + "rest pose and it has one because its own art is drawn wrong for a thrust");
        assertFalse(ROPERA.evaluate(ViewModelInput.RESTING).isZero(),
                "sanity: the ropera's is not zero, which is what makes the two rules different "
                        + "rather than the same rule written twice");
    }

    /**
     * It thrusts, and it does not chop.
     *
     * <p>A spear that chops is a spear being used as a sword, and the two motions drive the same
     * arm &mdash; whichever track is larger wins, so reading both means the weapon gets whichever
     * number happened to be bigger and not one anybody chose.</p>
     */
    @Test
    @DisplayName("the trident thrusts and never chops")
    void itThrusts() {
        assertTrue(TRIDENT.reads(Channel.STRIKE), "a spear at five and a half blocks is a thrust");
        assertFalse(TRIDENT.reads(Channel.CHOP),
                "and it is not a chop. Both tracks drive the same arm and the larger one wins, so "
                        + "reading both gives the weapon whichever number is bigger rather than "
                        + "one anybody decided");
        assertTrue(TRIDENT.reads(Channel.WINDUP),
                "**and it reads the wind-up**, which is the charge for every weapon in this game. "
                        + "That is the whole feedback argument for giving a vanilla item a profile "
                        + "at all: the loaded thrust drags the target in, and no bar along the "
                        + "bottom of the screen says that");
    }
}
