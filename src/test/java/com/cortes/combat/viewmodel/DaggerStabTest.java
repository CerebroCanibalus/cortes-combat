package com.cortes.combat.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The dagger's pick: it goes <em>up</em>, and the ropera is still the reference it is measured
 * against.
 *
 * <p><b>This test file asserted the opposite of the weapon twice, and both times the playtest was
 * right.</b> It required more than a third of an item of forward depth, on the reasoning that "the
 * blade goes further forward than it turns" is the shape of a thrust -- which is true, and is the
 * <em>ropera's</em> shape. The dagger was modelled on it as "a third of the ropera's travel" and
 * came out stabbing with the flat of the blade. The report was "it hits with the edge and it should
 * be an upward pick".</p>
 *
 * <p>So the depth assertions are gone and the direction is inverted: the travel is on {@code y}, the
 * depth is small, and the cock drops the <em>hand</em> rather than the point. The ropera's numbers
 * are still here, because "a pick is not a small rapier" is a claim that only means something
 * against the rapier.</p>
 */
class DaggerStabTest {

    private static final ViewModelProfile DAGGER =
            ViewModelDefaults.of(WeaponProfile.DAGGER);
    private static final ViewModelProfile ROPERA =
            ViewModelDefaults.of(WeaponProfile.ROPERA);

    /** The wind-up on its own: every other channel rests at zero, so the sum is this track. */
    private static Offset cocked() {
        return DAGGER.evaluate(ViewModelInput.of(1.0F, 0.0F));
    }

    /** The strike on its own. */
    private static Offset driven() {
        return DAGGER.evaluate(ViewModelInput.of(0.0F, 1.0F));
    }

    /**
     * The direction is the whole change, and the depth is what had to go.
     *
     * <p>It used to require {@code |z| > 0.3} against a pitch of thirty, because seventeen
     * hundredths of an item of forward travel is a twitch and once the translation is that small the
     * only motion left is rotation -- and rotation of a small object reads as a spin. That reasoning
     * was right about the <em>spin</em> and wrong about the cure: the dagger does not need more
     * forward travel, it needs the travel somewhere else.</p>
     */
    @Test
    @DisplayName("the blade goes UP further than it turns, and barely forward at all")
    void thePickLiftsRatherThanThrusts() {
        Offset pick = driven();

        assertTrue(Math.abs(pick.z()) < 0.15,
                "the depth is " + Math.abs(pick.z()) + " and it has to stay small. A pick lifts; if "
                        + "it also covers ground forward it is a thrust with a rake on it, which is "
                        + "the motion that was rejected -- and it is the motion that put the flat of "
                        + "the blade in front of the target");
        assertTrue(pick.y() > 0.2,
                "and it goes UP, by " + pick.y() + " of an item. The old motion spent its travel on "
                        + "z and the new one spends it on y, and the difference between the two is a "
                        + "dagger poking and a pick driving");
        assertTrue(Math.abs(pick.y()) > Math.abs(pick.z()),
                "with more travel up than forward, " + Math.abs(pick.y()) + " against "
                        + Math.abs(pick.z()) + ". Anything else is a thrust");

        assertTrue(pick.pitch() < 0.0,
                "and the pitch is " + pick.pitch() + " degrees, negative, which rakes the point "
                        + "toward the camera instead of leaving the blade standing up while the hand "
                        + "moves. That raking is the other half of the fix: travel up is a swing, "
                        + "travel up with the point leading is a pick");
    }

    /**
     * The cock makes a bottom to pick from, and the bottom is the hand.
     *
     * <p>It used to require the pitch to be negative -- "the cock drops the point" -- because a stab
     * dips before it drives in. A pick has to start low, and a low start is the hand going down
     * rather than the point: so the y is negative and the pitch is positive, tipping the blade off
     * the line it is about to travel.</p>
     */
    @Test
    @DisplayName("the cock drops the hand and tips the blade off the line")
    void theCockMakesABottomToPickFrom() {
        Offset cock = cocked();

        assertTrue(cock.y() < 0.0,
                "the hand drops, by " + cock.y() + " of an item. A pick needs a bottom to push "
                        + "from, and the bottom is the hand");
        assertTrue(cock.pitch() > 0.0,
                "and the blade tips off the line by " + cock.pitch() + " degrees, which is what "
                        + "leaves something for the upward drive to rake against");
        assertTrue(Math.abs(cock.z()) < 0.2,
                "but it barely travels in depth: " + Math.abs(cock.z()) + ". The backward travel is "
                        + "a set-up and not a motion of its own, and if it were as long as the pick "
                        + "the whole thing would read as two strokes");
    }

    /**
     * It is the ropera's thrust, and its normal strike is not the comparison.
     *
     * <p><b>The ropera's evaluated lunge reads 84 degrees of pitch and that number is a trap.</b>
     * Fifty eight of it is the weapon's rest pose -- its art is drawn with the blade vertical, so it
     * holds that offset permanently -- and only twenty six is movement. Reading the total as
     * rotation is exactly how the first version of the dagger ended up a spin: it counted a pose
     * the weapon was already in as motion.</p>
     */
    @Test
    @DisplayName("it is not a small rapier, and the ropera's own numbers are the reference")
    void itIsNotTheRoperaAtADifferentLength() {
        Offset rapierCut = ROPERA.evaluate(ViewModelInput.of(0.0F, 1.0F));
        assertTrue(Math.abs(rapierCut.pitch()) > 30.0,
                "the ropera's NORMAL strike is " + Math.abs(rapierCut.pitch()) + " degrees of pitch "
                        + "for half an item of depth. It is a cut, and matching a pick against it is "
                        + "how the dagger came out as a spin");

        Offset rapierThrust = ROPERA.evaluate(ViewModelInput.lunging(1.0F));
        assertEquals(84.0, Math.abs(rapierThrust.pitch()), 1.0E-6,
                "and its committed thrust evaluates to 84: fifty eight of rest plus twenty six of "
                        + "actual movement");
        assertEquals(-0.72, rapierThrust.z(), 1.0E-9,
                "with 0.72 of travel, and none of that travel is rest");

        Offset dagger = driven();
        assertTrue(Math.abs(rapierThrust.z()) > Math.abs(dagger.z()) * 2.0,
                "so the dagger covers far less ground forward than the rapier: "
                        + Math.abs(dagger.z()) + " against " + Math.abs(rapierThrust.z())
                        + ". It is not a small rapier, it goes somewhere else");
        assertTrue(dagger.y() > 0.2,
                "and the travel it does have is upward, which is the whole difference");
    }

    /** And it comes back down the same line, so the follow-through is not a second motion. */
    @Test
    @DisplayName("the follow-through comes back where it went")
    void itComesBackWhereItWent() {
        Offset after = DAGGER.evaluate(ViewModelInput.recovering(1.0F));
        Offset pick = driven();

        assertTrue(after.y() < 0.0,
                "the recover goes down at " + after.y() + " against the strike's " + pick.y()
                        + ". Same line, opposite direction -- a pick that did not come back down "
                        + "would leave the knife in the air");
        assertTrue(Math.abs(after.y()) < Math.abs(pick.y()),
                "and shorter than the pick went up, or the weapon is drawn further down than it "
                        + "started, which reads as a wind up nobody asked for");
    }

    /** And the whole profile rests at zero, like every other one. */
    @Test
    @DisplayName("the dagger rests at zero")
    void itRestsAtZero() {
        assertTrue(DAGGER.evaluate(ViewModelInput.RESTING).isZero(),
                "a resting dagger is offset " + DAGGER.evaluate(ViewModelInput.RESTING)
                        + ". Every channel rests at zero, so a non-zero rest is a weapon that is "
                        + "never in its pose");
        assertTrue(DAGGER.ownsSwing());
    }
}