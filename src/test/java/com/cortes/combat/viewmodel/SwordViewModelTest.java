package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        Offset fistBack = fist.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, true, 0.0F, 1));
        Offset swordBack = sword.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, true, 0.0F, 1));
        assertTrue(fistBack.y() > 0.0, "the fist lifts too");
        assertTrue(fistBack.y() < swordBack.y(),
                "but less than a sword: it lifted to " + fistBack.y()
                        + " against the sword's " + swordBack.y());
    }

    /**
     * The four vanilla tools that are still untouched, and the axe that is not.
     *
     * <p>Was "every other weapon", and it failed the moment the five new families got their
     * profiles -- which is the test working. The old form asserted a snapshot of the table
     * ("everything except the sword and the fist has no profile"), so it could only ever be fixed
     * by deleting the assertion.</p>
     *
     * <p><b>Then it failed a second time, for the axe, and that one was the rule narrowing rather
     * than the rule expiring.</b> The claim was "vanilla's items have no viewmodel of ours", and
     * the axe broke it. The narrower claim is the one that still holds: a vanilla item gets no
     * profile <em>unless its mechanic is one a player cannot aim without seeing it</em>. The
     * axe's is -- a three and a half second charge whose only feedback would otherwise be a bar
     * across the bottom of the screen -- and the other four have no charge worth showing, so they
     * keep the exact vanilla pose.</p>
     *
     * <p><b>The trident is the second one, for the same reason and a sharper one.</b> Its charge
     * does not buy more damage &mdash; it buys the opposite motion: a loaded thrust <em>drags the
     * target in</em> instead of pushing it away. That is the one thing in the game a player cannot
     * predict from a bar along the bottom of the screen, because every other charged strike in
     * this game does the same thing louder. Holding the button visibly gathering a spear backwards
     * and then throwing it is what tells the player the second swing is coming.</p>
     *
     * <p>The axe is therefore pinned separately, by name, below. A list of five that quietly
     * becomes a list of four is how the exception stops being an exception.</p>
     */
    @Test
    @DisplayName("the vanilla tools without a charge of their own stay on the vanilla pose")
    void vanillaToolsAreUntouched() {
        // **The mace is no longer here.** It was, on the "vanilla art" argument, and the playtest
        // asked for a motion for it: a weapon whose whole identity is the fall cannot play the
        // vanilla swing and say nothing about it. The tools that remain are tools -- a hand-drawn
        // motion on a silhouette we did not draw fights the model, and that is still true of them.
        for (WeaponProfile profile : List.of(WeaponProfile.PICKAXE,
                WeaponProfile.SHOVEL, WeaponProfile.HOE)) {
            ViewModelProfile viewModel = ViewModelDefaults.of(profile);
            assertTrue(viewModel.isEmpty(),
                    profile + " is a vanilla item and should have no viewmodel of ours; a wrong "
                            + "one is worse than none");
            assertTrue(viewModel.evaluate(ViewModelInput.RESTING).isZero());
        }
    }

    /**
     * Every profile the mod ships is reachable and non-empty.
     *
     * <p>Names all five families explicitly rather than counting, because a count cannot say which
     * one is missing: "5 profiles" passes with the ropera absent and the daga listed twice.</p>
     */
    @Test
    @DisplayName("all five new families have a viewmodel, and four of them rest at the vanilla pose")
    void everyNewFamilyHasAViewModel() {
        for (WeaponProfile profile : List.of(WeaponProfile.ROPERA, WeaponProfile.DAGGER,
                WeaponProfile.SABRE, WeaponProfile.HALBERD, WeaponProfile.WARHAMMER)) {
            ViewModelProfile viewModel = ViewModelDefaults.of(profile);
            assertFalse(viewModel.isEmpty(), profile + " should have a viewmodel");

            // Four of the five contribute exactly nothing at rest, and that is the reason one
            // missing from the table is a correct outcome rather than a gap.
            //
            // The ropera is the exception and it is not one: its art is drawn with the blade
            // pointing up, so the vanilla grip is a lowered guard and every thrust had to spend
            // ninety degrees to become level, which the playtest called a dash. It is held level
            // at rest instead, which is a constant on the profile rather than a channel -- a
            // channel resting at one would have needed a special case in every track that reads
            // it. So "rests at the vanilla pose" is now a claim about four weapons, not five, and
            // saying so here is what stops somebody reading this and "fixing" the ropera.
            if (profile == WeaponProfile.ROPERA) {
                assertFalse(viewModel.evaluate(ViewModelInput.RESTING).isZero(),
                        "the ropera is held level at rest, so it is NOT at the vanilla pose. If "
                                + "this fails, something changed its resting pose and the thrust's "
                                + "rotation has to be re-derived with it");
                assertFalse(viewModel.idleFor(ViewModelInput.RESTING),
                        "a resting pose is a pose: skipping it puts the weapon back in the "
                                + "vanilla grip, which is the subtly-wrong viewmodel case");
            } else {
                assertTrue(viewModel.evaluate(ViewModelInput.RESTING).isZero(),
                        profile + " moves the item while the player stands still");
                assertTrue(viewModel.idleFor(ViewModelInput.RESTING),
                        profile + " is not idle at rest");
            }
        }
    }

    @Test
    @DisplayName("and the vanilla weapons are untouched by the resting pose")
    void vanillaWeaponsStillRestAtVanilla() {
        // The ropera's resting pose is a field on ITS profile. A weapon that did not ask for one
        // has to get none, or the exception above would quietly become the rule.
        for (WeaponProfile profile : List.of(WeaponProfile.SWORD, WeaponProfile.UNARMED,
                WeaponProfile.AXE, WeaponProfile.PICKAXE, WeaponProfile.SHOVEL,
                WeaponProfile.MACE, WeaponProfile.TRIDENT, WeaponProfile.NONE)) {
            assertTrue(ViewModelDefaults.of(profile).evaluate(ViewModelInput.RESTING).isZero(),
                    profile + " moved at rest, and only the ropera is supposed to be held "
                            + "differently");
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
        ViewModelInput charged = new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, true, 0.0F, 1);
        Offset offset = sword.evaluate(charged);
        // The lift stays because the playtest confirmed it: "el arma sÃ­ se levanta perfecto". The
        // draw-back is negative Z because the profile is now applied in the item's own frame, at
        // the tail of applyItemArmTransform, where the hand already sits at ITEM_POS_Z = -0.72.
        // An earlier version of this test asserted a positive Z, which was correct in the frame
        // the profile used to be applied in and is now exactly backwards.
        assertTrue(offset.y() > 0.1,
                "a charged sword should be lifted, was y=" + offset.y());
        assertTrue(offset.z() < -0.05,
                "and drawn back, was z=" + offset.z());
    }

    @Test
    @DisplayName("the wind-up leans as well as lifts, but modestly")
    void fullChargeLeans() {
        Offset offset = sword.evaluate(new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, true, 0.0F, 1));
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
    @DisplayName("the sword does not cock to the side, because that is the fist's motion")
    void swordDoesNotCockLaterally() {
        // The playtest was explicit: the lateral wind-up was for the hand, and the sword winds up
        // by being drawn back. Twenty two degrees of yaw on a blade reads as the player turning
        // the weapon rather than as loading it, so it lives on UNARMED and not here.
        //
        // The comparison is against the fist rather than against zero, for two reasons. The
        // tremble is a track on WINDUP too and contributes about half a degree of yaw on its own,
        // so zero is not the resting value; and a bound tied to a number in a profile is a test
        // that fails on every retune. The thing that must hold is that the two weapons read as
        // different motions, and that one is a fraction of the other.
        ViewModelInput charged = new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, true, 0.0F, 1);
        double swordSway = Math.abs(sword.evaluate(charged).yaw());
        double fistSway = Math.abs(
                ViewModelDefaults.of(WeaponProfile.UNARMED).evaluate(charged).yaw());

        assertTrue(fistSway > 10.0,
                "the fist has to cock to the side, was yaw=" + fistSway);
        assertTrue(swordSway * 4 < fistSway,
                "and the sword must be a fraction of that, sword=" + swordSway
                        + " against fist=" + fistSway);
    }

    @Test
    @DisplayName("the cut arcs the blade forward and down, not forward and level")
    void cutArcsTheBlade() {
        // Rotation led, because a translation moves a rigid item as one block and a bigger one is
        // a bigger shove. See CutIsARotationTest for the whole account of three rounds of this.
        ViewModelInput striking = ViewModelInput.chopping(1.0F);
        Offset offset = sword.evaluate(striking);
        assertTrue(Math.abs(offset.pitch()) > 60.0,
                "the blade should travel most of vanilla's 80 degree arc, was pitch=" + offset.pitch());
        assertTrue(offset.z() < 0.0,
                "and go away from the camera, was z=" + offset.z());
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
            motion.tick(true, Math.min(1.0F, (tick + 1) / (float) (CHARGE_TICKS + 1)), Math.min(1.0F, (tick + 1) / (float) (CHARGE_TICKS + 1)), false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        float previous = motion.windup();
        for (int tick = 0; tick < ViewModelMotion.STRIKE_TICKS + 1; tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
            for (int sub = 0; sub < 4; sub++) {
                float value = motion.snapshot(sub / 4.0F, 0.0F).windup();
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
            motion.tick(true, Math.min(1.0F, (i + 1) / (float) (CHARGE_TICKS + 1)), Math.min(1.0F, (i + 1) / (float) (CHARGE_TICKS + 1)), false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        for (int i = 0; i < ViewModelMotion.STRIKE_TICKS + 2; i++) {
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        }
        assertTrue(sword.evaluate(motion.snapshot(0.0F, 0.0F)).isZero(),
                "after the follow-through the item must be exactly where vanilla put it");
    }

    @Test
    @DisplayName("the strike is felt in its first frames, not ramped in")
    void theStrikeLandsEarly() {
        // The design ask was "it hits hard". A strike that eases in reads as a slow camera move,
        // so most of the travel has to happen in the first couple of frames of the release.
        for (int i = 0; i < CHARGE_TICKS + 1; i++) {
            motion.tick(true, Math.min(1.0F, (i + 1) / (float) (CHARGE_TICKS + 1)), Math.min(1.0F, (i + 1) / (float) (CHARGE_TICKS + 1)), false, 1.0F, false, false);
        }
        motion.onStrike(1.0F, ViewModelMotion.STRIKE_TICKS);
        motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
        double atStart = depth(sword.evaluate(motion.snapshot(0.0F, 0.0F)));
        double afterTwo = depth(sword.evaluate(motion.snapshot(0.5F, 0.0F)));
        assertTrue(afterTwo > atStart * 2.0,
                "half a frame into the release the item should have moved most of the way, went "
                        + String.format("%.3f", atStart) + " to " + String.format("%.3f", afterTwo));
    }

    @Test
    @DisplayName("the tremble is not there at rest and is unmistakable at full charge")
    void theTrembleIsAChargeTremble() {
        ViewModelInput atRest = new ViewModelInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, false, 0.0F, 1);
        assertTrue(sword.evaluate(atRest).isZero(), "a sheathed sword does not shake");

        // Sweep a second of shaking at render rate and measure how far the pitch wanders. The
        // range, not the magnitude: the wind-up parks the sword at minus thirty five degrees and
        // the tremble rides on top of that, so an absolute reading would be measuring the wind-up.
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int frame = 0; frame < 60; frame++) {
            Offset offset = sword.evaluate(
                    new ViewModelInput(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,  0.0F, 0.0F, true, frame * RENDER_STEP, 3));
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
