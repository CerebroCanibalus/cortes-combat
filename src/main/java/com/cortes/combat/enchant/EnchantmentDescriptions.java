package com.cortes.combat.enchant;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Las descripciones de los encantamientos, leidas de <b>una tabla</b> y no de cuarenta y ocho
 * lineas de Java.
 *
 * <h2>Por que existe</h2>
 *
 * <p>La version anterior vivia dentro de {@code CortesLanguageProvider}: una llamada
 * {@code add(...)} por encantamiento con un ternario {@code spanish ? "..." : "..."} adentro.
 * Agregar o corregir una descripcion costaba editar Java, acordarse de las dos lenguas en la
 * misma linea, y volver a compilar -- y el que se olvidaba de una mitad no se enteraba. Con
 * cuarenta y ocho entradas eso no es un estilo, es una tarea que se abandona.</p>
 *
 * <p>Ahora la tabla es {@code data/cortescombat/enchantment_descriptions.yaml}: una entrada por
 * id con sus dos lenguas, UTF-8 (asi que los acentos son acentos). El provider la recorre al
 * generar los idiomas, y {@code EnchantmentDescriptionsTest} camina los ids de
 * {@code CombatEnchantRules} para que una entrada que falte rompa el build en vez de dejar un
 * encantamiento mudo.</p>
 *
 * <p><b>Sin tipos de Minecraft</b> a proposito: solo SnakeYAML, para que el test la cargue en
 * JVM pelada. La clave que se escribe sigue siendo
 * {@code enchantment.cortescombat.<id>.desc}, que es la que lee Enchantment Descriptions.
 * (⌒‿⌒)</p>
 */
public final class EnchantmentDescriptions {

    /** El recurso, en el classpath de las dos patas (vive en `src/main/resources`). */
    public static final String RESOURCE = "/data/cortescombat/enchantment_descriptions.yaml";

    /** Una descripcion en las dos lenguas. */
    public record Text(String es, String en) {
    }

    private static final Map<String, Text> TABLE = load();

    private EnchantmentDescriptions() {
    }

    /** Los 48, en el orden del archivo. Inmutable. */
    public static Map<String, Text> all() {
        return TABLE;
    }

    /**
     * Lee la tabla del classpath. Un archivo ausente, vacio o con una entrada a la que le falta
     * una lengua <b>explota al cargar</b> y no devuelve una tabla a medias: el unico consumidor
     * es el datagen, y un datagen que genera cuarenta y siete descripciones de cuarenta y ocho
     * es exactamente el fallo silencioso que esta clase existe para evitar.
     */
    private static Map<String, Text> load() {
        try (InputStream in = EnchantmentDescriptions.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("falta el recurso " + RESOURCE
                        + " -- las descripciones de encantamientos no se pueden generar");
            }
            Map<?, ?> raw = new Yaml().load(in);
            if (raw == null || raw.isEmpty()) {
                throw new IllegalStateException(RESOURCE + " esta vacio");
            }
            Map<String, Text> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String id = String.valueOf(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> langs)) {
                    throw new IllegalStateException(RESOURCE + " :: " + id
                            + " -- se esperaba un mapa con `es` y `en`");
                }
                String es = langs.get("es") == null ? null : String.valueOf(langs.get("es"));
                String en = langs.get("en") == null ? null : String.valueOf(langs.get("en"));
                if (es == null || es.isBlank() || en == null || en.isBlank()) {
                    throw new IllegalStateException(RESOURCE + " :: " + id
                            + " -- le falta `es` o `en`; una descripcion a medias es un "
                            + "encantamiento mudo en un idioma");
                }
                out.put(id, new Text(es, en));
            }
            return Collections.unmodifiableMap(out);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("no se pudo leer " + RESOURCE, e);
        }
    }
}
