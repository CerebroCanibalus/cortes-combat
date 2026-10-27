package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponProfile;

/**
 * <b>La clasificaci&oacute;n autom&aacute;tica de armas ajenas, por sus atributos.</b> El pedido
 * del due&ntilde;o fue con nombre: "mejorar SEVERAMENTE la compatibilidad autom&aacute;tica".
 * Esta clase es la mitad sin YAML: toda arma ajena cuyo &uacute;nico dato era el tag
 * <code>#minecraft:swords</code> deja de heredar la espada completa y se clasifica por
 * <b>lo que el arma declara de s&iacute;</b> &mdash; da&ntilde;o y velocidad, en la escala del
 * tooltip (da&ntilde;o = 1 + modificadores, velocidad = 4 + modificadores, igual que lo muestra
 * el propio tooltip del juego).
 *
 * <h2>La l&iacute;nea de calibraci&oacute;n</h2>
 *
 * <p>La espada de hierro de vanilla: 6 de da&ntilde;o, 1.6 de velocidad. Todo el cat&aacute;logo
 * ajeno se ordena alrededor de esa l&iacute;nea: una katana de Simply Swords va por 6/2.0, una
 * daga de Spartan por 3/3.0, un martillo de guerra de Spartan por 10-12/0.9. La espada de
 * vanilla no se mueve de la suya y las dem&aacute;s caen al arquetipo que su forma de pelear
 * describe.</p>
 *
 * <h2>Lo que NO hace</h2>
 *
 * <p>No toca: los items de <code>minecraft</code> ni los de <code>cortescombat</code> (los
 * nuestros ya tienen componente y tag de molde por encima de esta capa); nada con entrada de
 * pack (el YAML manda por encima de todo lo autom&aacute;tico); ni tags de vanilla que no sean
 * el de espadas (un hacha ajena en <code>#minecraft:axes</code> ya dijo lo suyo y el molde del
 * hacha es informaci&oacute;n, no herencia ciega). El caso degenerado es exactamente el tag de
 * espada: <b>casi cualquier arma de mod se declara en
 * <code>#minecraft:swords</code></b>, y &eacute;se es el signo de que el tag contest&oacute;
 * por omisi&oacute;n y no por decisi&oacute;n.</p>
 *
 * <p>Pura: dos floats entran, un perfil sale, sin Minecraft en el medio &mdash; testeable en
 * JVM como el resto de las tablas.</p>
 */
public final class AutoProfile {

    private AutoProfile() {
    }

    /** Una velocidad ( tooltips ) igual o mayor que esta es "arma rapida" a secas: daga. */
    public static final float FAST_SPEED = 2.2F;

    /** La ventana "rapida y liviana": debajo de este dano la velocidad corta, aunque no llegue a 2.2. */
    public static final float QUICK_AND_LIGHT_DAMAGE = 6.0F;

    /** La velocidad minima de la ventana "rapida y liviana". */
    public static final float QUICK_SPEED = 1.9F;

    /** La velocidad maxima bajo la que un arma pesada deja de ser molde de espada. */
    public static final float HEAVY_SPEED = 1.3F;

    /** Y el dano a partir del que peso es peso: el martillo de una espada ajena paso a serlo. */
    public static final float HEAVY_DAMAGE = 9.0F;

    /**
     * Whether a namespace is outside the two this mod owns: the game's and its own. Namespaces
     * are the cheap identity check -- an item id carries who wrote the item, and the
     * classifier may only refine the ones that did not.
     */
    public static boolean isForeign(String namespace) {
        return !"minecraft".equals(namespace) && !"cortescombat".equals(namespace);
    }

    /**
     * The refined archetype for a foreign item that arrived here through the vanilla sword tag.
     *
     * <p>El orden es del "casi": una daga pasada de dano (una excavadora de mod con velocidad 3)
     * sigue siendo daga, porque la VELOCIDAD es lo que su jugador tiene que aprender a manejar.
     * Y una espada pesada en el limite (8 de dano, 1.2 de velocidad) queda espada, a proposito:
     * el martillo es 9 en la tabla y el ultimo paso de la clasificacion no es el rebano ni la
     * media.</p>
     */
    public static WeaponProfile refineForeignSword(float damage, float speed) {
        if (speed >= FAST_SPEED) {
            return WeaponProfile.DAGGER;
        }
        if (speed >= QUICK_SPEED && damage <= QUICK_AND_LIGHT_DAMAGE) {
            return WeaponProfile.DAGGER;
        }
        if (damage >= HEAVY_DAMAGE && speed <= HEAVY_SPEED) {
            return WeaponProfile.WARHAMMER;
        }
        return WeaponProfile.SWORD;
    }
}
