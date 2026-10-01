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
    SABRE("sabre");

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

    /** Whether the vanilla item behind this profile can break blocks. */
    public boolean isTool() {
        return switch (this) {
            case AXE, PICKAXE, SHOVEL -> true;
            default -> false;
        };
    }
}
