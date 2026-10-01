package com.cortes.combat.sound;

import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which sound each weapon makes, as names and numbers, with no Minecraft in it.
 *
 * <p>Pure for the same reason {@code ConeMath} is: this is a table, and a table that can only be
 * checked by launching a client is a table that will be wrong. Every question answerable here --
 * does the dagger have a sound, does anything resolve to nothing, is the shotel really louder --
 * is answerable in a bare JVM.</p>
 *
 * <p><b>The pools are not here.</b> They live in {@code sounds.json}, where three files under one
 * name become a random pick, which is the game's own mechanism and needs no code. This class only
 * ever names an event; the list behind the name is data.</p>
 *
 * <p>Three of the seven weapons with art disagree with their enum on the filename, the same three
 * the slash sprites disagreed on. Generating names from the profile id would have been wrong
 * three times in this file and three more in the particle one.</p>
 */
public final class ModSounds {

    /** What happened, so one weapon can answer for several moments. */
    public enum Kind {
        /** The swing connected. */
        HIT,
        /** The swing connected at full charge, which is vanilla's crit and nothing of ours. */
        CHARGED_HIT,
        /** The swing found nothing. */
        MISS,
        /** The button was held past the charge threshold. */
        CHARGE,
        /** The guard broke. */
        GUARD_BROKEN,
        LUNGE,

        /** A right button special went off. */
        SPECIAL,
        /**
         * The sabre's parry connected.
         *
         * <p>Its own kind and not a reuse of {@link #SPECIAL}, even though both are right button
         * specials, because they are the two halves of the same press at different moments: one
         * is the blade going out and the other is the blade having done something. Reusing one
         * would have meant the parry sounding like a halberd sweep.</p>
         */
        PARRY,
        /** The charge went all the way up. Not used yet: no sound is bound to it. */
        CHARGE_FULL
    }

    private static final Map<WeaponProfile, String> HIT = new EnumMap<>(WeaponProfile.class);
    private static final Map<WeaponProfile, String> MISS = new EnumMap<>(WeaponProfile.class);
    private static final Map<WeaponProfile, String> CHARGED_HIT = new EnumMap<>(WeaponProfile.class);

    /** The pitch the shotel adds to everything it borrows from the sword. */
    public static final float SHOTEL_PITCH = 1.2F;

    /** The lunge plays far outside the range everything else does. */
    public static final float LUNGE_PITCH = 2.0F;

    /**
     * How much lower the vanilla charged hit is played for a weapon with no art of its own.
     *
     * <p>Pickaxe, shovel and bare hands borrow vanilla's own swing and only the charged one is
     * changed, by dropping it an octave-ish rather than adding a second set of recordings. Pitch is
     * a playback rate, so 0.8 is a fifth below where it was written and lands as weight without
     * turning into a growl.</p>
     */
    public static final float VANILLA_CHARGED_PITCH = 0.8F;

    /** Vanilla's own swing. Borrowed outright by the weapons with no recordings. */
    public static final String VANILLA_HIT = "minecraft:entity.player.attack.weak";
    public static final String VANILLA_CHARGED_HIT = "minecraft:entity.player.attack.strong";

    /** Every weapon that loads a charge. One file, for all of them. */
    public static final String CHARGE = "cortescombat:charge_generic";

    static {
        HIT.put(WeaponProfile.SWORD, "cortescombat:sword_hit");
        MISS.put(WeaponProfile.SWORD, "cortescombat:sword_miss");
        CHARGED_HIT.put(WeaponProfile.SWORD, "cortescombat:sword_charge_hit");

        HIT.put(WeaponProfile.AXE, "cortescombat:axe_hit");
        MISS.put(WeaponProfile.AXE, "cortescombat:axe_miss");
        CHARGED_HIT.put(WeaponProfile.AXE, "cortescombat:axe_charge_hit");

        HIT.put(WeaponProfile.DAGGER, "cortescombat:daga_hit");
        MISS.put(WeaponProfile.DAGGER, "cortescombat:daga_miss");

        HIT.put(WeaponProfile.ROPERA, "cortescombat:ropera_hit");
        MISS.put(WeaponProfile.ROPERA, "cortescombat:ropera_miss");

        HIT.put(WeaponProfile.HALBERD, "cortescombat:alabarda_hit");
        MISS.put(WeaponProfile.HALBERD, "cortescombat:alabarda_miss");
        CHARGED_HIT.put(WeaponProfile.HALBERD, "cortescombat:alabarda_charged_hit");

        HIT.put(WeaponProfile.WARHAMMER, "cortescombat:martillo_hit");
        MISS.put(WeaponProfile.WARHAMMER, "cortescombat:martillo_miss");
        CHARGED_HIT.put(WeaponProfile.WARHAMMER, "cortescombat:martillo_charged_hit");

        // The shotel borrows the sword's recordings and is played back sharper. The charge is
        // deliberately NOT sharpened: it is the same file for every weapon in the game, and a
        // shared cue that changes for one of them stops being shared.
        HIT.put(WeaponProfile.SABRE, "cortescombat:sword_hit");
        MISS.put(WeaponProfile.SABRE, "cortescombat:sword_miss");
        CHARGED_HIT.put(WeaponProfile.SABRE, "cortescombat:sword_charge_hit");
    }

    private ModSounds() {
    }

    /**
     * The event for a moment, or a vanilla one when the weapon has no art of its own.
     *
     * <p>Never null and never empty. A sound that resolves to nothing is silence, and silence is
     * the one failure a playtest cannot report because nothing looks wrong.</p>
     */
    public static String of(WeaponProfile profile, Kind kind) {
        if (profile == null) {
            profile = WeaponProfile.UNARMED;
        }
        return switch (kind) {
            case HIT -> HIT.getOrDefault(profile, VANILLA_HIT);
            case CHARGED_HIT -> CHARGED_HIT.getOrDefault(profile, VANILLA_CHARGED_HIT);
            case MISS -> MISS.getOrDefault(profile, VANILLA_HIT);
            case CHARGE -> CHARGE;
            case GUARD_BROKEN -> "cortescombat:sword_guardia_rota";
            case SPECIAL -> "cortescombat:alabarda_wide_swing";
            case LUNGE -> "cortescombat:alabarda_wide_swing";
            case PARRY -> "cortescombat:shotel_parry";
            case CHARGE_FULL -> CHARGE;
        };
    }

    /**
     * The pitch to play a weapon's sound at.
     *
     * <p>Two rules and no others. The shotel is sharper on the three it borrows, and everything it
     * does not borrow -- the shared charge cue and its own parry -- plays as written. And the
     * vanilla charged hit is dropped a fifth for the weapons borrowing vanilla, because that one
     * is the same recording for all of them and the pitch is the only thing telling them apart.</p>
     */
    public static float pitch(WeaponProfile profile, Kind kind) {
        WeaponProfile resolved = profile == null ? WeaponProfile.UNARMED : profile;
        if (resolved == WeaponProfile.SABRE
                && (kind == Kind.HIT || kind == Kind.MISS || kind == Kind.CHARGED_HIT)) {
            return SHOTEL_PITCH;
        }
        if (kind == Kind.LUNGE) {
            return LUNGE_PITCH;
        }
        if (kind == Kind.CHARGED_HIT
                && !CHARGED_HIT.containsKey(resolved)) {
            return VANILLA_CHARGED_PITCH;
        }
        return 1.0F;
    }

    /** Whether a weapon has recordings of its own, as opposed to borrowing vanilla's. */
    public static boolean hasOwnArt(WeaponProfile profile, Kind kind) {
        WeaponProfile resolved = profile == null ? WeaponProfile.UNARMED : profile;
        return switch (kind) {
            case HIT -> HIT.containsKey(resolved);
            case MISS -> MISS.containsKey(resolved);
            case CHARGED_HIT -> CHARGED_HIT.containsKey(resolved);
            default -> true;
        };
    }
}