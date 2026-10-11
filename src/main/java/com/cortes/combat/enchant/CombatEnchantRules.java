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

    /** Daga -- Sanguijuela: cura por cantidad de golpes, no por daño. */
    public static final String SANGUJUELA_ID = "sanguijuela";

    /** Espada -- Aventurero: más experiencia al matar. */
    public static final String AVENTURERO_ID = "aventurero";

    /** Arcabuz -- Culata: la bayoneta empuja. */
    public static final String CULATA_ID = "culata";

    /** Ropera -- Estocada Perforante: el golpe del lunge entra por donde la armadura no cubre. */
    public static final String ESTOCADA_ID = "estocada";

    /** Espada -- Segundo Aliento: el premio por que te rompan la guardia. */
    public static final String SEGUNDO_ALIENTO_ID = "segundo_aliento";

    /** Espada -- Guardia Firme: el primer bloqueo de cada tres segundos es gratis. */
    public static final String GUARDIA_FIRME_ID = "guardia_firme";

    /** Azada -- Cicatrizador: el efecto Anti-curacion. */
    public static final String CICATRIZADOR_ID = "cicatrizador";

    /** Alabarda -- Destripamiento: el cargado deja Sangrado. */
    public static final String DESTRIPAMIENTO_ID = "destripamiento";

    /** Generico de pesadas -- Quebranto: la armadura del rival vale menos. */
    public static final String QUEBRANTO_ID = "quebranto";

    /** Shotel -- Desvio Vampirico: el parry roba vida maxima. */
    public static final String VAMPIRICO_ID = "desvio_vampirico";

    /** Arcabuz -- Mecha Corta: la mecha dura menos. */
    public static final String MECHA_CORTA_ID = "mecha_corta";

    /** Alabarda -- Barredor: el barrido cubre mas. */
    public static final String BARREDOR_ID = "barredor";

    /** Todos, para que un test pueda recorrerlos en vez de nombrarlos. */
    public static final java.util.List<String> IDS = java.util.List.of(STEEL_REFLEX_ID, AUDACITY_ID,
            FLEETING_EDGE_ID, RHYTHM_OF_IRON_ID, RIPOSTE_ID, DRY_POWDER_ID, PUNTA_FINA_ID,
            FILO_ASTILLADO_ID, APUNALAR_ID, SANGUJUELA_ID, AVENTURERO_ID, CULATA_ID, ESTOCADA_ID,
            SEGUNDO_ALIENTO_ID, GUARDIA_FIRME_ID, CICATRIZADOR_ID, DESTRIPAMIENTO_ID, QUEBRANTO_ID,
            VAMPIRICO_ID, MECHA_CORTA_ID, BARREDOR_ID);

    /** Los genericos, que se ponen por clase y no por molde. */

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
    public static final java.util.List<String> GENERIC_IDS = java.util.List.of(RIPOSTE_ID,
            QUEBRANTO_ID);

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

    // ---------------------------------------------------------------- los otros cuatro

    /**
     * Sanguijuela (daga): la fracción del daño hecho que vuelve como curación.
     *
     * <p><b>Es un porcentaje y no un flat, y el número de la daga es la razón.</b> La daga pega 1,6
     * a seis ticks: un flat de medio corazón por golpe sería, contra esa base, más de un tercio del
     * daño entero devuelto como vida. El porcentaje escala con lo que el arma hace, que es la única
     * forma de que el encantamiento signifique lo mismo en una daga de madera que en una de
     * netherite.</p>
     */
    public static final int SANGUJUELA_MAX_LEVEL = 2;

    public static float sanguijuela(int level) {
        return switch (clamp(level, SANGUJUELA_MAX_LEVEL)) {
            case 1 -> 0.08F;
            case 2 -> 0.14F;
            default -> 0.0F;
        };
    }

    /**
     * Aventurero (espada): la fracción de experiencia extra al morir, por nivel.
     *
     * <p><b>Es un genérico a propósito</b> (decisión del dueño): le da a las espadas un propósito
     * late-game, y se acepta que su mecánica funcione igual en cualquier arma. Un encantamiento de
     * XP no es identidad y no se hace pasar por ella.</p>
     */
    public static final int AVENTURERO_MAX_LEVEL = 5;

    /** Veinte por ciento por nivel: al cinco, el doble. */
    public static final float AVENTURERO_PER_LEVEL = 0.20F;

    public static float aventurero(int level) {
        return AVENTURERO_PER_LEVEL * clamp(level, AVENTURERO_MAX_LEVEL);
    }

    /**
     * Culata (arcabuz): la fracción de empuje extra de la bayoneta.
     *
     * <p>El arcabuz tiene dos botones, y el izquierdo es "se me acabó la pólvora, uso la bayoneta".
     * Su función no es pegar -- para eso está el disparo -- sino <b>hacer espacio para recargar</b>,
     * así que lo que el encantamiento sube es el empuje y no el daño.</p>
     */
    public static final int CULATA_MAX_LEVEL = 3;

    public static float culata(int level) {
        return switch (clamp(level, CULATA_MAX_LEVEL)) {
            case 1 -> 0.25F;
            case 2 -> 0.50F;
            case 3 -> 0.75F;
            default -> 0.0F;
        };
    }

    /**
     * Estocada Perforante (ropera): la fracción de armadura que el golpe <b>del lunge</b> ignora.
     *
     * <p>El lunge es el único ataque del juego que se resuelve por movimiento, y la ropera es la
     * única que lo tiene. El encantamiento premia la única forma de pelear que ninguna otra arma
     * puede copiar.</p>
     *
     * <p><b>Bajó de 25 %/50 % a 15 %/30 %</b> por pedido del dueño: contra una armadura completa, un
     * 50 % es media armadura ignorada en cada lunge, y el lunge pega además con el 75 % del daño del
     * arma.</p>
     */
    public static final int ESTOCADA_MAX_LEVEL = 2;

    public static float estocada(int level) {
        return switch (clamp(level, ESTOCADA_MAX_LEVEL)) {
            case 1 -> 0.15F;
            case 2 -> 0.30F;
            default -> 0.0F;
        };
    }

    /**
     * Segundo Aliento (espada): la fracción de daño extra del golpe que cobra la ventana.
     *
     * <p>La espada es la única cuyo bloqueo se puede <b>romper</b> y dejar al jugador con una deuda
     * que cobrar: el hacha te stunea de un golpe, y las cinco familias nuevas no cubren. El
     * encantamiento convierte el castigo en el premio, y por eso es identidad de la espada y no de
     * la guardia en general -- una guardia que no se puede romper no tiene esta deuda.</p>
     */
    public static final int SEGUNDO_ALIENTO_MAX_LEVEL = 2;

    /** Y cuánto empuja de más, sobre el empuje propio del arma. Igual en los dos niveles. */
    public static final float SEGUNDO_ALIENTO_PUSH = 0.50F;

    public static float segundoAliento(int level) {
        return switch (clamp(level, SEGUNDO_ALIENTO_MAX_LEVEL)) {
            case 1 -> 0.15F;
            case 2 -> 0.25F;
            default -> 0.0F;
        };
    }

    /**
     * Guardia Firme (espada): si el encantamiento está puesto.
     *
     * <p><b>Un nivel y sin número, y es lo único de esta lista que no escala.</b> La pregunta es
     * binaria -- ¿este bloqueo es gratis? -- y un segundo nivel sólo podría cambiar el intervalo, que
     * es el número que el diseño ya eligió ("cada tres segundos"). Un nivel que no cambia ninguna
     * respuesta no es un nivel.</p>
     */
    public static boolean guardiaFirme(int level) {
        return clamp(level, 1) >= 1;
    }

    // ---------------------------------------------- los cuatro de los efectos (tanda 3)

    /**
     * El nivel del <b>efecto</b> que corresponde a este nivel del encantamiento: uno menos.
     *
     * <p>Minecraft numera los efectos desde cero -- el nivel I es {@code amplifier 0} -- y los
     * encantamientos desde uno. La conversión vive acá y en un solo lugar porque cuatro
     * encantamientos la necesitan, y cuatro copias de {@code nivel - 1} son cuatro lugares donde
     * olvidarse de restar, que se ve como un efecto un nivel más fuerte y ningún error.</p>
     */
    public static int amplifierFor(int level) {
        return Math.max(0, clamp(level, 4) - 1);
    }

    /** Destripamiento: seis segundos al nivel uno, diez al dos. */
    public static final int DESTRIPAMIENTO_MAX_LEVEL = 2;

    public static int sangradoTicks(int level) {
        return switch (clamp(level, DESTRIPAMIENTO_MAX_LEVEL)) {
            case 1 -> 120;
            case 2 -> 200;
            default -> 0;
        };
    }

    /**
     * Cicatrizador: cinco segundos, <b>iguales en los cuatro niveles</b>.
     *
     * <p>El nivel compra cuánto reduce, no cuánto dura -- la misma regla que Desvío Vampírico. Un
     * encantamiento que además alargara la duración tendría dos ejes de escalado para el mismo
     * número, y el jugador no podría aprender cuál está comprando.</p>
     */
    public static final int CICATRIZADOR_MAX_LEVEL = 4;

    public static final int CICATRIZADOR_TICKS = 100;

    /** Quebranto: cuatro segundos, y reaplicar refresca el timer sin apilar mas alla del tope. */
    public static final int QUEBRANTO_MAX_LEVEL = 2;

    public static final int QUEBRANTO_TICKS = 80;

    /** Desvio Vampirico: cuatro niveles, y los diez segundos viven en el efecto. */
    public static final int VAMPIRICO_MAX_LEVEL = 4;

    /**
     * Anti-curacion: la fraccion de curacion que se pierde con este nivel del EFECTO.
     *
     * <p><b>Vive aca y no en el efecto, y el motivo no es estetico.</b> Un {@code MobEffect} extiende
     * una clase de Minecraft, asi que un test no puede ni cargar la clase que lo contiene: la cuenta
     * adentro del efecto seria una cuenta que ningun test mira. Es la linea exacta del proyecto --
     * "implementa algo de Minecraft?" -- y esta es la mitad que tiene que quedar afuera.</p>
     */
    public static final float ANTI_HEALING_PER_LEVEL = 0.15F;

    /** El amplifier del último nivel del encantamiento. Cuatro niveles, así que tres. */
    public static final int ANTI_HEALING_MAX_AMPLIFIER = 3;

    public static float antiHealingReduction(int amplifier) {
        int clamped = Math.min(ANTI_HEALING_MAX_AMPLIFIER, Math.max(0, amplifier));
        return ANTI_HEALING_PER_LEVEL * (clamped + 1);
    }

    /**
     * Desvio Vampirico: si este cuerpo puede pagar el robo sin quedar con el techo por debajo de uno.
     *
     * <p>Se pregunta con la vida <b>maxima actual</b> del cuerpo y no con su vida, porque lo que se
     * baja es el techo: un enemigo a un punto de vida con veinte de techo puede pagar el robo sin
     * morir, y uno a veinte con dos de techo no.</p>
     *
     * <p><b>Y el signo es negativo, que es el bug que el test cazo.</b> Un robo <em>baja</em> el techo
     * del que lo paga: con el numero en positivo la funcion decia que si a todo, el techo del enemigo
     * subia, y el robo era un regalo para los dos lados. La cuenta se ve bien leida y al reves, que
     * es la peor forma de fallar.</p>
     */
    public static final double DRAIN_PER_LEVEL = -2.0D;

    /** El techo minimo que un cuerpo puede conservar. Uno: nunca por debajo. */
    public static final float MIN_CEILING = 1.0F;

    public static boolean canDrain(float maxHealth, int amplifier) {
        return maxHealth + DRAIN_PER_LEVEL * (Math.max(0, amplifier) + 1) >= MIN_CEILING;
    }

    /** Quebranto: ocho por ciento de armadura menos por stack. */
    public static final double QUEBRANTO_PER_STACK = -0.08D;

    /** Tres stacks: veinticuatro por ciento con el efecto al maximo. */
    public static final int QUEBRANTO_MAX_STACKS = 3;

    public static double quebrantoArmour(int amplifier) {
        int stacks = Math.min(QUEBRANTO_MAX_STACKS, Math.max(1, amplifier + 1));
        return QUEBRANTO_PER_STACK * stacks;
    }

    /**
     * Desvio Vampirico: cuantos corazones roba <b>este</b> parry, contando los que ya robaste.
     *
     * <h2>El nivel es el TOPE, no el tamano del robo</h2>
     *
     * <p><b>Y eso es lo que hace que el encantamiento sea del shotel.</b> Con un robo por nivel, el
     * nivel compraba un numero mas grande en el primer parry y nada mas -- un premio que se cobra una
     * vez. Asi, cada parry exitoso suma <b>un corazon</b> al acumulado y el nivel es cuantos
     * corazones aguanta la cadena: al nivel cuatro hay que desviar <b>cuatro veces</b> para llegar al
     * tope. El maximo final es el mismo, y la habilidad que lo desbloquea es la lectura repetida, que
     * es la unica cosa que el sable sabe hacer.</p>
     *
     * <p><b>Y de paso arregla el problema de la vida actual.</b> Bajarle el techo a un cuerpo le baja
     * la vida -- vanilla la clampea cada tick -- y con un robo grande de una sola vez eso era un
     * golpe; con uno por parry es un desangrado lento que el enemigo puede contestar. El piso de
     * {@link #canDrain} sigue siendo lo que garantiza que no mate.</p>
     *
     * <p><b>Devuelve el acumulado NUEVO</b> y no la diferencia, porque el llamador tiene que
     * aplicarlo como el nivel del efecto: el acumulado ES el {@code amplifier}, y contarlo aparte
     * seria una segunda verdad sobre cuanta vida maxima tiene el jugador.</p>
     */
    public static int vampiricoStolen(int alreadyStolen, int level) {
        return Math.min(clamp(level, VAMPIRICO_MAX_LEVEL), Math.max(0, alreadyStolen) + 1);
    }

    /**
     * Mecha Corta (arcabuz): cuantos ticks dura la mecha con este nivel.
     *
     * <h2>La trampa que resulto no serlo</h2>
     *
     * <p>El documento decia que esto estaba <b>atado al {@code .mcmeta}</b> de la tira de cinco
     * frames y que por eso necesitaba trabajo de arte. <b>No es asi, y conviene que quede escrito:</b>
     * los cinco frames son <b>archivos separados</b> ({@code textures/item/arcabuz_fuse/}) y el que
     * se dibuja lo elige un <em>predicado</em> -- {@code ArcabuzModelProperties.FUSE} lee
     * {@code ArcabuzFuseState.progress()}. No hay animacion de atlas que re-temporizar.</p>
     *
     * <p><b>Lo que si habia era un numero escondido:</b> {@code progress()} dividia por la constante
     * {@code ArcabuzState.FUSE_TICKS}, asi que con una mecha mas corta el frame habria ido lento y la
     * animacion habria terminado despues del disparo. El total ahora viaja -- el servidor lo manda al
     * encender y el cliente lo lee del stack para el predicado -- y la division es por el total de
     * ESTA mecha.</p>
     *
     * <p>{@code baseTicks} entra como argumento y no como constante para que este archivo no
     * importe {@code ArcabuzState}: la tabla es pura y el numero base vive donde vive el arma.</p>
     */
    public static final int MECHA_CORTA_MAX_LEVEL = 2;

    public static int fuseTicks(int level, int baseTicks) {
        return switch (clamp(level, MECHA_CORTA_MAX_LEVEL)) {
            case 1 -> 15;
            case 2 -> 10;
            default -> baseTicks;
        };
    }

    /**
     * Barredor (alabarda): cuanto crece el cono del barrido con este nivel.
     *
     * <h2>Y el HUD tiene que enterarse, que es el punto entero</h2>
     *
     * <p>El documento de diseno tiene un veto escrito para esto -- el <b>C4</b>, "alcance extendido"
     * -- y no es un veto contra el encantamiento: es un veto contra <b>olvidarse del indicador</b>.
     * La razon esta escrita ahi y vale repetirla: un alcance que crece y un crosshair que no lo
     * dice convierten al indicador en una mentira, y una mentira del indicador se reporta como
     * <em>"no noto el encantamiento"</em> y no como un bug.</p>
     *
     * <p>Por eso {@code MaceAir.displayedFor} pregunta por este numero: el cono que el jugador ve es
     * el del <b>barrido</b> cuando el arma tiene Barredor. Es el mismo trato que el arcabuz, donde el
     * indicador describe el disparo y no la bayoneta -- <b>el boton que el jugador esta por apretar es
     * el que manda</b>.</p>
     *
     * <p>{@code base} entra como argumento por la misma razon que en {@link #fuseTicks}: el 1,35 de
     * fabrica vive en {@code HalberdSweep}, que es donde vive el barrido, y esta tabla no tiene por
     * que saber de barridos.</p>
     */
    public static final int BARREDOR_MAX_LEVEL = 2;

    public static float sweepScale(int level, float base) {
        return switch (clamp(level, BARREDOR_MAX_LEVEL)) {
            case 1 -> base * 1.15F;
            case 2 -> base * 1.30F;
            default -> base;
        };
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
