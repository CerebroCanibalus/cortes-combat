package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The behavioural mould of a weapon: what kind of thing it is, independent of its numbers.
 *
 * <p>This is the thing the automatic conversion (ARCHITECTURE.md section 12) assigns to
 * foreign items, and the thing the player writes in the override config. It is deliberately
 * a small closed set: the numbers live in {@link ReachProfile} and {@link ChargeProfile}, so
 * a foreign weapon can also be fully custom without needing a new profile.</p>
 */
public enum WeaponProfile implements StringRepresentable {

    UNARMED("unarmed"),
    SWORD("sword"),
    AXE("axe"),
    PICKAXE("pickaxe"),
    SHOVEL("shovel"),
    MACE("mace"),
    TRIDENT("trident"),
    RAPIER("rapier"),
    HALBERD("halberd"),
    WARHAMMER("warhammer"),
    DAGGER("dagger"),
    SABRE("sabre");

    private static final Codec<WeaponProfile> CODEC = StringRepresentable.fromEnum(() -> values());
    public static final Codec<WeaponProfile> TYPE = CODEC;

    private final String id;

    WeaponProfile(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Whether the vanilla item behind this profile can break blocks. Used by the reach check. */
    public boolean isTool() {
        return switch (this) {
            case AXE, PICKAXE, SHOVEL -> true;
            default -> false;
        };
    }
}
