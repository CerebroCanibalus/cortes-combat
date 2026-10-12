package com.cortes.combat.particle;

/**
 * Los colores de la lectura de daño, en un solo lugar y sin nada de Minecraft.
 *
 * <h2>Dos ejes, y la razón de que sean dos</h2>
 *
 * <p>Un golpe contesta <b>dos</b> preguntas y el dueño las separó al diseñarlo:</p>
 *
 * <ul>
 *   <li><b>El split</b> -- <i>¿qué hizo el enemigo con mi golpe?</i> Entró, atravesó la armadura, lo
 *       paró la armadura, lo frenó una poción, lo frenó el escudo. Eso es {@link #dealt()},
 *       {@link #PURE}, {@link #ARMOUR}, {@link #EFFECT} y {@link #GUARD}.</li>
 *   <li><b>El tipo</b> -- <i>¿de dónde vino el daño?</i> Fuego, veneno, wither, caída. Eso es la
 *       tabla de familias, para el daño que <b>no</b> es un golpe tuyo (el tick del veneno, el
 *       sangrado).</li>
 * </ul>
 *
 * <p><b>Y son 12 familias y no 48 colores.</b> Vanilla tiene 48 tipos de daño (leídos de
 * {@code DamageTypes.java}, ninguno inventado) y nadie memoriza 48 colores: un código que nadie
 * memoriza no es un código, es decoración. Con 12 familias que cubren los 48, el jugador los aprende
 * sin que nadie se lo explique -- y es la misma decisión que {@code CombatFeedback} ya tomó al
 * cerrar su enum en tres valores.</p>
 *
 * <h2>Los dos avisos que importan</h2>
 *
 * <p><b>El rojo ya está gastado dos veces.</b> {@code CombatFeedback} usa un rojo para {@code HIT} y
 * otro más vivo para {@code KILL}. Acá hay <b>un solo</b> rojo para "entró", porque el punto ya dice
 * otra cosa y un tercer rojo distinto sería un color que nadie distingue.</p>
 *
 * <p><b>El escudo reusa el color que ya existe.</b> {@link #GUARD} es el mismo {@code 0x8296AD} de
 * {@code CombatFeedback.GUARDED}: si el crosshair se tiñe de un azul gris y los puntos de otro, son
 * dos códigos para el mismo hecho.</p>
 */
public final class HurtPalette {

    private HurtPalette() {
    }

    // ---- Eje A: el split del golpe ------------------------------------------------

    /** La vida que bajó. El color que el jugador ya asocia con pegar. */
    public static final int DEALT = 0xE11B1B;

    /** Atravesó la armadura. El mismo blanco para todo lo que la ignora, propio o no. */
    public static final int PURE = 0xF2F2F2;

    /** Lo paró la armadura. */
    public static final int ARMOUR = 0x4A7FE0;

    /** Lo frenó una poción (Resistencia). */
    public static final int EFFECT = 0x9A9A9A;

    /** Lo frenó un escudo. Reusa el color de {@code CombatFeedback.GUARDED}. */
    public static final int GUARD = 0x8296AD;

    // ---- Eje B: el tipo, en familias ----------------------------------------------

    /** Filo y cuerpo a cuerpo: {@code player_attack}, {@code mob_attack}, {@code thorns}. */
    public static final int MELEE = 0xE11B1B;

    /** Proyectiles: {@code arrow}, {@code trident}, {@code mob_projectile}, {@code spit}, {@code thrown}. */
    public static final int PROJECTILE = 0x8C1A14;

    /** Fuego: {@code in_fire}, {@code on_fire}, {@code campfire}, {@code lava}, {@code fireball}. */
    public static final int FIRE = 0xFF7A18;

    /** Explosiones: {@code explosion}, {@code player_explosion}. */
    public static final int EXPLOSION = 0xFFD24A;

    /** Magia y alma: {@code magic}, {@code dragon_breath}, {@code sonic_boom}, {@code wither_skull}. */
    public static final int MAGIC = 0xB44CE0;

    /** Podredumbre: {@code wither}. */
    public static final int WITHER = 0x4A4A4A;

    /** Hambre y deshidratación: {@code starve}, {@code dry_out}. */
    public static final int STARVE = 0x8C8C4A;

    /** Frío: {@code freeze}. */
    public static final int FREEZE = 0x7FD8FF;

    /** Caídas e impactos: {@code fall}, {@code fly_into_wall}, {@code falling_block}, {@code stalagmite}. */
    public static final int IMPACT = 0xD8D8D8;

    /** Asfixia y aplastamiento: {@code drown}, {@code in_wall}, {@code cramming}. */
    public static final int SUFFOCATE = 0x2E5A8C;

    /** Vegetal y espinas: {@code cactus}, {@code sweet_berry_bush}, {@code sting}. */
    public static final int THORNS = 0x8FA83C;

    /** Eléctrico: {@code lightning_bolt}. */
    public static final int LIGHTNING = 0xE8F4FF;

    /** Vacío y administrativo: {@code out_of_world}, {@code generic}, {@code generic_kill}. */
    public static final int VOID = 0x141414;

    /** Veneno y sus primos del mod: {@code MOCO}, el sangrado, la anti-curación. */
    public static final int POISON = 0x5FA83C;

    // ---- Los dos tipos propios del mod ---------------------------------------------

    /**
     * El hacha cargada, {@code cortescombat:axe_charged}.
     *
     * <p><b>Más vivo que {@link #DEALT}, y a propósito.</b> Es el golpe más caro del juego y el
     * único que conserva la armadura y la destroza al doble: el color dice "esto pesó".</p>
     */
    public static final int AXE_CHARGED = 0xFF3B1B;

    /**
     * El martillo, {@code cortescombat:warhammer_ignored_armour}.
     *
     * <p><b>El mismo blanco que {@link #PURE}, y es una decision, no una coincidencia.</b> El
     * martillo <b>es</b> un daño que atraviesa: darle un blanco propio -- "para que se note" --
     * diria que es otra cosa y obligaria al jugador a aprender dos colores para el mismo hecho.</p>
     *
     * <p>El guard lo afirma ({@code theWarhammerSharesThePureFamily}), y mordio: la primera version
     * de esta tabla le ponia {@code 0xFFFFFF} mientras el javadoc decia "la misma familia". <b>La
     * prosa y el numero se contradecian y el test eligio el numero.</b></p>
     */
    public static final int WARHAMMER = PURE;

    /** Los canales de 24 bits, para el dibujo. */
    public static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }
}
