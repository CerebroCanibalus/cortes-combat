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
    public static final String ADRENALINA_ID = "adrenalina";

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

    /** Hacha -- Astilladora: un golpe bloqueado gasta dos de aguante. */
    public static final String ASTILLADORA_ID = "astilladora";

    /** Hacha -- Mella: el desgaste a la armadura sube. */
    public static final String MELLA_ID = "mella";

    /** Alabarda -- Segunda Pasada: el barrido que no mato devuelve cooldown. */
    public static final String SEGUNDA_PASADA_ID = "segunda_pasada";

    /** Azada -- Santa Muerte: matar revienta en una nube de putrefaccion. */
    public static final String SANTA_MUERTE_ID = "santa_muerte";

    /** Martillo -- Inercia: el empuje de la cadena va al aire. */
    public static final String INERCIA_ID = "inercia";



    /** Largas -- Ensarte: la estocada atraviesa al de adelante. */
    public static final String ENSARTE_ID = "ensarte";

    /** Pala -- Paliza: el tercer golpe cargado de una serie empuja muchisimo. */
    public static final String PALIZA_ID = "paliza";

    /** Ropera -- Retirada: agachado, el lunge sale para atras. */
    public static final String RETIRADA_ID = "retirada";

    /** Martillo -- Sentencia: cinco golpes de cadena y el cuerpo revienta. */
    public static final String SENTENCIA_ID = "sentencia";

    /** Martillo -- Trabazon: el golpe cargado clava al rival en el piso. */
    public static final String TRABAZON_ID = "trabazon";

    /** Daga -- Gripa: los golpes dejan moco, que dana y hace estornudar. */
    public static final String GRIPA_ID = "gripa";

    /** Shotel -- Corte Gemelo: el golpe normal da un segundo corte. */
    public static final String CORTE_GEMELO_ID = "corte_gemelo";

    /** Shotel -- Capacitor: el proyectil parriado vuelve como rayo. */
    public static final String CAPACITOR_ID = "capacitor";

    /** Pala -- Pala Antimaterial: el click derecho suelta un orbe que implosiona. */
    public static final String PALA_ANTIMATERIAL_ID = "pala_antimaterial";

    /** Arcabuz -- Rey de las Moscas: carne podrida de municion y una nube de moscas. */
    public static final String REY_DE_LAS_MOSCAS_ID = "rey_de_las_moscas";

    /** Arcabuz -- Mungus: un Firestone enorme que revienta el terreno. */
    public static final String MUNGUS_ID = "mungus";

    /**
     * Todos, para que un test pueda recorrerlos en vez de nombrarlos.
     *
     * <p><b>Y los tres que faltan no estan aca a proposito.</b> Pala Antimaterial, Rey de las Moscas y
     * Mungus necesitan una entidad propia y un camino de municion que este mod todavia no tiene; sus
     * ids, sus claves y sus tablas de numeros estan escritas mas abajo, pero <b>no entran a esta
     * lista</b> -- y la lista es la que hace que un test pida el archivo de datos, el tag, el idioma y
     * la forma del campo. Un encantamiento declarado que no se puede conseguir no es un encantamiento a
     * medias: es un canal muerto con el build verde, que es exactamente lo que `corte_gemelo` ya pago
     * una vez en esta misma ronda.</p>
     */
    public static final java.util.List<String> IDS = java.util.List.of(STEEL_REFLEX_ID, AUDACITY_ID,
            ADRENALINA_ID, RHYTHM_OF_IRON_ID, RIPOSTE_ID, DRY_POWDER_ID, PUNTA_FINA_ID,
            FILO_ASTILLADO_ID, APUNALAR_ID, SANGUJUELA_ID, AVENTURERO_ID, CULATA_ID, ESTOCADA_ID,
            SEGUNDO_ALIENTO_ID, GUARDIA_FIRME_ID, CICATRIZADOR_ID, DESTRIPAMIENTO_ID, QUEBRANTO_ID,
            VAMPIRICO_ID, MECHA_CORTA_ID, BARREDOR_ID, ASTILLADORA_ID, MELLA_ID, SEGUNDA_PASADA_ID,
            SANTA_MUERTE_ID, INERCIA_ID, ENSARTE_ID, PALIZA_ID, RETIRADA_ID, SENTENCIA_ID,
            TRABAZON_ID, GRIPA_ID, CORTE_GEMELO_ID, CAPACITOR_ID, REY_DE_LAS_MOSCAS_ID,
            PALA_ANTIMATERIAL_ID, MUNGUS_ID);

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

    // ---------------------------------------------------------------- Adrenalina (daga)

    /** El nivel máximo, el del archivo de encantamiento. */
    public static final int ADRENALINA_MAX_LEVEL = 2;

    /**
     * Cuánto del daño que entra se ahorra la daga: −25 % → −35 %.
     *
     * <p><b>Es una reducción y no una curación, y la diferencia es toda la identidad del
     * encantamiento.</b> Una curación despues del golpe llega tarde -- el daño ya se cobro, la muerte
     * ya pudo pasar. Lo que la daga compra es <em>entrar</em> a un intercambio que no podia ganar: la
     * reduccion se cobra antes de que el golpe se resuelva, asi que el golpe que te iba a matar no te
     * mata.</p>
     *
     * <p><b>Y el 35 % no es un numero chico.</b> Es mas de un tercio de todo el daño que te entra,
     * durante el cooldown entero. La daga ya tiene el daño mas bajo del juego; lo que no puede tener
     * es ademas el cuerpo mas fragil, y esta es la mitad defensiva de su identidad.</p>
     */
    public static float adrenalinaReduction(int level) {
        return switch (clamp(level, ADRENALINA_MAX_LEVEL)) {
            case 1 -> 0.25F;
            case 2 -> 0.35F;
            default -> 0.0F;
        };
    }

    /**
     * El cooldown de la Adrenalina: 30 s → 15 s.
     *
     * <p><b>Largo a proposito, y el nivel dos lo parte al medio.</b> Un daño reducido que se puede
     * encender cada tres segundos no es una decision, es un estado; a treinta segundos el jugador
     * tiene que elegir <em>que</em> golpe come con la adrenalina puesta, y esa eleccion es el
     * encantamiento. El L2 no sube el numero: <b>duplica la frecuencia</b>, que en una pelea larga
     * vale mas que un 10 % mas de reduccion.</p>
     */
    public static int adrenalinaCooldown(int level) {
        return switch (clamp(level, ADRENALINA_MAX_LEVEL)) {
            case 1 -> 600;
            case 2 -> 300;
            default -> 0;
        };
    }

    /**
     * Cuánto dura la Velocidad que viene con la Adrenalina.
     *
     * <p>Cinco segundos, y <b>no dura el cooldown</b>: la reduccion es un estado del cuerpo y la
     * Velocidad es la salida. Un jugador que corriera durante los treinta segundos enteros estaria
     * corriendo <em>todo el tiempo</em>, que es lo que el proyecto veta en C5 -- la velocidad de
     * movimiento como estado permanente y no como decision.</p>
     */
    public static final int ADRENALINA_SPEED_TICKS = 100;

    /**
     * El nivel de Velocidad, que es el del encantamiento menos uno.
     *
     * <p>Uno menos porque los niveles de efecto arrancan en cero: "Velocidad I" es {@code amplifier}
     * cero. La conversion vive aca y no en el handler porque es la clase de resta que se escribe al
     * reves y no se nota hasta que el jugador tiene Velocidad III con un encantamiento de nivel dos.
     * </p>
     */
    public static int adrenalinaSpeedAmplifier(int level) {
        return Math.max(0, clamp(level, ADRENALINA_MAX_LEVEL) - 1);
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
     * Astilladora (hacha): cuanta aguante gasta un golpe bloqueado.
     *
     * <p><b>Es un gasto y no un dano, y la diferencia es la identidad del hacha.</b> El hacha no
     * rompe guardias -- eso es el martillo, que vacia la barra entera de un golpe -- sino que las
     * <em>cansa</em>: la misma guardia que aguanta tres golpes de espada aguanta uno y medio de
     * hacha. El jugador que se cubre del hacha tiene que bajar la guardia antes, y ese es el momento
     * que el hacha estaba comprando.</p>
     */
    public static final int ASTILLADORA_COST = 2;

    public static int astilladoraCost(int level) {
        return clamp(level, 1) >= 1 ? ASTILLADORA_COST : 1;
    }

    /**
     * Mella (hacha): cuanto se multiplica el desgaste que el golpe cargado le hace a la armadura.
     *
     * <p>Es el <b>unico encantamiento del proyecto que no toca el dano</b>: toca el desgaste, que es
     * un recurso del rival que se gasta en una escala de minutos. Por eso los numeros son grandes
     * (hasta x6 con el x2 base) y por eso el encantamiento vive en el hacha: es el arma que ya
     * desgasta al doble, y Mella es "lo mismo, pero de verdad".</p>
     *
     * <p>El nivel maximo es 4 y los saltos son de 0,5: 1,5 / 2,0 / 2,5 / 3,0 sobre el x2 base, o sea
     * x3 / x4 / x5 / x6 de desgaste final. Un salto mas chico haria que el cuarto nivel fuera
     * invisible, y un encantamiento cuyo ultimo nivel no se nota es un nivel que el jugador paga.</p>
     */
    public static final int MELLA_MAX_LEVEL = 4;

    public static float mellaWear(int level) {
        return switch (clamp(level, MELLA_MAX_LEVEL)) {
            case 1 -> 1.5F;
            case 2 -> 2.0F;
            case 3 -> 2.5F;
            case 4 -> 3.0F;
            default -> 1.0F;
        };
    }

    /**
     * Segunda Pasada (alabarda): que fraccion del cooldown devuelve un barrido que no mato a nadie.
     *
     * <p><b>La condicion es "no mato a NADIE", y no "no acerto".</b> Un barrido que acerto a tres y no
     * mato a ninguno devuelve el cooldown igual, porque el problema que este encantamiento resuelve es
     * el barrido contra un grupo que aguanta: cuatro swings de precio por un intercambio que no
     * termino nada. Un barrido que <em>si</em> mata ya cobro lo suyo y no devuelve nada -- y eso es lo
     * que impide que el encantamiento sea "el barrido cuesta la mitad siempre".</p>
     *
     * <p>Devuelve una <b>fraccion del cooldown ya cobrado</b>, no un numero de ticks: el cooldown del
     * barrido sale del intervalo del arma, asi que un numero fijo seria distinto en cada alabarda.</p>
     */
    public static final int SEGUNDA_PASADA_MAX_LEVEL = 2;

    public static float segundaPasada(int level) {
        return switch (clamp(level, SEGUNDA_PASADA_MAX_LEVEL)) {
            case 1 -> 0.30F;
            case 2 -> 0.50F;
            default -> 0.0F;
        };
    }

    /**
     * Santa Muerte (azada): el radio y el dano de la nube que deja un enemigo muerto.
     *
     * <p>Un solo nivel y numeros fijos, porque el efecto es un <b>evento</b> y no una escala: el
     * encantamiento se llama asi por lo que <em>hace</em>, no por cuanto hace. Wither I durante cinco
     * segundos es un dano que no se ve venir y que no se puede curar, que es exactamente lo que una
     * nube de putrefaccion tiene que ser.</p>
     *
     * <p>Y el Wither va al <b>radio</b>, no a quien murio: el cuerpo que cae ya no importa, y lo que
     * el jugador compra es que el siguiente cuerpo que entre al hueco lo reciba.</p>
     */
    public static final int SANTA_MUERTE_RADIUS = 3;
    public static final int SANTA_MUERTE_WITHER_TICKS = 100;

    /**
     * Inercia (martillo): cuanto sube el empuje vertical por golpe de cadena.
     *
     * <p><b>Es el unico encantamiento del proyecto que manda un empuje al aire, y el codigo decia
     * que eso no se hacia.</b> {@code Knockback.apply} tiene un javadoc escrito a proposito -- "rerouting
     * the enchantment through the vertical would smuggle the launch back in through the one number
     * that is supposed to be a property of the weapon" -- y esa regla nacio de sacar el empuje
     * vertical de vanilla, que ponia a un mob en el aire con cualquier golpe.</p>
     *
     * <p><b>La regla sigue siendo cierta y esto es la excepcion, con nombre.</b> Lo que estaba mal en
     * vanilla no era el aire: era que el aire venia <em>gratis y sin decirlo</em>, en cada golpe de
     * cada arma. Aca lo pide un encantamiento que hay que conseguir, con un arma que tiene que
     * construir una cadena primero, y la horizontal queda en <b>cero</b> -- no es "un empuje mas
     * fuerte", es el mismo empuje apuntado a otro lado. Un mob que sale volando hacia arriba y se
     * queda donde esta es lo contrario de un mob que sale despedido.</p>
     *
     * <p>Por eso el numero es un <b>porcentaje del vertical propio del arma</b> y no un valor
     * absoluto: una cadena larga tiene que poder tocar el techo del martillo, no el de la pala.</p>
     */
    public static final int INERCIA_MAX_LEVEL = 2;

    public static float inerciaLift(int level, int streakHits) {
        float perHit = switch (clamp(level, INERCIA_MAX_LEVEL)) {
            case 1 -> 0.10F;
            case 2 -> 0.20F;
            default -> 0.0F;
        };
        return perHit * Math.max(0, streakHits);
    }

    // **Corte Gemelo vivio aca una hora y se fue, y vale anotar por que.** Las reglas estaban escritas
    // -- el intervalo del L1, la fraccion del corte -- y el archivo de datos tambien, y el
    // encantamiento se podia poner en el sable. Lo que no existia era **el cableado**: nadie leia el
    // intervalo, asi que el L1 y el L2 pegaban lo mismo. Un encantamiento con reglas, archivo, idioma
    // y tag, que no hace nada, es la peor clase de canal muerto porque **el build esta verde** y el
    // unico sintoma es que un jugador pague un nivel que no recibe. Se retira entero -- no se deja a
    // medias -- y vuelve con su attachment en la ronda que le toca.

    /**
     * Ensarte (largas): cuanto del dano le llega al cuerpo de atras.
     *
     * <p><b>El segundo cuerpo no cobra bonus de encantamientos</b>, y no es un detalle: los bonuses
     * por objetivo leen <em>a quien</em> se golpea (armadura, angulo, distancia), asi que pagarlos dos
     * veces por un solo golpe seria cobrar dos veces por una decision que el jugador tomo una vez.
     * La estocada atraviesa y reparte el dano base; lo que encuentre del otro lado no es una segunda
     * eleccion.</p>
     */
    public static final int ENSARTE_MAX_LEVEL = 2;

    public static float ensarteShare(int level) {
        return switch (clamp(level, ENSARTE_MAX_LEVEL)) {
            case 1 -> 0.50F;
            case 2 -> 1.00F;
            default -> 0.0F;
        };
    }

    // -------------------------------------------------- Paliza (pala)

    /**
     * Paliza: cuantos golpes normales hay que encadenar antes de que el cargado empuje como un cañon.
     *
     * <p><b>El nivel no sube el empuje: acorta la serie.</b> El numero que el jugador siente es "cuanto
     * me cuesta llegar", y con dos golpes ya es una secuencia que hay que querer; con uno es un golpe
     * cargado que empuja mas, y el encantamiento pasa de combo a pasiva. Los dos numeros -- 2,4 y 0,6 --
     * son los mismos en los dos niveles, y por eso el techo de knockback tuvo que subir: 2,4 horizontal
     * es un 50 % mas que la pala sin encantamiento y el techo viejo era 1,6.</p>
     */
    public static final int PALIZA_MAX_LEVEL = 2;

    /** La ventana de la serie: cuatro segundos, la misma que la cadena del martillo. */
    public static final int PALIZA_WINDOW_TICKS = 80;

    /** Golpes encadenados que hacen falta antes del cargado. L1 dos, L2 uno. */
    public static int palizaStreak(int level) {
        return switch (clamp(level, PALIZA_MAX_LEVEL)) {
            case 1 -> 2;
            case 2 -> 1;
            default -> -1;
        };
    }

    /** El empuje del golpe de Paliza. Los dos niveles empujan igual. */
    public static final float PALIZA_HORIZONTAL = 2.4F;
    public static final float PALIZA_VERTICAL = 0.6F;

    /**
     * Cuanto sube el empuje <em>vertical propio</em> del arma encima del de Paliza.
     *
     * <p><b>Cero, y por eso es una constante con nombre y no un literal.</b> Paliza reemplaza el empuje
     * del golpe: los 2,4 y 0,6 de arriba son el empuje entero, no una suma sobre el de la pala. Y el
     * literal tiene un problema que este proyecto ya pago -- {@code MaceFallWiringTest} prohibe un
     * {@code 0.0F} al ras en {@code CombatEngine} porque es como se llama por accidente al overload del
     * resolver que no cobra la caida. Un cero con nombre no se puede confundir con eso.</p>
     */
    public static final float PALIZA_LIFT_SHARE = 0.0F;

    // -------------------------------------------------- Retirada (ropera)

    /**
     * Retirada: el lunge que sale para atras cuando el jugador esta agachado.
     *
     * <p>Un solo nivel y ningun numero propio, <b>y eso es el diseño</b>: el impulso es el del lunge
     * con el signo cambiado, y el cubo que barre la marcha atras esta centrado en el jugador y sin
     * direccion, asi que ya alcanza lo que quedo adelante. Darle una tabla de distancias propia
     * habria sido un segundo lunge que mantener en paralelo con el primero, y el dia que uno se
     * retoca el otro queda mintiendo.</p>
     *
     * <h2>Por qué la fraccion es 1,0 y no una parte</h2>
     *
     * <p>Porque <b>el golpe del frente no es un bonus: es el golpe del lunge adelantado al press.</b>
     * Cada objetivo del golpe va a {@code Lunge.prehit}, que es lo que impide que el barrido lo cobre
     * otra vez en el primer tick de la marcha -- asi que nadie recibe dos golpes por una pulsacion.
     * Una fraccion menor habria sido un nerf arbitrario sobre un golpe que ya estaba pagado.</p>
     *
     * <p>Lo que Retirada compra no es daño, es <b>garantia</b>: el golpe se cobra aunque la retirada
     * la corte una pared, que es exactamente lo que el lunge normal no puede prometer porque su
     * golpe depende de que el cuerpo llegue. Y el precio es el mismo en las dos direcciones -- dos
     * bloques que no gastas en acercarte son dos bloques que no te acercan.</p>
     */
    public static final float RETIRADA_FORWARD_HIT_SHARE = 1.0F;

    // -------------------------------------------------- Sentencia (martillo)

    /**
     * Sentencia: cinco golpes de cadena y el cuerpo revienta.
     *
     * <p><b>Cinco y no tres, y la razon es la cadena entera.</b> La ventana del martillo son cuatro
     * segundos y cada golpe la renueva, asi que llegar a cinco ya es estar ganando el intercambio a
     * proposito. A tres el reventon caeria dentro de cualquier pelea normal y el encantamiento seria
     * dano gratis en vez de un premio por mantener la presion.</p>
     */
    public static final int SENTENCIA_MAX_LEVEL = 2;
    public static final int SENTENCIA_STREAK = 5;

    /** Lo que revienta el cuerpo: al que lo recibe y a lo que tenga alrededor. */
    public static float sentenciaDamage(int level) {
        return switch (clamp(level, SENTENCIA_MAX_LEVEL)) {
            case 1 -> 8.0F;
            case 2 -> 12.0F;
            default -> 0.0F;
        };
    }

    public static float sentenciaSplash(int level) {
        return switch (clamp(level, SENTENCIA_MAX_LEVEL)) {
            case 1 -> 4.0F;
            case 2 -> 6.0F;
            default -> 0.0F;
        };
    }

    public static final float SENTENCIA_RADIUS = 2.5F;

    /** La fraccion del reventon que cobran los de al lado en el nivel dos. */
    public static final float SENTENCIA_CHAIN_SHARE = 0.5F;

    // -------------------------------------------------- Trabazon (martillo)

    /**
     * Trabazon: el golpe cargado clava al rival en el piso.
     *
     * <p><b>Se llama Trabazon y no Aturdimiento</b> porque el proyecto ya llama stun a la guardia rota,
     * y dos cosas distintas con el mismo nombre es la clase de confusion que no se arregla despues.</p>
     *
     * <p>Y el nivel agrega una prohibicion, no tiempo: el L1 saca el salto -- que es lo que impide
     * cruzar un hueco -- y el L2 saca tambien la carrera. Un segundo y medio es lo que dura una
     * ventana de guardia; mas tiempo convertiria el encantamiento en un control de mas.</p>
     */
    public static final int TRABAZON_MAX_LEVEL = 2;
    public static final int TRABAZON_TICKS = 30;

    /** Si el nivel tambien saca la carrera. */
    public static boolean trabazonBlocksSprint(int level) {
        return clamp(level, TRABAZON_MAX_LEVEL) >= 2;
    }

    // -------------------------------------------------- Gripa (daga)

    /**
     * Gripa: el moco que la daga deja, y el estornudo.
     *
     * <p><b>Los stacks suben el daño y no la duracion</b>, que es lo que hace que el encantamiento se
     * sienta como una cuenta que sube: cada golpe agrega un stack hasta el tope, y el daño por tick se
     * multiplica por cuantos haya. Un moco que solo durara mas seria un temporizador que el jugador no
     * mira.</p>
     *
     * <p><b>Y el estornudo es lo que se ve.</b> Un daño por tick no tiene feedback -- el rival pierde
     * vida y nada lo dice --, asi que cada cuarenta ticks el cuerpo estornuda: un punto de daño, un
     * salto de 0,4 y, en el nivel dos, una nube que empuja a los de al lado. La particula y el sonido
     * son los del panda, asi que <b>no hay arte nuevo</b>.</p>
     */
    public static final int GRIPA_MAX_LEVEL = 2;

    public static int gripaMaxStacks(int level) {
        return switch (clamp(level, GRIPA_MAX_LEVEL)) {
            case 1 -> 4;
            case 2 -> 6;
            default -> 0;
        };
    }

    /**
     * Cada cuantos ticks estornuda el cuerpo, segun cuantos mocos tenga encima.
     *
     * <p><b>La frecuencia sube con los stacks, y el estornudo es el UNICO dano del
     * encantamiento.</b> Ese es el rediseno que pidio el playtest, y las dos mitades van juntas: con
     * un moco el cuerpo estornuda cada tres segundos, y con los seis del tope estornuda <b>una vez
     * por segundo</b>. "Mas seguido" es exactamente "mas dano por segundo", asi que la escalada
     * entera del encantamiento vive en este numero.</p>
     *
     * <p><b>Y el dano por tick que habia antes se fue.</b> Multiplicaba un punto por stack cada
     * veinte ticks y no se veia: el rival perdia vida y nada lo decia. El estornudo si se ve -- es
     * la particula del panda, el sonido y un envion -- y cobrar las dos cosas era cobrar dos veces
     * por el mismo moco.</p>
     *
     * <p>El piso de veinte no es decorativo: sin el, seis mocos serian un estornudo por tick y el
     * encantamiento dejaria de ser una cuenta que sube para volverse un interruptor.</p>
     */
    public static int gripaSneezeInterval(int stacks) {
        int s = Math.max(1, stacks);
        return Math.max(GRIPA_SNEEZE_MIN_TICKS,
                GRIPA_SNEEZE_BASE_TICKS - (s - 1) * GRIPA_SNEEZE_STEP_TICKS);
    }

    /** Un moco solo: un estornudo cada tres segundos. */
    public static final int GRIPA_SNEEZE_BASE_TICKS = 60;
    /** Lo que adelanta cada moco mas. */
    public static final int GRIPA_SNEEZE_STEP_TICKS = 8;
    /** El piso: un estornudo por segundo, que es lo mas seguido que puede pasar. */
    public static final int GRIPA_SNEEZE_MIN_TICKS = 20;

    /** Cuanto dura el moco en el cuerpo. Cuatro segundos, y se renueva con cada golpe. */
    public static final int GRIPA_TICKS = 80;

    /**
     * El estornudo: <b>el unico dano del encantamiento, y es grande a proposito.</b>
     *
     * <p>Dos corazones y medio por estornudo. Un moco solo son 2,5 cada tres segundos; los seis del
     * tope son 2,5 <b>por segundo</b>, que es mas de lo que pega cualquier arma del catalogo. El
     * precio es que hay que llegar a los seis, y que el rival se quede el tiempo suficiente para que
     * la cuenta suba.</p>
     */
    public static final float GRIPA_SNEEZE_DAMAGE = 5.0F;

    /** El envion hacia arriba, que es lo que un estornudo tiene de estornudo. */
    public static final float GRIPA_SNEEZE_JUMP = 0.6F;
    public static final float GRIPA_CLOUD_RADIUS = 2.0F;
    public static final float GRIPA_CLOUD_DAMAGE = 2.0F;

    // -------------------------------------------------- Corte Gemelo (shotel)

    /**
     * Corte Gemelo: el golpe normal da un segundo corte.
     *
     * <p>El nivel contesta dos preguntas a la vez: <b>L1 pega el corte extra cada cuatro segundos, L2 lo
     * pega siempre</b>. La fraccion del daño base es la mitad en los dos niveles, y no puede ser mas:
     * al 100 % el golpe normal del sable pegaria el doble y el encantamiento seria el arma.</p>
     */
    public static final int CORTE_GEMELO_MAX_LEVEL = 2;
    public static final float CORTE_GEMELO_SHARE = 0.5F;
    public static final int CORTE_GEMELO_INTERVAL = 80;

    /** {@code -1} sin encantamiento, {@code 0} para "siempre". */
    public static int corteGemeloInterval(int level) {
        return switch (clamp(level, CORTE_GEMELO_MAX_LEVEL)) {
            case 1 -> CORTE_GEMELO_INTERVAL;
            case 2 -> 0;
            default -> -1;
        };
    }

    // -------------------------------------------------- Capacitor (shotel)

    /**
     * Capacitor: el proyectil que el parry devuelve como rayo.
     *
     * <p><b>El daño es una suma y no un reemplazo</b>: lo que devuelve es el rayo <em>mas</em> el daño
     * base del arma, porque el parry ya devolvia el proyectil antes de este encantamiento -- el numero
     * que agrega es el rayo, y el golpe que ya existia no se toca. Y quema cuatro segundos, que es lo
     * que convierte una devolucion en una advertencia: el que disparo tiene que apagarse.</p>
     */
    public static final int CAPACITOR_MAX_LEVEL = 2;

    public static float capacitorDamage(int level) {
        return switch (clamp(level, CAPACITOR_MAX_LEVEL)) {
            case 1 -> 3.0F;
            case 2 -> 4.0F;
            default -> 0.0F;
        };
    }

    public static final int CAPACITOR_FIRE_TICKS = 80;

    // -------------------------------------------------- Pala Antimaterial

    /**
     * Pala Antimaterial: el orbe que atrae y despues implosiona.
     *
     * <p><b>Es {@code treasure} y weight 1 por decision del dueño</b> -- raro y poderoso --, asi que los
     * numeros pueden ser grandes y lo son: catorce de daño en un radio de cuatro es mas que cualquier
     * golpe del juego. Lo que lo hace justo es todo lo de alrededor: <b>cuarenta ticks de carga</b> (el
     * doble que la carga mas larga del catalogo), <b>ciento veinte de cooldown</b> -- seis segundos, mas
     * que el barrido de la alabarda -- y una entidad que hay que apuntar.</p>
     *
     * <p><b>Y atrae antes de reventar.</b> El tiron de cinco bloques no es decoracion: es lo que
     * convierte el orbe en un encantamiento de control del espacio y no en una bomba que hay que
     * tirarle a alguien. El que esta cerca viene, y despues el radio de cuatro lo cobra.</p>
     */
    public static final int PALA_ANTIMATERIAL_CHARGE_TICKS = 40;
    public static final int PALA_ANTIMATERIAL_COOLDOWN_TICKS = 120;
    public static final float PALA_ANTIMATERIAL_PULL_RADIUS = 5.0F;
    public static final float PALA_ANTIMATERIAL_BLAST_RADIUS = 4.0F;
    public static final float PALA_ANTIMATERIAL_DAMAGE = 14.0F;

    /**
     * Lo que viaja el orbe: despacio, y por eso se ve.
     *
     * <p><b>0,35 y no 1,2, que era el numero de la primera version.</b> El playtest pidio que la
     * esfera "viaje toda la trayectoria, no debe ser muy rapida": a 1,2 bloques por tick cruzaba el
     * alcance de la pala en un tercio de segundo, o sea que el orbe era un fogonazo y el tiron que
     * tiene que hacer mientras viaja no llegaba a pasar. A 0,35 el viaje dura segundos, el jugador
     * ve de donde viene el tiron y el enemigo tiene la opcion de correrse -- que es lo que hace que
     * esto sea un ataque y no un boton.</p>
     */
    public static final double PALA_ANTIMATERIAL_SPEED = 0.35;

    /**
     * Lo que aguanta el orbe en vuelo. A la velocidad de arriba son unos treintaitres bloques.
     *
     * <p><b>Noventa y seis ticks y no ochenta: el playtest pidio el rango un veinte por ciento mas
     * largo.</b> El rango es <em>velocidad por vida</em> y la velocidad esta fijada por "que se
     * vea viajar", asi que la palanca es la vida: ochenta por 1,2 son noventa y seis, o de
     * veintiocho a treintaitres y medio bloques. Se toca la vida y no la velocidad a proposito --
     * subir la velocidad habria acortado el viaje en segundos, que es justo lo que hace que el
     * orbe sea un ataque con aviso y no un fogonazo.</p>
     *
     * <p>Y esta constante ahora es <b>el radio maximo de verdad</b>, no solo un temporizador: el
     * orbe implosiona al agotarla en vez de apagarse. Ver {@code AntimatterOrb#tick}.</p>
     */
    public static final int PALA_ANTIMATERIAL_LIFETIME_TICKS = 96;

    /** El radio del tiron MIENTRAS viaja, y el tiron que aplica por tick. */
    public static final float PALA_ANTIMATERIAL_TRAVEL_PULL_RADIUS = 3.0F;
    public static final double PALA_ANTIMATERIAL_TRAVEL_PULL = 0.18;

    /** Y el dano que va repartiendo en el camino: poco y seguido, para que se lea como arrastre. */
    public static final float PALA_ANTIMATERIAL_TRAVEL_DAMAGE = 2.0F;
    public static final int PALA_ANTIMATERIAL_TRAVEL_DAMAGE_TICKS = 5;

    /**
     * La implosion: los ticks que dura el tiron violento antes del golpe.
     *
     * <p><b>Seis ticks y no un instante, porque una implosion es un proceso y no un evento.</b> Un
     * tiron de golpe es un teleport: el cuerpo aparece pegado al orbe y el jugador no lee "me
     * chupo", lee "se tecleo". Seis ticks de tiron fuerte dan el tiempo justo para ver a los de
     * alrededor venir, y son lo que separa una implosion de una explosion.</p>
     */
    public static final int PALA_ANTIMATERIAL_IMPLODE_TICKS = 6;

    /** Y cuanto tira por tick en esos seis: casi dos bloques en total, contra los 0,18 del viaje. */
    public static final double PALA_ANTIMATERIAL_IMPLODE_PULL = 0.32;

    // -------------------------------------------------- Rey de las Moscas (arcabuz)

    /**
     * Rey de las Moscas: carne podrida de municion y una nube de moscas.
     *
     * <p><b>El daño total es MENOR que el del disparo normal, a proposito.</b> Cuatro por tick cada
     * diez, durante cuatro segundos, son dieciseis repartidos entre todo lo que este en el radio --
     * contra los diez objetivos a plena potencia que hace un Firestone. Lo que el encantamiento
     * compra no es daño: es <b>rango</b> (ocho contra seis y medio) y <b>no gastar polvora</b>. La
     * carne podrida es un recurso que sobra, y esa es la identidad entera.</p>
     *
     * <p><b>Y no reinfecta.</b> El que ya tiene el efecto no lo recibe otra vez, asi que el
     * encantamiento no se puede apilar sobre un mismo cuerpo por tirarle dos veces -- y eso es lo que
     * lo mantiene siendo area y no una ametralladora de moscas.</p>
     */
    public static final float MOSCAS_DAMAGE = 4.0F;
    public static final int MOSCAS_INTERVAL = 10;
    public static final int MOSCAS_TICKS = 80;
    public static final float MOSCAS_RADIUS = 3.0F;
    public static final float MOSCAS_RANGE = 8.0F;

    // -------------------------------------------------- Mungus (arcabuz)

    /**
     * Mungus: un Firestone enorme que revienta el terreno.
     *
     * <p><b>La potencia es 24 y no 12, y el 12 era el numero de la primera version.</b> TNT es 4 en
     * este juego, asi que veinticuatro son seis TNT juntas: un crater que ya no es "abrir una pared"
     * sino "borrar la esquina de una casa". El playtest pidio el doble sobre el 12, que es lo que
     * hay aca -- y sigue <b>sin romper obsidiana</b>, porque la obsidiana es el material
     * anti-explosion del juego y un encantamiento que la atraviesa no tiene respuesta.</p>
     *
     * <p>Y el costo es lo que lo mantiene siendo un tiro de asedio: diez Firestones, la carga al doble
     * y un proyectil que <b>cae en arco</b> -- ver {@code MungusShell}, porque esa gravedad estuvo
     * prometida en este javadoc desde la primera version y el proyectil volaba recto. El que lo usa
     * renuncia a pelear durante dos segundos largos para abrir una pared, y esa renuncia es el
     * precio.</p>
     */
    public static final int MUNGUS_POWER = 24;
    public static final int MUNGUS_FIRESTONES = 10;
    public static final float MUNGUS_CHARGE_FACTOR = 2.0F;

    /** Un nivel dentro de los que el encantamiento tiene.
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
