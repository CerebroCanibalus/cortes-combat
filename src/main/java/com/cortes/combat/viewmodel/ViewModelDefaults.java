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
        TABLE.put(WeaponProfile.UNARMED, chargingFist());
        // Everything else is deliberately absent for now. A weapon with no entry gets the exact
        // vanilla pose, which is the correct default: a viewmodel that is subtly wrong is worse
        // than no viewmodel, and a player who has not noticed a missing one has lost nothing.
    }

    private ViewModelDefaults() {
    }

    /**
     * The sword: drawn back in proportion to the charge, trembling harder the fuller it gets, cut
     * forward on release and falling back under its own weight.
     *
     * <p>Four tracks and each is a different question. "How far back am I" has to be proportional
     * or the player cannot read the charge off the item. "How much is this shaking" has to be
     * invisible at the start: a tremble running from the first tick reads as a broken viewmodel
     * rather than as effort. "The cut" is on its own channel so it can be hard and fast while the
     * wind-up is still unwinding underneath. "The fall" is on a channel that starts where the cut
     * finishes, because a cut that goes out and comes back is two displacements with a peak
     * between them and one bump averages them into a single shove.</p>
     *
     * <p>Positions are item units, where a held item is about one across.</p>
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
                // *down*.
                //
                // The playtest confirmed the lift: "el arma sí se levanta perfecto". The lean is
                // still 18 degrees and is still the one number here that is a guess.
                Track.of(Channel.WINDUP, Curve.SMOOTH,
                        new Offset(-0.02, 0.22, -0.10, 18.0, 6.0, 0.0)),
                // The tremble. Weighted on the wind-up so it is a suggestion at a quarter charge
                // and unmistakable at full, and never stepped: a shake that switches on is a
                // glitch, a shake that grows is a weapon under load.
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.014, 1.8, 0.6, 0.9),
                        14.0F, Curve.SMOOTH),
                // The cut. "A cut forward, then falling with weight back towards the player",
                // and the first version was neither: 0.30 units of Z on a fast curve is a nudge,
                // and the playtest called it pathetic. It is a lunge now, twice the travel, and
                // mostly translation for the same reason the wind-up is.
                Track.of(Channel.STRIKE, Curve.EXPO,
                        new Offset(0.04, -0.06, 0.62, -22.0, -8.0, 0.0)),
                // The fall back. Down and towards the player, which is what recovering under
                // weight means: the arm comes back to where it was rather than springing to a
                // pose, and the negative Z is what makes it read as coming back rather than
                // simply dropping.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(-0.04, -0.30, -0.26, 26.0, 6.0, 0.0)));
    }

    /**
     * The bare hand, which the design makes chargeable.
     *
     * <p>Same three beats as the sword and about half the travel, because a fist is not a blade.
     * It charges because in the design the fists are the fastest thing in the game and that speed
     * is the only thing that makes them worth having with nothing equipped.</p>
     *
     * <p>No tremble track, and that is a judgement rather than an omission: a fist shaking is a
     * different and much sillier read than a sword shaking, and it would be the first thing a
     * player noticed and the last thing they would want.</p>
     */
    private static ViewModelProfile chargingFist() {
        return ViewModelProfile.owningSwing(
                Track.of(Channel.WINDUP, Curve.SMOOTH,
                        new Offset(-0.01, 0.13, -0.07, 11.0, 4.0, 0.0)),
                Track.of(Channel.STRIKE, Curve.EXPO,
                        new Offset(0.02, -0.04, 0.30, -14.0, -5.0, 0.0)),
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(-0.02, -0.16, -0.13, 16.0, 4.0, 0.0)));
    }

    /** The viewmodel for a weapon profile, or {@link ViewModelProfile#NONE} if it has none yet. */
    public static ViewModelProfile of(WeaponProfile profile) {
        return TABLE.getOrDefault(profile, ViewModelProfile.NONE);
    }
}
