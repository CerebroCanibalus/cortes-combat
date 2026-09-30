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
                // The wind-up. Rises slowly and settles at the top, so the last of the charge
                // feels like arriving somewhere instead of running out of travel.
                Track.of(Channel.WINDUP, Curve.SMOOTH,
                        new Offset(-0.02, 0.06, -0.18, -35.0, 8.0, 0.0)),
                // The tremble. Weighted on the wind-up so it is a suggestion at a quarter charge
                // and unmistakable at full, and never stepped: a shake that switches on is a
                // glitch, a shake that grows is a weapon under load.
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.014, 1.8, 0.6, 0.9),
                        14.0F, Curve.SMOOTH),
                // The strike. Snaps forward and past rest, then comes back as the channel does.
                // The pitch change is larger than the translation on purpose: what sells weight
                // is the rotation leading and the position following, not the other way around.
                Track.of(Channel.STRIKE, Curve.EXPO,
                        new Offset(0.03, -0.05, 0.30, 42.0, -5.0, 0.0)));
    }

    /** The viewmodel for a weapon profile, or {@link ViewModelProfile#NONE} if it has none yet. */
    public static ViewModelProfile of(WeaponProfile profile) {
        return TABLE.getOrDefault(profile, ViewModelProfile.NONE);
    }
}
