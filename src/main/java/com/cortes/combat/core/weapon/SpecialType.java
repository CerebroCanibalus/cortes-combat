package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * What right click does.
 *
 * <p>The rule this enum exists to enforce: a special has to do something the left click
 * cannot. If it is just more damage, it should not exist. Most weapons therefore have
 * {@link #NONE} and no right click behaviour at all.</p>
 *
 * <p>Like {@link WeaponProfile}, this avoids {@code StringRepresentable} so it stays pure
 * Java and does not drag the game onto the classpath of anything that holds it.</p>
 */
public enum SpecialType {

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

    public static final Codec<SpecialType> TYPE = Codec.STRING.comapFlatMap(SpecialType::byId, s -> s.id);

    private final String id;

    SpecialType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    private static DataResult<SpecialType> byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (SpecialType type : values()) {
            if (type.id.equals(key)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "Unknown special type: " + id);
    }

    public boolean isPresent() {
        return this != NONE;
    }
}
