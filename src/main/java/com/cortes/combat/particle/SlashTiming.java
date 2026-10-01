package com.cortes.combat.particle;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;

/**
 * When a slash is drawn, as numbers, with nothing from Minecraft in sight.
 *
 * <h2>Why the timing is not on the particle's options</h2>
 *
 * <p>{@link SlashParticleOptions} implements {@code ParticleOptions}, so a bare JVM cannot load it
 * and none of its arithmetic is testable: the test that touched it died at
 * {@code error: cannot access ParticleOptions}, which is the game being absent rather than a
 * number being wrong.</p>
 *
 * <p>So the decisions live here and the options record only carries them across the wire. That is
 * the split this project has already learned to insist on -- the arithmetic of a widget in
 * {@code ConeGauge} rather than in the layer that draws it -- and it is the only reason the gap
 * between the two cuts is a decision that can be argued about instead of a constant nobody
 * questions.</p>
 */
public final class SlashTiming {

    /**
     * The gap between the two cuts of a doubled strike, in ticks.
     *
     * <p>Two, and that number is a decision rather than a default. One tick is under a fifth of a
     * second and the two cuts overlap into one bright shape -- which is the bug: a player who can
     * tell the damage doubled and cannot see why. Three is far enough that the eye reads two
     * separate events, and the pair stops looking like one committed strike. Two is the only value
     * that says "twice" while still saying "now".</p>
     */
    /**
     * The gap between the two cuts, in ticks.
     *
     * <p><b>Four, and it was two for two rounds.</b> Two ticks is under a tenth of a second, and
     * the slash lives for seven: the two cuts overlapped for five of them, drawing the same sprite
     * at the same place, which on screen is one slightly brighter slash. The playtest read that as
     * a single hit and was right -- the log said {@code aterrizaron=2} while the screen showed one.
     *
     * <p>Four leaves them overlapping for three ticks, which is enough to read as one weapon and one
     * strike twice rather than as one strike drawn thicker. The cost is that the pair takes a
     * quarter of a second instead of a tenth, which is inside the swing's own tempo.</p>
     */
    /**
 * The gap between the two cuts, in ticks.
 *
 * <p><b>Sixteen, and it is past the length of the slash.</b> The gap was two, then four, and both
 * times the answer was that it did not matter -- because the second cut was never being drawn at
 * all, its sprite being nulled at spawn and never restored. The playtest could not hear or see it
 * and the log said both hits landed.</p>
 *
 * <p>With the sprite fixed, the gap can finally be judged as a gap. Sixteen ticks is more than
 * twice the strip's life, so the first cut is gone before the second begins and there is no overlap
 * left to read as a single thicker slash. Four ticks was always going to be the wrong answer: any
 * overlap at all turns two cuts of the same sprite into one shape, and the shorter the strip the
 * more of it they share.</p>
 *
 * <p>It is a long time for a swing's second beat -- four fifths of a second -- and that is the
 * honest cost of making it unmistakable. The damage lands on the first one; the second is the one
 * the player is waiting for, and waiting for it is what makes it read.</p>
 */
    public static final int DOUBLE_GAP_TICKS = 8;

    /**
     * How far to the side the second cut is drawn, in blocks.
     *
     * <p><b>Half a block, and it is what makes the pair read as two.</b> Two of the same sprite at
     * the same place are one shape drawn twice, however far apart in time: the eye reads the
     * silhouette and the silhouette is identical. Moving it sideways makes the second cut a
     * different shape on screen, which is a cue that does not depend on the player watching at the
     * exact moment the second one fires.</p>
     *
     * <p>Half a block rather than a whole one: the slash is about one and a half across, so a whole
     * block apart puts the second cut outside the enemy it hit, and half puts the pair across the
     * body of the thing that was cut.</p>
     */
    public static final double SECOND_CUT_OFFSET = 0.5;

    /**
     * Where the second cut of a doubled swing is drawn, as an offset in x and z.
     *
     * <p><b>Perpendicular to the swing, and to one side.</b> Pure, because the arithmetic behind "the
     * two cuts are on top of each other" has to be checkable rather than something the playtest has
     * to describe.</p>
     *
     * <p>Perpendicular rather than along the swing, because a cut drawn further down the same line is
     * indistinguishable from the same cut drawn twice: the player has no frame of reference for where
     * the blade already went. Across it, the pair reads as two.</p>
     *
     * @param direction the unit direction the swing was aimed, in x and z
     */
    public static double[] secondCutOffset(double[] direction) {
        double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]);
        if (length < 1.0E-6) {
            return new double[]{0.0, 0.0};
        }
        double x = -direction[1] / length;
        double z = direction[0] / length;
        return new double[]{x * SECOND_CUT_OFFSET, z * SECOND_CUT_OFFSET};
    }

    private SlashTiming() {
    }

    /**
     * How long the strip runs, unchanged.
     *
     * <p>Kept here so the lifetime arithmetic in {@link #totalTicks} has no reference to the
     * particle, which is the only reason that arithmetic is a test.</p>
     */
    public static final int STRIP_TICKS = 7;

    /** A slash with no wait: the ordinary hit. */
    public static final int NO_DELAY = 0;

    /**
     * How long the whole particle lives, wait included.
     *
     * <p><b>The wait is added, not subtracted.</b> Measuring a delayed slash's animation against its
     * full lifetime would start it a third of the way through the strip, so the second cut would
     * appear pre-faded and cut out -- which reads as a second, <em>weaker</em> hit. That is worse
     * than showing one hit, because it tells the player the weapon has a light and a heavy swing
     * rather than that it swung twice.</p>
     */
    public static int totalTicks(int delayTicks) {
        return STRIP_TICKS + delayTicks(delayTicks);
    }

    /** The delay, never negative. */
    public static int delayTicks(int requested) {
        return Math.max(NO_DELAY, requested);
    }

    /**
     * Whether this weapon's full charge is a second hit rather than merely a heavier one.
     *
     * <p><b>Asked by the weapon, and it has to be.</b> The first version of this asked whether the
     * charge bonus was large enough, and the playtest's own numbers broke it: the sword and the
     * trident carry the sabre's bonus exactly, so a threshold on the number would have handed the
     * doubling to three weapons. A bonus is a damage number and two weapons sharing one is
     * unremarkable; "lands two hits" is an <em>identity</em> and exactly one weapon has it.</p>
     *
     * <p>This is the same lesson the parry channel produced, from the other direction. There the
     * gate could not be "is this the sabre" because the sabre might get a second item; here it is
     * the opposite problem, where the number is genuinely shared and so genuinely cannot carry the
     * question.</p>
     */
    public static boolean chargesToDouble(WeaponProfile profile, ChargeProfile charge) {
        return profile == WeaponProfile.SABRE && charge.canCharge();
    }

    /**
     * How many hits this swing lands at that charge.
     *
     * <p><b>The sabre lands two, and that is the whole of what "carga da doble" means.</b> It was
     * implemented as one hit with double damage, which is arithmetically the same total and
     * completely different to play: the playtest read the sabre as "pega doble al cargar", counted
     * two charged swings to kill a zombie, and asked why it was not giving both.</p>
     *
     * <p>The two are not the same event. One heavy hit is one number on one target; two hits are
     * two chances to connect, two knockbacks and two impacts that can be watched. And the damage
     * is not the same either, because each of the two carries the charge multiplier: a full charge
     * is <em>four</em> times base rather than two, which is the difference between a charged swing
     * killing a zombie in one and killing it in two.</p>
     *
     * <p>Requires the <em>full</em> charge and not merely a held button. The earlier version asked
     * only whether the charge was above zero, so a swing that was five ticks into a twenty tick
     * window drew two cuts -- the promise before the player had earned it, which is the same
     * mistake the charge threshold exists to prevent everywhere else.</p>
     *
     * <p>Pure, so the rule that the damage and the picture both read is a test rather than an
     * agreement between two call sites.</p>
     */
    public static int hitsAt(WeaponProfile profile, ChargeProfile charge, float chargeRatio) {
        if (!chargesToDouble(profile, charge) || chargeRatio < FULL_CHARGE) {
            return 1;
        }
        return 2;
    }

    /**
     * The charge ratio at which a swing counts as fully charged.
     *
     * <p>{@code 1.0F} and not a hair under it. The damage path clamps the ratio to one and the
     * charge bar fills to one, so "full" has exactly one spelling, and a threshold near but not at
     * the top would make the last tick of a hold a different attack from the one before it.</p>
     */
    public static final float FULL_CHARGE = 1.0F;
}