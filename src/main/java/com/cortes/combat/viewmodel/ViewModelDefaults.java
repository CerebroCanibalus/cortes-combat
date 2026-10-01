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
        TABLE.put(WeaponProfile.ROPERA, ropera());
        TABLE.put(WeaponProfile.DAGGER, dagger());
        TABLE.put(WeaponProfile.SABRE, shotel());
        TABLE.put(WeaponProfile.HALBERD, halberd());
        TABLE.put(WeaponProfile.WARHAMMER, warhammer());
        // Vanilla's axe, pickaxe, shovel, mace and trident are deliberately absent. They are
        // vanilla items: a hand-drawn motion on an item whose silhouette we did not draw would
        // fight the model, and a viewmodel that is subtly wrong is worse than none, because a
        // player cannot tell that a motion was meant to be there. A weapon with no entry gets the
        // exact vanilla pose, which is the correct default.
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
     * <p>Positions are item units, where a held item is about one across, and angles are degrees.
     * Both are vanilla's own units, because these numbers are written against vanilla's frame: the
     * profile is applied at the tail of {@code applyItemArmTransform}, which is where the item
     * sits at {@code ITEM_POS = (0.56, -0.52, -0.72)}. <b>Negative Z is away from the camera</b>,
     * so a cut forward is a negative Z, and a rotation pivots on the grip so the tip of the blade
     * travels while the hand stays put.</p>
     */
    private static ViewModelProfile chargedSword() {
        return ViewModelProfile.owningSwing(
                // The wind-up: drawn back and lifted, no sway.
                //
                // The lateral cocking went to the fist, not here. The playtest was explicit: a
                // sword winds up by being drawn back, and a hand winds up by being cocked to the
                // side. Twenty two degrees of yaw on a sword reads as the player turning the
                // weapon rather than loading it, so it is on UNARMED below and not duplicated
                // here.
                //
                // EXPO and not SMOOTH, and that is the answer to "the cocking starts too late".
                // Smoothstep is flatter than a straight line at both ends and steeper in the
                // middle, so it spends the first part of the hold almost still and then rushes.
                // The wind-up is the one motion in this profile that has to read as immediate
                // feedback that the press was heard, so it is the one motion that has to be
                // ahead of linear rather than behind it.
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.02, 0.20, -0.14, 10.0, 0.0, 0.0)),
                // The tremble. Weighted on the wind-up so it is a suggestion at a quarter charge
                // and unmistakable at full, and never stepped: a shake that switches on is a
                // glitch, a shake that grows is a weapon under load.
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.014, 1.8, 0.6, 0.9),
                        14.0F, Curve.SMOOTH),
                // The cut. Deep, slow, and almost straight down.
                //
                // The playtest asked for three things and they are one thing: a much heavier
                // swing, a normal swing nearly as heavy as the charged one, and both pointing at
                // the middle of the screen rather than off to the side. So the lateral goes to
                // almost nothing, the pitch takes everything, and the weight that is left comes
                // from the swing lasting as long as the weapon's cooldown rather than from a
                // bigger angle travelled faster.
                //
                // Then it came back one notch: a hundred fifty two was "a little too heavy and
                // otherwise perfect", which is the only number anybody has ever given about this
                // that was not a complaint, so it is worth writing down that the ceiling was
                // found rather than guessed.
                //
                // Yaw 4 and roll -6 are a hint of a diagonal and no more. Anything larger
                // reads as the player swiping across the screen instead of cutting into it, and
                // the crosshair is the thing the player is aiming with.
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.02, -0.04, -0.14, -140.0, 4.0, -6.0)),
                // The weight, which is a separate beat and not part of the attack. It goes down
                // and back to where the hand is, which is what the playtest asked for by name:
                // the weight is not the cut going further, it is the fall after it.
                //
                // It unwinds most of the pitch, so the blade comes back up the way it went down.
                // Anything less and the sword spends the rest of the swing pointing at the floor,
                // which is the "drifts off the vanilla pose" trap the channel design exists to
                // to prevent.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(-0.02, -0.36, 0.12, 118.0, -3.0, 5.0)),
                // The guard: the sword across the body, blade up and forward.
                //
                // Not invented. Vanilla's own blocking pose was read out of the 1.21.1 bytecode,
                // inside renderArmWithItem's isUsingItem branch, and it is a pose in exactly the
                // frame this profile is written in:
                //
                //     translate(-0.4786 * side, -0.0944, +0.0573)
                //     rotateX(-11.935)  rotateY(65.3 * side)  rotateZ(-9.785 * side)
                //
                // That is Mojang's answer to "hold this thing up in front of you", in this frame,
                // and copying its directions beats three signed numbers picked from a
                // description. What a sword needs that a shield does not is LENGTH: the blade has
                // to come up rather than just angle across, so the pitch is pushed further in the
                // direction the swing already goes, and the yaw is pulled in, because a sword held
                // at the shield's sixty-five degrees points off the side of the screen instead of
                // in front of it.
                //
                // One binary unknown, and it is worth naming: the sign of the pitch is the one
                // thing that cannot be read off a screen. It follows vanilla's swing sign, which
                // is known good because it IS vanilla's -80 on X, so if the blade ends up
                // pointing at the floor the fix is one sign on one number here.
                // The guard, and the third time this number moved in the same direction.
                //
                // Vanilla's blocking pose is a **verified** pose in exactly the frame this
                // profile is written in, read out of the 1.21.1 bytecode:
                //
                //     translate(-0.4786 * side, -0.0944, +0.0573)
                //     rotateX(-11.935)  rotateY(65.3 * side)  rotateZ(-9.785 * side)
                //
                // I read a screenshot of it, decided the sixty-five degrees of yaw was "too far
                // to the side", and took it to 30, then to 6, then to 0. Two playtests later the
                // report was the most precise sentence anyone has written about this: "it has to
                // be ROTATED TOWARDS THE PLAYER".
                //
                // Which is the yaw. A held sword shows its EDGE to the camera, and turning the
                // flat of the blade toward the player is a rotation of about a quarter turn on Y.
                // Sixty-five degrees is that, and it is Mojang's number rather than mine. "Too far
                // to the side" and "the blade faces the wrong way" are the same complaint about
                // two different axes, and I spent two rounds moving the wrong one.
                //
                // The pitch is the part that was genuinely too big: a rotation pivots on the grip
                // and the tip is over a unit from it, so -46 swept the tip further than the whole
                // distance between the hand and the eye. It is a third of that now.
                Track.of(Channel.GUARD, Curve.SMOOTH,
                        new Offset(-0.34, -0.02, 0.02, -14.0, 62.0, -9.0)),
                // The collapse when the guard breaks: a short punch, down and back.
                //
                // Small on purpose, and **the opposite direction** to the stance above: the guard
                // was up and forward and this throws it down and back. Same direction would read
                // as a deeper guard rather than as a hit landing on it.
                //
                // It was a five second slow drift and it was not a numbers problem. The motion
                // ended the collapse in seven ticks the whole time; the caller was restarting it
                // on every tick of the item cooldown's countdown, so a short bump was held at its
                // first seventh for a hundred ticks. The fix is on the caller's edge and the
                // lesson is in ViewModelMotion.observeStagger.
                Track.of(Channel.BREAK, Curve.SMOOTH,
                        new Offset(0.05, -0.07, 0.06, 16.0, 7.0, 4.0)));
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
                // The wind-up: cocked to the side, which is the part a sword does not do.
                //
                // The playtest asked for the lateral cocking on the hand and the sword was moved
                // back to a plain draw-back, because a hand is a joint and a blade is a lever:
                // twenty degrees of yaw is a wrist, and it would read as the player turning the
                // weapon rather than as loading it. It lives here and only here.
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.02, 0.08, -0.12, 6.0, 20.0, 0.0)),
                // A fist does not have a blade to sweep, so the motion is a hook and a straight
                // drive rather than an arc. The angles are well under the sword's because there
                // is no leverage: the same rotation on a hand reads as a twitch and on a blade as
                // a cut, and the difference is entirely the length of the thing rotating.
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.01, -0.03, -0.09, -68.0, 5.0, -5.0)),
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(-0.01, -0.16, 0.06, 52.0, -4.0, 4.0)));
    }

    /**
     * The ropera: a thrust, not a swing.
     *
     * <p>Every other profile here is a cut, because a sword is a cut and the ropera is the one
     * weapon in the set that is not. Its design is precision at a twelve degree cone, which means
     * the motion that sells it is the smallest, straightest and fastest of the five: almost no
     * travel, almost no arc, and a pull that brings the point back to the crosshair rather than
     * sweeping across the screen.</p>
     *
     * <p>The yaw is near zero on purpose and it is the third time this number has moved in the
     * same direction across the project. "Too much to the side" and "the blade faces the wrong
     * way" are the same complaint about two different axes, and the sword already paid two
     * playtests for having them confused.</p>
     *
     * <p>No tremble at all, and this is a judgement rather than an omission. A rapier held like
     * this is aimed, and a shaking point would read as the crosshair being unsteady -- which is
     * the one thing a twelve degree cone cannot afford to look like.</p>
     */
    private static ViewModelProfile ropera() {
        return ViewModelProfile.owningSwing(
                // Drawn back along the line of the aim, no sway.
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.01, 0.06, -0.10, 4.0, 0.0, 0.0)),
                // The thrust: straight forward, almost no rotation. A rotation here would sweep the
                // point across the screen and that is the one thing a precision weapon must not do.
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.0, -0.01, -0.16, -18.0, 1.0, -1.0)),
                // Back to the hand, and no further than the wind-up took it.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(0.01, -0.10, 0.07, 16.0, -1.0, 1.0)));
    }

    /**
     * The dagger: the shortest motion of the five and the fastest.
     *
     * <p>Less travel than the fist on the strike, because a dagger at 2.6 attacks per second is
     * a flick and a flick that travels far reads as slow. What it has instead is a roll: the
     * blade turns over as it comes in, which is what makes a small weapon read as a stab rather
     * than as a poke.</p>
     */
    private static ViewModelProfile dagger() {
        return ViewModelProfile.owningSwing(
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.01, 0.05, -0.08, 3.0, -8.0, 0.0)),
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.0, -0.02, -0.11, -24.0, 6.0, -14.0)),
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(0.0, -0.09, 0.05, 20.0, -5.0, 12.0)));
    }

    /**
     * The shotel: a cut that comes back around, because the blade is curved.
     *
     * <p>The only profile here with a real lateral component, and the reason is the shape of the
     * weapon rather than a preference: a curved blade cutting inward is a hook, and a hook that
     * goes straight reads as a sword. Seventy five degrees of cone is the widest in the game and
     * the reach is among the shortest, so the motion has to sell a lot of sweep from very little
     * distance.</p>
     *
     * <p>Charges, and charges hard: the shotel's full charge doubles damage, the only weapon that
     * does, so the tremble is weighted high up its curve. It is the one weapon where a player
     * should be able to see the charge on the item and not just on the bar.</p>
     *
     * <p><b>EXPO and not SMOOTH for the tremble weight, and the difference is the whole claim.</b>
     * SMOOTH is flat at both ends, so a tremble weighted by it is nearly invisible for the first
     * third of the charge and then rushes -- the player sees nothing, then everything. EXPO spends
     * almost all of its range up front, which is what "a suggestion at half charge and
     * unmistakable at full" actually means: a weight that grows the whole way.</p>
     */
    private static ViewModelProfile shotel() {
        return ViewModelProfile.owningSwing(
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.04, 0.10, -0.09, 8.0, -16.0, 6.0)),
                // Weighted on the wind-up with EXPO, so the tremble is a suggestion at half charge
                // and unmistakable at full. A charge that doubles damage should look like it.
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.012, 2.0, -1.2, 1.0),
                        17.0F, Curve.EXPO),
                // The cut goes across and down, which is a hook rather than a chop.
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.05, -0.03, -0.11, -96.0, 26.0, -10.0)),
                // Comes back the way it went, from the other side.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(-0.05, -0.30, 0.10, 86.0, -22.0, 9.0)),
                Track.of(Channel.GUARD, Curve.SMOOTH,
                        new Offset(-0.30, -0.02, 0.02, -12.0, 58.0, -8.0)));
    }

    /**
     * The halberd: the widest sweep and the slowest recovery, and it is a polearm.
     *
     * <p>A two and a half metre weapon on screen has to be told apart from a sword by its reach
     * alone, so this profile is the one with real travel: a wide lateral arc, a big recovery, and
     * a pitch that ends well below where it started.</p>
     *
     * <p>The wide cone at fifty five degrees is the design's answer to crowds, and the motion is
     * shaped to match: the yaw is the largest of any profile here. A wide swing that looks like
     * a narrow one teaches the player the wrong thing about which enemies it reaches.</p>
     */
    private static ViewModelProfile halberd() {
        return ViewModelProfile.owningSwing(
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.05, 0.16, -0.10, 10.0, -34.0, 4.0)),
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.010, 1.4, -0.8, 0.6),
                        12.0F, Curve.SMOOTH),
                // The sweep. Yaw is the point of this weapon.
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.10, -0.05, -0.10, -70.0, 62.0, -8.0)),
                // A polearm's recovery is long, and that is what makes the multi-hit's penalty
                // read as a cost rather than as a cooldown.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(-0.09, -0.40, 0.13, 62.0, -54.0, 7.0)));
    }

    /**
     * The warhammer: the slowest thing in the game, and the heaviest.
     *
     * <p>Twenty two ticks between swings, so there is time to watch a motion that would read as a
     * twitch on a fast weapon. That is what the duration buys, and it is why this profile has the
     * largest angles in the set rather than the smallest.</p>
     *
     * <p>Almost straight down and barely any lateral, for the same reason the sword's is almost
     * straight down: the crosshair is the thing the player is aiming with, and a swing that ends
     * up off to the side has spent its weight on decoration. The pitch is what carries the mass.</p>
     *
     * <p>No guard track. A warhammer does not go across the body -- it is not a stance weapon and
     * the design gives it no right-click -- and a guard pose on it would teach a posture the
     * weapon cannot hold.</p>
     */
    private static ViewModelProfile warhammer() {
        return ViewModelProfile.owningSwing(
                // Lifted, not drawn back. The wind-up is the only upward motion in the set.
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.01, 0.26, -0.06, -22.0, 0.0, 0.0)),
                // A slow tremble, and weighted low: a warhammer at full charge ignores armour, and
                // that should feel like something under load rather than like a fast weapon.
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.009, 1.1, 0.0, 0.5),
                        9.0F, Curve.SMOOTH),
                // The largest pitch of any profile, and almost no yaw. All of the weight is in
                // the rotation: a translation would move the head and the grip alike and read as
                // a shove no matter how far it went.
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.0, -0.06, -0.10, -168.0, 2.0, -4.0)),
                // Comes most of the way back. A warhammer is not a sword and it does not recover
                // like one.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(0.0, -0.44, 0.09, 126.0, -2.0, 3.0)));
    }

    /** The viewmodel for a weapon profile, or {@link ViewModelProfile#NONE} if it has none yet. */
    public static ViewModelProfile of(WeaponProfile profile) {
        return TABLE.getOrDefault(profile, ViewModelProfile.NONE);
    }
}
