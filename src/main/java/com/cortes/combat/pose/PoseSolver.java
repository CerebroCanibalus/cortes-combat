package com.cortes.combat.pose;

import com.cortes.combat.thirdperson.BodyPose;
import com.cortes.combat.thirdperson.BodyPoses;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.Offset;
import com.cortes.combat.viewmodel.ViewModelInput;

/**
 * The whole of "what is this weapon doing", as one pure function of a {@link PoseState}.
 *
 * <h2>The one function, and why there is one</h2>
 *
 * <p><b>A weapon's animation is currently four files</b> -- the {@code ViewModelProfile} in
 * {@code ViewModelDefaults}, the channels it reads, {@code ViewModelMotion}'s clock, and the
 * constants in {@code BodyPoses} -- and a number written in the wrong one does not fail the build.
 * It fails on screen, three playtests later.</p>
 *
 * <p>Both halves of the answer, the first person offset and the body pose, are computed here from
 * the same state and from the same gates. Before this class they were computed in two places with
 * two sets of gates, which is the shape of the bug that let the sabre's parry move the weapon in
 * first person and leave the arm standing still in third: the viewmodel had the track, the body
 * dispatcher did not know to look.</p>
 *
 * <h2>What is <em>not</em> here</h2>
 *
 * <p>No clock and no randomness. Both live in {@code ViewModelMotion} and arrive already baked
 * into the {@link ViewModelInput}. That is what makes this a function rather than a simulation, and
 * it is why a test can ask for the frame at the middle of a parry and get the same answer every
 * time.</p>
 *
 * <p>Pure: no Minecraft, no statics, no clock. A test builds a {@link PoseState} by hand.</p>
 *
 * @see PoseState for what a state is and why it is a value
 * @see WeaponPose for the file format these numbers will eventually be declared in
 */
public final class PoseSolver {

    private PoseSolver() {
    }

    /**
     * One frame: what the weapon does in the hand, and what the body carrying it does.
     *
     * <p><b>Both come out of one call, and that is the point.</b> The two used to be computed
     * independently and each one had its own copy of "is this weapon allowed to look at that
     * channel", which is a second copy of a fact that lives in the profile.</p>
     *
     * <p>The order of the additions is fixed and it matters: the charge is <em>replaced</em> by the
     * chop rather than added to it, because they write the same arm.</p>
     */
    public static Pose solve(PoseState state) {
        return new Pose(hand(state), body(state));
    }

    /**
     * The weapon in the hand: its resting pose plus every track, in the viewmodel's own frame.
     *
     * <p>This is {@code ViewModelProfile#evaluate}, which is already the right answer and already
     * tested. It is called rather than reimplemented, because a second copy of the arithmetic is a
     * diagnostic that can lie -- the lesson this project has already paid for with {@code /cc hud},
     * which read the same geometry as the layer and drifted from it.</p>
     */
    public static Offset hand(PoseState state) {
        return state.viewmodel().evaluate(state.channels());
    }

    /**
     * The body, in degrees, added to whatever vanilla's walk cycle already put there.
     *
     * <h2>The four gates</h2>
     *
     * <p>Every one of them is the profile's own question and not a list of weapon names, which is
     * the project's rule: a list is a second copy of a fact the profile already states, and the day
     * a weapon was added to one and not the other the arm would do the wrong thing silently.</p>
     *
     * <p><b>The chop replaces the wind-up, and it is an arithmetic fact rather than a style
     * choice.</b> Both write the arm's pitch; a wind-up at -104 under a chop at -85 is -189, which
     * is past vertical, and past vertical is a somersault -- the one this project already shipped
     * once. The chop starts at exactly the height the wind-up left, so handing the arm over costs
     * nothing.</p>
     *
     * <p><b>The shield is vanilla's and is left alone.</b> A raised shield already has an arm and
     * an animation, so a pose on top moves the arm the shield is held in.</p>
     */
    public static BodyPose body(PoseState state) {
        ViewModelInput in = state.channels();

        // **The chop takes the arm away from the wind-up rather than sitting on top of it.**
        float chop = in.chop();
        BodyPose pose = chop > 0.0F
                ? BodyPose.NONE
                : BodyPoses.charging(state.weapon(), state.charge(), in.windup());

        // **A weapon carried on the shoulder, and the gate is the profile's own HELD track.**
        //
        // The arcabuz is the only profile with one, so "can this weapon be shouldered" has a
        // profile-shaped answer instead of a name. It is also load-bearing arithmetic and not only
        // a pose: the strike below peaks at -104 and the aim sits at -66, so a left click would
        // have summed to -188 and put the arm over the player's own head.
        boolean shouldered = state.reads(Channel.HELD);
        if (shouldered) {
            // **Always on, at one, because it is a posture and not a motion.** Vanilla's crossbow
            // holds CROSSBOW_HOLD for as long as it is charged; a gun carried low and brought up
            // when it is loaded is a different weapon.
            pose = pose.plus(BodyPoses.aiming(1.0F));
            pose = pose.plus(BodyPoses.reloading(in.reload()));
        }

        // **Two swings, and the profile decides which one.** The strike is a bump -- up and back --
        // which suits a thrust. On everything else it was reported twice as starting from below and
        // going up. So the chop exists, it only falls, and the gate is reads(CHOP).
        BodyPose swing;
        if (state.reads(Channel.CHOP)) {
            swing = BodyPoses.chopping(in.chop());
        } else if (shouldered) {
            // **The poke, and not the sword's strike.** See the arithmetic above.
            swing = BodyPoses.bayonet(in.strike());
        } else {
            swing = BodyPoses.striking(in.strike());
        }
        pose = pose.plus(swing);

        if (!state.vanillaShield()) {
            pose = pose.plus(BodyPoses.guarding(state.guard(), in.guard()));
        }

        // **The parry, which was in the viewmodel and missing from the body.** The playtest said
        // "the shotel has no animation for when it parries", and it was true from behind: the
        // viewmodel had a PARRY track the whole time, so the blade snapped across in first person
        // and the arm stood still in third. A component in its place and nothing drawing it.
        if (state.reads(Channel.PARRY)) {
            pose = pose.plus(BodyPoses.parrying(in.parry()));
        }

        // **The two specials that are a body motion of their own.** The gate is the profile's own
        // track on LUNGE and on SWEEP, which today happens to be the ropera and the halberd and
        // nothing else -- a gate that is a capability, so a weapon that grows a lunge gets a body
        // without anybody editing a list.
        if (state.reads(Channel.LUNGE)) {
            pose = pose.plus(BodyPoses.lunging(in.lunge()));
        }
        if (state.reads(Channel.SWEEP)) {
            pose = pose.plus(BodyPoses.sweeping(in.sweep()));
        }
        return pose;
    }

    /**
     * One frame's answer to both questions.
     *
     * <p>A record of two rather than a method that fills a caller, and the reason is the same one
     * that made {@code BodyPoseDispatcher} lose the ropera's lunge: the first version of that class
     * called a static {@code apply(pose, ...)} and threw the result away, so the code read as if it
     * contributed something and it contributed nothing.</p>
     */
    public record Pose(Offset hand, BodyPose body) {

        /** Neither pose moves anything. */
        public static final Pose STILL = new Pose(Offset.ZERO, BodyPose.NONE);

        /** Whether this frame has nothing to draw, which is the question a culling path asks. */
        public boolean isStill() {
            return hand.isZero() && body.isZero();
        }
    }
}