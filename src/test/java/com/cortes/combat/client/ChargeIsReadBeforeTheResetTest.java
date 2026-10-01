package com.cortes.combat.client;

import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.viewmodel.Offset;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelInput;
import com.cortes.combat.viewmodel.ViewModelMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a held charge is still a charge by the time anyone reads it.
 *
 * <p>This is the one that cost four rounds of playtesting. {@code SwingInputHandler.release} set
 * {@code holding = false} and then called {@code chargedRatio}, which opens with
 * {@code if (!holding) return 0}. Every release therefore classified itself as a tap, forever, and
 * one inverted pair of lines did all of this at once:</p>
 *
 * <ul>
 *   <li>{@code onStrike(0)} scaled every viewmodel channel by zero, so the cut, the weight and
 *       the wind-up unwind were all multiplied by nothing;</li>
 *   <li>{@code charge <= 0} sent every swing down {@code player.swing}, so the <b>vanilla</b>
 *       animation played and the viewmodel never did;</li>
 *   <li>the charge bar, the FOV and the wind-up all read the same zero.</li>
 * </ul>
 *
 * <p>The server kept its own tick count, so the damage bonus landed correctly. That is what made
 * it so confusing to watch: the sword hurt, and the client showed nothing, and every number in
 * the profile looked reasonable.</p>
 *
 * <p>It cannot be unit tested where it lives, because the bug is an ordering of two statements
 * inside a method that talks to {@code Minecraft}. What is testable, and what this pins, is the
 * property the ordering exists to protect: a charge read while the button is still down is a
 * charge, and it produces a viewmodel frame that moves.</p>
 */
class ChargeIsReadBeforeTheResetTest {

    private static final int FULL_CHARGE_TICKS = 25;

    /** The client's own rule, verbatim in behaviour: zero below the threshold, then a ramp. */
    private static float classify(int heldTicks) {
        return AttackTiming.chargeRatioFor(heldTicks, FULL_CHARGE_TICKS);
    }

    @Test
    @DisplayName("a full hold classifies as a charge, which is the whole point of holding")
    void aFullHoldIsACharge() {
        float charge = classify(FULL_CHARGE_TICKS);
        assertEquals(1.0F, charge, 1.0E-6F,
                "a full hold must classify as a full charge, was " + charge);
    }

    @Test
    @DisplayName("a charged swing carries further than a tap does")
    void theClassificationReachesTheViewmodel() {
        // The chain that was severed: classification -> onStrike -> the channels -> an offset.
        // When the classification was always zero this produced an identity frame, and the
        // profile could not tell that apart from a weapon with no profile at all.
        //
        // The PEAK over the whole follow-through, and not a single frame. The first frame after
        // the release is still unwinding the wind-up, and the wind-up scales with the charge too,
        // so a one-frame reading measures the wind-up and can even come out inverted: this test
        // reported a tap travelling further than a full charge, which is the measurement being
        // wrong rather than the motion.
        double charged = deepestPitch(FULL_CHARGE_TICKS);
        double tapped = deepestPitch(0);

        assertTrue(charged < -60.0,
                "a full charge has to move the sword, and it moved " + charged
                        + ": the classification never reached the viewmodel");
        assertTrue(charged < tapped,
                "and a full charge has to carry further than a tap: " + charged
                        + " against " + tapped);
    }

    /** Charges for the given number of ticks, releases, and returns the deepest pitch. */
    private static double deepestPitch(int heldTicks) {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, 1.0F, false, false);
        }
        motion.onStrike(classify(heldTicks), ViewModelMotion.STRIKE_TICKS);
        double deepest = 0.0;
        for (int tick = 0; tick <= motion.strikeTicks(); tick++) {
            motion.tick(false, 0.0F, 0.0F, false, 1.0F, false, false);
            for (int sub = 0; sub < 4; sub++) {
                deepest = Math.min(deepest, ViewModelDefaults.of(WeaponProfile.SWORD)
                        .evaluate(motion.snapshot(sub / 4.0F, 0.0F)).pitch());
            }
        }
        return deepest;
    }

    @Test
    @DisplayName("reading a charge after the button is up gives zero, which is the bug")
    void readingAfterTheResetGivesZero() {
        // Stated as an executable claim rather than a comment, because the comment is what the
        // bug contradicted. `chargedRatio` is `if (!holding) return 0`, so a read taken after
        // `holding = false` is zero no matter how long the button was down, and that is exactly
        // what the playtest log showed: `release: carga=0.0` against a server that reported
        // `ticks=40`.
        boolean holding = true;
        int ticksHeld = FULL_CHARGE_TICKS;
        float readWhileHeld = holding ? classify(ticksHeld) : 0.0F;
        holding = false;
        float readAfterReset = holding ? classify(ticksHeld) : 0.0F;

        assertEquals(1.0F, readWhileHeld, 1.0E-6F, "reading while held is a charge");
        assertEquals(0.0F, readAfterReset, 0.0F,
                "and reading after the reset is a tap, whatever the button did. This is the "
                        + "ordering that release must not get wrong.");
    }

    @Test
    @DisplayName("a client that has never heard from the server is allowed to try")
    void anUnsyncedClientIsNotLockedOut() {
        // The nineteen seconds. A press the client refuses is swallowed rather than handed to
        // vanilla, so a pessimistic default on a replicated value is not a wrong answer, it is a
        // player who cannot play. The server is the authority either way.
        assertEquals(1.0F, ClientCombatState.cooldownRatio(), 0.0F,
                "the default must be ready, because that is the safe direction to be wrong in");
        assertTrue(!ClientCombatState.cooldownKnown(),
                "and the client must know it has not heard back yet, so it does not treat its "
                        + "own default as the server's opinion");
    }

    @Test
    @DisplayName("a resting frame is still the resting frame after all of this")
    void restIsStillRest() {
        assertTrue(ViewModelDefaults.of(WeaponProfile.SWORD).evaluate(ViewModelInput.RESTING)
                .isZero(), "nothing happening must still move nothing");
    }
}
