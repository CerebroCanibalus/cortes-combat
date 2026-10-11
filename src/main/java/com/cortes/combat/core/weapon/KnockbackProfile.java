package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How hard a weapon pushes, and in which direction.
 *
 * <p>Two numbers rather than one, and the split is the point. A single "knockback" value cannot
 * express a weapon that shoves sideways without launching anyone, which is most of them, and the
 * alternative, one number for everyone, is what made every hit in the game feel like it came from
 * a trebuchet.</p>
 *
 * <p>{@code vertical} being a separate multiplier is what stops a hit from popping a mob off the
 * ground. Vanilla's own knockback has a fixed upward component that scales with the push, so a
 * strong horizontal push is also a strong vertical one, and a sword sends a zombie airborne for
 * no reason the player did anything to earn. Here the two are independent: a sword can have a
 * small vertical and a large horizontal, and a warhammer can have the reverse.</p>
 *
 * <p>The vertical number is added, not scaled, so a value of zero means "do not lift at all" and
 * is a reachable, meaningful setting rather than a small number that still lifts a little.</p>
 */
public record KnockbackProfile(float horizontal, float vertical) {

    public static final KnockbackProfile NONE = new KnockbackProfile(0.0F, 0.0F);

    public static final Codec<KnockbackProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("horizontal").forGetter(KnockbackProfile::horizontal),
            Codec.FLOAT.fieldOf("vertical").forGetter(KnockbackProfile::vertical)
    ).apply(instance, KnockbackProfile::new));

    /**
     * Clamped so a data file or a config override cannot launch anyone across the map.
     *
     * <p><b>1.2 to 1.6, and the shovel is why.</b> The design gives the shovel the hardest shove in
     * the game at 0.95, with the argument that its use is putting something off a cliff. At the old
     * ceiling the number would have been nearly at the cap already, and the vertical at 0.28 would
     * have been at four fifths of its own &mdash; so "the biggest number in the game" would have
     * been a weapon that had no headroom left to be tuned in.</p>
     *
     * <p><b>Y subio otra vez, a 2,5, por dos encantamientos y no por un arma.</b> Paliza e Inercia
     * son los dos casos que piden un empuje que ninguna arma del catalogo tiene: el primero es un
     * tercer golpe cargado que tiene que mandar a alguien a otra provincia, y el segundo es un
     * empuje <em>entero</em> al aire. Con el techo en 1,6 los dos habrian quedado clampeados al
     * mismo numero que la pala alcanza sin encantamiento, que es la forma exacta en que un
     * encantamiento deja de existir sin que nada falle.</p>
     *
     * <p>What 2.5 is <em>not</em>: a number anything can reach. Nothing in the catalogue is above
     * the shovel, and this is a ceiling for a config override and for two enchantments, not a
     * target. Anything that reads this file should assume the highest real value in the game is
     * 0.95, and that the only things above it are two named enchantments.</p>
     */
    public static final float MAX_HORIZONTAL = 2.5F;

    /**
     * The upward push is deliberately much lower than the horizontal one. See the type comment.
     *
     * <p>Also raised, from 0.35 to 0.45, for the same reason as the horizontal and in the same
     * proportion: the shovel at 0.28 would have had no room either.</p>
     *
     * <p><b>Y a 0,70 por Inercia, que es el unico encantamiento que sube el vertical a proposito.</b>
     * El martillo ya esta en 0,30, asi que con el techo viejo dos golpes de cadena lo tocaban -- y un
     * encantamiento cuyo segundo golpe ya no puede subir mas es un encantamiento que solo tiene un
     * nivel de verdad.</p>
     */
    public static final float MAX_VERTICAL = 0.70F;

    public float clampedHorizontal() {
        return clamp(horizontal, 0.0F, MAX_HORIZONTAL);
    }

    public float clampedVertical() {
        return clamp(vertical, 0.0F, MAX_VERTICAL);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        return value < min ? min : (value > max ? max : value);
    }
}
