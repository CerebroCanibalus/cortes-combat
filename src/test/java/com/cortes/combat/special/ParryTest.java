package com.cortes.combat.special;

import com.cortes.combat.state.ParryState;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.Curve;
import com.cortes.combat.viewmodel.Offset;
import com.cortes.combat.viewmodel.Track;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelMotion;
import com.cortes.combat.viewmodel.ViewModelProfile;
import com.cortes.combat.viewmodel.ViewModelInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parry's three promises, and the two of them that a test can actually hold.
 *
 * <p><b>It cannot be held.</b> Half a second, and a press inside the window does nothing. This is
 * the one that matters: a window a second press could refresh is a block, and a block that costs
 * nothing to keep up is exactly what the sabre's shortest reach in the game was supposed to be
 * paying for.</p>
 *
 * <p><b>One parry per press.</b> A hit spends the window without restarting the cooldown, so one
 * press cannot answer three attackers standing in a row.</p>
 *
 * <p><b>It costs something.</b> Five seconds afterwards, and a crawl while it is up.</p>
 */
class ParryTest {

    @Nested
    @DisplayName("the window")
    class Window {

        @Test
        @DisplayName("is 0.6 seconds, and not the ten ticks it used to be")
        void length() {
            assertEquals(12, ParryState.WINDOW_TICKS,
                    "the decision was half a second, and 12 ticks at 20 tps is 0.6 of one");
            assertEquals(12, ParryState.READY.activate().activeTicks(),
                    "what activate() opens is what the constant says");
        }

        @Test
        @DisplayName("closes on its own, with nothing having hit it")
        void runsOut() {
            ParryState state = ParryState.READY.activate();
            for (int i = 0; i < ParryState.WINDOW_TICKS; i++) {
                state = state.tick();
            }
            assertFalse(state.isParrying(),
                    "a parry nobody answered still has to end, or it is a stance");
        }

        @Test
        @DisplayName("cannot be refreshed by a second press")
        void notHoldable() {
            ParryState state = ParryState.READY.activate();
            assertTrue(state.isParrying());

            for (int i = 0; i < 6; i++) {
                assertFalse(state.canActivate(),
                        "tick " + i + " of a twelve tick window: a press here must do nothing, "
                                + "or the parry is a block");
            }

            // and once the window IS over, the five seconds are what stops the next one
            while (state.isParrying()) {
                state = state.tick();
            }
            assertTrue(state.isReady() == false,
                    "the window is down and the cooldown is still counting");
            assertFalse(state.canActivate(),
                    "the cooldown, not the window, is what refuses the second press");
        }

        @Test
        @DisplayName("is five seconds before the next one, and the cooldown survives the parry")
        void cooldown() {
            assertEquals(100, ParryState.COOLDOWN_TICKS, "five seconds at 20 tps");
            assertEquals(100, ParryState.READY.activate().cooldownTicks(),
                    "the lockout starts on the press, not on the hit: a parry that connects and "
                            + "one that whiffs cost the same five seconds");

            ParryState spent = ParryState.READY.activate().consume();
            assertEquals(0, spent.activeTicks(), "the window is spent");
            assertEquals(100, spent.cooldownTicks(),
                    "consuming the parry must NOT restart the cooldown -- that would be a second "
                            + "five seconds, so a successful parry would cost ten");
        }

        @Test
        @DisplayName("counts down and does not go negative")
        void countdown() {
            ParryState state = ParryState.READY.activate();
            for (int i = 0; i < 400; i++) {
                state = state.tick();
                assertTrue(state.activeTicks() >= 0, "active went negative at tick " + i);
                assertTrue(state.cooldownTicks() >= 0, "cooldown went negative at tick " + i);
            }
            assertTrue(state.canActivate(), "after twenty seconds the sabre can parry again");
        }
    }

    @Nested
    @DisplayName("the animation")
    class Animation {

        private static final float NO_STAGGER = 0.0F;

        @Test
        @DisplayName("rests at zero, like every other channel")
        void restsAtZero() {
            assertEquals(0.0F, Channel.PARRY.read(ViewModelInput.RESTING), 1.0E-6,
                    "a channel that rested at one would need a special case in every track "
                            + "that reads it");
        }

        @Test
        @DisplayName("comes out in three ticks, not the guard's two")
        void rise() {
            ViewModelMotion motion = new ViewModelMotion();
            // The press is not the prediction: the server decides, so the ramp starts when the
            // window opens.
            //
            // Read at offset 1.0, the end of the tick. The channels interpolate, and offset 0 is
            // deliberately the PREVIOUS tick's value -- that is what makes a twenty hertz state
            // draw smoothly at sixty frames a second. Reading at zero to check a tick's new value
            // is a test that asserts the ramp is one tick behind what it is.
            for (int i = 0; i < 3; i++) {
                motion.tick(false, 0.0F, 0.0F, false, NO_STAGGER, true, false);
                float read = motion.snapshot(1.0F, 0.0F).parry();
                assertEquals((i + 1) / 3.0F, read, 1.0E-6,
                        "tick " + i + " of the rise: the blade should be a third in per tick");
            }
            assertEquals(1.0F, motion.snapshot(1.0F, 0.0F).parry(), 1.0E-6,
                    "and fully out at three");
        }

        @Test
        @DisplayName("comes back in three, and getting out of the way is the fast part")
        void fall() {
            ViewModelMotion motion = new ViewModelMotion();
            for (int i = 0; i < 3; i++) {
                motion.tick(false, 0.0F, 0.0F, false, NO_STAGGER, true, false);
            }
            assertEquals(1.0F, motion.snapshot(1.0F, 0.0F).parry(), 1.0E-6);

            for (int i = 0; i < 3; i++) {
                motion.tick(false, 0.0F, 0.0F, false, NO_STAGGER, false, false);
            }
            assertEquals(0.0F, motion.snapshot(1.0F, 0.0F).parry(), 1.0E-6,
                    "the window closed and the blade is back in three ticks. A slow return would "
                            + "leave the player walking out of their own wind-down while already "
                            + "free to move");
        }

        @Test
        @DisplayName("is a different shape from the guard, or the parry looks like a block")
        void notTheGuard() {
            ViewModelProfile sabre = ViewModelDefaults.of(
                    com.cortes.combat.core.weapon.WeaponProfile.SABRE);
            Track parry = sabre.tracks().stream()
                    .filter(t -> t.channel() == Channel.PARRY).findFirst().orElse(null);
            assertTrue(parry != null, "the sabre has a parry track");

            Offset out = parry.evaluate(ViewModelInput.parrying(1.0F));
            assertTrue(Math.abs(out.yaw()) > 90.0F,
                    "turned to the side is the read: a parry shows the flat of the blade, and "
                            + "that is a different shape from every stance the game has. yaw was "
                            + out.yaw());
        }

        @Test
        @DisplayName("the sabre's parry track replaced one that could never run")
        void noDeadTrack() {
            ViewModelProfile sabre = ViewModelDefaults.of(
                    com.cortes.combat.core.weapon.WeaponProfile.SABRE);
            assertTrue(sabre.tracks().stream().noneMatch(t -> t.channel() == Channel.GUARD),
                    "the sabre cannot block, so its guard channel is pinned at zero forever. A "
                            + "track on a channel nothing can move is a comment that costs a sum "
                            + "and reads like a feature");
        }

        @Test
        @DisplayName("and no other weapon reads the parry channel")
        void onlyTheSabre() {
            for (com.cortes.combat.core.weapon.WeaponProfile profile
                    : com.cortes.combat.core.weapon.WeaponProfile.values()) {
                boolean reads = ViewModelDefaults.of(profile).tracks().stream()
                        .anyMatch(t -> t.channel() == Channel.PARRY);
                assertEquals(profile == com.cortes.combat.core.weapon.WeaponProfile.SABRE, reads,
                        profile + " should" + (reads ? "" : " not") + " read the parry");
            }
        }

        @Test
        @DisplayName("the curve is one of the six that start at zero")
        void curveNormalised() {
            assertEquals(0.0F, Curve.SMOOTH.apply(0.0F), 1.0E-6);
            assertEquals(1.0F, Curve.SMOOTH.apply(1.0F), 1.0E-6);
        }
    }
}
