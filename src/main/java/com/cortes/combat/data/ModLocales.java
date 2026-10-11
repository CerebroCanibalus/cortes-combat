package com.cortes.combat.data;

import java.util.List;

/**
 * Los idiomas que este mod escribe, en un solo lugar.
 *
 * <h2>Por qué los códigos viven acá y no en el provider</h2>
 *
 * <p>Porque el provider es una clase de Minecraft (extiende {@code LanguageProvider}) y <b>no se
 * puede cargar en un test de JVM pelada</b>, así que una lista ahí adentro obligaría al test que la
 * comprueba a escribir la suya -- y dos listas de lo mismo se desincronizan. Es la misma jugada que
 * {@code CombatEnchantRules.IDS}: el dato puro afuera, el envoltorio de Minecraft adentro.</p>
 *
 * <h2>Y por qué son siete españoles</h2>
 *
 * <p><b>Porque ninguno es un alias de otro.</b> La pila de idiomas de Minecraft es
 * <em>[seleccionado, en_us]</em>: no hay un paso intermedio por {@code es_es}, así que un jugador en
 * {@code es_mx} no cae a español de España -- cae a <b>inglés</b>. Un mod con un solo archivo
 * español se ve en inglés para la mayoría de la gente que habla español, y el archivo que falta no
 * da ningún error: simplemente no está.</p>
 *
 * <p>La lista salió de la lista real de idiomas de 1.21.1 ({@code misode/mcmeta}, la referencia de
 * los datos del juego), no de memoria.</p>
 */
public final class ModLocales {

    /** El idioma que Minecraft corre hasta que alguien abra las opciones. No es opcional. */
    public static final String ENGLISH = "en_us";

    /** Las siete variantes de español que trae el juego. */
    public static final List<String> SPANISH = List.of(
            "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve");

    /** Todos, para que el datagen los recorra y un test los compruebe contra los archivos. */
    public static final List<String> ALL = List.copyOf(combined());

    private static List<String> combined() {
        List<String> all = new java.util.ArrayList<>(SPANISH.size() + 1);
        all.add(ENGLISH);
        all.addAll(SPANISH);
        return all;
    }

    private ModLocales() {
    }
}
