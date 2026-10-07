package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The behavioural mould of a weapon: what kind of thing it is, independent of its numbers.
 *
 * <p>This is the thing the automatic conversion (ARCHITECTURE.md section 12) assigns to
 * foreign items, and the thing the player writes in the override config. It is deliberately
 * a small closed set: the numbers live in {@link ReachProfile} and {@link ChargeProfile}, so
 * a foreign weapon can also be fully custom without needing a new profile.</p>
 *
 * <p><strong>Does not implement {@code StringRepresentable} on purpose.</strong> That is a
 * Minecraft class, and anything holding this enum would then need the game on its classpath
 * to initialise, which breaks the unit tests. A hand-written codec keeps this enum pure Java
 * with only DataFixerUpper, which the test runtime already has.</p>
 */
public enum WeaponProfile {

    /**
     * Not a weapon at all: a block, a stick, a bowl, anything held that is not in a tool tag.
     *
     * <p>This value exists because the derivation used to fall back to {@link #SWORD}, and the
     * consequence was that <em>every item in the game charged</em>. A dirt block in the hand
     * derived to a sword, inherited a sword's charge profile, and could be charged for a damage
     * bonus. "If we do not recognise it, treat it as the most common weapon" is a default that
     * quietly turns the whole world into a weapon, and it was invisible until a player picked
     * up a block.</p>
     *
     * <p>It is its own profile rather than a special case inside the charge system, so the rule
     * stays a data lookup: this item has no charge, this reach, this viewmodel. Adding a weapon
     * that is not a tool is still just a tag.</p>
     */
    NONE("none"),

    UNARMED("unarmed"),
    SWORD("sword"),
    AXE("axe"),
    PICKAXE("pickaxe"),
    SHOVEL("shovel"),
    MACE("mace"),
    TRIDENT("trident"),
    ROPERA("ropera"),
    HALBERD("halberd"),
    WARHAMMER("warhammer"),
    DAGGER("dagger"),
    SABRE("sabre"),
    /**
     * The arcabuz: a cane gun whose left click is a bayonet poke and whose right click is a
     * twenty tick fuse and a blast.
     *
     * <p><b>A profile and not a {@code WeaponFamily}, and that is the whole reason it exists.</b>
     * The thirty catalogue weapons are (family, material) pairs, so adding one meant choosing
     * which of the five shapes it was and inventing six tiers for a firearm. This is a single
     * item with one set of numbers, and pretending it was a family would have meant six
     * identical registrations and a {@code WeaponShape} grid nobody drew.
     *
     * <p>It still needs a profile because that is what the four tables are keyed by, and the
     * tables being complete for every profile is the invariant {@code WeaponTablesTest} holds.
     * A profile with no row in any of the four is the ropera's bug wearing a different hat.</p>
     */
    ARCABUZ("arcabuz"),

    /**
     * The hoe: six things at once, gently, forever.
     *
     * <p><b>Declared last, and that is not an accident.</b> {@link #byOrdinal} says the order is a
     * wire contract, and inserting here would renumber every value after it. There is one value
     * after nothing at the moment, which is the only reason this note is cheap to follow: the day
     * someone adds a profile they have to read it, because the alternative is a client on an older
     * build reading a different weapon's charge ratio.</p>
     *
     * <p>It is a weapon and not a tool the way the shovel is one, with one difference that matters:
     * <b>the shovel throws one thing across a room and this clears six things in a circle.</b>
     * Neither of them kills anything on its own, which is why both sit at the bottom of the damage
     * table and take their value from what surrounds them.</p>
     */
    HOE("hoe");

    private static final Function<String, DataResult<WeaponProfile>> BY_ID = id -> {
        String key = id.toLowerCase(Locale.ROOT);
        for (WeaponProfile profile : values()) {
            if (profile.id.equals(key)) {
                return DataResult.success(profile);
            }
        }
        return DataResult.error(() -> "Unknown weapon profile: " + id);
    };

    public static final Codec<WeaponProfile> TYPE = Codec.STRING.comapFlatMap(
            BY_ID, profile -> profile.id);

    private final String id;

    WeaponProfile(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * The profile at that position in the declaration, or {@code null} if there is none.
     *
     * <p><b>Reads off {@code values()} and returns null rather than throwing.</b> The only caller is
     * {@code PlayerCombatState}'s wire codec, and the case it has to survive is a client receiving a
     * payload from a server whose enum is longer than its own: an out-of-range ordinal there is a
     * version skew, and an {@code ArrayIndexOutOfBoundsException} inside a packet handler drops the
     * packet and takes the connection with it. A null is an answer the caller can check.</p>
     *
     * <p>The second thing worth knowing is that <b>this makes the declaration order a wire
     * contract</b>: inserting a profile renumbers the ones after it. {@code PlayerCombatState} says
     * why that is accepted rather than worked around.</p>
     */
    public static WeaponProfile byOrdinal(int ordinal) {
        WeaponProfile[] all = values();
        return ordinal < 0 || ordinal >= all.length ? null : all[ordinal];
    }

    /** Whether the vanilla item behind this profile can break blocks. */
    public boolean isTool() {
        return switch (this) {
            // The hoe is in this list because it already was a tool before it was a weapon: it
            // tills, and giving it a combat profile must not take the farmland away.
            case AXE, PICKAXE, SHOVEL, HOE -> true;
            default -> false;
        };
    }
}
