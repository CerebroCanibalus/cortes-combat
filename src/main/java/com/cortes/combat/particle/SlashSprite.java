package com.cortes.combat.particle;

import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which slash sprite each weapon profile hits with, as a name and nothing else.
 *
 * <p>Pure on purpose: this is a binding between an enum and a filename, and the only interesting
 * thing about it is that a profile with no art of its own still resolves to something. That is a
 * question a unit test can answer and a {@code Player} cannot, so it lives here with no
 * Minecraft in the signature.</p>
 *
 * <p>The names are the ones the art actually ships under, which is not the same as the enum's
 * own id: the warhammer profile is {@code warhammer} and the file is {@code martillo_hit}, the
 * sabre profile is {@code sabre} and the file is {@code shotel_hit}, the dagger profile is
 * {@code dagger} and the file is {@code daga_hit}. Three of them disagree with the code, and a
 * generated name would have been wrong three times.</p>
 *
 * <p><b>Everything unmapped falls back to the sword.</b> That is a decision, not a shrug: a
 * missing texture that renders nothing is worse than a wrong one that renders the wrong slash,
 * because "no particle" reads as the game being broken and "the sword slash on the axe" reads as
 * one asset being reused, which is what it is.</p>
 */
public final class SlashSprite {

    /** The one every unmapped profile lands on. */
    public static final String FALLBACK = "sword_hit";

    /**
     * Every sprite this particle can draw, in the order the atlas binds them.
     *
     * <p><b>The order here and the order in {@code assets/cortescombat/particles/slash.json} are
     * the same list and they have to stay that way.</b> A particle type registered from code does
     * not get its textures from the provider callback: {@code ParticleEngine#bindSpriteSets} walks
     * the description JSONs under {@code assets/<ns>/particles/} and hands each one's texture list
     * to the sprite set registered for that type. With no description, the set stays empty,
     * {@code pickSprite} binds nothing, {@code sprite} stays null, and the first particle on screen
     * takes the render thread down with an NPE on {@code getU0()}.</p>
     *
     * <p>That is the whole reason one JSON listing eight sprites is the right shape here. A single
     * type still draws every weapon; the description says which textures exist, and the payload
     * says which one this swing used, resolved to an index into this list.</p>
     *
     * <p>Because the list is duplicated in a resource file, {@code SlashSpriteTest} reads the JSON
     * and compares, so the two cannot drift without a test failing.</p>
     */
    public static final java.util.List<String> SPRITES = java.util.List.of(
            "sword_hit",
            "axe_hit",
            "daga_hit",
            "ropera_hit",
            "martillo_hit",
            "shotel_hit",
            "alabarda_hit",
            "barrido_alabarda",
              // The arcabuz's muzzle flash, last so every index above it is unchanged.
              //
              // It is not a slash and it does not pretend to be one: same 32x160 strip, same five
              // frames, same single animated quad. What makes it a different thing is where it is
              // spawned -- at the barrel, facing the way the gun points -- and a second particle
              // type would have been a second renderer for a difference in the spawn call.
              "arcabuz_shot",
              // **The maza and the pala, appended so no index above them moves.** Both are five
              // frame strips on the same canvas as every other slash -- the art was drawn to that
              // format -- so they are rows here and a pair of lines in `slash.json`, not a second
              // particle type. The maza's own hit used to fall through to the sword's slash and the
              // pala's to the sword's as well; these say what each of them actually left on the
              // body.
              "maza_hit",
              "shovel");

    private static final Map<WeaponProfile, String> BY_PROFILE = new EnumMap<>(WeaponProfile.class);

    static {
        BY_PROFILE.put(WeaponProfile.SWORD, "sword_hit");
        BY_PROFILE.put(WeaponProfile.AXE, "axe_hit");
        BY_PROFILE.put(WeaponProfile.DAGGER, "daga_hit");
        BY_PROFILE.put(WeaponProfile.SABRE, "shotel_hit");
        BY_PROFILE.put(WeaponProfile.HALBERD, "alabarda_hit");
        BY_PROFILE.put(WeaponProfile.WARHAMMER, "martillo_hit");
        BY_PROFILE.put(WeaponProfile.ROPERA, "ropera_hit");
        // **The bayonet borrows the daga's stab, and it used to fall through to the sword.**
        //
        // There was no row here for the arcabuz, so `of` returned the FALLBACK -- which is
        // `sword_hit` -- and a bayonet poke drew a sword's slash. The player saw a sword arc on a
        // gun, which is worse than no effect at all: it says the weapon is something it is not.
        //
        // **The daga and not a sprite of its own**, and the reason is the shape rather than the
        // convenience: the bayonet is a *stab*, the daga is this game's stab, and the two are the
        // same kind of motion at the same scale. `axe_hit` is a chop and `martillo_hit` is a
        // collapse, and a third of the two reads as a different weapon's swing.
        //
        // If a bayonet strip is ever drawn, this is the one line to change, and the list above grows
        // at the end so no existing index moves.
        BY_PROFILE.put(WeaponProfile.ARCABUZ, "daga_hit");
        // **The maza and the pala, which used to fall through to the sword's slash.** Both had no
        // row, so both drew `sword_hit` on every hit -- a sword's arc on a hammer and on a shovel.
        // The art for each exists now and is a five frame strip like the rest, so it is two rows
        // here and two names appended to `SPRITES`.
        BY_PROFILE.put(WeaponProfile.MACE, "maza_hit");
        BY_PROFILE.put(WeaponProfile.SHOVEL, "shovel");
    }

    private SlashSprite() {
    }

    /**
     * The sprite name for a profile, never null and never empty.
     *
     * @param profile the held item's profile, or null for a bare hand
     */
    public static String of(WeaponProfile profile) {
        if (profile == null) {
            return FALLBACK;
        }
        return BY_PROFILE.getOrDefault(profile, FALLBACK);
    }

    /** Whether this profile has art of its own, which {@code /cc stats} can say out loud. */
    public static boolean hasOwn(WeaponProfile profile) {
        return profile != null && BY_PROFILE.containsKey(profile);
    }

    /** The sweep the halberd throws on its right button, hit or no hit. */
    public static final String SWEEP = "barrido_alabarda";

    /**
     * The arcabuz's muzzle flash.
     *
     * <p>Its own sprite for the same reason the sweep has one: a flash at the barrel is a
     * different event from a slash that connects, and borrowing the weapon's hit sprite would say
     * "something was cut" at the moment the shot goes off.</p>
     */
    public static final String MUZZLE = "arcabuz_shot";

    /**
     * Which entry of {@link #SPRITES} a name is, and zero for anything unknown.
     *
     * <p>Zero rather than an exception because the sprite set is bound by index and an out of
     * range index inside the particle is a crash on the render thread. An unknown name drawing the
     * sword's slash is a cosmetic bug; an unknown name crashing the client is not.</p>
     */
    public static int indexOf(String name) {
        int index = SPRITES.indexOf(name);
        return index < 0 ? 0 : index;
    }

    /**
     * The sprite id the particle atlas will know it by.
     *
     * <p>The particles atlas is declared by vanilla as
     * {@code {"type":"directory","source":"particle","prefix":""}}, so the directory is
     * {@code textures/particle/} and the id carries no path prefix at all: the file
     * {@code textures/particle/sword_hit.png} is the sprite {@code cortescombat:sword_hit}.
     *
     * <p>Read out of vanilla's own atlas rather than remembered, because getting the plural wrong
     * is invisible. Nothing scans {@code textures/particles/}, the sprite simply does not exist,
     * and every swing spawns a particle that renders as nothing without a word in the log.</p>
     */
    public static String spriteId(String name) {
        return com.cortes.combat.CortesCombat.MODID + ":" + name;
    }
}