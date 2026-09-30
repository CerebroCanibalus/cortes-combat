package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sword, swept a whole swing, and the property that catches both broken hand-offs.
 *
 * <p>The individual pieces are already covered. What is left is the thing you can only see by
 * playing the whole motion, and that is the per-frame continuity: a swing that reads as one
 * motion cannot teleport between two positions. That single assertion fails on the naive
 * "charge snaps to zero on release" implementation, which is the bug the design is most likely to
 * regress into, and it does so with a number rather than with a playtest.</p>
 */
class SwordViewModelTest {

    /**
     * The rate the motion is actually drawn at, which is the frame rate and not the tick rate.
     *
     * <p>Sampling per tick is a coarser question, and it would have passed even before the
     * viewmodel learned to interpolate, which is exactly the bug the hand-off test exists for.</p>
     */
    private static final float RENDER_STEP = 1.0F / 60.0F;

    private static final int CHARGE_TICKS = 25;

    private ViewModelProfile sword;
    private ViewModelMotion motion;

    @BeforeEach
    void setUp() {
        sword = ViewModelDefaults.of(WeaponProfile.SWORD);
        motion = new ViewModelMotion();
    }

    @Test
    @DisplayName("the sword has a profile, and it owns its strike")
    void swordHasAProfile() {
        assertFalse(sword.isEmpty(), "the sword is the weapon the framework was debugged on");
        assertTrue(sword.ownsSwing(),
                "a profile with a strike track must own it, or the vanilla swing doubles it");
    }

    @Test
    @DisplayName("the bare fist has a charge animation, and it is not the sword's")
    void theFistHasItsOwnAnimation() {
        ViewModelProfile fist = ViewModelDefaults.of(WeaponProfile.UNARMED);
        assertFalse(fist.isEmpty(), "the playtest asked for a charge animation on the fist");
        assertTrue(fist.ownsSwing(), "a charged fist owns its strike, like any charged weapon");

        Offset fistBack = fist.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, true, 0.0F, 1));
        Offset swordBack = sword.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, true, 0.0F, 1));
        assertTrue(fistBack.y() > 0.0, "the fist lifts too");
        assertTrue(fistBack.y() < swordBack.y(),
                "but less than a sword: it lifted to " + fistBack.y()
                        + " against the sword's " + swordBack.y());
    }

    @Test
    @DisplayName("every other weapon still gets the exact vanilla pose")
    void otherWeaponsAreUntouched() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            // The two with a profile so far. Everything else is deliberately absent, because a
            // viewmodel that is subtly wrong is worse than no viewmodel.
            if (profile == WeaponProfile.SWORD || profile == WeaponProfile.UNARMED) {
                continue;
            }
            ViewModelProfile viewModel = ViewModelDefaults.of(profile);
            assertTrue(viewModel.isEmpty(),
                    profile + " should have no viewmodel yet, a wrong one is worse than none");
            assertTrue(viewModel.evaluate(ViewModelInput.RESTING).isZero());
        }
    }

    @Test
    @DisplayName("a sword in a resting hand is exactly the vanilla pose")
    void restingSwordDoesNotMove() {
        assertTrue(sword.idleFor(ViewModelInput.RESTING));
        assertTrue(sword.evaluate(ViewModelInput.RESTING).isZero());
    }

    @Test
    @DisplayName("a full charge draws the item back and lifts it")
    void fullChargeDrawsBack() {
        ViewModelInput charged = new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, true, 0.0F, 1);
        Offset offset = sword.evaluate(charged);
        // The lift is carried by Y and the pull by Z, because those are the two axes whose sign
        // is not a guess: vanilla's own arm constants put the hand at ARM_POS_Y = -0.75, which
        // fixes +Y as up, and its bow pulls back along negative Z.
        assertTrue(offset.y() > 0.1,
                "a charged sword should be lifted, was y=" + offset.y());
        assertTrue(offset.z() < -0.05,
                "and drawn back, was z=" + offset.z());
    }

    @Test
    @DisplayName("the wind-up leans as well as lifts, but modestly")
    void fullChargeLeans() {
        Offset offset = sword.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, true, 0.0F, 1));
        // The pitch sign is the one number in this profile that has to be read off a screen
        // rather than off a constant, so the test pins its magnitude and leaves its direction
        // to the next playtest. What it does enforce is that the lean stays small enough that
        // being wrong about the sign cannot invert the read: a first attempt led with -35
        // degrees of it and the sword came out going down instead of up.
        assertTrue(Math.abs(offset.pitch()) > 1.0,
                "the wind-up should lean, was pitch=" + offset.pitch());
        assertTrue(Math.abs(offset.pitch()) < 30.0,
                "but modestly, so a wrong sign cannot flip the read, was pitch=" + offset.pitch());
    }

    @Test
    @DisplayName("the strike sends the item forward past the resting pose")
    void strikeGoesForward() {
        ViewModelInput striking = new ViewModelInput(0.0F, 1.0F, 0.0F, 1.0F, false, 0.0F, 1);
        Offset offset = sword.evaluate(striking);
        assertTrue(offset.z() > 0.1,
                "the strike should be well in front of the resting pose, was z=" + offset.z());
    }

    @Test
    @DisplayName("the wind-up never loses more than a tick's worth in one frame")
    void windUpUnwindsWithoutAJump() {
        // The whole hand-off, and the only test of it.
        //
        // A first attempt asserted continuity on the *composed* offset, with a bound in item
        // units. It was wrong twice over and both errors are worth keeping. Folding degrees into
        // item units needs an arbitrary equivalence, and the number that came out was tight enough
        // to reject the strike's own fast first frames. Worse, the bound had to be derived from the
        // sword's current numbers, and those numbers are written rather than played, so the test
        // would have failed on every retune. A test coupled to provisional data is worse than no
        // test: it trains you to change the test.
        //
        // The invariant belongs on the channel instead. "The wind-up does not lose more than a
        // tick's worth in a frame" is exact, independent of the profile, and the naive hand-off
        // violates it by dropping the whole wind-up at once.
        float worst = 0.0F;
        for (int tick = 0; tick <= CHARGE_TICKS; tick++) {
            motion.tick(true, Math.min(1.0F, (tick + 1) / (float) (CHARGE_TICKS + 1)));
        }
        motion.onStrike(1.0F);
        float previous = motion.windup();
        for (int tick = 0; tick < ViewModelMotion.STRIKE_TICKS + 1; tick++) {
            motion.tick(false, 0.0F);
            for (int sub = 0; sub < 4; sub++) {
                float value = motion.snapshot(sub / 4.0F, 0.0F, false).windup();
                worst = Math.max(worst, Math.abs(previous - value));
                previous = value;
            }
        }
        float perTick = 1.0F / ViewModelMotion.STRIKE_TICKS;
        assertTrue(worst <= perTick + 0.01F,
                "the wind-up dropped " + String.format("%.3f", worst)
                        + " in a single frame, which is the teleport at the release");
    }

    @Test
    @DisplayName("the swing ends back at the vanilla pose")
    void theSwingEndsAtRest() {
        for (int i = 0; i < CHARGE_TICKS + 1; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / (float) (CHARGE_TICKS + 1)));
        }
        motion.onStrike(1.0F);
        for (int i = 0; i < ViewModelMotion.STRIKE_TICKS + 2; i++) {
            motion.tick(false, 0.0F);
        }
        assertTrue(sword.evaluate(motion.snapshot(0.0F, 0.0F, false)).isZero(),
                "after the follow-through the item must be exactly where vanilla put it");
    }

    @Test
    @DisplayName("the strike is felt in its first frames, not ramped in")
    void theStrikeLandsEarly() {
        // The design ask was "it hits hard". A strike that eases in reads as a slow camera move,
        // so most of the travel has to happen in the first couple of frames of the release.
        for (int i = 0; i < CHARGE_TICKS + 1; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / (float) (CHARGE_TICKS + 1)));
        }
        motion.onStrike(1.0F);
        motion.tick(false, 0.0F);
        double atStart = depth(sword.evaluate(motion.snapshot(0.0F, 0.0F, false)));
        double afterTwo = depth(sword.evaluate(motion.snapshot(0.5F, 0.0F, false)));
        assertTrue(afterTwo > atStart * 2.0,
                "half a frame into the release the item should have moved most of the way, went "
                        + String.format("%.3f", atStart) + " to " + String.format("%.3f", afterTwo));
    }

    @Test
    @DisplayName("the tremble is not there at rest and is unmistakable at full charge")
    void theTrembleIsAChargeTremble() {
        ViewModelInput atRest = new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);
        assertTrue(sword.evaluate(atRest).isZero(), "a sheathed sword does not shake");

        // Sweep a second of shaking at render rate and measure how far the pitch wanders. The
        // range, not the magnitude: the wind-up parks the sword at minus thirty five degrees and
        // the tremble rides on top of that, so an absolute reading would be measuring the wind-up.
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int frame = 0; frame < 60; frame++) {
            Offset offset = sword.evaluate(
                    new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, true, frame * RENDER_STEP, 3));
            min = Math.min(min, (float) offset.pitch());
            max = Math.max(max, (float) offset.pitch());
        }
        float spread = max - min;
        assertTrue(spread > 1.0F,
                "a fully charged sword should be visibly trembling, pitch spanned "
                        + String.format("%.2f", spread) + " degrees");
        assertTrue(spread < 12.0F,
                "and not vibrating itself out of the player's hands, pitch spanned "
                        + String.format("%.2f", spread) + " degrees");
    }

    /** How far forward of the resting pose the item is. Negative means drawn back. */
    private static double depth(Offset offset) {
        return offset.z();
    }

    private static double distance(Offset a, Offset b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double dz = b.z() - a.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
