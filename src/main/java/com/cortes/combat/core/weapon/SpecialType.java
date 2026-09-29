package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * What right click does.
 *
 * <p>The rule this enum exists to enforce: a special has to do something the left click
 * cannot. If it is just more damage, it should not exist. Most weapons therefore have
 * {@link #NONE} and no right click behaviour at all.</p>
 */
public enum SpecialType implements StringRepresentable {

    NONE("none"),
    /** Halberd: hits everything in the cone, and slows the next left-click attack. */
    MULTI_HIT("multi_hit"),
    /** Sabre: reflects the damage of the parried hit back, on a 5s cooldown. */
    PARRY("parry"),
    /** Axe: ground AoE that lifts. */
    SLAM("slam"),
    /** Hands: short push, no damage. */
    SHOVE("shove"),
    /** Pickaxe: short forward dash. */
    LUNGE("lunge"),
    /** Shovel: ground hit that pushes in a forward arc. */
    SWEEP("sweep"),
    /** Mace: the vanilla throw. */
    THROW("throw");

    private static final Codec<SpecialType> CODEC = StringRepresentable.fromEnum(() -> values());
    public static final Codec<SpecialType> TYPE = CODEC;

    private final String id;

    SpecialType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public boolean isPresent() {
        return this != NONE;
    }
}
