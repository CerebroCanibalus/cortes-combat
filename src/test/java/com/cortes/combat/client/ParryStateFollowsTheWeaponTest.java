package com.cortes.combat.client;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelMotion;
import com.cortes.combat.viewmodel.ViewModelProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parry's channels are global; the state that drives them is the player's, not the item's.
 *
 * <p>That mismatch is the whole of the playtest report: parry with the sabre, pick up a sword,
 * and the sword plays the sabre's lockout for the five seconds it had left. The lockout rides
 * {@code BREAK}, and the sword reads {@code BREAK} for its guard -- so the sword collapsed for
 * five seconds and the player read it as a guard stuck down and switched weapons twice more.</p>
 *
 * <p>The fix is a gate on "does this profile have anything to do with the parry", and these are
 * the tests that hold it there.</p>
 */
class ParryStateFollowsTheWeaponTest {

    private static final int LOCKOUT = 100;

    private static ViewModelProfile profile(WeaponProfile p) {
        return ViewModelDefaults.of(p);
    }

    @Test
    @DisplayName("only the sabre reads the parry, and it is the gate")
    void theGateIsTheParryTrack() {
        for (WeaponProfile p : WeaponProfile.values()) {
            boolean reads = profile(p).reads(Channel.PARRY);
            assertEquals(p == WeaponProfile.SABRE, reads,
                    p + " reads the parry channel: " + reads
                            + ". This is the gate, so getting it wrong here is the bug");
        }
    }

    @Test
    @DisplayName("a weapon that does not read the parry never had a reason to")
    void theOthersHaveNoTrack() {
        // The report was a leak, not a missing feature: the sword never wanted this. If a track
        // appears here the gate is doing nothing and the fix is cosmetic.
        assertFalse(profile(WeaponProfile.SWORD).reads(Channel.PARRY),
                "the sword has no parry motion, and the gate is what keeps the sabre's out of it");
        assertTrue(profile(WeaponProfile.SWORD).reads(Channel.BREAK),
                "and it does read BREAK, which is exactly why the leak was visible");
    }

    @Test
    @DisplayName("switching weapons half way through the lockout stops the motion at once")
    void switchingCutsTheLockout() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.observeParry(true, LOCKOUT);
        for (int i = 0; i < 20; i++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, true, false);
        }
        assertEquals(1.0F, motion.guardBreak(), 1.0E-6, "the sabre is off line, mid lockout");

        // The player switched to a sword. The sabre's lockout is not the sword's business, and a
        // zero cooldown would not stop a bump already running -- it only stops the next one.
        motion.clearParry();
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);

        assertEquals(0.0F, motion.guardBreak(), 1.0E-6,
                "the sword is on line on the same tick. It was drawing a collapse for another "
                        + (LOCKOUT - 20) + " ticks, which is what the player reported");
    }

    @Test
    @DisplayName("and the sabre's own lockout still works after a round trip through another weapon")
    void theSabreSurvivesTheRoundTrip() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.observeParry(true, LOCKOUT);
        for (int i = 0; i < 10; i++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, true, false);
        }
        motion.clearParry();
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        assertEquals(0.0F, motion.guardBreak(), 1.0E-6);

        // Back to the sabre, and the lockout runs its full length again.
        motion.observeParry(true, LOCKOUT);
        int ticks = 0;
        // Ticked once before the loop, because on the tick the window opens the bump is at age 0
        // and the rise is still at zero -- which is the snap working, not a lockout that never
        // started.
        motion.tick(false, 0.0F, 0.0F, false, 0.0F, true, false);
        // Counted from the press, so the tick above is inside the total and not next to it.
        // Ninety-nine was this test's own off-by-one, not the motion's.
        ticks = 1;
        while (motion.guardBreak() > 0.0F && ticks < LOCKOUT * 2) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, true, false);
            ticks++;
        }
        assertEquals(LOCKOUT, ticks,
                "clearing it was not a state change that leaked into the next parry");
    }

    @Test
    @DisplayName("a motion that has never parried rests at zero, and the guard does too")
    void everyChannelRestsAtZero() {
        // "Todos los canales descansan en cero" is the invariant the whole sum-of-tracks design
        // rests on, and the ramp was breaking it: a motion that had never been asked to parry
        // computed `1 - 1/3` on its first tick, so the sabre started life two thirds out of line.
        // The guard's ramp had the same missing branch.
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 20; i++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
            var frame = motion.snapshot(1.0F, 0.0F);
            assertEquals(0.0F, frame.parry(), 1.0E-6, "parry rested at " + frame.parry()
                    + " on tick " + i);
            assertEquals(0.0F, frame.guard(), 1.0E-6, "guard rested at " + frame.guard()
                    + " on tick " + i + " -- a weapon drawn off line for the first ticks of every "
                    + "session is a weapon flying in when you join a world");
            assertEquals(0.0F, frame.guardBreak(), 1.0E-6, "break rested at " + frame.guardBreak());
        }
    }
}
