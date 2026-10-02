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
    THROW("throw"),
    /**
     * The arcabuz: light a fuse, wait a whole second, and then everything in the cone burns.
     *
     * <p><b>The right button, and the reason is the one rule this enum exists to enforce.</b> The
     * left click already takes one target out of the cone with its full damage. A special that
     * dealt more damage would be a strictly better left button, so the arcabuz's shot has to be
     * the thing a single-target cone cannot do: ten targets at once, each of them set on fire,
     * for one firestone and one second of standing perfectly still.
     *
     * <p>It is also the only special in the set whose effect is twenty ticks late. The halberd's
     * sweep and the ropera's lunge resolve on the press; the parry opens a window and resolves
     * inside it. This one has a fuse, and a fuse is a state that outlives the button that lit
     * it -- which is the family of bug this project has paid for three times, so it is a record
     * with a Codec and an attachment and a rule about the second press, not a countdown on a
     * field.</p>
     */
    FUSEE("fusee");

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
