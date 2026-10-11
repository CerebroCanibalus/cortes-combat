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

    /** E5 -- Filo Fugaz. */
    public static final String FLEETING_EDGE_ID = "fleeting_edge";

    /** E4 -- Ritmo de Hierro. */
    public static final String RHYTHM_OF_IRON_ID = "rhythm_of_iron";

    /** E7 -- Contragolpe, el genérico de guardia. */
    public static final String RIPOSTE_ID = "riposte";

    /** E1 -- Pólvora Seca, la identidad del arcabuz. */
    public static final String DRY_POWDER_ID = "dry_powder";

    /** Ropera -- Punta Fina: pelea pegada. */
    public static final String PUNTA_FINA_ID = "punta_fina";

    /** Hacha -- Filo Astillado: castiga la armadura que el enemigo lleva puesta. */
    public static final String FILO_ASTILLADO_ID = "filo_astillado";

    /** Daga -- Apuñalar: el golpe por la espalda. */
    public static final String APUNALAR_ID = "apunalar";

    /** Todos, para que un test pueda recorrerlos en vez de nombrarlos. */
    public static final java.util.List<String> IDS = java.util.List.of(STEEL_REFLEX_ID, AUDACITY_ID,
            FLEETING_EDGE_ID, RHYTHM_OF_IRON_ID, RIPOSTE_ID, DRY_POWDER_ID, PUNTA_FINA_ID,
            FILO_ASTILLADO_ID, APUNALAR_ID);

    /**
     * Los genéricos, que se ponen por <b>clase</b> y no por molde.
     *
     * <p>La distinción es del diseño (§F) y tiene consecuencia en los datos: un encantamiento de
     * <b>identidad</b> es de una familia, así que su alcance es el tag del molde
     * ({@code #cortescombat:profile/sabre}); un <b>genérico</b> es de un eje que cruza familias
     * (Contragolpe = lo que puede cubrir = las espadas), así que su alcance es una clase
     * ({@code #cortescombat:enchantable/sword}). Un test que exigiera molde a todos estaba afirmando
     * la regla de uno solo.</p>
     */
    public static final java.util.List<String> GENERIC_IDS = java.util.List.of(RIPOSTE_ID);

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

    // ---------------------------------------------------------------- E5 · Filo Fugaz (daga)

    /** El nivel máximo, el del archivo de encantamiento. */
    public static final int FLEETING_EDGE_MAX_LEVEL = 2;

    /**
     * Cuántos ticks dura la Velocidad que la daga se gana al conectar: 40 → 60.
     *
     * <p><b>No es lineal, y no es un descuido.</b> El nivel uno son dos segundos -- lo justo para
     * salir del rango de lo que acabás de apuñalar -- y el dos son tres, que es una ventana de
     * reposicionamiento de verdad. Una progresión lineal (40 → 80) convertiría el segundo nivel en
     * un buff de cuatro segundos que se pisa con el gate de abajo y se siente igual que el primero
     * casi siempre.</p>
     *
     * <p><b>El cero no es un caso especial: es la ausencia del encantamiento</b>, y devuelve cero
     * ticks, que es lo que el llamador usa para no aplicar nada. Ver el gate en el handler: un
     * efecto de duración cero es un efecto que existe y no hace nada, que es peor que no ponerlo.</p>
     */
    public static int fleetingEdgeTicks(int level) {
        return switch (clamp(level, FLEETING_EDGE_MAX_LEVEL)) {
            case 1 -> 40;
            case 2 -> 60;
            default -> 0;
        };
    }

    // ---------------------------------------------------------------- E4 · Ritmo de Hierro (martillo)

    /** El nivel máximo, el del archivo de encantamiento. */
    public static final int RHYTHM_OF_IRON_MAX_LEVEL = 2;

    /** Cuánto se alarga la ventana por nivel: un segundo. */
    public static final int RHYTHM_OF_IRON_TICKS_PER_LEVEL = 20;

    /**
     * La ventana de la cadena del martillo con Ritmo de Hierro: 80 → 100 → 120 ticks.
     *
     * <p><b>Es el número más peligroso de los cuatro, y el que más necesita playtest.</b> El
     * escalado del martillo no tiene tope, así que alargar la ventana no sube el daño por golpe:
     * sube <em>el techo al que la cadena puede llegar antes de que el mundo te obligue a
     * parar</em>. En un pasillo con un mob, seis segundos y cuatro segundos son lo mismo; contra
     * tres mobs que se turnan, no. La regla del proyecto es que si queda fuerte se cambia con el
     * dato medido, y este es el número a medir.</p>
     *
     * <p>Por ticks fijos y no por fracción, a diferencia de Audacia: la ventana del martillo no se
     * deriva de nada del arma -- son cuatro segundos de diseño -- así que no hay una escala con la
     * que ser proporcional.</p>
     */
    public static int streakWindow(int level) {
        int clamped = clamp(level, RHYTHM_OF_IRON_MAX_LEVEL);
        return com.cortes.combat.state.StreakState.WINDOW_TICKS
                + RHYTHM_OF_IRON_TICKS_PER_LEVEL * clamped;
    }

    // ---------------------------------------------------------------- E7 · Contragolpe (guardia)

    /** El nivel máximo, el del archivo de encantamiento. */
    public static final int RIPOSTE_MAX_LEVEL = 2;

    /**
     * Qué porción de la armadura del objetivo deja de contar en el golpe que cobra la ventana.
     *
     * <p><b>Una fracción y no un booleano, y eso es todo el encantamiento.</b> El proyecto ya tiene
     * un "ignora la armadura del todo" -- el martillo a carga completa, con su tipo de daño en
     * {@code bypasses_armor} -- y no sirve acá por dos razones. Una es de balance: un cuarto o la
     * mitad es un premio, y el 100 % en cada golpe de una ventana de dos segundos no lo es. La otra
     * es que {@code bypasses_armor} <b>arrastra consecuencias que no elegimos</b>: vanilla lo anida
     * dentro de {@code bypasses_shield}, así que un golpe que ignora la armadura también pasa por
     * encima de un escudo levantado -- y Contragolpe es, precisamente, un encantamiento de guardia.
     * Que su premio ignorara la guardia del rival sería el chiste contado al revés.</p>
     *
     * <p>Se aplica bajando la armadura del objetivo durante ese único golpe, no sumando daño: la
     * cuenta la hace vanilla ({@code CombatRules.getDamageAfterAbsorb}) con el número que le
     * dejemos, así que no hay una copia de su aritmética que se desincronice en la próxima
     * versión.</p>
     */
    public static float riposteArmourIgnored(int level) {
        return switch (clamp(level, RIPOSTE_MAX_LEVEL)) {
            case 1 -> 0.25F;
            case 2 -> 0.50F;
            default -> 0.0F;
        };
    }

    // ---------------------------------------------------------------- E1 · Pólvora Seca (arcabuz)

    /** El nivel máximo, el del archivo de encantamiento. */
    public static final int DRY_POWDER_MAX_LEVEL = 2;

    /** Cuánto acorta la recarga por nivel: medio segundo. */
    public static final int DRY_POWDER_TICKS_PER_LEVEL = 10;

    /**
     * La recarga del arcabuz con Pólvora Seca: 50 → 40 → 30 ticks.
     *
     * <h2>Por qué esto es nuestro y no Quick Charge de vanilla</h2>
     *
     * <p><b>Porque el efecto de vanilla es un número absoluto y el nuestro tiene que ser una
     * fracción, y la diferencia se midió en juego.</b> Quick Charge resta <b>0,25 s por nivel</b>,
     * que sobre la ballesta (1,25 s de base) es un −20 % que se nota muchísimo y sobre nuestro
     * arcabuz (2,5 s, el doble) es un −10 % que apenas se siente. El diseño pedía <b>−10 ticks por
     * nivel</b>; vanilla daba la mitad, y no hay forma de arreglarlo desde el lado del arcabuz sin
     * tocar la ballesta también.</p>
     *
     * <p>Se intentó primero reutilizar el de vanilla -- la regla del proyecto es extender lo que ya
     * existe antes de inventar -- y el playtest lo desmintió con los números: el log mostró
     * {@code de 50} y {@code de 40} y la conclusión fue "apenas se nota". <b>Una reutilización que
     * da la mitad de lo que el diseño pide no es una reutilización, es un número mal puesto con
     * otro nombre.</b></p>
     *
     * <p>El piso es el mismo de siempre ({@code MIN_RELOAD_TICKS}, un segundo) y vive acá, en la
     * parte pura, por la misma razón que el de la carga: <b>nunca resta de más, y el piso se fuerza
     * en código</b>. Un encantamiento que pudiera llevar la recarga a cero sería un disparo
     * instantáneo.</p>
     */
    public static int reloadTicks(int baseTicks, int level) {
        int clamped = clamp(level, DRY_POWDER_MAX_LEVEL);
        return Math.max(com.cortes.combat.state.ArcabuzState.MIN_RELOAD_TICKS,
                baseTicks - DRY_POWDER_TICKS_PER_LEVEL * clamped);
    }

    // ------------------------------------------- los bonus por objetivo (un solo canal, tres reglas)

    /**
     * Punta Fina (ropera): la fracción de daño extra, o cero si el objetivo está lejos.
     *
     * <p><b>La distancia entra como argumento y la condición es el encantamiento entero.</b> La
     * ropera es el arma de pelear pegada -- su alcance es medio y su lunge la mete adentro -- y
     * ninguna otra arma premia estar tan cerca. Un encantamiento que diera el bonus siempre sería un
     * número plano más; el que lo ata a 1,5 bloques es una decisión de distancia, que es lo único
     * que la ropera puede elegir.</p>
     */
    public static final int PUNTA_FINA_MAX_LEVEL = 2;

    /** "Pegado", en bloques. La ropera tiene 3,6 de alcance: esto es menos de la mitad. */
    public static final double PUNTA_FINA_RANGE = 1.5;

    public static float puntaFina(int level, double distance) {
        if (distance > PUNTA_FINA_RANGE) {
            return 0.0F;
        }
        return switch (clamp(level, PUNTA_FINA_MAX_LEVEL)) {
            case 1 -> 0.15F;
            case 2 -> 0.25F;
            default -> 0.0F;
        };
    }

    /**
     * Filo Astillado (hacha): la fracción de daño extra, creciendo con la armadura que el rival
     * lleva puesta.
     *
     * <p><b>Es la inversa exacta del martillo</b>, y las dos armas pesadas quedan opuestas: el
     * martillo premia pegarle al blindado (ignora armadura a carga completa) y el hacha castiga al
     * que se puso todo. El número es <b>por pieza</b> y no un total, porque "cuánta armadura lleva"
     * es la variable que el jugador ve en el inventario del otro.</p>
     */
    public static final int FILO_ASTILLADO_MAX_LEVEL = 2;

    /** Cuatro piezas, que es el máximo que un cuerpo puede llevar. */
    public static final int FILO_ASTILLADO_MAX_PIECES = 4;

    public static float filoAstillado(int level, int pieces) {
        int worn = Math.max(0, Math.min(FILO_ASTILLADO_MAX_PIECES, pieces));
        return switch (clamp(level, FILO_ASTILLADO_MAX_LEVEL)) {
            case 1 -> 0.06F * worn;
            case 2 -> 0.10F * worn;
            default -> 0.0F;
        };
    }

    /**
     * Apuñalar (daga): la fracción de daño extra por pegar desde la espalda.
     *
     * <p>La daga es la única arma que <b>puede</b> reposicionarse lo bastante rápido para buscar la
     * espalda: seis ticks por golpe y la más rápida del juego. El encantamiento le paga esa vuelta,
     * que es la decisión que su tempo compra.</p>
     */
    public static final int APUNALAR_MAX_LEVEL = 2;

    /** El arco trasero: más de 90° de la mirada del objetivo. */
    public static final double APUNALAR_ARC = 90.0;

    public static float apunalar(int level, boolean fromBehind) {
        if (!fromBehind) {
            return 0.0F;
        }
        return switch (clamp(level, APUNALAR_MAX_LEVEL)) {
            case 1 -> 0.60F;
            case 2 -> 0.90F;
            default -> 0.0F;
        };
    }

    /**
     * Si el atacante está detrás del objetivo.
     *
     * <h2>La convención del yaw, que es donde esto se rompe en silencio</h2>
     *
     * <p>En Minecraft el yaw de una entidad mira hacia <b>+Z</b> en 0°, hacia <b>-X</b> en 90°, y
     * crece hacia la izquierda. El yaw que apunta de un punto a otro es
     * {@code toDegrees(atan2(-dx, dz))} -- <b>con el menos en X</b>, y sin él el "detrás" sale
     * espejado: el golpe por la espalda pagaría cuando pegás <em>de frente</em>, que es la peor
     * forma de fallar porque el número se ve bien y el jugador cobra al revés.</p>
     *
     * <p>{@code dx, dz} son <b>del objetivo al atacante</b>, no al revés: la pregunta es hacia dónde
     * está el que pega, vista desde el que recibe.</p>
     */
    public static boolean fromBehind(double targetYaw, double dx, double dz) {
        return angleBetween(targetYaw, Math.toDegrees(Math.atan2(-dx, dz))) > APUNALAR_ARC;
    }

    /** La diferencia más corta entre dos ángulos, en grados, entre 0 y 180. */
    public static double angleBetween(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
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
