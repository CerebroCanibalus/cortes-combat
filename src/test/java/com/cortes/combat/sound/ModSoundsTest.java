package com.cortes.combat.sound;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every weapon answers every moment with a real sound, and that the shotel is the shotel.
 *
 * <p>Silence is the failure mode here. A sound that resolves to nothing produces no error, no
 * warning and nothing on screen, and the only way a playtest finds it is by saying "the halberd
 * sounds like nothing", which is a slow way to find a missing line in a table.</p>
 */
class ModSoundsTest {

    @Test
    @DisplayName("every profile resolves every kind to something")
    void nothingIsSilent() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            for (ModSounds.Kind kind : ModSounds.Kind.values()) {
                String name = ModSounds.of(profile, kind);
                assertFalse(name == null || name.isBlank(),
                        profile + " has no " + kind + " sound");
                assertTrue(name.contains(":"),
                        profile + " / " + kind + " is not a namespaced id: " + name);
            }
        }
        assertEquals(ModSounds.of(WeaponProfile.UNARMED, ModSounds.Kind.HIT),
                ModSounds.of(null, ModSounds.Kind.HIT),
                "no profile is a bare hand and has to borrow vanilla like one");
    }

    @Test
    @DisplayName("the weapons that own a charged recording play it as written")
    void chargedHitsAreReplacedNotStacked() {
        // "Replaces", not "on top of". A charged hit that played both the recording and the
        // vanilla one would be the same slash twice at two pitches, which is not a bigger sound,
        // it is a broken one.
        //
        // The sabre is deliberately NOT in this loop even though it has a charged entry, because
        // its entry borrows the sword's recording and is played sharper. An earlier version of
        // this test listed it and asserted a pitch of 1.0, which contradicted the test below that
        // asserts 1.2 -- and the code was right both times. Having an entry is not owning a file.
        for (WeaponProfile profile : EnumSet.of(WeaponProfile.SWORD, WeaponProfile.AXE,
                WeaponProfile.HALBERD, WeaponProfile.WARHAMMER)) {
            assertTrue(ModSounds.of(profile, ModSounds.Kind.CHARGED_HIT)
                            .startsWith("cortescombat:"),
                    profile + " has a charged recording and should be using it");
            assertEquals(1.0F, ModSounds.pitch(profile, ModSounds.Kind.CHARGED_HIT),
                    profile + " plays its own charged recording as written");
        }

        // And the sabre's is the sword's, at the sword's own name.
        assertEquals(ModSounds.of(WeaponProfile.SWORD, ModSounds.Kind.CHARGED_HIT),
                ModSounds.of(WeaponProfile.SABRE, ModSounds.Kind.CHARGED_HIT),
                "the sabre points at the sword's recording rather than owning one");
        assertEquals(ModSounds.SHOTEL_PITCH,
                ModSounds.pitch(WeaponProfile.SABRE, ModSounds.Kind.CHARGED_HIT),
                "and plays it sharper, which is the whole of the shotel's voice");
    }

    @Test
    @DisplayName("the ropera and the dagger have no charge at all, so they have no charged hit")
    void theTwoWithoutAChargeHaveNoChargedRecording() {
        // They are the two profiles whose ChargeProfile is NONE. A charged hit for a weapon that
        // cannot charge would be a sound for an event that never happens.
        for (WeaponProfile profile : EnumSet.of(WeaponProfile.ROPERA, WeaponProfile.DAGGER)) {
            assertFalse(ModSounds.hasOwnArt(profile, ModSounds.Kind.CHARGED_HIT),
                    profile + " cannot charge, so it should not have a charged recording");
        }
    }

    @Test
    @DisplayName("borrowing vanilla is one shared recording, dropped a fifth when charged")
    void theVanillaBorrow() {
        for (WeaponProfile profile : EnumSet.of(WeaponProfile.UNARMED, WeaponProfile.PICKAXE,
                WeaponProfile.SHOVEL, WeaponProfile.MACE, WeaponProfile.TRIDENT, WeaponProfile.NONE)) {
            assertEquals(ModSounds.VANILLA_HIT, ModSounds.of(profile, ModSounds.Kind.HIT),
                    profile + " borrows vanilla for the ordinary swing");
            assertEquals(ModSounds.VANILLA_CHARGED_HIT,
                    ModSounds.of(profile, ModSounds.Kind.CHARGED_HIT),
                    profile + " borrows vanilla for the charged one too");
            assertEquals(ModSounds.VANILLA_CHARGED_PITCH,
                    ModSounds.pitch(profile, ModSounds.Kind.CHARGED_HIT),
                    "and the pitch is the only thing telling two borrowings apart");
            assertEquals(1.0F, ModSounds.pitch(profile, ModSounds.Kind.HIT),
                    "the ordinary swing is played as written");
        }
    }

    @Test
    @DisplayName("the shotel is the sword, sharper, except where it has its own")
    void theShotelIsTheSwordSharper() {
        // Three kinds are sharpened and the rest are not. The charge cue is shared by every weapon
        // in the game, and a shared cue that changes for one of them stops being shared; the parry
        // is the shotel's own recording.
        for (ModSounds.Kind kind : EnumSet.of(ModSounds.Kind.HIT, ModSounds.Kind.MISS,
                ModSounds.Kind.CHARGED_HIT)) {
            assertEquals(ModSounds.of(WeaponProfile.SWORD, kind),
                    ModSounds.of(WeaponProfile.SABRE, kind),
                    "the shotel borrows the sword's " + kind);
            assertEquals(ModSounds.SHOTEL_PITCH, ModSounds.pitch(WeaponProfile.SABRE, kind),
                    "and plays it at " + ModSounds.SHOTEL_PITCH);
        }
        assertEquals(1.0F, ModSounds.pitch(WeaponProfile.SABRE, ModSounds.Kind.CHARGE),
                "the shared charge cue is not sharpened for one weapon");
        assertEquals(1.0F, ModSounds.pitch(WeaponProfile.SWORD, ModSounds.Kind.CHARGE),
                "and it is the same cue for everybody");
        assertEquals(ModSounds.CHARGE, ModSounds.of(WeaponProfile.SABRE, ModSounds.Kind.CHARGE));
    }

    @Test
    @DisplayName("every weapon loads the same charge cue")
    void oneChargeCueForTheWholeGame() {
        // All ten loading profiles share one file. A per-weapon charge cue would be ten files for
        // a sound that is supposed to mean one thing.
        Set<String> seen = new HashSet<>();
        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile == WeaponProfile.ROPERA || profile == WeaponProfile.DAGGER
                    || profile == WeaponProfile.NONE) {
                continue;
            }
            seen.add(ModSounds.of(profile, ModSounds.Kind.CHARGE));
        }
        assertEquals(1, seen.size(), "more than one charge cue is in play: " + seen);
    }
}