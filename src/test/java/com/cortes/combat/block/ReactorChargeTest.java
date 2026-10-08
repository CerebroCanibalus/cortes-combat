package com.cortes.combat.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nether reactor's numbers, held to their shape.
 *
 * <p><b>Everything the reactor promises is a number, and every one of them is read from
 * {@link ReactorCharge} rather than from a second copy at the call site.</b> The radius is three per
 * firestone, the light reaches full at thirty, the automation ceiling is thirty and the manual one
 * forty, and an hour is seventy-two thousand ticks. If any of those drifts, this test names which
 * promise moved.</p>
 *
 * <p><b>And it is deliberately about the relationships, not the values.</b> "The light at the normal
 * ceiling equals the light ceiling" stays true if the ceiling is retuned to twenty-five; "the light
 * at thirty is fifteen" would not, and would turn a playtest tweak into a broken build for no
 * reason. The constants themselves are the balance knobs and are allowed to move.</p>
 */
class ReactorChargeTest {

    @Test
    @DisplayName("the radius is three blocks per firestone, and nothing else")
    void theRadiusIsThreePerCharge() {
        assertEquals(0, ReactorCharge.radius(0), "an empty reactor reaches nowhere");
        assertEquals(2, ReactorCharge.radius(1), "one firestone and a half, rounded to two blocks");
        assertEquals(45, ReactorCharge.radius(30),
                "thirty charges is the design's forty-five blocks");
        assertEquals((int) Math.round(ReactorCharge.RADIUS_PER_CHARGE * 30),
                ReactorCharge.radius(30),
                "and it is the constant that says so, not a literal that agrees with it today");
    }

    @Test
    @DisplayName("the radius keeps growing into the overload band and then stops")
    void theRadiusClamps() {
        assertTrue(ReactorCharge.radius(40) > ReactorCharge.radius(30),
                "the overload band is still firestone, so it is still radius");
        assertEquals(ReactorCharge.radius(ReactorCharge.MAX_OVERLOAD), ReactorCharge.radius(999),
                "past the ceiling the radius stops: a slot cannot hold more than forty");
        assertEquals(0, ReactorCharge.radius(-5), "a negative charge is no charge, not negative reach");
    }

    @Test
    @DisplayName("the light is zero at zero, full at the normal ceiling, and capped after")
    void theLightRisesToFullAndStops() {
        assertEquals(0, ReactorCharge.lightFor(0), "no firestone, no light");
        assertEquals(ReactorCharge.LIGHT_MAX, ReactorCharge.lightFor(ReactorCharge.MAX_NORMAL),
                "thirty charges is a full fifteen -- the design's top of the curve");
        assertEquals(ReactorCharge.LIGHT_MAX, ReactorCharge.lightFor(ReactorCharge.MAX_OVERLOAD),
                "and the overload band cannot be brighter than vanilla allows");
        assertTrue(ReactorCharge.lightFor(1) > 0, "one firestone is visible, or the curve starts dead");
        assertEquals(2, ReactorCharge.lightFor(2),
                "**one more firestone is one more light level.** The half-step curve answered 1 "
                        + "here, so the second firestone changed nothing on screen and the player "
                        + "reported the light as stuck -- the step is the feature, not rounding");
        assertEquals(ReactorCharge.LIGHT_MAX, ReactorCharge.lightFor(15),
                "fifteen firestones reach the ceiling vanilla allows");
        assertEquals(ReactorCharge.LIGHT_MAX, ReactorCharge.lightFor(16),
                "and past the ceiling the light stops -- vanilla's scale has no sixteen");
    }

    @Test
    @DisplayName("the light never goes down as the charge goes up")
    void theLightIsMonotonic() {
        int previous = -1;
        for (int charge = 0; charge <= ReactorCharge.MAX_OVERLOAD; charge++) {
            int light = ReactorCharge.lightFor(charge);
            assertTrue(light >= previous,
                    "light dropped from " + previous + " to " + light + " at charge " + charge
                            + ". More firestone reading as less light is the one thing the curve "
                            + "cannot do");
            assertTrue(light >= 0 && light <= ReactorCharge.LIGHT_MAX,
                    "light " + light + " is outside vanilla's range");
            previous = light;
        }
    }

    @Test
    @DisplayName("the two ceilings are the two the design named")
    void theCeilingsAreThirtyAndForty() {
        assertEquals(30, ReactorCharge.MAX_NORMAL, "a hopper stops at thirty");
        assertEquals(40, ReactorCharge.MAX_OVERLOAD, "a hand can reach forty");
        assertTrue(ReactorCharge.MAX_OVERLOAD > ReactorCharge.MAX_NORMAL,
                "the overload band only exists because the manual ceiling is above the automatic one");
    }

    @Test
    @DisplayName("an hour is twenty ticks a second for an hour")
    void anHourIsAnHour() {
        assertEquals(72_000, ReactorCharge.HOUR_TICKS);
        assertEquals(20 * 60 * 60, ReactorCharge.HOUR_TICKS,
                "and it is the arithmetic that says so: ticks a second times seconds a minute times "
                        + "minutes an hour");
    }

    @Test
    @DisplayName("the charge runs it, and the signal switches it off")
    void theSignalSwitchesItOff() {
        assertFalse(ReactorCharge.isActive(false, 0), "no firestone is off, signal or not");
        assertTrue(ReactorCharge.isActive(false, 30), "**a full reactor with no signal is ON.** The "
                + "charge is what runs it; reading the signal as a requirement made a reactor that "
                + "did nothing at all until a lever was placed, which reads as broken rather than "
                + "as switched off");
        assertFalse(ReactorCharge.isActive(true, 0), "a signal over an empty reactor is still off");
        assertFalse(ReactorCharge.isActive(true, 1), "**and a signal switches a charged one OFF**");
    }

    @Test
    @DisplayName("charged and overloaded are different questions")
    void chargedVersusOverloaded() {
        assertFalse(ReactorCharge.isCharged(0));
        assertTrue(ReactorCharge.isCharged(1));
        assertFalse(ReactorCharge.isOverloaded(30), "thirty is the normal maximum, not an overload");
        assertTrue(ReactorCharge.isOverloaded(31), "the overload band starts one past the hopper cap");
        assertTrue(ReactorCharge.isOverloaded(40));
    }

    @Test
    @DisplayName("the reactor presents full redstone strength")
    void theSignalIsFullStrength() {
        assertEquals(15, ReactorCharge.SIGNAL_STRENGTH,
                "a reactor is not a dimmer; the wire's own attenuation makes the gradient");
    }
}
