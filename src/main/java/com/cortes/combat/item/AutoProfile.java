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
     * El alcance que el generador escribe para una espada larga: medio bloque mas que la espada
     * (3.6) y un bloque menos que la alabarda (5.0). Vive en el <b>archivo generado</b> como
     * {@code reach: {distance: ...}}, no en la tabla: una espada larga es CONSENSO DE ESCRITORIO,
     * no un dato del juego, y una tabla por cada nombre que un mod elige es la lista que se pudre.
     */
    public static final float LONGSWORD_REACH = 4.1F;

    /** La carga completa acelera (o frena) este tanto por delta de tier: netherite corri 16%. */
    public static final float TICKS_PER_DELTA = -0.08F;

    /** Y el bono de carga gana este tanto por delta de tier. */
    public static final float BONUS_PER_DELTA = 0.15F;

    /** El piso de la ventana de carga escalada: una carga que no se puede apuntar no es carga. */
    public static final int MIN_FULL_CHARGE_TICKS = 5;

    /** El material (el ore) de un arma ajena, leido del id. Puro: strings entran, floats salen. */
    public enum OreTier {
        // El orden NO importa: of() escanea el id por nombre, y el primero que pega gana porque
        // la tabla esta escrita del mas especifico al mas generico (netherite antes que iron).
        NETHERITE(2.0F, "netherite"),
        OBSIDIAN(1.0F, "obsidian"),
        DIAMOND(1.0F, "diamond"),
        STEEL(0.5F, "steel"),
        IRON(0.0F, "iron"),
        BRONZE(-0.5F, "bronze"),
        SILVER(-0.5F, "silver"),
        GOLD(-1.5F, "gold"),
        COPPER(-1.5F, "copper"),
        STONE(-1.0F, "stone"),
        WOODEN(-2.0F, "wood");

        /** El escalon contra el hierro, que es la linea donde vanilla pone el cero de la serie. */
        private final float delta;
        private final String keyword;

        OreTier(float delta, String keyword) {
            this.delta = delta;
            this.keyword = keyword;
        }

        /** El escalon de tier: positivo = material mejor, negativo = peor. Hierro es 0. */
        public float delta() {
            return delta;
        }

        /** Si ningun nombre material pega, el hierro es la respuesta neutral. */
        public static OreTier of(String path) {
            for (OreTier tier : values()) {
                if (path.contains(tier.keyword)) {
                    return tier;
                }
            }
            return IRON;
        }
    }

    /**
     * The window a foreign weapon charges in, scaled by its ore tier.
     *
     * <p>Un material mejor carga MAS RAPIDO hasta 16% (netherite) y uno peor hasta 16%
     * mas lento (madera). El piso es {@link #MIN_FULL_CHARGE_TICKS}: la ventana sigue
     * siendo una decision apuntada, no un press instantaneo.</p>
     */
    public static int tieredChargeTicks(int base, OreTier tier) {
        int scaled = Math.round(base * (1 + TICKS_PER_DELTA * tier.delta()));
        return Math.max(MIN_FULL_CHARGE_TICKS, scaled);
    }

    /**
     * The charge bonus a foreign weapon adds, scaled by its ore tier: +0.3 for netherite,
     * -0.3 for wood, over whatever the mould says. <b>The floor stays 0</b>, the same one the
     * YAML column has: the bonus never subtracts from the hit.
     */
    public static float tieredChargeBonus(float base, OreTier tier) {
        return Math.max(0.0F, base + BONUS_PER_DELTA * tier.delta());
    }

    /**
     * The archetype a foreign item's own name claims, by the words in its id.
     *
     * <p><b>El nombre es la unica fuente que no miente sobre lo que el mod QUISO fabricar</b>:
     * el tag de vanilla clasifica por omision (casi cualquier arma entra en
     * {@code #minecraft:swords}) y los atributos miden lo que el arma termina siendo, pero
     * "trident" en el id es el mod pidiendo el sistema del tridente. El orden de las familias
     * importa: "longsword" contiene "sword", así que la familia larga se pregunta antes, y unos
     * pocos nombres han de resolverse en el set del otro. Devuelve el NOMBRE del arquetipo
     * built-in (la llave del YAML), o {@code null} si ningun nombre miente bien.
     *
     * <p>* PILA: este metodo es el RUIDO del generador, no una ley. Un cuchillo de cocina que
     * dice "knife" entra como daga y el usuario borra la linea: el archivo es de el.
     */
    public static String archetypeOf(String path) {
        // Espada larga primero: contiene "sword" y por eso no puede venir despues.
        if (contains(path, "longsword", "greatsword", "great_sword", "claymore", "zweihander",
                "flamberge", "bastard_sword", "executioner", "montante", "odachi")) {
            return "sword"; // con reach especial: el generador pregunta longswordReach aparte
        }
        if (contains(path, "dagger", "dirk", "stiletto", "katar", "shiv", "knife", "kunai",
                "katana", "shortsword", "short_sword", "main gauche", "maingauche")) {
            return "dagger";
        }
        if (contains(path, "rapier", "estoc", "fleuret", "foil", "smallsword")) {
            return "rapier";
        }
        if (contains(path, "sabre", "saber", "scimitar", "cutlass", "shamshir", "falchion",
                "shotel", "cutlass")) {
            return "sabre";
        }
        // Lanzas y el tridente: el sistema del tridente es el molde de TODA lanza (decision
        // del dueno: "las lanzas usan automaticamente el sistema del tridente").
        if (contains(path, "spear", "lance", "pike", "javelin", "spetum", "ranseur", "partisan",
                "pilum", "sarissa", "hasta", "trident")) {
            return "spear";
        }
        if (contains(path, "halberd", "glaive", "polearm", "poleaxe", "naginata", "voulge",
                "bardiche", "scythe", "lucerne", "fauchard", "guisarme", "billhook")) {
            return "polearm";
        }
        if (contains(path, "hammer", "mallet", "maul", "gavel")) {
            return "hammer";
        }
        if (contains(path, "mace", "bludgeon", "cudgel")) {
            return "mace";
        }
        if (contains(path, "axe", "hatchet", "battle axe")) {
            return "axe";
        }
        return null;
    }

    /**
     * Whether the name names a LONGSWORD specifically -- the only archetype whose generated
     * entry carries a stat of its own ({@link #LONGSWORD_REACH}).
     */
    public static boolean isLongsword(String path) {
        return contains(path, "longsword", "greatsword", "great_sword", "claymore", "zweihander",
                "flamberge", "bastard_sword", "executioner", "montante", "odachi");
    }

    /** The archetype a refined sword-tag item maps back to, for the generator's output rows. */
    public static String archetypeFromProfile(WeaponProfile profile) {
        return switch (profile) {
            case DAGGER -> "dagger";
            case WARHAMMER -> "hammer";
            case SWORD -> "sword";
            default -> null;
        };
    }

    private static boolean contains(String path, String... keywords) {
        for (String keyword : keywords) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

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
