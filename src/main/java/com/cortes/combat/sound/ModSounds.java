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
        CHARGE_FULL,
        /**
         * The arcabuz's blast, and the only event in the game that is twenty ticks late.
         *
         * <p>Its own kind because it has no analogue on any other weapon, not because borrowing
         * one would be wrong: a shot does not sound like a slash, and reusing {@link #SPECIAL}
         * would have made every blast sound like a halberd sweep.</p>
         *
         * <p><b>Vanilla's own recording, by name.</b> This mod ships thirty five files and none
         * of them is a gunshot, and inventing one to fill the gap would have produced a silence
         * no test can see. {@code SoundPoolTest} checks both directions -- a name in
         * {@code sounds.json} with no file behind it is a line in the log, and a file nothing
         * names is dead weight in the jar -- and neither of them notices a sound that was never
         * declared at all. So the arcabuz borrows, and the borrowing is written down here rather
         * than left to whoever reads {@link #of}.</p>
         */
        /**
         * The fuse was struck. Vanilla's creeper hiss is the closest thing the game has to a match
         * lighting, and at one and a half it is a fuse rather than a gunshot.
         */
        FUSE_LIT,
        /** The reload started: the press, before any of the fifty ticks. */
        RELOAD_START,
        /** The reload finished and the firestone was paid for. */
        LOADED,
        FUSEE
    }

    private static final Map<WeaponProfile, String> HIT = new EnumMap<>(WeaponProfile.class);
    private static final Map<WeaponProfile, String> MISS = new EnumMap<>(WeaponProfile.class);
    private static final Map<WeaponProfile, String> CHARGED_HIT = new EnumMap<>(WeaponProfile.class);

    /** The arcabuz's fuse: a vanilla event, named as a string and played as an event. */
    private static final String FUSE_LIT_SOUND = "minecraft:entity.creeper.primed";

    public static final float SHOTEL_PITCH = 1.2F;

    /**
     * The hoe's sweep: the halberd's event, lower.
     *
     * <p><b>The event is shared and the pitch is the whole difference.</b> The hoe's right button
     * is the halberd's {@code MULTI_HIT} (see {@code WeaponTables}), so it throws the same
     * {@code alabarda_wide_swing}; dropping it makes a farm tool's arc read as its own thing
     * instead of as a halberd that happens to be in the hand. 0.7 is a fourth below where it was
     * written and well inside the engine's [0.5, 2.0] clamp, so it is a pitch and not a limit.</p>
     */
    public static final float HOE_SWEEP_PITCH = 0.7F;

    /**
     * The pala's miss, which is the sword's recording dropped.
     *
     * <p>A shovel has no recordings of its own, and its miss used to borrow vanilla's weak hit at
     * 1.0 -- the same sound as every other unrecorded weapon. Dropped a fifth it is recognisably
     * the pala's: the same swing-finds-nothing cue, at the weight of the tool doing it.</p>
     */
    public static final float SHOVEL_MISS_PITCH = 0.8F;

    /**
     * The hoe's miss, the same recording raised instead.
     *
     * <p><b>And not the pala's number.</b> Two tools sharing a recording have to be told apart by
     * their pitch or they are one weapon with two reach values, which is exactly the kind of
     * second-opinion the tables exist to avoid.</p>
     */
    public static final float HOE_MISS_PITCH = 1.2F;

    /** The maza's core, under its hammer recording on every ordinary hit. */
    public static final float MACE_CORE_PITCH = 1.0F;

    /**
     * And on the air smash, dropped.
     *
     * <p>The smash already plays the hammer lower (MACE_PITCH) and the anvil by fall; the core at
     * 0.6 is what makes the biggest hit in the game also the deepest, and it moves the same
     * direction as the damage the way every weight cue in this file does.</p>
     */
    public static final float MACE_CORE_SMASH_PITCH = 0.6F;

    /** The pala's lantern-breaking impact, under the borrowed vanilla swing. */
    public static final float SHOVEL_HIT_PITCH = 0.8F;

    /** And the wind burst that layers onto a <em>charged</em> pala hit, lower still. */
    public static final float SHOVEL_CHARGED_PITCH = 0.6F;

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

    /**
     * The blast, and it is vanilla's {@code entity.blaze.shoot}.
     *
     * <p><b>Why that one and not {@code entity.generic.explode}.</b> The explosion is already
     * spoken for: vanilla's own blasts, the charge's look, and a dozen other things, so the
     * player has heard it and will file it under "a creeper went off" rather than under "my
     * gun went off". The blaze's shot is a short, sharp, broadband crack with a fast tail, which
     * is the shape a musket report actually is, and it is a sound nothing in ordinary play is
     * standing next to.</p>
     *
     * <p>Written out rather than left to a fallback so that {@code ModSounds#of} has one arm per
     * kind with no "and otherwise vanilla" branch hiding a decision in it.</p>
     */
    public static final String ARCABUZ_SHOT = "cortescombat:arcabuz_shot";

    /**
     * El disparo de Rey de las Moscas, que tiene su propia grabacion.
     *
     * <p>La dejo el usuario en {@code arcabuz_shot_mosca.ogg} y <b>no la nombraba ningun evento</b>,
     * asi que no sonaba nunca -- y encima la rama de las moscas hace {@code return} antes del
     * disparo normal, con lo cual el tiro mas caro del juego salia <b>mudo</b>. El archivo sin
     * evento no da error en el log: es peso muerto en el jar y silencio en el juego.</p>
     */
    public static final String ARCABUZ_SHOT_MOSCA = "cortescombat:arcabuz_shot_mosca";

    /**
     * El zumbido del enjambre de Rey de las Moscas.
     *
     * <p>Segundo archivo que el usuario dejo sin evento detras ({@code arcabuz_mosca_loop.ogg}).
     * Suena sobre el cuerpo donde cae la nube y no en la boca del arcabuz: el disparo ya tiene el
     * suyo y esto es el enjambre, que vive en el objetivo.</p>
     */
    public static final String ARCABUZ_MOSCA_LOOP = "cortescombat:arcabuz_mosca_loop";

    /**
     * How far down the blaze's shot is played for the arcabuz.
     *
     * <p><b>0.55, and it is a multiplier on the pitch rather than an absolute.</b> The vanilla
     * recording is a fireball cracking past; dropped to roughly a fifth below where it was
     * written it loses the whoosh and keeps the thump, which is the part a gun is made of. Pitch
     * is a playback rate, so this is also a little shorter, and a report that is a fifth shorter
     * reads as closer.</p>
     *
     * <p>Not sharpened and not dropped by an octave like the charged borrows: those two changes
     * exist to tell two recordings of the <em>same</em> weapon apart, and this is the only weapon
     * that plays it.</p>
     */
    public static final float ARCABUZ_SHOT_PITCH = 0.55F;

    /**
     * The fuse lighting, as a multiple of the creeper hiss it borrows.
     *
     * <p>Half a step up, and the only pitch here that is chosen against the recording rather than
     * against another sound. {@code entity.creeper.primed} is a hiss with a long tail and every
     * creeper on the map plays it, so a player who hears one has no way to tell an ignition from a
     * creeper winding up. Raising it shortens the tail, which is what makes the two read as
     * different events instead of the same one in a different place.</p>
     *
     * <p>It was silently 1.0 because the pitch ladder had no branch for {@link Kind#FUSE_LIT} and
     * returns its default past the end. A {@code Kind} that is declared, registered in
     * {@code sounds.json} and played is not thereby given a pitch; the ladder is a second list of
     * the same kinds and it has to be told separately.</p>
     */
    public static final float ARCABUZ_FUSE_PITCH = 1.5F;

    /**
     * How low the maza plays the warhammer's recordings.
     *
     * <p><b>0.5, and it is the floor of the engine's own clamp.</b> Verified in the 1.21.1 sources:
     * {@code SoundEngine} clamps a played pitch to {@code [0.5, 2.0]}, so anything below half is
     * half -- writing 0.35 would be a number that reads as a decision and behaves as a constant.
     * An octave down is also all the pitch can say before it stops being the same impact dropped
     * and starts being a different recording.</p>
     */
    public static final float MACE_PITCH = 0.5F;

    /**
     * The maza's air smash, and it is vanilla's anvil landing.
     *
     * <p><b>Not one of ours, and for the reason the warhammer's streak is a note block.</b> The
     * fall is the maza's whole argument and it had no voice of its own: the hit was the hammer's
     * recording at half pitch, identical at two blocks and at eight, while the damage doubled.
     * {@code block.anvil.land} is a boom with a metal ring inside it, it is a sound every player
     * has heard a hundred times, and it is the loudest heavy thing in the vanilla library.</p>
     *
     * <p>Written as a name here and played by {@code CombatSounds#playMaceSmash}, which is
     * <b>deliberately not a {@link Kind}</b>: this sound is pitched by how far the player fell, and
     * the pitch ladder in {@link #pitch} answers only "which weapon". A {@code Kind} for it would
     * go into that ladder, find no branch, and come out at 1.0 -- the failure this file already
     * documents twice.</p>
     */
    public static final String SMASH = "minecraft:block.anvil.land";

    /** Where a smash's pitch starts: a short drop is a sharp anvil. */
    public static final float SMASH_PITCH_HIGH = 1.4F;

    /** And where a full drop lands: the same anvil an octave and a half down. The engine's floor. */
    public static final float SMASH_PITCH_LOW = 0.5F;

    /**
     * The pitch a smash is played at, which falls as the fall grows.
     *
     * <p><b>The ladder is the mechanic's own readout.</b> "The higher you were, the more it hurts"
     * is invisible in a hit that looks and sounds the same at every height, so the pitch is what
     * the ear uses to tell a two block drop from an eight block one -- and it moves in the same
     * direction as the damage, because a lower note is what weight sounds like.</p>
     *
     * @param fallRatio how much of the fall's bonus was earned, 0..1
     */
    public static float smashPitch(float fallRatio) {
        float ratio = Math.max(0.0F, Math.min(1.0F, fallRatio));
        return SMASH_PITCH_HIGH + (SMASH_PITCH_LOW - SMASH_PITCH_HIGH) * ratio;
    }

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

        // The maza borrows the warhammer's recordings and is played back far lower -- the same
        // borrowing the sabre does off the sword, and for the same reason: it is the same shape of
        // impact, a head on a haft, and the hammer's art is the only one in the jar that is.
        HIT.put(WeaponProfile.MACE, "cortescombat:martillo_hit");
        MISS.put(WeaponProfile.MACE, "cortescombat:martillo_miss");
        CHARGED_HIT.put(WeaponProfile.MACE, "cortescombat:martillo_charged_hit");

        // The shotel borrows the sword's recordings and is played back sharper. The charge is
        // deliberately NOT sharpened: it is the same file for every weapon in the game, and a
        // shared cue that changes for one of them stops being shared.
        HIT.put(WeaponProfile.SABRE, "cortescombat:sword_hit");
        MISS.put(WeaponProfile.SABRE, "cortescombat:sword_miss");
        CHARGED_HIT.put(WeaponProfile.SABRE, "cortescombat:sword_charge_hit");

        // **The two tools borrow the sword's miss, at their own pitches.** Neither the pala nor the
        // hoe has a recording, so before this their miss was vanilla's weak hit at 1.0 -- the same
        // event as every other unrecorded weapon. Borrowing the sword's miss is not a third sound
        // to ship; it is the one "the swing found nothing" cue the jar already has, and the pitch
        // is what makes it theirs (see {@link #pitch}).
        MISS.put(WeaponProfile.SHOVEL, "cortescombat:sword_miss");
        MISS.put(WeaponProfile.HOE, "cortescombat:sword_miss");
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
            case FUSEE -> ARCABUZ_SHOT;
        // **The three sounds the playtest asked for, by name.** A gunshot of our own now exists,
        // so FUSEE stops borrowing the blaze and names it. RELOAD_START is the press and LOADED is
        // the tick the firestone is paid for: two different moments of the same two and a half
        // seconds, which is why they are two kinds rather than one sound played twice.
        case RELOAD_START -> "cortescombat:arcabuz_reload";
        case LOADED -> "cortescombat:arcabuz_loaded";
        case FUSE_LIT -> FUSE_LIT_SOUND;
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
        // The maza borrows the hammer's three recordings, so it needs a branch here for the same
        // reason the shotel does: `hasOwnArt` is now true for it, which means the vanilla-charged
        // fallback below no longer applies and the default would have played them at 1.0 -- the
        // hammer's own sound, and the borrowing would have been invisible.
        if (resolved == WeaponProfile.MACE
                && (kind == Kind.HIT || kind == Kind.MISS || kind == Kind.CHARGED_HIT)) {
            return MACE_PITCH;
        }
        // The pala's and the hoe's borrowed miss, each at its own pitch so the two tools do not
        // sound like one weapon with two reach values. See the constants for why they move in
        // opposite directions off 1.0.
        if (resolved == WeaponProfile.SHOVEL && kind == Kind.MISS) {
            return SHOVEL_MISS_PITCH;
        }
        if (resolved == WeaponProfile.HOE && kind == Kind.MISS) {
            return HOE_MISS_PITCH;
        }
        // **The hoe's sweep, and it is the halberd's event at a lower pitch.** The two share the
        // special (`MULTI_HIT`), so they share the event; the pitch is the only thing telling a
        // farm tool's arc from a polearm's without shipping a second recording.
        if (resolved == WeaponProfile.HOE && kind == Kind.SPECIAL) {
            return HOE_SWEEP_PITCH;
        }
        // The arcabuz is the one weapon whose blast is a vanilla recording nobody else plays, so
        // it is the only one whose blast is dropped. Same reasoning as VANILLA_CHARGED_PITCH,
        // inverted: there the pitch was the only thing telling two borrowings apart, and here
        // there is nothing to tell apart.
        if (kind == Kind.FUSEE) {
            return ARCABUZ_SHOT_PITCH;
        }
        // **The fuse, and the reason it is the one pitch in this file that is about the recording
        // rather than about telling two borrows apart.**
        //
        // Every other branch here earns its number by separating two sounds a player might confuse.
        // This one separates a sound from its own family: `entity.creeper.primed` is a hiss with a
        // long tail, and every creeper on the map plays it, so the arcabuz lighting its fuse is
        // indistinguishable from a creeper about to go off. Half a step up and it is not -- the tail
        // shortens, and the "wrongness" that makes a creeper hiss read as an ignition.
        //
        // It was 1.0 because there was no case for it here at all, which is the shape this file
        // keeps taking: a new `Kind` arrives, it is declared and it is played, and the pitch ladder
        // quietly returns the default because nobody added the branch. The log is what said so --
        // `'minecraft:entity.creeper.primed' pedido, pitch 1.0`, from a number in the source that
        // reads 1.5 everywhere else.
        if (kind == Kind.FUSE_LIT) {
            return ARCABUZ_FUSE_PITCH;
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
