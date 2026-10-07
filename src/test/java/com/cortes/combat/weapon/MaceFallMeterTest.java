package com.cortes.combat.weapon;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.sound.ModSounds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The meter the player reads on the way down, and the voice it lands with.
 *
 * <h2>The two halves of the same complaint</h2>
 *
 * <p>The playtest asked for both at once: "faltó la animación de la maza cargando a medida que
 * caes, debe ser una forma visual para que el jugador mida la cantidad de daño", and a smash whose
 * pitch falls as the damage climbs. They are one problem -- <b>the fall was invisible</b> -- with a
 * picture and a sound for an answer, and the shared number between them is
 * {@link MaceFall#progressFor}.</p>
 *
 * <h2>Why the meter is the fall and not the damage</h2>
 *
 * <p>Because it was the damage's ratio first, and the playtest could not see it. The damage is zero
 * until one and a half blocks -- that is where a smash begins -- and an ordinary jump falls about
 * one and a quarter, so the needle sat at rest for the whole of every jump and the report was
 * "no aparece la animación". <b>The meter's question is how much of the fall you have; the
 * threshold is a rule about a hit.</b> Those are two questions and a meter has to answer the first
 * one or it is not a meter.</p>
 */
class MaceFallMeterTest {

    private static final boolean AIR = true;
    private static final boolean GROUND = false;

    @Test
    @DisplayName("the meter is the fall's own ratio: zero at rest, full at the cap")
    void theMeterIsTheFall() {
        assertEquals(0.0F, MaceFall.progressFor(WeaponProfile.MACE, 0.0F, AIR), 1.0E-6F,
                "a maza standing still has no cock");
        assertEquals(1.0F, MaceFall.progressFor(WeaponProfile.MACE, MaceFall.CAP_FALL, AIR),
                1.0E-6F,
                "**full at the cap**, which is the same thirty one blocks the bonus tops out at, "
                        + "so the cock and the damage are full at the same moment");
        assertEquals(1.0F, MaceFall.progressFor(WeaponProfile.MACE, 400.0F, AIR), 1.0E-6F,
                "and it stays full: the damage is capped, so the meter is too");
    }

    @Test
    @DisplayName("and it moves on an ordinary jump, which is the bug this fixes")
    void itMovesOnAJump() {
        // A vanilla jump is about one and a quarter blocks. Under the damage's threshold -- there
        // is no smash at all -- and under the OLD meter, so the playtest never saw it move.
        assertTrue(MaceFall.progressFor(WeaponProfile.MACE, 1.25F, AIR) > 0.0F,
                "**the needle has to move on a normal jump.** This is the whole correction: the "
                        + "damage is zero here and the fall is not, and a meter that reported the "
                        + "damage sat still for every jump in the game");
        assertTrue(MaceFall.progressFor(WeaponProfile.MACE, 1.25F, AIR) < 0.1F,
                "and only just, because one and a quarter blocks out of thirty one is not much");
    }

    @Test
    @DisplayName("it climbs with the fall, never past full")
    void itClimbsWithTheFall() {
        float previous = -1.0F;
        for (float fall = 0.0F; fall <= 60.0F; fall += 0.25F) {
            float ratio = MaceFall.progressFor(WeaponProfile.MACE, fall, AIR);
            assertTrue(ratio >= 0.0F && ratio <= 1.0F,
                    "a meter outside 0..1 is a cock driven past its stop at " + fall + " blocks");
            assertTrue(ratio >= previous - 1.0E-6F,
                    "**it cannot go down as the fall grows.** At " + fall + " blocks it read "
                            + ratio + " after " + previous + ", so the cock would unwind on the way "
                            + "down -- the one direction a fall never goes");
            previous = ratio;
        }
    }

    @Test
    @DisplayName("it is dead on the ground, and dead for every other weapon")
    void nothingElseMoves() {
        assertEquals(0.0F, MaceFall.progressFor(WeaponProfile.MACE, 40.0F, GROUND), 1.0E-6F,
                "**on the ground the cock is at rest however far the player fell.** The fall is "
                        + "over by then, and `airborne` is what says so -- it is also the half that "
                        + "stops swimming from driving it");
        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile == WeaponProfile.MACE) {
                continue;
            }
            assertEquals(0.0F, MaceFall.progressFor(profile, 40.0F, AIR), 1.0E-6F,
                    profile + " reads a fall and must not: the channel is shared, and a sword "
                            + "cocking itself on a fall is a pose nobody asked for");
        }
    }

    @Test
    @DisplayName("the smash's pitch falls as the damage climbs")
    void theSmashGetsLowerAsItGetsHarder() {
        assertEquals(ModSounds.SMASH_PITCH_HIGH, ModSounds.smashPitch(0.0F), 1.0E-6F,
                "a short drop is a sharp anvil");
        assertEquals(ModSounds.SMASH_PITCH_LOW, ModSounds.smashPitch(1.0F), 1.0E-6F,
                "**and a full one is the lowest note the engine will play.** Verified in the "
                        + "1.21.1 sources: SoundEngine clamps to [0.5, 2.0], so anything lower is "
                        + "0.5 and writing it would be a decision that behaves as a constant");

        assertTrue(ModSounds.smashPitch(0.7F) < ModSounds.smashPitch(0.3F),
                "**more damage, lower note.** The direction is the whole readout: if it went the "
                        + "other way the ear would be told the biggest hit was the lightest one");

        assertEquals(ModSounds.SMASH_PITCH_HIGH, ModSounds.smashPitch(-2.0F), 1.0E-6F);
        assertEquals(ModSounds.SMASH_PITCH_LOW, ModSounds.smashPitch(9.0F), 1.0E-6F);
    }

    @Test
    @DisplayName("the maza borrows the hammer's three recordings, far lower")
    void theMazaBorrowsTheHammer() {
        for (ModSounds.Kind kind : new ModSounds.Kind[]{
                ModSounds.Kind.HIT, ModSounds.Kind.MISS, ModSounds.Kind.CHARGED_HIT}) {
            assertEquals(ModSounds.of(WeaponProfile.WARHAMMER, kind),
                    ModSounds.of(WeaponProfile.MACE, kind),
                    "the maza is the hammer's recordings: same shape of impact, and the only art "
                            + "in the jar that is");
            assertEquals(ModSounds.MACE_PITCH, ModSounds.pitch(WeaponProfile.MACE, kind), 1.0E-6F,
                    "**and it has to be pitched down.** `hasOwnArt` is now true for the maza, so "
                            + "the vanilla-charged fallback no longer applies -- without this branch "
                            + "the ladder returns 1.0 and the hammer's own sound plays, which is "
                            + "the borrowing made invisible");
        }
    }
}
