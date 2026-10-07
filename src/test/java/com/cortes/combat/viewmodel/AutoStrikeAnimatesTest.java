package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the automatic strike animates, which was the missing half of the held button.
 *
 * <h2>The bug this exists for</h2>
 *
 * <p>{@code autoStrikeWhileHeld} sends a strike from the tick, while the button is <b>still
 * down</b>, and {@code onStrike} announces it by setting {@code pendingStrike}. The tick's
 * {@code if (holding)} branch then cleared {@code pendingStrike}, {@code strikeAge},
 * {@code released}, {@code chopAge} and {@code classified} on <em>every held tick</em> -- so the
 * one-shot was erased on the very next frame and a weapon that is held down produced no motion at
 * all while held.</p>
 *
 * <p>Every weapon that can be held -- the sword, the axe, the hoe, the shovel, the arcabuz -- was
 * affected. It was reported as the arcabuz losing its bayonet, because that is the one whose whole
 * left button is a single stab and whose motion was therefore the one a player would miss.</p>
 *
 * <p>The fix is the press <em>edge</em>: the reset belongs to the frame the button goes down and
 * not to every frame it stays down. A release and an automatic strike both go through
 * {@code onStrike}, and both play now.</p>
 */
class AutoStrikeAnimatesTest {

    private static void tick(ViewModelMotion motion, boolean holding) {
        motion.tick(holding, 0.0F, 0.0F, false, 0.0F, false, false);
    }

    /**
     * Press, hold a few ticks, then an automatic strike -- and the motion has to run.
     *
     * <p>Two ticks after the strike, because {@code strike()} is a rise: it reads zero on the
     * frame the strike starts and climbs after, which is the opposite of {@code chop()}. The
     * assertion is "the age advanced at all", and before the fix it never did.</p>
     */
    @Test
    @DisplayName("a strike sent while the button is still down still plays")
    void theAutoStrikePlays() {
        ViewModelMotion motion = new ViewModelMotion();

        tick(motion, true);
        for (int i = 0; i < 4; i++) {
            tick(motion, true);
        }
        assertEquals(0.0F, motion.strike(), 1.0E-6F, "nothing has struck yet");

        // Exactly what SwingInputHandler's automatic strike does: onStrike, from the tick, with
        // the button still down.
        motion.onStrike(0.0F, 6);
        tick(motion, true);
        tick(motion, true);

        assertTrue(motion.strike() > 0.0F,
                "**the automatic strike has to animate.** The old `if (holding)` branch cleared "
                        + "`pendingStrike` on every held tick, so the strike was erased one frame "
                        + "after it was announced and the whole held-button motion was dead. The "
                        + "arcabuz reported it as 'lost its stab animation', and it was the same "
                        + "nothing for the sword, the axe, the hoe and the shovel");
    }

    /** The same for the chop, which is the motion the sword and the axe actually use. */
    @Test
    @DisplayName("and the chop of a held weapon plays too")
    void theAutoChopPlays() {
        ViewModelMotion motion = new ViewModelMotion();
        tick(motion, true);
        tick(motion, true);

        motion.onStrike(0.0F, 6);
        tick(motion, true);

        assertTrue(motion.chop() > 0.0F,
                "the sword's and the axe's motion is the CHOP -- a fall from one -- and the same "
                        + "branch erased it");
    }

    /**
     * And a NEW press still abandons the strike that was running.
     *
     * <p>The edge exists for this, and moving the reset onto it is what keeps that while fixing
     * the auto-strike at once. Without it a fast second click inherits the first one's charge.</p>
     */
    @Test
    @DisplayName("a new press still abandons the strike that was running")
    void aNewPressAbandons() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.onStrike(1.0F, 6);
        tick(motion, false);              // the release announces it
        tick(motion, false);              // and it is running
        assertTrue(motion.strike() > 0.0F, "the strike is running");

        tick(motion, true);               // a new press, which is the edge
        assertEquals(0.0F, motion.strike(), 1.0E-6F,
                "**a new press abandons what the last one was doing**, or a fast second click "
                        + "inherits the first one's charge and the wind-up jumps to a value the "
                        + "player never charged to");
    }
}
