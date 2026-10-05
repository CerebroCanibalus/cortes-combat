package com.cortes.combat.pose;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.viewmodel.ViewModelInput;
import com.cortes.combat.viewmodel.ViewModelProfile;

/**
 * Everything one player is doing, as a value, with nothing in it that has to be asked of the game.
 *
 * <h2>Why a record instead of a method that reads the world</h2>
 *
 * <p><b>Because the two poses this feeds were computed from the wrong side of the network.</b>
 * {@code BodyPoseDispatcher#poseFor} took one {@code AbstractClientPlayer}, reached into its two
 * hands and into a static viewmodel singleton, and produced a {@code BodyPose}. Every one of those
 * reads says <em>the local player</em>: the charge comes from {@code ItemViewModel.frame()}, which
 * is the client's prediction for the weapon in <em>your</em> hand.</p>
 *
 * <p>That is correct for you and blank for anybody else, and it is why the dispatcher's own javadoc
 * says other players' charge and guard are invisible. The limitation was assumed, and the user
 * promoted it to required. So the pose has to be a function of a <b>value</b>, and this record is
 * that value.</p>
 *
 * <h2>What is in it, and what is not</h2>
 *
 * <p>Five things: the weapon, its charge profile, the guard the two hands are worth, whether the
 * main hand is a vanilla shield, and the channels. Nothing else. Anything else would be a second
 * clock for a fact that already has one.</p>
 *
 * <p><b>{@link #vanillaShield} is a flag and not an item.</b> The guard is skipped when the main
 * hand is a vanilla shield, because {@code poseBlockingArm} has already moved that arm and a pose
 * on top moves the arm the shield is held in. A record that carried the {@code ItemStack} would be
 * a record that cannot be built in a test, and this is the only honest reason for the flag to
 * exist.</p>
 *
 * <h2>What the channels carry that must not travel</h2>
 *
 * <p>{@link ViewModelInput} has {@code time} and {@code seed} in it, and they are the <b>tremble's</b>
 * clock, not the player's state: a client builds its own, per strike, and two clients with the same
 * weapon and the same charge may shake differently. That is correct -- a tremble is noise, and
 * making it agree across the wire would make it a bug -- but it means {@code channels} is not yet
 * the thing that goes over the network. The payload will build one out of the fields that do
 * travel. It is a {@code ViewModelInput} rather than thirteen floats so this record does not have
 * to be rewritten when the first one does.</p>
 *
 * <p>Pure, so a pose can be computed in a unit test with nothing else on the classpath.</p>
 */
public record PoseState(WeaponProfile weapon,
                        ChargeProfile charge,
                        GuardProfile guard,
                        boolean vanillaShield,
                        ViewModelInput channels,
                        ViewModelProfile viewmodel) {

    /**
     * Nobody doing anything: no weapon, no channels, no pose.
     *
     * <p>And not simply {@code null} everywhere, because "the player is holding a sword" and "the
     * player has no weapon profile" are the same <em>answer</em> here and a solver that had to
     * null-check five fields would be a solver whose interesting failure is a
     * {@code NullPointerException}.</p>
     */
    public static final PoseState STILL = new PoseState(
            WeaponProfile.NONE, null, null, false,
            ViewModelInput.RESTING, ViewModelProfile.NONE);

    public PoseState {
        // **Nulls are normalised in the constructor and not checked at each read.** `charge` and
        // `guard` are genuinely optional -- a weapon with no charge component and a bare hand with
        // nothing to guard with both arrive as null -- and the solver already asks "can this one
        // do it", which is the question, so a null there is information and not a mistake.
        //
        // `channels` and `viewmodel` are different: a null there is a caller that forgot, and it
        // would surface as a pose that is subtly wrong rather than as an error.
        channels = channels == null ? ViewModelInput.RESTING : channels;
        viewmodel = viewmodel == null ? ViewModelProfile.NONE : viewmodel;
    }

    /** The same player, one frame later. */
    public PoseState withChannels(ViewModelInput next) {
        return new PoseState(weapon, charge, guard, vanillaShield, next, viewmodel);
    }

    /** The same player holding something else. */
    public PoseState withWeapon(WeaponProfile next, ChargeProfile nextCharge) {
        return new PoseState(next, nextCharge, guard, vanillaShield, channels, viewmodel);
    }

    /**
     * Whether this weapon's pose is allowed to look at that channel at all.
     *
     * <p><b>The gate is the profile and not the weapon's name, and it is the project's own
     * lesson.</b> A playtest parried with a sabre, switched to a sword, and the sword played the
     * sabre's five-second lockout, because the lockout rides {@code BREAK} and the sword reads
     * {@code BREAK} for its guard. "Is this the sabre" is a name; "can this profile do anything
     * with a parry" is the question, it survives the sabre getting a second item, and a test can
     * read it.</p>
     */
    public boolean reads(com.cortes.combat.viewmodel.Channel channel) {
        return viewmodel.reads(channel);
    }

    /** Whether this weapon charges at all, which is a fact about the item and not about the frame. */
    public boolean canCharge() {
        return charge != null && charge.canCharge();
    }
}