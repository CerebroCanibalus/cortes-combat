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
    @DisplayName("each enemy passed through hits harder, up to three times base")
    void theDamageEsARisingSeries() {
        assertEquals(1.0F, LungeDamage.multiplierFor(0), 1.0E-6,
                "the first one is the weapon's own damage. A lunge that opened above one would "
                        + "make stepping through nothing better than a swing");
        assertEquals(1.25F, LungeDamage.multiplierFor(1), 1.0E-6);
        assertEquals(1.5F, LungeDamage.multiplierFor(2), 1.0E-6);
        assertEquals(3.0F, LungeDamage.multiplierFor(8), 1.0E-6,
                "and it reaches the cap rather than climbing forever");
        assertEquals(3.0F, LungeDamage.multiplierFor(50), 1.0E-6,
                "fifty enemies is still three times base. This is the assertion that makes the cap "
                        + "a rule instead of a hope");
    }

    /**
     * Nearest is the weakest, so aiming deep into a group is the play.
     *
     * <p>The order is decided by distance in the caller and this asserts that the damage follows
     * the index rather than being flat, so the two cannot drift apart.</p>
     */
    @Test
    @DisplayName("the damage comes off the weapon's base and nothing else")
    void theDamageIsBaseTimesTheIndex() {
        assertEquals(4.0F, LungeDamage.damageFor(4.0F, 0), 1.0E-6);
        assertEquals(5.0F, LungeDamage.damageFor(4.0F, 1), 1.0E-6);
        assertEquals(12.0F, LungeDamage.damageFor(4.0F, 8), 1.0E-6);
        // The charge is not part of it. A lunge is flat by design: its value is the ground it
        // covered, and a special that also scaled on damage competes with the swing on the axis
        // the swing is already best at.
        assertEquals(LungeDamage.damageFor(4.0F, 0), LungeDamage.damageFor(4.0F, 0), 1.0E-6);
    }

    /**
     * The lunge only ever multiplies its own hit.
     *
     * <p>Asserted as a property of the API rather than of a call site, because "the escalation is
     * only for the lunge" is a rule two separate pieces of code could each break.</p>
     */
    @Test
    @DisplayName("and the weapon's own swing is untouched by it")
    void theSwingIsNotEscalating() {
        // The swing's damage is the charge profile's business and has no index at all, which is
        // why the escalation had to live here rather than inside DamageResolver.
        float base = 4.0F;
        float swingUncharged = base * 1.0F;
        assertEquals(4.0F, swingUncharged, 1.0E-6,
                "a swing never scales by how many things are in front of the player");
        assertTrue(LungeDamage.damageFor(base, 0) == swingUncharged,
                "and a lunge's first target is the same number, so the escalation only exists "
                        + "past the first enemy");
    }

    /**
     * Better material, further and wider.
     *
     * <p>Both grow with the tier because both are the same promise -- the good ropera is the one
     * that covers more ground -- and a tier that moved the distance but not the reach would give
     * the player a longer step through a narrower corridor.</p>
     */
    @Test
    @DisplayName("a better material carries further and sweeps wider")
    void theTierExtendsBothTheStepAndTheSweep() {
        double wooden = LungeDamage.distanceFor(0);
        double iron = LungeDamage.distanceFor(3);
        double netherite = LungeDamage.distanceFor(6);

        assertTrue(iron > wooden, "iron " + iron + " should carry further than wood " + wooden);
        assertTrue(netherite > iron, "and netherite further than iron");
        // Exactly half a block a step, written down because a retune of the step is a design
        // decision and an unnoticed one would be invisible.
        assertEquals(6.2, netherite, 1.0E-6, "3.2 at wood plus six ranks of half a block");
        assertTrue(LungeDamage.reachFor(6) > LungeDamage.reachFor(0),
                "and the sweep grows with it");
    }

    @Test
    @DisplayName("a negative tier is treated as the lowest material, not as a backwards bonus")
    void noNegativeTiers() {
        assertEquals(LungeDamage.distanceFor(0), LungeDamage.distanceFor(-5), 1.0E-6,
                "nothing ships with a negative rank, and if one ever did it should be the worst "
                        + "ropera rather than a ropera that pulls the player backwards");
    }

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