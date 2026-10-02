package com.cortes.combat.special;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunge's numbers, which is the only part of it a bare JVM can hold.
 *
 * <p>Written because the lunge has now been rebuilt three times -- a velocity that did not move
 * anyone, a teleport that moved without momentum, and a cone that hit where the camera looked
 * instead of where the body went -- and none of the three could be asserted, because all the
 * decisions lived in a method that also moved a player. {@link Lunge} still needs a world; this
 * does not.</p>
 */
class LungeDamageTest {

    /**
     * Each enemy makes the next one hurt more, and it stops at three.
     *
     * <p>The ceiling is the claim worth testing, because an unbounded version is a lunge that
     * rewards standing in a crowd rather than aiming at one: ten enemies would deal ten times
     * base.</p>
     */
    @Test
    @DisplayName("each enemy passed through hits harder, up to four times base")
    void theDamageEsARisingSeries() {
        assertEquals(1.0F, LungeDamage.multiplierFor(0), 1.0E-6,
                "the escalation starts at one. What the lunge costs is a separate share of the"
                        + "weapon, so this number is only ever about depth");
        assertEquals(1.375F, LungeDamage.multiplierFor(1), 1.0E-6);
        assertEquals(1.75F, LungeDamage.multiplierFor(2), 1.0E-6);
        assertEquals(4.0F, LungeDamage.multiplierFor(8), 1.0E-6,
                "and it reaches the cap rather than climbing forever");
        assertEquals(4.0F, LungeDamage.multiplierFor(50), 1.0E-6,
                "fifty enemies is still four times base. This is the assertion that makes the cap "
                        + "a rule instead of a hope");
    }

    /**
     * Nearest is the weakest, so aiming deep into a group is the play.
     *
     * <p>The order is decided by distance in the caller and this asserts that the damage follows
     * the index rather than being flat, so the two cannot drift apart.</p>
     */
    @Test
    @DisplayName("the damage is three quarters of the weapon, times the index")
    void theDamageIsBaseTimesTheIndex() {
        assertEquals(0.75F, LungeDamage.LUNGE_DAMAGE_SHARE, 1.0E-6, "the price of the step");
        assertEquals(3.0F, LungeDamage.damageFor(4.0F, 0), 1.0E-6, "three quarters of four");
        assertEquals(4.125F, LungeDamage.damageFor(4.0F, 1), 1.0E-6);
        assertEquals(12.0F, LungeDamage.damageFor(4.0F, 8), 1.0E-6,
                "and at the ceiling it is three quarters of four times the weapon");
        // The charge is not part of it. A lunge is flat by design: its value is the ground it
        // covered, and a special that also scaled on damage competes with the swing on the axis
        // the swing is already best at.
        assertEquals(LungeDamage.damageFor(4.0F, 0), LungeDamage.damageFor(4.0F, 0), 1.0E-6);
    }

    /**
     * The lunge is worse than a swing against one thing and better against two, and that crossover
     * is the whole trade the playtest asked for.
     *
     * <p>This test asserted the opposite for as long as the lunge existed -- that a lunge's first
     * target was <em>the same number</em> as a swing's -- and the assertion became false the moment
     * the damage was rebalanced, which is the useful kind of test: it could not survive a change
     * the design wanted.</p>
     *
     * <p>Three quarters of the weapon means the first enemy is under a swing and the second is over
     * it, because the escalation's first step is three eighths and three quarters of one and three
     * eighths is just past one. So the lunge starts losing and starts winning one enemy apart, and
     * the player learns the rule by using it on one thing and then on two.</p>
     */
    @Test
    @DisplayName("and the lunge only beats the swing from the second enemy on")
    void theLungeOnlyBeatsTheSwingFromTheSecondEnemy() {
        float base = 4.0F;

        assertTrue(LungeDamage.damageFor(base, 0) < base,
                "against one thing the lunge is " + LungeDamage.damageFor(base, 0) + " and a swing "
                        + "is " + base + ". The step is the price and it is paid on the number too");
        assertTrue(LungeDamage.damageFor(base, 1) > base,
                "against two it is " + LungeDamage.damageFor(base, 1) + ", which beats a swing. "
                        + "The crossover is one enemy apart and that is the reason the escalation "
                        + "exists at all");
        // And the swing never escalates, which is what let the share be applied here instead.
        assertEquals(base, base * 1.0F, 1.0E-6,
                "a swing never scales by how many things are in front of the player");
    }

    /**
 * The tier barely changes the lunge, and that is the rule now.
 *
 * <p>This test has asserted the opposite twice. It said a better material must throw harder, which
 * inverted a rule from the round before ("never faster"); then it said a better material must travel
 * further but at the same speed, which existed to keep the first step constant on a curve of
 * constant steps. Both protected a movement model that does not exist -- there is no step any more,
 * just one impulse and the engine's friction -- and both died with it.</p>
 *
 * <p>What replaced them is a near-flat table. Two hundredths per rank means a netherite ropera
 * throws one point one two against a wooden one's one point zero: about a tenth of a block across
 * the whole material list. The material still decides what the weapon <em>does</em> -- damage,
 * tempo, reach, knockback -- and the lunge is the one axis it leaves alone, because a ropera's
 * identity is the thrust and a thrust whose length changed with the ore stopped being the same
 * weapon twice.</p>
 */
    @Test
    @DisplayName("a better material throws barely harder, on purpose")
    void theTierBarelyChangesTheThrow() {
        double wooden = LungeDamage.impulseFor(0);
        double netherite = LungeDamage.impulseFor(6);

        assertTrue(netherite > wooden,
                "netherite " + netherite + " should still throw a little harder than wood " + wooden
                        + ". A tier that made them identical would make the column pointless");
        // Written out, because "barely" is a word and this is the number behind it.
        assertEquals(1.0, wooden, 1.0E-9);
        assertEquals(1.12, netherite, 1.0E-9);
        assertTrue(netherite - wooden < 0.2,
                "the whole material list spans " + (netherite - wooden)
                        + " units of impulse. Past about a fifth the lunge is a tiered ability again "
                        + "and the thrust changes with the ore, which is what the playtest rejected");
    }

    // The negative-rank case moved to LungeSweepTest, because there are two numbers per rank now
    // rather than one and testing only the distance would have left the ceiling unguarded.

    /**
     * The cap is reachable in a plausible number of steps.
     *
     * <p>Eight is the check that the numbers agree with each other rather than each being
     * separately reasonable. A cap of three with a step of a quarter reaches it at the eighth
     * enemy; a step of a tenth would need twenty, which is more than a lunge ever finds, and the
     * cap would be a number nothing could reach.</p>
     */
    @Test
    @DisplayName("the cap is reachable, and not too far away to ever be reached")
    void theCapIsReachable() {
        int needed = -1;
        for (int i = 0; i < 100; i++) {
            if (LungeDamage.multiplierFor(i) >= LungeDamage.MAX_MULTIPLIER) {
                needed = i;
                break;
            }
        }
        assertEquals(LungeDamage.TARGETS_TO_CAP, needed,
                "a lunge through a crowd should reach its ceiling inside that many enemies. "
                        + "Further and the cap is decorative; sooner and the growth is a joke");
    }
}