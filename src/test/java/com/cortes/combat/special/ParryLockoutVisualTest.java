package com.cortes.combat.special;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parry lockout reuses the guard's collapse, and the reuse is where it could break.
 *
 * <p>The idea is that the player already knows this motion -- it is what a shield does when it
 * gets knocked down -- and the sabre saying the same thing for a different reason needs no new
 * vocabulary. The length carries the time: the blade drops off line on the press and comes back
 * as the five seconds run out, which makes the weapon returning the recharge indicator.</p>
 *
 * <p>What can go wrong is the <b>edge</b>, and it is the same edge {@code observeStagger} got
 * wrong once. A bump driven off a descending counter restarts on every tick of that counter, and a
 * hundred tick bump held at its first hundredth is a weapon parked off line with no way back.</p>
 */
class ParryLockoutVisualTest {

    private static final int COOLDOWN = 100;

    private static ViewModelMotion pressed() {
        ViewModelMotion motion = new ViewModelMotion();
        // What the server sends on the press: the window opens and the full lockout starts.
        motion.observeParry(true, COOLDOWN);
        return motion;
    }

    private static void advance(ViewModelMotion motion, int ticks) {
        for (int i = 0; i < ticks; i++) {
            motion.tick(false, 0.0F, 0.0F, false, 0.0F, false, false);
        }
    }

    @Test
    @DisplayName("the weapon leaves the line on the press, in the guard's two ticks")
    void startsOnThePress() {
        ViewModelMotion motion = pressed();
        advance(motion, ViewModelMotion.GUARD_RISE_TICKS);
        assertEquals(1.0F, motion.guardBreak(), 1.0E-6,
                "the departure is the guard's, not a stretched collapse. sin(sqrt(f)*PI/2) over a "
                        + "hundred ticks would take fifty of them to get here, and a blade that "
                        + "drifts off line over half a second is being carried, not knocked aside");
    }

    @Test
    @DisplayName("and it is off line the whole lockout, which is the whole readout")
    void holdsThroughTheLockout() {
        ViewModelMotion motion = pressed();
        advance(motion, 30);
        float at30 = motion.guardBreak();
        advance(motion, 30);
        float at60 = motion.guardBreak();

        assertEquals(1.0F, at30, 1.0E-6, "halfway through the lockout the blade is fully out: " + at30);
        assertEquals(1.0F, at60, 1.0E-6, "and still fully out later. A seven tick collapse "
                + "stretched to a hundred would have finished at tick seven and the player would "
                + "have had no idea the sabre was still locked");
    }

    @Test
    @DisplayName("and it comes back over the guard's fall, on the tick the lockout ends")
    void endsWithTheLockout() {
        ViewModelMotion motion = pressed();
        advance(motion, COOLDOWN - ViewModelMotion.GUARD_FALL_TICKS);
        assertEquals(1.0F, motion.guardBreak(), 1.0E-6,
                "the return starts a fall before the end, which is what makes it readable as a "
                        + "countdown rather than as a switch");

        advance(motion, ViewModelMotion.GUARD_FALL_TICKS / 2);
        float halfway = motion.guardBreak();
        assertTrue(halfway > 0.0F && halfway < 1.0F,
                "and it is part way back halfway through the fall: " + halfway);

        advance(motion, ViewModelMotion.GUARD_FALL_TICKS);
        assertEquals(0.0F, motion.guardBreak(), 1.0E-6,
                "and on the tick the lockout runs out the weapon is back on line, which is the "
                        + "player learning they can parry again without reading a number");
    }

    @Test
    @DisplayName("a repeated correction from the server does not restart it")
    void doesNotRestartOnARepeatedValue() {
        ViewModelMotion motion = pressed();
        advance(motion, 10);
        // The window closes and the server sends what is left. This is the value the client was
        // already counting down through, so it is a correction and not a new press.
        motion.observeParry(false, COOLDOWN - 10);
        advance(motion, 1);

        assertEquals(1.0F, motion.guardBreak(), 1.0E-6,
                "the bump was ten ticks in and a correction must not have sent it back to zero. "
                        + "was " + motion.guardBreak());
    }

    @Test
    @DisplayName("a parry that CONNECTS does not make the lockout longer")
    void aHitDoesNotExtendIt() {
        ViewModelMotion motion = pressed();
        advance(motion, 5);
        // This is what a parry that lands sends: the window is closed and the cooldown is still
        // its full length, because consuming the parry does not touch the lockout. A version that
        // read "the number went up" as a fresh press restarted the bump HERE, and the weapon came
        // back a hundred ticks after the parry landed instead of ninety-five.
        motion.observeParry(false, COOLDOWN);

        // The invariant, measured rather than computed: how long the blade stays off line, from
        // the press, is the number the server sent. Not the press plus the hit, and not a
        // hand-written sum of the rise and the fall that a retune would silently break.
        //
        // Seeded with the five ticks already spent, because the bump started at the press and
        // counting only the tail would report ninety-five and look like a number worth writing
        // down. Ninety-five was the bug this test exists for.
        int ticks = 5;
        while (motion.guardBreak() > 0.0F && ticks < COOLDOWN * 2) {
            advance(motion, 1);
            ticks++;
        }
        assertEquals(COOLDOWN, ticks,
                "the lockout is five seconds from the PRESS. A parry that lands in the first "
                        + "half second must not buy the player a second one");
    }

    @Test
    @DisplayName("and a press with nothing hitting it lasts exactly the same")
    void aWhiffLastsTheSame() {
        ViewModelMotion motion = pressed();
        advance(motion, 3);
        motion.observeParry(false, COOLDOWN - 3);

        int ticks = 3;
        while (motion.guardBreak() > 0.0F && ticks < COOLDOWN * 2) {
            advance(motion, 1);
            ticks++;
        }
        assertEquals(COOLDOWN, ticks,
                "a parry that connects and one that whiffs cost the same five seconds, which is "
                        + "the whole reason the lockout starts on the press");
    }

    @Test
    @DisplayName("and a genuinely new press restarts it")
    void restartsOnANewPress() {
        ViewModelMotion motion = pressed();
        advance(motion, 40);
        // Five seconds later the sabre can parry again, so the server's answer is the full length
        // once more. That is higher than anything the client has counted, so it is a new press.
        motion.observeParry(true, COOLDOWN);
        advance(motion, ViewModelMotion.GUARD_RISE_TICKS);

        assertEquals(1.0F, motion.guardBreak(), 1.0E-6,
                "a fresh parry drops the blade off line again from the top. was "
                        + motion.guardBreak());
    }

    @Test
    @DisplayName("a zero cooldown never starts anything")
    void zeroIsNotAnEdge() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.observeParry(false, 0);
        advance(motion, 1);
        assertEquals(0.0F, motion.guardBreak(), 1.0E-6);
    }

    @Test
    @DisplayName("the sabre reads the same collapse the guard does")
    void theSabreHasTheTrack() {
        assertTrue(ViewModelDefaults.of(WeaponProfile.SABRE).tracks().stream()
                        .anyMatch(t -> t.channel() == Channel.BREAK),
                "without a BREAK track the sabre would have a lockout signal and no lockout "
                        + "motion: the component is in place and its consumer is missing, which "
                        + "is the failure the viewmodel had once with ropera() and dagger()");
    }

    @Test
    @DisplayName("and the sword's collapse and the sabre's are the same numbers")
    void sameShapeAsTheSword() {
        var sword = ViewModelDefaults.of(WeaponProfile.SWORD).tracks().stream()
                .filter(t -> t.channel() == Channel.BREAK).findFirst().orElseThrow();
        var sabre = ViewModelDefaults.of(WeaponProfile.SABRE).tracks().stream()
                .filter(t -> t.channel() == Channel.BREAK).findFirst().orElseThrow();

        assertEquals(sword.amplitude(), sabre.amplitude(),
                "the same motion with different numbers is a second thing to learn, and a player "
                        + "holding a sabre and a shield should not have to work out that the two "
                        + "unavailable motions are the same unavailable motion");
    }
}
