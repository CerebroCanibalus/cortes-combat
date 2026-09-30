package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.EnumMap;
import java.util.Map;

/**
 * The viewmodel each weapon profile gets until someone writes a better one.
 *
 * <p>This is a placeholder table with one real entry, and it is here rather than in
 * {@code ViewModelRenderer} for the reason the rest of the project keeps data out of code: the
 * renderer must not know which weapon is which. Adding the remaining twenty-nine weapons is a
 * row here, and so is a foreign mod's, without a single line changing in the machinery.</p>
 *
 * <p>The numbers below were written, not tuned. They are the right order of magnitude and the
 * right shape, and what they are not is a feel anyone has played. Treat them as a starting point
 * and change them against the game, not against a test.</p>
 */
public final class ViewModelDefaults {

    private static final Map<WeaponProfile, ViewModelProfile> TABLE = new EnumMap<>(WeaponProfile.class);

    static {
        TABLE.put(WeaponProfile.SWORD, chargedSword());
        // Everything else is deliberately absent for now. A weapon with no entry gets the exact
        // vanilla pose, which is the correct default: a viewmodel that is subtly wrong is worse
        // than no viewmodel, and a player who has not noticed a missing one has lost nothing.
    }

    private ViewModelDefaults() {
    }

    /**
     * The sword: drawn back in proportion to the charge, trembling harder the fuller it gets, and
     * thrown forward on release.
     *
     * <p>Three tracks, and each one is a different question. The first is "how far back am I",
     * which has to be proportional or the player cannot see how much charge they have through
     * the item as well as through the meter. The second is "how much is this shaking", which
     * needs to be invisible at the start of the wind-up: a tremble that runs from the first tick
     * reads as the viewmodel being broken rather than as effort. The third is the strike, and it
     * is on its own channel precisely so that it can be fast and hard while the wind-up is still
     * unwinding underneath it.</p>
     *
     * <p>Positions are item units, where a held item is about one across. The backward pull is
     * modest on purpose: the sword is a one hand weapon held near the camera, and half a unit of
     * travel puts the blade outside the frame at some fields of view.</p>
     */
    private static ViewModelProfile chargedSword() {
        return ViewModelProfile.owningSwing(
                // The wind-up.
                //
                // Carried by the Y translation rather than by the pitch, and that is not a taste
                // decision. Vanilla's own arm constants put the hand at ARM_POS_Y = -0.75, which
                // makes +Y up in this frame beyond argument, and vanilla's bow pulls back along
                // negative Z. The pitch is the one axis whose sign has to be read off the screen,
                // and a first attempt that led with -35 degrees of it read as the sword going
                // *down*. So the lift is now mostly translation, which is verifiable, and the
                // pitch is a small lean that cannot invert the read even if its sign is wrong.
                //
                // If the lean turns out to be backwards it is one number: the 18.0 below.
                Track.of(Channel.WINDUP, Curve.SMOOTH,
                        new Offset(-0.02, 0.22, -0.10, 18.0, 6.0, 0.0)),
                // The tremble. Weighted on the wind-up so it is a suggestion at a quarter charge
                // and unmistakable at full, and never stepped: a shake that switches on is a
                // glitch, a shake that grows is a weapon under load.
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.014, 1.8, 0.6, 0.9),
                        14.0F, Curve.SMOOTH),
                // The strike. Snaps forward and past rest, then comes back as the channel does.
                //
                // Scaled by the charge at the moment of release, so a tap gets a small jab and a
                // full charge gets the whole thing. Before that it did not, and a click was the
                // same 42 degree swing as a held charge, which is what made an ordinary attack
                // look like a bad charge.
                Track.of(Channel.STRIKE, Curve.EXPO,
                        new Offset(0.03, -0.12, 0.30, 34.0, -5.0, 0.0)));
    }

    /** The viewmodel for a weapon profile, or {@link ViewModelProfile#NONE} if it has none yet. */
    public static ViewModelProfile of(WeaponProfile profile) {
        return TABLE.getOrDefault(profile, ViewModelProfile.NONE);
    }
}
