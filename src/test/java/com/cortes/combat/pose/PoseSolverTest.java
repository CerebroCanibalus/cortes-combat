package com.cortes.combat.pose;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.thirdperson.BodyPose;
import com.cortes.combat.viewmodel.Offset;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelInput;
import com.cortes.combat.viewmodel.ViewModelProfile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That {@link PoseSolver} is a pure function of its state, and that each of its four gates is a
 * question about the profile rather than a name.
 *
 * <h2>Why this can run with nothing else loaded</h2>
 *
 * <p>Everything {@link PoseSolver} touches is a project record -- {@link PoseState},
 * {@link ViewModelInput}, {@link BodyPose}, {@link WeaponProfile}, {@link GuardProfile} -- and
 * {@code ChargeProfile} only reaches for {@code Mth} inside {@code multiplierFor}, which this never
 * calls. The old version read an {@code AbstractClientPlayer} and a static viewmodel singleton, so
 * <b>none of the logic below had a test at all</b>: it was a hundred lines of gating decided by
 * playtests, and the one time a gate was wrong -- the sabre's parry, present in the viewmodel and
 * absent from the body -- it was a playtest that reported it, which is the slowest possible
 * verification loop.</p>
 */
class PoseSolverTest {

    /** A charge profile that can charge, so the charging gate is open. */
    private static final ChargeProfile CHARGES = new ChargeProfile(true, 1.0F, 40);

    private static PoseState holding(WeaponProfile weapon, ViewModelInput frame) {
        ViewModelProfile profile = ViewModelDefaults.of(weapon);
        return new PoseState(weapon, CHARGES, GuardProfile.NONE, false, frame, profile);
    }

    // ---------------------------------------------------------------- the four gates

    /**
     * The chop has to take the arm <em>away</em> from the wind-up, not sit on top of it.
     *
     * <p><b>The guard has to bite, and this is how it bites.</b> A wind-up at -104 under a chop at
     * -85 is -189, which is past vertical: the somersault this project already shipped once, in the
     * arm rather than in the item. If someone changes the chop from a replacement to an addition,
     * this goes red.</p>
     */
    @Test
    @DisplayName("the chop replaces the wind-up instead of adding to it")
    void theChopReplacesTheWindup() {
        WeaponProfile axe = WeaponProfile.AXE;

        // **Both channels open at once, and that is the whole reason this frame exists.**
        //
        // The first version of this test asked for `chopping(1.0F)`, which opens CHOP and leaves
        // WINDUP at zero, and then I sabotaged the solver into always applying the wind-up and
        // *the build stayed green*. Not because the sabotage was invisible: because the frame never
        // had a wind-up to see. `charging` returns NONE below the first step, so both branches of
        // the ternary answered the same thing.
        //
        // The state this is copied from is the one the game is actually in: the player is holding
        // the attack button *and* the chop has come down. Both are open, the arm writes its pitch
        // twice, and that is the only frame where replacing and adding differ.
        ViewModelInput bothAtOnce = new ViewModelInput(
                1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, false, 0.0F, 1);

        float alone = Math.abs(PoseSolver.body(holding(axe, ViewModelInput.of(1.0F, 0.0F)))
                .rightArmPitch());
        float underChop = Math.abs(PoseSolver.body(holding(axe, bothAtOnce)).rightArmPitch());

        // **The sum, stated as a sum, because that is what would break.**
        //
        // CHOP_ARM_PITCH is -140 and CHARGE_ARM_PITCH is -104, both read out of BodyPoses. Added,
        // that is -244: past vertical, over the player's own head. So the assertion is not "the
        // chop is under some ceiling" -- a ceiling of 120 is a number I made up and the chop is
        // legitimately at 140 -- it is that the chop's pitch IS the chop, with the wind-up's 104
        // nowhere in it.
        assertEquals(140.0F, underChop, 0.0F,
                "mid-chop the arm is at exactly CHOP_ARM_PITCH. This is not a ceiling check: the "
                        + "chop is legitimately at 140, and what is illegitimate is 140 plus the "
                        + "104 of CHARGE_ARM_PITCH = 244, which is past vertical and is the "
                        + "somersault this project shipped once. If this number is " + (alone + 140)
                        + " the replacement became an addition");

        // And the direct statement: with the chop running there is no charge arm at all.
        assertEquals(0.0F, PoseSolver.body(holding(axe, bothAtOnce)).leftArmPitch(),
                "the off arm must be untouched by a chop. A chop is the weapon arm's fall, and the "
                        + "off arm coming up with it is the 'converged on the chest' shape the "
                        + "guard pose had once");
    }

    /**
     * A weapon with no CHOP track swings as a thrust; one with a CHOP track falls.
     *
     * <p><b>Two profiles with the same strike input and different arms.</b> The dagger has no CHOP
     * and the warhammer has one, and the playtest called the dagger's bump excellent and reported
     * the axe "starting from below and going up" twice -- once for the normal hit and once for the
     * release. That difference is this gate.</p>
     */
    @Test
    @DisplayName("the chop track decides whether the swing falls or thrusts")
    void theChopTrackDecidesTheSwingShape() {
        ViewModelInput strike = ViewModelInput.of(0.0F, 1.0F);

        BodyPose dagger = PoseSolver.body(holding(WeaponProfile.DAGGER, strike));
        BodyPose hammer = PoseSolver.body(holding(WeaponProfile.WARHAMMER, strike));

        assertNotEquals(dagger.rightArmPitch(), hammer.rightArmPitch(),
                "the dagger has no CHOP track and the warhammer has one, so with the same strike "
                        + "input the two have to hold the arm at different heights. Equal pitches "
                        + "means the gate is dead and every weapon is doing the same swing, which "
                        + "is the bug the channel was written for");
    }

    /**
     * The shoulder, and the arithmetic that comes with it.
     *
     * <p><b>The arcabuz is the only profile with a HELD track.</b> Left false and the gun is
     * carried low and brought up when it is loaded, which is a different weapon; left true and the
     * strike at -104 sums with the aim at -66 into -188, which is the arm over the player's own
     * head. Both halves of that are asserted here, because the sum is the thing that ships.</p>
     */
    @Test
    @DisplayName("the arcabuz aims in both arms, and its strike does not put the arm over the head")
    void theShoulderedWeaponAimsAndDoesNotSomersault() {
        ViewModelProfile arcabuz = ViewModelDefaults.of(WeaponProfile.ARCABUZ);
        assertTrue(arcabuz.reads(com.cortes.combat.viewmodel.Channel.HELD),
                "the arcabuz has a HELD track, so the shoulder gate must open for it");

        // Aiming is a posture: on at one, with nothing open.
        BodyPose aimed = PoseSolver.body(holding(WeaponProfile.ARCABUZ, ViewModelInput.RESTING));
        assertTrue(aimed.rightArmPitch() < -30.0F,
                "aiming puts the weapon arm forward, which is a negative pitch in this frame. "
                        + "Read " + aimed.rightArmPitch() + ", which is not forward");

        // **And the one that shipped as a bug: aim plus strike.**
        // **The bayonet, not the sword's strike.** The arcabuz is shouldered, so a left click is a poke
// and not the -104 bump: aim (-66) plus a -104 strike is -170, past vertical. So the number to
// check is that a full strike on a shouldered weapon lands on AIMING_ARM_PITCH + BAYONET's, and
// that the arm is still forward rather than overhead.
float struck = PoseSolver.body(holding(WeaponProfile.ARCABUZ, ViewModelInput.of(0.0F, 1.0F)))
                .rightArmPitch();
float swordStrike = Math.abs(PoseSolver.body(holding(WeaponProfile.SWORD, ViewModelInput.of(0.0F, 1.0F)))
        .rightArmPitch());

assertNotEquals(-104.0F - 66.0F, struck, 0.0F,
        "aiming (-66) plus the sword's strike (-104) is -170, which is past vertical and is the "
                + "somersault PlayerModelMixin's javadoc documents as already having happened once. "
                + "The arcabuz has to take the bayonet instead, and it reads " + struck);
assertTrue(struck < 0.0F,
        "and the arm still has to end up forward. Read " + struck + ", which is not a strike");
assertTrue(swordStrike > struck,
        "for comparison the sword's own strike is at " + swordStrike
                + ", deeper than the arcabuz's poke. That is correct -- the arcabuz is aimed and "
                + "does not have to swing as far -- and it is the reason the gate exists");
    }

    /**
     * The shield is vanilla's and this does not touch it.
     *
     * <p><b>Same state, one flag.</b> A raised shield already has an arm and an animation, so a
     * pose on top moves the arm the shield is held in -- which is the same mistake as moving the
     * shield during a sword charge.</p>
     */
    @Test
    @DisplayName("a vanilla shield in the main hand gets no guard pose of ours")
    void theShieldIsVanillas() {
        GuardProfile shield = new GuardProfile(3, 90.0F, 40, 20);
        ViewModelInput guarding = ViewModelInput.guarding(1.0F);

        ViewModelProfile sword = ViewModelDefaults.of(WeaponProfile.SWORD);
        BodyPose withSword = PoseSolver.body(
                new PoseState(WeaponProfile.SWORD, CHARGES, shield, false, guarding, sword));
        BodyPose withShield = PoseSolver.body(
                new PoseState(WeaponProfile.SWORD, CHARGES, shield, true, guarding, sword));

        assertNotEquals(BodyPose.NONE, withSword,
                "a sword with a guard profile and the guard channel open has to raise the arm");
        assertEquals(BodyPose.NONE, withShield,
                "and a vanilla shield in the same state has to be left entirely to vanilla. This "
                        + "is the flag's whole job: a raised shield has an arm already and ours "
                        + "would move the arm it is held in");
    }

    /**
     * The sabre's parry reaches the body, which is the bug this project already paid for once.
     *
     * <p><b>Written because its absence is invisible.</b> The playtest said "the shotel has no
     * animation for when it parries", and it was right <em>from behind</em>: the viewmodel had a
     * PARRY track the whole time, so in first person the blade snapped across and in third person
     * the arm stood still while the weapon in its hand did something. A component in its place and
     * nothing drawing it -- the same shape as the missing {@code @SubscribeEvent} that cost the
     * arcabuz its fuse frames.</p>
     *
     * <p>And it is the failure mode a rewrite invites: I replaced a hundred lines of gating with a
     * pure function, and a gate left out of a rewrite does not fail anything. It renders a weapon
     * whose arm does not move. Sabotaging this solver's PARRY gate to {@code false} produced zero
     * failures across the whole suite, which is how this test came to exist.</p>
     *
     * <p>The roll matters as much as the pitch. A parry that is not a guard pose <em>looks</em>
     * like a guard pose if it only raises the arm; the big yaw and the blade turned across is the
     * read, and this is the one assertion that says a parry is not a guard with the arm in a
     * different place.</p>
     */
    @Test
    @DisplayName("the sabre's parry moves the body, and not as a guard would")
    void theParryReachesTheBody() {
        PoseState parrying = holding(WeaponProfile.SABRE, ViewModelInput.parrying(1.0F));
        PoseState guarding = holding(WeaponProfile.SABRE, ViewModelInput.guarding(1.0F));

        assertTrue(parrying.reads(com.cortes.combat.viewmodel.Channel.PARRY),
                "the sabre's profile has a PARRY track, so the gate opens for it");

        BodyPose parry = PoseSolver.body(parrying);
        BodyPose guard = PoseSolver.body(guarding);

        assertNotEquals(BodyPose.NONE, parry,
                "**This is the assertion that was missing and it cost a playtest.** The parry is in "
                        + "the viewmodel, so the blade snaps across in first person, and if the body "
                        + "has no PARRY gate the arm stands still in third and nobody notices until "
                        + "they watch from behind. A gate left out of a rewrite does not fail "
                        + "anything: it renders a weapon whose arm does not move");

        assertNotEquals(guard.rightArmPitch(), parry.rightArmPitch(),
                "and the parry has to be a different arm position from the guard's. Both raise the "
                        + "arm, so a parry that reuses the guard's pose is indistinguishable from "
                        + "standing covered -- and the sabre cannot block, which is the whole reason "
                        + "the blade comes across instead of up");
        assertNotEquals(BodyPose.NONE.rightArmRoll(), parry.rightArmRoll(),
                "the roll is where the read lives: blade turned across the body, edge out. A parry "
                        + "with no roll is an arm raised straight up and it looks like surrender");
    }

    // ---------------------------------------------------------------- purity

    /**
     * The same state twice gives the same pose.
     *
     * <p><b>The property the multiplayer work rests on.</b> If this fails, a second client
     * computing its own pose cannot agree with the first, and "the server sends state and the
     * client calculates" stops being viable -- which is the entire design of that step. It is one
     * line and it is the difference between a function and a sampling of shared mutable
     * state.</p>
     */
    @Test
    @DisplayName("the same state gives the same pose, twice, with nothing between them")
    void theSameStateGivesTheSamePose() {
        PoseState state = holding(WeaponProfile.HALBERD, ViewModelInput.sweeping(0.6F));

        PoseSolver.Pose first = PoseSolver.solve(state);
        PoseSolver.Pose second = PoseSolver.solve(state);

        assertEquals(first.hand(), second.hand(),
                "the weapon in the hand has to be a function of the state. A difference here means "
                        + "something in the chain reads a clock or a counter, and two clients would "
                        + "then draw the same swing differently");
        assertEquals(first.body(), second.body(),
                "and the body has to be a function of the state for the same reason");
    }

    /**
     * Solving does not change the state it was handed.
     *
     * <p><b>Records are immutable, so this is nearly free -- and nearly free is why it is
     * asserted.</b> A solver that normalised its input in place would not show up here on the
     * first run, it would show up two players later as one of them holding a different weapon.</p>
     */
    @Test
    @DisplayName("solving leaves the state exactly as it was handed in")
    void solvingDoesNotMutateTheState() {
        ViewModelInput frame = ViewModelInput.of(0.4F, 0.6F);
        PoseState state = holding(WeaponProfile.ROPERA, frame);

        PoseSolver.solve(state);

        assertEquals(frame, state.channels(),
                "the record the caller passed in is still the caller's. If a solver can change "
                        + "what it was given, the caller's own frame -- which the viewmodel renderer "
                        + "also reads -- would move underneath it");
        assertEquals(CHARGES, state.charge(),
                "and the charge profile is untouched, which matters because the same record is the "
                        + "one the attack resolver reads");
    }

    /**
     * Two different weapons in the same state are not the same weapon.
     *
     * <p><b>Written to fail if the weapon field is ever ignored.</b> Every gate in the solver except
     * the HELD and LUNGE ones comes from the profile, so a version that dropped the {@code weapon}
     * field entirely would still pass all the tests above -- they each vary the profile
     * too.</p>
     */
    @Test
    @DisplayName("the weapon decides the pose, and two weapons never share one")
    void theWeaponDecidesThePose() {
        ViewModelInput frame = ViewModelInput.of(0.0F, 1.0F);

        PoseSolver.Pose ropera = PoseSolver.solve(holding(WeaponProfile.ROPERA, frame));
        PoseSolver.Pose dagger = PoseSolver.solve(holding(WeaponProfile.DAGGER, frame));

        assertNotEquals(ropera.hand(), dagger.hand(),
                "the ropera and the dagger have different profiles in first person. Equal offsets "
                        + "means the viewmodel being resolved is not the weapon's, which would make "
                        + "every weapon in the game animate as one");

        // **The body's difference is the off arm, and only because the ropera reads LUNGE.**
        //
        // The first version of this test asserted the two bodies differed on a strike frame and
        // they did not, correctly: a strike is STRIKE for both, so both are at STRIKE_ARM_PITCH
        // and both are the same. The ropera's extra body motion is its lunge, which rides LUNGE
        // and is at zero on that frame. So the frame has to open the channel that makes them
        // different -- which is also a useful thing to assert, because it says the difference is
        // earned by a channel rather than baked into a list of weapon names.
        PoseState roperaLunge = holding(WeaponProfile.ROPERA, ViewModelInput.lunging(0.6F));
        PoseState daggerLunge = holding(WeaponProfile.DAGGER, ViewModelInput.lunging(0.6F));

        assertTrue(roperaLunge.reads(com.cortes.combat.viewmodel.Channel.LUNGE),
                "the ropera's profile has a LUNGE track, so the gate opens for it. This is the "
                        + "capability the body pose is decided by, not the name ROPERA");
        assertFalse(daggerLunge.reads(com.cortes.combat.viewmodel.Channel.LUNGE),
                "and the dagger has none, so the same channel value does nothing for it. That is "
                        + "the whole point of the gate: a lunge channel that reached every weapon "
                        + "would make every weapon step");

        assertNotEquals(PoseSolver.solve(roperaLunge).body(), PoseSolver.solve(daggerLunge).body(),
                "so the same lunge value has to reach the ropera's body and not the dagger's. Equal "
                        + "bodies means the gate is not the profile's own question -- which would be "
                        + "the second copy of a fact, the trap the parry already fell into once");
    }

    // ---------------------------------------------------------------- the record's own contract

    /**
     * A state with nothing in it is still a state, not a crash.
     *
     * <p><b>{@code STILL} exists so the solver never has to null-check five fields.</b> A player who
     * disconnects mid-tick has no weapon, no charge, no guard and no channels, and that has to be
     * an answer rather than a {@code NullPointerException} on the render thread.</p>
     */
    @Test
    @DisplayName("an empty state answers rather than throwing")
    void anEmptyStateAnswers() {
        PoseSolver.Pose pose = PoseSolver.solve(PoseState.STILL);

        assertTrue(pose.isStill(),
                "nothing happening has to pose as nothing. A pose that moved here would be a "
                        + "disconnected player whose arms are still swinging, which the multiplayer "
                        + "step has to survive without a special case");
    }

    /**
     * {@code isStill} is a real question and not {@code false} written as a method.
     *
     * <p><b>It has to open as well as close.</b> A culling path asks it every frame for every
     * player, so a version that returned {@code true} for everything would be invisible: no pose
     * would ever draw.</p>
     */
    @Test
    @DisplayName("isStill opens on any movement and closes only on real stillness")
    void isStillOpensOnMovement() {
        assertTrue(PoseSolver.solve(PoseState.STILL).isStill(),
                "nothing is still");

        assertFalse(PoseSolver.solve(holding(WeaponProfile.SABRE, ViewModelInput.of(0.5F, 0.0F)))
                        .isStill(),
                "half a wind-up is not still, and that is the frame a culling path would drop if "
                        + "this returned true");

        // **The body alone is enough**, and the case is a weapon with no profile of its own.
        //
        // Two wrong answers came before this one and both are worth keeping, because they are the
        // same mistake: I picked the most obvious example and assumed its numbers.
        //
        // The first was the ropera's lunge, asserting the hand offset is zero. It is not: the
        // ropera reads LUNGE, so the weapon in the hand tilts with the step. The second was the
        // sword's guard, asserting the sword has no GUARD track. It does -- `chargedSword` reads
        // WINDUP, CHOP, RECOVER, GUARD and BREAK, which I established by reading the method rather
        // than by scanning a fixed window, after a scan of ninety lines had reported three
        // channels and stopped short of the method's real end.
        //
        // **The real case is `ViewModelProfile.NONE`.** That is not a contrivance: it is what a
        // weapon from another mod resolves to, and a foreign weapon that can guard is exactly the
        // state a multiplayer client has to get right. `guarding` is the one body pose with no
        // profile gate -- it asks "can this thing guard" rather than "does this profile have a
        // GUARD track" -- so with a guard profile and the channel open, the body moves and there is
        // no viewmodel track that could answer.
        PoseState midGuard = new PoseState(WeaponProfile.SWORD, CHARGES,
                new GuardProfile(3, 90.0F, 40, 20), false,
                ViewModelInput.guarding(0.5F), ViewModelProfile.NONE);
        PoseSolver.Pose pose = PoseSolver.solve(midGuard);

        assertFalse(midGuard.reads(com.cortes.combat.viewmodel.Channel.GUARD),
                "no profile means no GUARD track, which is the precondition");
        assertTrue(pose.hand().isZero(),
                "and therefore the hand offset really is zero with the guard up, which is the part "
                        + "that had to be verified rather than assumed");
        assertFalse(pose.isStill(),
                "so the body has to be what keeps the player on screen. A culling path that asked "
                        + "only about the hand would drop a player standing in a guard with a "
                        + "foreign weapon, which is the shape a compat game spends real time in");
    }

    /**
     * The rest pose is a pose, so a still weapon is not one to be skipped.
     *
     * <p><b>Read off {@code ViewModelProfile#idleFor}'s own rule, not invented.</b> That method's
     * javadoc says a resting pose counts as doing something, because skipping it puts the weapon
     * back into the vanilla grip -- the "a viewmodel subtly wrong is worse than none" case this
     * project paid for once with two profiles that were written and never rendered.</p>
     */
    @Test
    @DisplayName("a weapon held differently at rest is never still")
    void aRestingPoseIsStillAPose() {
        ViewModelProfile held = ViewModelProfile.heldAs(
                new Offset(0.1D, -0.2D, 0.0D, -15.0D, 30.0D, 0.0D), false);
        PoseState state = new PoseState(WeaponProfile.ROPERA, CHARGES, GuardProfile.NONE,
                false, ViewModelInput.RESTING, held);

        assertFalse(PoseSolver.solve(state).isStill(),
                "a weapon whose resting pose is not vanilla's is doing something on every single "
                        + "frame, including the frames where nothing happens. Treating it as idle "
                        + "is what puts the weapon back into the vanilla grip");
    }

    /**
     * The state carries the profile, not a name to look one up with.
     *
     * <p><b>Stated because the alternative is tempting.</b> A solver could take a
     * {@code WeaponProfile} and call {@code ViewModelDefaults.of} on it, and that would work today.
     * It would also make the pose a function of <em>the mod's</em> table rather than of
     * <em>the state</em>, which is the difference between a pose another player can be sent and a
     * pose each client has to agree to invent. The state carries the resolved profile so that a
     * payload can say "this is the profile the server saw".</p>
     */
    @Test
    @DisplayName("the profile travels in the state, so a pose is sendable")
    void theProfileTravelsInTheState() {
        ViewModelProfile sabre = ViewModelDefaults.of(WeaponProfile.SABRE);
        PoseState state = new PoseState(WeaponProfile.SABRE, CHARGES, GuardProfile.NONE,
                false, ViewModelInput.RESTING, sabre);

        assertEquals(sabre, state.viewmodel(),
                "the state carries the resolved profile itself. A pose computed from it can be "
                        + "reproduced on a second machine that has never heard of our table, which "
                        + "is what 'the server sends state and the client calculates' needs");
        assertTrue(state.reads(com.cortes.combat.viewmodel.Channel.PARRY),
                "and the gate reads that profile rather than a name, which is why a second item "
                        + "sharing the sabre's profile would parry without any other change");
    }
}