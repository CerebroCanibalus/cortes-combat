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
    TABLE.put(WeaponProfile.ARCABUZ, arcabuz());
    TABLE.put(WeaponProfile.AXE, axe());
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
                Track.of(Channel.CHOP, Curve.LINEAR,
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
     * weapon in the set that is not.</p>
     *
     * <p><b>Which axis is which, read out of vanilla instead of guessed.</b> The first person
     * item transform is {@code translate(0.51, -0.08, -0.75)}, so <b>−z is into the screen</b>.
     * Vanilla's verified blocking pose is {@code translate(-0.4786 * side, -0.0944, +0.0573)},
     * which moves almost half a unit sideways and a twentieth of one in depth, so <b>x is
     * lateral</b>. The first version of this profile put the travel on x and the playtest said
     * so in four words: "se hace de lado". A thrust drawn along the lateral axis is a cut, and no
     * amount of making it big turns a cut into a thrust.</p>
     *
     * <p><b>And the resting blade points UP, which is the half of the motion that was missing.</b>
     * The ropera's texture is drawn with the blade vertical, so pushing it away from the camera
     * slides an <em>upward</em> pointing blade into the screen -- a poke at the ceiling. A thrust
     * is <b>ninety degrees of rotation and then a push</b>, and the rotation is the bigger half:
     * at the peak of the strike the pitch is <b>−90</b> and the point is level with the crosshair.
     *
     * <p>That also explains why the number is not a small one. A rotation pivots on the grip and
     * a rapier's point is a unit and a half from it, so ninety degrees throws the tip much further
     * than the distance between the hand and the eye -- and that is not a problem here, because
     * <em>forward</em> is where the tip is supposed to end up. The warning about big pitches is
     * about a sword whose tip should stay in the frame; a rapier's tip is meant to leave it.</p>
     *
     * <p>The lunge is the same thrust with the pitch stopped short of level. At <b>−70</b> the
     * point finishes above the crosshair, which is what a real lunge is: the body goes forward and
     * up at the same time. It is also deeper, because the step already committed the player.</p>
     *
     * <p>Yaw stays at zero and it is the third time this number has moved in the same direction
     * across the project. "Too much to the side" and "the blade faces the wrong way" are the same
     * complaint about two different axes, and the sword already paid two playtests for having
     * them confused.</p>
     *
     * <p>No tremble at all, and this is a judgement rather than an omission. A rapier held like
     * this is aimed, and a shaking point would read as the crosshair being unsteady.</p>
     *
     * <p><b>These numbers are written, not played.</b> The axes are derived from the frame and the
     * ninety is derived from the art; how hard the push reads on screen is a playtest question.</p>
     */
    private static ViewModelProfile ropera() {
        return ViewModelProfile.heldAs(
                // **The rest pose is the fix, and it is a field rather than a channel.** The art
                // is drawn with the blade vertical, so the vanilla grip is a lowered guard and
                // every thrust had to spend ninety degrees to become level -- which the playtest
                // called "casi como un dash". Fifty-eight degrees holds the line just above
                // horizontal, which is where a rapier lives, and it takes the strike's rotation
                // from ninety degrees down to about thirty. That is the whole difference between
                // a thrust and a movement the arm makes.
                new Offset(0.0, 0.0, 0.0, -58.0, 0.0, 0.0),
                true,
                // Cocked: the point tips up off the line, toward the player. Positive, because up
                // is less negative in this frame, and it is a wrist and not an arm.
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(0.0, 0.03, 0.13, 16.0, 0.0, 0.0)),
                // The thrust. Sixty degrees and most of a unit into the screen, and EXPO rather
                // than LINEAR so the blade LEAVES instead of easing out: the playtest called the
                // thrust "not enough intensity", and a movement that reaches its furthest point
                // gradually is a swing, because a swing is a movement that takes its time.
                //
                // It also rotates past level rather than onto it. The rest is 58 degrees up off
                // horizontal and the strike adds sixty, so the point ends up eight degrees BELOW
                // the line -- the blade has gone over the top of its arc and is driving down into
                // the target, which is where a thrust actually ends. Landing exactly on level
                // would be a stab that stopped when it arrived.
                Track.of(Channel.STRIKE, Curve.EXPO,
                        new Offset(0.0, -0.06, -0.62, -60.0, 0.0, 0.0)),
                // Back onto the line and well past it, which is what a wrist does after a thrust.
                // A motion that stops exactly where it started is a machine returning to a
                // position, and the overshoot is what has to sell the whole thing as a lunge
                // rather than a poke.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(0.0, 0.02, 0.21, 14.0, 0.0, 0.0)),
                // The lunge, on its own channel because it finishes at a different angle: the step
                // is already paid for, so the blade goes further and it goes UP off the line.
                // EXPO spends most of its range in the first third, which is what makes an
                // extension read as explosive rather than as a longer version of the same push.
                Track.of(Channel.LUNGE, Curve.EXPO,
                        // Further than the thrust and finishing ABOVE it. The thrust now drives the
                        // point eight degrees below level, because that is where a stab ends; the
                        // lunge stops short of that by eighteen, so the body is going forward and
                        // up at the same time and the blade leads it. Deeper than the thrust too,
                        // by an eighth of a unit -- small, and deliberately so: the step is what
                        // carries the lunge, and a blade that shoots out a metre further than the
                        // thrust reads as a different weapon rather than as the same one committed.
                        new Offset(0.0, -0.09, -0.72, -26.0, 0.0, 4.0)));
    }

    /**
     * The dagger: the shortest motion of the five and the fastest.
     *
     * <p>Less travel than the fist on the strike, because a dagger at 2.6 attacks per second is
     * a flick and a flick that travels far reads as slow. What it has instead is a roll: the
     * blade turns over as it comes in, which is what makes a small weapon read as a stab rather
     * than as a poke.</p>
     */
    /**
     * The dagger, and it is a pick going up rather than a stab going forward.
     *
     * <p><b>"It hits with the edge and it should be an upward pick."</b> The old stab was
     * {@code Offset(0, 0.03, -0.34, 14, 0, 6)}: a third of an item of forward travel with the pitch
     * at fourteen degrees. Fourteen degrees on a knife whose art is drawn with the blade standing up
     * means the blade is <em>still</em> standing up while the hand thrusts -- so what arrives first is
     * the flat of the blade, and it arrives sideways. That is a cut delivered by a weapon that was
     * supposed to be poking, and it is why the point of a dagger was being wasted.</p>
     *
     * <p><b>Two changes and they are one change.</b> The hand goes UP rather than forward -- nearly a
     * quarter of an item of {@code y} against a third of an item of {@code z} -- and the pitch drops
     * to thirty degrees so the point leads instead of the edge. Rake and travel in the same direction
     * is what makes it a pick: the tip is the lowest thing on screen at the start and the thing that
     * arrives highest at the end.</p>
     *
     * <p>The cock drops the hand and cocks the blade back, so the upward drive has something to push
     * against, and the follow-through comes back down the same line -- one gesture, not a jab and a
     * settle. It is the fastest weapon in the set, so the motion is short by necessity.</p>
     */
    private static ViewModelProfile dagger() {
        return ViewModelProfile.owningSwing(
                // The cock: the hand drops and the blade is thrown BACK off the line, forty degrees
                // of it, so the drive has a hundred and twelve degrees of rotation to travel
                // through. A pick that starts aligned has nothing to swing.
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.02, -0.15, 0.05, 40.0, 0.0, 4.0)),
                // **The pick, and the rotation is the second half of it.**
                //
                // "It attacks without rotating, straight up, it has to be with a stock, rotated
                // toward the front." The first attempt at this motion added the travel and left the
                // pitch at thirty degrees, and thirty degrees on a blade whose art is drawn standing
                // up is *still standing up*: the hand went up and the knife went up with it, edge
                // first, which is a slash drawn vertically rather than a pierce.
                //
                // Seventy two degrees is the number that matters. It is the ropera's thrust pitch
                // (sixty) plus the twelve that a dagger being shorter can afford, and it is what
                // takes the point from straight up to straight forward -- so at the top of the
                // travel the thing arriving at the target is the point, and the hand having gone up
                // is what carried it there.
                Track.of(Channel.STRIKE, Curve.LINEAR,
                        new Offset(0.0, 0.22, -0.14, -72.0, 0.0, -6.0)),
                // And it comes back down the same line, so the follow-through is not a second motion.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(0.0, -0.13, 0.06, 22.0, 0.0, 3.0)));
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
                Track.of(Channel.CHOP, Curve.LINEAR,
                        new Offset(0.05, -0.03, -0.11, -96.0, 26.0, -10.0)),
                // Comes back the way it went, from the other side.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(-0.05, -0.30, 0.10, 86.0, -22.0, 9.0)),
                // The parry: the blade comes up ACROSS the body, edge out, and the yaw is large
                // because that is the read. A parry is not a guard pose and it must not look like
                // one: the sabre cannot block, so the motion that says "I am not protecting
                // myself, I am waiting for your hit" has to be a different shape from the one that
                // says "I am protecting myself". The big yaw is the whole difference -- turned to
                // the side, the flat of the blade faces the attacker, which is what a parry is.
                //
                // This replaced a GUARD track that was here and never ran: the sabre's guard
                // profile is NONE, so the channel was pinned at zero for every frame of every
                // session. A track on a channel nothing can move is a comment that costs a sum
                // and reads like a feature.
                Track.of(Channel.PARRY, Curve.SMOOTH,
                        new Offset(-0.26, 0.06, -0.14, -22.0, 104.0, -18.0)),
                // The parry lockout, on the guard's own collapse channel and with the guard's own
                // numbers. The player has already seen this motion mean "not right now": it is
                // what a shield does when something knocks it down, and this is the same blade
                // saying the same thing for a different reason.
                //
                // Copied rather than invented, and that is the point -- a second set of numbers
                // for "you cannot do this yet" would be a second thing to learn, and a player
                // holding a sabre and a shield should not have to work out that the two
                // unavailable motions are the same unavailable motion.
                //
                // It rides the BREAK channel, whose bump is one hundred ticks long here instead of
                // seven. The shape carries the reason and the length carries the time, so the
                // weapon coming back is the recharge indicator and nothing had to be drawn.
                Track.of(Channel.BREAK, Curve.SMOOTH,
                        new Offset(0.05, -0.07, 0.06, 16.0, 7.0, 4.0)));
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
                // **The sweep, and every sign in it was flipped by a playtest.**
                //
                // The ask was: the tip has to rotate toward the FRONT, then toward the side OPPOSITE
                // to which it currently rotates, and swing across the screen until it returns to the
                // hand. It was doing none of them, because it was built to mirror the thrust -- yaw
                // negative against the thrust's +62 -- and mirroring an opposite motion is only
                // correct if the player is on the other side of it.
                //
                // So x, yaw and roll all went positive, and the last playtest asked for more still:
                // "it should go almost to the off hand and come back". **The lateral travel is very
                // nearly an entire item**, which is what reaches across to the other hand rather than
                // crossing a third of the way. Seventy four of yaw against the thrust's sixty two,
                // and thirty of roll turning the blade over as it goes.
                //
                // The pitch is the "toward the front" and it is the new part: it was fourteen degrees
                // with a comment saying a swept polearm stays level -- true of a polearm being held
                // and not of one being swung.
                //
                // The shape was never wrong. `Channel.SWEEP` is a one to zero decay, so the weapon is
                // at this offset on the first frame and back at the hand on the last, which is
                // literally a swing across the screen until it returns to the hand.
                Track.of(Channel.SWEEP, Curve.LINEAR,
                        new Offset(0.98, -0.08, -0.30, -52.0, 74.0, -30.0)),
                // The thrust, which is a chop like everything else that is not a ropera.
                Track.of(Channel.CHOP, Curve.LINEAR,
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
                Track.of(Channel.CHOP, Curve.LINEAR,
                        new Offset(0.0, -0.06, -0.10, -168.0, 2.0, -4.0)),
                // Comes most of the way back. A warhammer is not a sword and it does not recover
                // like one.
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(0.0, -0.44, 0.09, 126.0, -2.0, 3.0)));
    }

    /**
     * The arcabuz: four states, and the fourth is the one everything else exists for.
     *
     * <p><b>IDLE needed no track.</b> It is the profile's {@code rest}, and the resting pose is by
     * definition the one with nothing happening -- which is the whole answer to "why is there no IDLE
     * animation". An item with a track for being empty is an item that has to be told to be empty.</p>
     *
     * <p><b>LOADED is a channel and not a state machine</b>, so it fits the framework rather than
     * bending it: the {@code HELD} channel is 1 while a firestone is in the gun, and the track on it
     * moves the flintlock down and squares the weapon against the shoulder. One track, and the weapon
     * is either loaded or it is not.</p>
     *
     * <p><b>RELOAD tilts the muzzle down and over, and comes back.</b> Half a second, a bump, and the
     * muzzle goes to <em>down</em> because the player's hand is putting a ball in the pan of a gun,
     * which is down and to the side. It is the only weapon here whose reload is a motion at all.</p>
     *
     * <p><b>SHOOT is the reason the fuse is a ramp and not a bump.</b> A bump is over in a few ticks
     * and a fuse is a second, so a bump would finish before the player saw it begin. The recoil kicks
     * hard and then the weapon settles back over the rest of the burn, and the channel only reaches
     * zero when the server says the shot is over.</p>
     */
    private static ViewModelProfile arcabuz() {
        return ViewModelProfile.heldAs(
                // IDLE: a long gun held across the body, muzzle slightly down and forward.
                new Offset(0.0, -0.06, 0.10, -12.0, 0.0, 0.0),
                true,
                // LOADED: the flintlock comes down onto the pan and the gun squares up.
                Track.of(Channel.HELD, Curve.LINEAR,
                        new Offset(0.0, -0.10, -0.06, 22.0, 0.0, 0.0)),
                // RELOAD: muzzle down and to the side, the hand at the pan, and back in half a
                // second. The only track in this file that moves the weapon sideways, because it
                // is the only moment a long gun is not pointing where it is aimed.
                Track.of(Channel.RELOAD, Curve.LINEAR,
                        new Offset(0.04, -0.30, 0.24, 46.0, 0.0, 0.0)),
                // SHOOT: the recoil. It kicks the weapon up and back and then comes down over the
                // rest of the burn, so the rate of the return IS the fuse and nothing else.
                Track.of(Channel.SHOOT, Curve.LINEAR,
                        new Offset(0.0, 0.14, -0.30, -34.0, 0.0, 0.0)));
    }

    /**
     * The axe: a swing with the whole body behind it, and a tremble worth feeling.
     *
     * <p><b>Its tempo is 0.85, the slowest in the set after the warhammer, and that is the
     * playtest's word.</b> The first pass at 0.9 was called "inusable", and the difference matters:
     * slow is a cost the player chose, and unusable is a weapon that does not do its job. So it
     * gave up a little speed for reach -- 2.6 became 2.9 -- and the cone went from 40 to 35.</p>
     *
     * <p><b>The charge is a bonus of two and it does not change the tempo.</b> A charged swing has
     * to lose on damage per second to tapping, or holding the button is free damage; at 2.0 over
     * sixty ticks the charged hit is eighty six percent of what fast tapping produces, which is a
     * real cost for a real payoff. A bonus of 1.2 over seventy was tried first and it came out
     * forty four percent <em>below</em> tapping, which is not a choice, it is a trap.</p>
     *
     * <p>The wind-up lifts rather than draws back, like the warhammer's, and the tremble is on
     * EXPO so it is a suggestion at half charge and unmistakable at full.</p>
     */
    private static ViewModelProfile axe() {
        return ViewModelProfile.owningSwing(
                Track.of(Channel.WINDUP, Curve.LINEAR,
                        new Offset(-0.03, 0.30, -0.16, 40.0, 0.0, 0.0)),
                Track.shaking(Channel.WINDUP, Curve.LINEAR, Offset.ZERO,
                        new Offset(0.0, 0.0, 0.016, 2.2, 0.0, 0.0),
                        7.0F, Curve.EXPO),
                Track.of(Channel.CHOP, Curve.LINEAR,
                        new Offset(0.0, -0.26, -0.06, -184.0, 0.0, 0.0)),
                Track.of(Channel.RECOVER, Curve.SMOOTH,
                        new Offset(0.0, -0.20, 0.10, 30.0, 0.0, 0.0)));
    }

    /** The viewmodel for a weapon profile, or {@link ViewModelProfile#NONE} if it has none yet. */
    public static ViewModelProfile of(WeaponProfile profile) {
        return TABLE.getOrDefault(profile, ViewModelProfile.NONE);
    }
}
