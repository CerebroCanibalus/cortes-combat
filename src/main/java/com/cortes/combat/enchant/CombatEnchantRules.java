package com.cortes.combat.enchant;

/**
 * Los números de los encantamientos de combate propios, puros y en JVM pelada.
 *
 * <h2>Por qué las reglas viven acá y no en el call site</h2>
 *
 * <p>Mismo motivo que {@link GoldEnchantRules}: un encantamiento en 1.21 es una descripción y una
 * curva de coste, y todo número que signifique algo tiene que poder recorrerse sin abrir el juego.
 * Lo que se prueba acá no es "el cooldown es ochenta" sino la <b>forma</b>: que subir de nivel
 * siempre baja el número, que el nivel cero devuelve el de siempre, y que ningún nivel puede
 * reducirlo a cero o a negativo -- un cooldown de cero ticks no es una mejora, es un botón sin
 * precio, y el piso existe para que la tabla no pueda producir uno.</p>
 */
public final class CombatEnchantRules {

    // ---------------------------------------------------------------- los ids, acá y no en el wrapper

    /**
     * Los ids de registro de los encantamientos de combate, como texto plano.
     *
     * <p><b>Viven acá, en la clase pura, y no en {@link CombatEnchantments}.</b> Ese wrapper nombra
     * {@code ResourceKey} y {@code Enchantment}, que son tipos de Minecraft y no están en el
     * classpath de los tests -- así que un test que quisiera caminar los encantamientos para
     * comprobar que sus archivos existen no podría ni cargar la clase. Los ids como {@code String}
     * son la única forma de que la lista sea <b>una sola</b>: el wrapper arma sus claves desde acá,
     * y el test las lee de acá, sin una segunda lista escrita a mano que se desincronice.</p>
     */
    public static final String STEEL_REFLEX_ID = "steel_reflex";

    /** E3 -- Audacia. */
    public static final String AUDACITY_ID = "audacity";

    /** Todos, para que un test pueda recorrerlos en vez de nombrarlos. */
    public static final java.util.List<String> IDS = java.util.List.of(STEEL_REFLEX_ID, AUDACITY_ID);

    // ---------------------------------------------------------------- E2 · Reflejo de Acero (shotel)

    /** El nivel máximo, el del archivo de encantamiento. */
    public static final int STEEL_REFLEX_MAX_LEVEL = 2;

    /** Cuánto baja el cooldown por nivel: un segundo. */
    public static final int STEEL_REFLEX_TICKS_PER_LEVEL = 20;

    /**
     * El cooldown del parry con Reflejo de Acero: 100 → 80 → 60 ticks.
     *
     * <p><b>La ventana no entra acá y es a propósito.</b> Los doce ticks de la ventana son la
     * lectura que el jugador aprende ("medio segundo, y no se puede sostener") y el encantamiento
     * compra <em>frecuencia</em>, no duración. Un encantamiento que alargara la ventana estaría
     * vendiendo la postura que el parry no es.</p>
     */
    public static int parryCooldown(int baseTicks, int level) {
        int clamped = clamp(level, STEEL_REFLEX_MAX_LEVEL);
        return Math.max(1, baseTicks - STEEL_REFLEX_TICKS_PER_LEVEL * clamped);
    }

    // ---------------------------------------------------------------- E3 · Audacia (ropera)

    /** El nivel máximo, el del archivo de encantamiento. */
    public static final int AUDACITY_MAX_LEVEL = 2;

    /** Cuánto baja el cooldown por nivel, como fracción del total. */
    public static final float AUDACITY_REDUCTION_PER_LEVEL = 0.20F;

    /**
     * El cooldown del lunge con Audacia: el 20 % menos por nivel (80 → 64 → 48 ticks).
     *
     * <p><b>Por fracción y no por ticks fijos, a diferencia del parry, y la diferencia es del
     * diseño.</b> El cooldown del lunge ya se deriva del intervalo del arma
     * ({@code LungeDamage.COOLDOWN_FACTOR}), así que un descuento fijo chocaría con la escala del
     * tempo: en un arma lenta el mismo −20 ticks sería casi gratis y en una rápida sería todo. La
     * fracción deja el encantamiento proporcional a lo que el arma ya cuesta.</p>
     */
    public static int lungeCooldown(int baseTicks, int level) {
        int clamped = clamp(level, AUDACITY_MAX_LEVEL);
        float factor = 1.0F - AUDACITY_REDUCTION_PER_LEVEL * clamped;
        return Math.max(1, Math.round(baseTicks * factor));
    }

    /**
     * Un nivel dentro de los que el encantamiento tiene.
     *
     * <p>Clampear y no confiar en el dato es la regla del proyecto para todo número que entra desde
     * afuera: un archivo de datapack o un comando pueden poner un nivel que la tabla no espera, y
     * una tabla indexada por nivel que no clampea es un índice fuera de rango esperando.</p>
     */
    private static int clamp(int level, int max) {
        return Math.max(0, Math.min(max, level));
    }

    private CombatEnchantRules() {
    }
}
