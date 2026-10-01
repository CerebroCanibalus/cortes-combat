package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ropera's two thrusts, and the difference between them.
 *
 * <p><b>Which axis is which, read out of the vanilla first person transform:</b> the main hand
 * is drawn at {@code translate(0.51, -0.08, -0.75)} so <b>−z is into the screen</b>, and vanilla's
 * verified blocking pose is {@code translate(-0.4786 * side, -0.0944, +0.0573)} which moves half a
 * unit sideways and a twentieth of one in depth, so <b>x is lateral</b>.</p>
 *
 * <p>And the ropera's art is drawn with the <b>blade pointing up</b>, which is the half of the
 * motion that a pure translation cannot supply. A rapier at rest is a lowered guard: the tip is
 * over the player's head. A thrust is therefore ninety degrees of rotation <em>and then</em> a
 * push, and the rotation is the bigger half.</p>
 *
 * <p>These tests exist because the first two versions of this profile were wrong in ways no
 * magnitude assertion could have caught: the travel was on the lateral axis ("se hace de lado"),
 * and then the push was there without the rotation. A number is retunable; an axis and a resting
 * pose are the thing being wrong.</p>
 */
class RoperaThrustTest {

    private static Offset strike(WeaponProfile profile) {
        // STRIKE is a bump, so it has to be sampled where the bump is at its peak.
        return ViewModelDefaults.of(profile).evaluate(ViewModelInput.of(0.0F, 1.0F));
    }

    @Test
    @DisplayName("the strike goes INTO the screen, and that is the whole thrust")
    void theStrikeGoesForward() {
        assertTrue(strike(WeaponProfile.ROPERA).z() < -0.2,
                "z is the depth axis and negative is away from the player: it was "
                        + strike(WeaponProfile.ROPERA).z() + ". On x instead, the rapier sweeps its "
                        + "point sideways, which is a cut, and no amount of making it big turns a "
                        + "cut into a thrust");
    }

    @Test
    @DisplayName("it does not sweep sideways at all")
    void noLateralTravel() {
        // The one that was wrong first, pinned exactly rather than by a threshold.
        assertEquals(0.0, strike(WeaponProfile.ROPERA).x(), 1.0E-6,
                "x is lateral. A rapier that moves on x as well as on z is dragging its point "
                        + "across the screen, which is what a twelve degree cone cannot afford");
    }

    @Test
    @DisplayName("and the blade is rotated DOWN to level, because the sprite points up")
    void theBladeComesDownToLevel() {
        // The resting blade is vertical, tip over the player's head. Without the rotation the
        // thrust is an upward blade shoved into the screen, which is a poke at the ceiling.
        double pitch = strike(WeaponProfile.ROPERA).pitch();
        assertTrue(pitch < -70.0,
                "close to ninety: the point has to end level with the crosshair and it was "
                        + pitch + ". A rapier at rest is a lowered guard, and a thrust is the "
                        + "rotation that lifts it out of one");
    }

    @Test
    @DisplayName("it is the straightest thing in the set, by the only measure that means anything")
    void theStraightestWeaponInTheGame() {
        Offset mine = strike(WeaponProfile.ROPERA);
        double mineArc = Math.abs(mine.x()) + Math.abs(mine.yaw()) + Math.abs(mine.roll());

        for (WeaponProfile profile : WeaponProfile.values()) {
            Offset other = strike(profile);
            double arc = Math.abs(other.x()) + Math.abs(other.yaw()) + Math.abs(other.roll());
            assertTrue(arc >= mineArc - 1.0E-6,
                    profile + " sweeps " + arc + " and the ropera " + mineArc
                            + ". The ropera is the precision weapon; if something else is straighter "
                            + "then the ropera's identity is being taken by another item");
        }
    }

    @Test
    @DisplayName("the lunge goes FURTHER than the thrust, and it goes UP")
    void theLungeIsDeeperAndRises() {
        Offset thrust = strike(WeaponProfile.ROPERA);
        Offset lunge = ViewModelDefaults.of(WeaponProfile.ROPERA)
                .evaluate(ViewModelInput.lunging(1.0F));

        assertTrue(Math.abs(lunge.z()) > Math.abs(thrust.z()),
                "the step already committed the player, so the blade has to go further: lunge "
                        + lunge.z() + " against thrust " + thrust.z());
        assertTrue(lunge.pitch() > thrust.pitch(),
                "and it stops short of level, so the point finishes ABOVE where a plain thrust "
                        + "finishes: " + lunge.pitch() + " against " + thrust.pitch()
                        + ". That is what a lunge is -- the body goes forward and up at once");
        assertTrue(lunge.pitch() < 0.0,
                "but it is still a forward thrust, not a swing upward: " + lunge.pitch());
    }

    @Test
    @DisplayName("the lunge is the only thing in the game that reads its channel")
    void onlyTheRoperaLunges() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            assertEquals(profile == WeaponProfile.ROPERA,
                    ViewModelDefaults.of(profile).reads(Channel.LUNGE),
                    profile + " reads the lunge channel. It is the gate that keeps a ropera's step "
                            + "from drawing on whatever comes next, and a second weapon reading it "
                            + "means the channel is no longer the ropera's identity");
        }
    }

    @Test
    @DisplayName("the wind-up cocks the wrist, off the line it already rests on")
    void theWindupIsAWCock() {
        ViewModelProfile ropera = ViewModelDefaults.of(WeaponProfile.ROPERA);
        Offset back = ropera.evaluate(
                new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true, 0.0F, 1));

        assertTrue(back.z() > 0.0,
                "positive z is toward the camera, so the pull is positive: " + back.z());
        // Measured AGAINST the resting pose, not against zero. The ropera is held level at rest,
        // so an absolute "the pitch is positive" is now false for a motion that is exactly right
        // -- and a test that has to be rewritten every time a weapon's grip changes is a test
        // about the number rather than about the motion.
        assertTrue(back.pitch() > ropera.rest().pitch(),
                "the cock lifts the point OFF the line it rests on: " + back.pitch()
                        + " against a rest of " + ropera.rest().pitch()
                        + ". Positive in absolute terms is not the question any more");
    }

    @Test
    @DisplayName("the follow-through comes back to the line, and overshoots it")
    void theRecoverBringsTheBladeHome() {
        ViewModelProfile ropera = ViewModelDefaults.of(WeaponProfile.ROPERA);
        Offset recover = ropera.evaluate(ViewModelInput.recovering(1.0F));

        assertTrue(recover.z() > 0.0, "backwards past where it started: " + recover.z());
        assertTrue(recover.pitch() > ropera.rest().pitch(),
                "and the blade tips back UP past the line it rests on, which is what a wrist does "
                        + "after a thrust: " + recover.pitch() + " against a rest of "
                        + ropera.rest().pitch()
                        + ". A motion that stops exactly where it started is a machine returning "
                        + "to a position");
    }

    /**
     * The strike's rotation, measured from the resting pose.
     *
     * <p><b>This asserts a range and not a number, on purpose, and it is worth saying why.</b> The
     * first version of the thrust pinned thirty degrees, and the playtest answered "more intensity,
     * more forward" -- so sixty. A test that pins the exact figure cannot survive the player asking
     * for a stronger swing, and the response to that request is not to delete the assertion: it is
     * to assert what the number is <em>for</em>.</p>
     *
     * <p>Two failures are being ruled out at once. Too little and a point that barely moves is a
     * stab with no follow-through, which is not a thrust. Too much and it is back to being the
     * ninety-degree dash the resting pose was introduced to kill -- so the ceiling is the number
     * that must not come back, and it is a ceiling rather than a target for a reason.</p>
     */
    @Test
    @DisplayName("the strike rotates hard, and never far enough to be the dash again")
    void theStrikeRotatesHardButNotLikeADash() {
        ViewModelProfile ropera = ViewModelDefaults.of(WeaponProfile.ROPERA);
        double travel = Math.abs(strike(WeaponProfile.ROPERA).pitch() - ropera.rest().pitch());

        assertTrue(travel > 40.0,
                "the strike rotates " + travel + " degrees from the rest. The playtest asked for "
                        + "more intensity than thirty, and this is the number that answers it. "
                        + "Below forty the blade creeps and a thrust reads as a poke");
        assertTrue(travel < 90.0,
                "and " + travel + " degrees is under the ninety that read as a dash. That figure "
                        + "was the bug: it came from the blade starting vertical, and the resting "
                        + "pose is what took it away. Coming back to it means the thrust has "
                        + "become an arm movement again");
    }

    /**
     * The thrust drives past level rather than stopping on it.
     *
     * <p>The resting pose is fifty-eight degrees up off horizontal, so a strike that lands exactly
     * on level would be arriving at the pose a guard is held in. Ending a few degrees <em>below</em>
     * it puts the point over the top of its arc and driving down into the target, which is where a
     * stab actually finishes -- and it is the difference between a motion that stops when it arrives
     * and one that goes through.</p>
     */
    @Test
    @DisplayName("the thrust drives its point below the line it started on")
    void theThrustGoesOverTheTop() {
        ViewModelProfile ropera = ViewModelDefaults.of(WeaponProfile.ROPERA);
        Offset thrust = strike(WeaponProfile.ROPERA);

        assertTrue(thrust.pitch() < -90.0,
                "the strike ends at " + thrust.pitch() + " against a rest of "
                        + ropera.rest().pitch() + ". Below minus ninety is below horizontal, which "
                        + "is a thrust that has gone over its arc and into the target rather than "
                        + "one that stopped when it arrived");
    }

    /**
     * Intensity is the curve, not only the distance.
     *
     * <p>The playtest said the thrust did not have enough intensity, and "add more travel" is one
     * of two ways to answer that. The other is <em>how fast it gets there</em>, and it is the half
     * that was missing: LINEAR reaches its furthest point gradually, which is the shape of a swing,
     * because a swing is a movement that takes its time. EXPO spends most of its range in the first
     * third, so the blade leaves.</p>
     *
     * <p>Asserted on the curve rather than on the sampled motion, because the property being asked
     * for is the shape and the shape is what the curve is. A test that sampled frame twenty of the
     * motion would pass for a LINEAR curve that merely travelled further.</p>
     */
    @Test
    @DisplayName("the thrust LEAVES, it does not ease out")
    void theThrustIsNotALinearSweep() {
        // STRIKE is a bump, so "a quarter of the way through" is a quarter of the way up the bump and
        // back down its far side. The bump peaks at 1.0 and this asks for the ramp at its steepest.
        Offset first = ViewModelDefaults.of(WeaponProfile.ROPERA)
                .evaluate(ViewModelInput.of(0.0F, 0.5F));
        Offset full = strike(WeaponProfile.ROPERA);

        // A quarter of the way through a four-tick ramp, an exponential curve has already spent
        // well over half its range. Linear would be at exactly a quarter, and the whole difference
        // between "leaves" and "swings" lives in this number.
        double doneEarly = Math.abs(first.z()) / Math.abs(full.z());
        assertTrue(doneEarly > 0.5,
                "at a quarter of the strike the blade has travelled " + Math.round(doneEarly * 100)
                        + "% of its distance. Under half means it is easing out, and an easing "
                        + "movement is a swing. The curve is EXPO for exactly this");
    }

    @Test
    @DisplayName("the resting pose is a field on the profile and not a channel")
    void theRestIsNotAChannel() {
        // The reason it cannot be a channel: every channel rests at zero, and one that rested at
        // one would need a special case in every track that reads it. This is a property of the
        // weapon, the same on every frame, read once in the sum.
        assertTrue(ViewModelDefaults.of(WeaponProfile.ROPERA).rest().pitch() < -30.0,
                "the ropera rests held near level, not at the vanilla vertical grip: "
                        + ViewModelDefaults.of(WeaponProfile.ROPERA).rest().pitch());
        for (Channel channel : Channel.values()) {
            assertEquals(0.0F, channel.read(ViewModelInput.RESTING), 1.0E-6,
                    channel + " must still rest at zero. The resting pose lives on the profile "
                            + "precisely so that this stays true for all of them at once");
        }
    }

    @Test
    @DisplayName("nothing moves at rest, which is the invariant the sum of tracks rests on")
    void everyChannelRestsAtZero() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 20; i++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
            ViewModelInput frame = motion.snapshot(1.0F, 0.0F);
            assertEquals(0.0F, frame.lunge(), 1.0E-6, "lunge rested at " + frame.lunge()
                    + " on tick " + i + " -- a ramp with an unconditional else computes 1 - 1/4 on "
                    + "its first tick and the ropera would start life a quarter lunged");
            assertEquals(0.0F, frame.parry(), 1.0E-6, "parry rested at " + frame.parry());
            assertEquals(0.0F, frame.guard(), 1.0E-6, "guard rested at " + frame.guard());
        }
    }
}
