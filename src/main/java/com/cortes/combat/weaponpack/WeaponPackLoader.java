package com.cortes.combat.weaponpack;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * De texto YAML a {@link WeaponPackSpec}: la fase que SnakeYAML compra, y la frontera donde un
 * archivo mal escrito se convierte en un warning con nombre -- nunca en un crash.
 *
 * <p><b>Tres reglas que la forma del archivo impone y esta clase respeta:</b>
 *
 * <ol>
 *   <li><b>{@code version} es obligatoria y sólo acepta 1.</b> Una versión desconocida es error
 *       con nombre de archivo: parsear a medias un formato que va a crecer es una trampa.</li>
 *   <li><b>Campo de tipo equivocado o desconocido = warning + se ignora</b>, con
 *       {@code archivo :: campo} en el mensaje (el detalle fino -- y la línea -- lo trae el
 *       error de sintaxis de SnakeYAML, que sí va envuelto con el origen). Nunca en silencio:
 *       un campo que se cae solo es un número que el packmate cree que aplicó.</li>
 *   <li><b>Sin un solo tipo de Minecraft</b>: los enums del proyecto se traducen por su id vía
 *       {@code PackResolver}, que es el único sitio donde un string se vuelve perfil o especial.
 *       Así el test del parser corre en JVM pelada como todos los demás.</li>
 * </ol>
 *
 * <p>El merge no es de esta clase: {@link #loadAll} devuelve los packs <b>en orden de precedencia
 * creciente</b> (built-in primero, después los de {@code config/} alfabéticamente) y la regla de
 * quién gana vive entera en {@link PackResolver}.
 */
public final class WeaponPackLoader {

    private static final String BUILT_IN_RESOURCE = "data/cortescombat/weapon_packs/builtin.yaml";
    private static final Set<String> ROOT_KEYS = Set.of("version", "archetypes", "weapons");
    private static final Set<String> ENTRY_KEYS = Set.of("creates", "match", "archetype",
            "profile", "stats", "special", "cone", "reach", "shot", "texture", "sounds",
            "animation", "display", "guard");
    private static final Set<String> MATCH_KEYS = Set.of("tag", "id");
    private static final Set<String> STATS_KEYS = Set.of("damage", "attack_speed", "durability",
            "charge", "max_targets", "knockback");
    private static final Set<String> CHARGE_KEYS = Set.of("can_charge", "bonus",
            "full_charge_ticks");
    private static final Set<String> REACH_KEYS = Set.of("distance");
    private static final Set<String> CONE_KEYS = Set.of("horizontal", "vertical");
    private static final Set<String> SHOT_KEYS = Set.of("distance", "horizontal", "vertical");
    private static final Set<String> GUARD_KEYS = Set.of("points", "arc", "regen", "stun");
    private static final Set<String> KNOCKBACK_KEYS = Set.of("horizontal", "vertical");
    private static final Set<String> TEXTURE_KEYS = Set.of("slash");
    private static final Set<String> SOUND_KEYS = Set.of("swing", "hit", "charged");
    private static final Set<String> ANIMATION_KEYS = Set.of("pose");
    private static final Set<String> DISPLAY_KEYS = Set.of("name", "tab");
    private static final Set<String> ARCHETYPE_KEYS = Set.of("profile", "special", "charge",
            "slash", "pose");

    private WeaponPackLoader() {
    }

    /** Packs en orden de precedencia + warnings de parseo; los dos viajan juntos. */
    public record Result(List<WeaponPackSpec> packs, List<String> warnings) {
        public Result {
            packs = List.copyOf(packs);
            warnings = List.copyOf(warnings);
        }
    }

    /** Archivo que no se pudo leer como weapon pack. El mensaje siempre nombra el archivo. */
    public static class ParseException extends RuntimeException {
        private final String origin;

        public ParseException(String origin, String detail, Throwable cause) {
            super(origin + " :: " + detail, cause);
            this.origin = origin;
        }

        /** Errores propios, sin exception de SnakeYAML detrás: el detalle basta. */
        public ParseException(String origin, String detail) {
            this(origin, detail, null);
        }

        public String origin() {
            return origin;
        }
    }

    /** Parsea un archivo ya leído. {@code origin} es el nombre que llevan todos los mensajes. */
    public static Result fromYaml(String origin, String yaml) {
        Object root;
        try {
            root = new Yaml().load(yaml);
        } catch (RuntimeException e) {
            // Los errores de sintaxis de SnakeYAML traen la línea en el mensaje; el envoltorio
            // sólo agrega QUÉ archivo falló, que es lo que el jugador necesita para abrirlo.
            throw new ParseException(origin, String.valueOf(e.getMessage()), e);
        }
        if (!(root instanceof Map<?, ?>)) {
            throw new ParseException(origin, "no es un mapa de weapon pack -- ¿archivo vacío?");
        }
        Map<?, ?> map = cast(root);
        List<String> warnings = new ArrayList<>();
        unknownKeys(origin, "raíz", map, ROOT_KEYS, warnings);

        Object version = map.get("version");
        if (version == null) {
            throw new ParseException(origin, "falta `version` (obligatoria: 1)");
        }
        if (!(version instanceof Number number) || number.intValue() != 1) {
            throw new ParseException(origin, "versión desconocida '" + version
                    + "' -- este mod entiende la 1");
        }

        List<WeaponPackSpec.Entry> entries = new ArrayList<>();
        if (map.get("weapons") instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                WeaponPackSpec.Entry entry = parseEntry(origin, "weapons[" + i + "]", list.get(i),
                        warnings);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } else if (map.get("weapons") != null) {
            warnings.add(origin + " :: `weapons` no es una lista -- se ignora entera");
        }

        Map<String, ArchetypeSpec> archetypes = new LinkedHashMap<>();
        if (map.get("archetypes") instanceof Map<?, ?> raw) {
            raw.forEach((name, value) -> {
                ArchetypeSpec spec = parseArchetype(origin, String.valueOf(name), value, warnings);
                if (spec != null) {
                    archetypes.put(String.valueOf(name), spec);
                }
            });
        } else if (map.get("archetypes") != null) {
            warnings.add(origin + " :: `archetypes` no es un mapa -- se ignora entero");
        }

        return new Result(List.of(new WeaponPackSpec(origin, entries, archetypes)), warnings);
    }

    /** Todos los {@code *.yaml} de {@code configDir}, en orden alfabético (más abajo gana). */
    public static Result loadDirectory(Path configDir) {
        List<WeaponPackSpec> packs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!Files.isDirectory(configDir)) {
            return new Result(packs, warnings);
        }
        List<Path> files;
        try (Stream<Path> found = Files.list(configDir)) {
            files = found.filter(WeaponPackLoader::isYaml)
                    .sorted().toList();
        } catch (IOException e) {
            warnings.add(configDir + " :: no se pudo listar: " + e.getMessage());
            return new Result(packs, warnings);
        }
        for (Path file : files) {
            try {
                Result one = fromYaml(file.getFileName().toString(),
                        Files.readString(file, StandardCharsets.UTF_8));
                packs.addAll(one.packs());
                warnings.addAll(one.warnings());
            } catch (ParseException e) {
                // Un archivo roto no aborta la carga entera: se nombra completo (con su línea,
                // si SnakeYAML la trae) y los demás archivos siguen entrando.
                warnings.add(e.getMessage());
            } catch (IOException | RuntimeException e) {
                // Un archivo ilegible no tira la carga entera: se nombra y se salta.
                warnings.add(file.getFileName() + " :: ilegible: " + e.getMessage());
            }
        }
        return new Result(packs, warnings);
    }

    /** El pack del propio mod, desde el classpath; ausente = warning, no silencio. */
    public static Result loadBuiltIn() {
        try (InputStream stream = WeaponPackLoader.class.getClassLoader()
                .getResourceAsStream(BUILT_IN_RESOURCE)) {
            if (stream == null) {
                return new Result(List.of(),
                        List.of(BUILT_IN_RESOURCE + " no está en el classpath"));
            }
            return fromYaml("builtin.yaml",
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new Result(List.of(),
                    List.of(BUILT_IN_RESOURCE + " ilegible: " + e.getMessage()));
        }
    }

    /** El orden completo de precedencia: built-in primero, después config alfabético. */
    public static Result loadAll(Path configDir) {
        Result builtIn = loadBuiltIn();
        Result directory = loadDirectory(configDir);
        List<WeaponPackSpec> packs = new ArrayList<>(builtIn.packs());
        packs.addAll(directory.packs());
        List<String> warnings = new ArrayList<>(builtIn.warnings());
        warnings.addAll(directory.warnings());
        return new Result(packs, warnings);
    }

    // ---------------------------------------------------------------- parsing

    private static WeaponPackSpec.Entry parseEntry(String origin, String where, Object raw,
                                                   List<String> warnings) {
        if (!(raw instanceof Map<?, ?>)) {
            warnings.add(origin + " :: " + where + " no es un mapa -- se ignora la entrada");
            return null;
        }
        Map<?, ?> map = cast(raw);
        unknownKeys(origin, where, map, ENTRY_KEYS, warnings);

        String creates = text(origin, where, map, "creates", warnings);
        String tag = null;
        String id = null;
        if (map.get("match") instanceof Map<?, ?> matchMap) {
            unknownKeys(origin, where + ".match", matchMap, MATCH_KEYS, warnings);
            tag = text(origin, where + ".match", matchMap, "tag", warnings);
            id = text(origin, where + ".match", matchMap, "id", warnings);
        } else if (map.get("match") != null) {
            warnings.add(origin + " :: " + where + ".match no es un mapa {tag|id} -- se ignora");
        }

        WeaponPackSpec.Stats stats = null;
        if (map.get("stats") instanceof Map<?, ?> statsMap) {
            unknownKeys(origin, where + ".stats", statsMap, STATS_KEYS, warnings);
            Boolean canCharge = null;
            Float chargeBonus = null;
            Integer fullChargeTicks = null;
            if (statsMap.get("charge") instanceof Map<?, ?> chargeMap) {
                unknownKeys(origin, where + ".stats.charge", chargeMap, CHARGE_KEYS, warnings);
                canCharge = bool(origin, where + ".stats.charge", chargeMap, "can_charge", warnings);
                chargeBonus = number(origin, where + ".stats.charge", chargeMap, "bonus", warnings);
                fullChargeTicks = whole(origin, where + ".stats.charge", chargeMap,
                        "full_charge_ticks", warnings);
            } else if (statsMap.get("charge") != null) {
                warnings.add(origin + " :: " + where
                        + ".stats.charge no es un mapa -- se ignora (¿`charge: {can_charge: ...}`?)");
            }
            WeaponPackSpec.Knockback knockback = null;
            if (statsMap.get("knockback") instanceof Map<?, ?> kbMap) {
                unknownKeys(origin, where + ".stats.knockback", kbMap, KNOCKBACK_KEYS, warnings);
                knockback = new WeaponPackSpec.Knockback(
                        number(origin, where + ".stats.knockback", kbMap, "horizontal", warnings),
                        number(origin, where + ".stats.knockback", kbMap, "vertical", warnings));
            } else if (statsMap.get("knockback") != null) {
                warnings.add(origin + " :: " + where + ".stats.knockback no es un mapa -- se ignora");
            }
            stats = new WeaponPackSpec.Stats(
                    number(origin, where + ".stats", statsMap, "damage", warnings),
                    number(origin, where + ".stats", statsMap, "attack_speed", warnings),
                    whole(origin, where + ".stats", statsMap, "durability", warnings),
                    canCharge, chargeBonus, fullChargeTicks,
                    whole(origin, where + ".stats", statsMap, "max_targets", warnings),
                    knockback);
        } else if (map.get("stats") != null) {
            warnings.add(origin + " :: " + where + ".stats no es un mapa -- se ignora");
        }

        // **El cono es radio crudo y `reach` sólo distancia -- dos parámetros separados**
        // (decisión del General, 2026-10-09). La banda `cone: small` era el apodo de unos
        // ángulos, y un apodo no deja elegir: quien escriba la forma vieja recibe el mapa a
        // escribir, no un "campo desconocido" que lo deja adivinando.
        WeaponPackSpec.Cone cone = null;
        if (map.get("cone") instanceof Map<?, ?> coneMap) {
            unknownKeys(origin, where + ".cone", coneMap, CONE_KEYS, warnings);
            cone = new WeaponPackSpec.Cone(
                    number(origin, where + ".cone", coneMap, "horizontal", warnings),
                    number(origin, where + ".cone", coneMap, "vertical", warnings));
        } else if (map.get("cone") instanceof String band) {
            warnings.add(origin + " :: " + where + ".cone: la banda '" + band
                    + "' ya no existe -- radio en crudo: cone: {horizontal: 22, vertical: 18}");
        } else if (map.get("cone") != null) {
            warnings.add(origin + " :: " + where + ".cone no es un mapa {horizontal, vertical}");
        }

        WeaponPackSpec.Reach reach = null;
        if (map.get("reach") instanceof Map<?, ?> reachMap) {
            unknownKeys(origin, where + ".reach", reachMap, REACH_KEYS, warnings);
            reach = new WeaponPackSpec.Reach(
                    number(origin, where + ".reach", reachMap, "distance", warnings));
        } else if (map.get("reach") != null) {
            warnings.add(origin + " :: " + where + ".reach no es un mapa -- se ignora");
        }

        WeaponPackSpec.ShotReach shot = null;
        if (map.get("shot") instanceof Map<?, ?> shotMap) {
            unknownKeys(origin, where + ".shot", shotMap, SHOT_KEYS, warnings);
            shot = new WeaponPackSpec.ShotReach(
                    number(origin, where + ".shot", shotMap, "distance", warnings),
                    number(origin, where + ".shot", shotMap, "horizontal", warnings),
                    number(origin, where + ".shot", shotMap, "vertical", warnings));
        } else if (map.get("shot") != null) {
            warnings.add(origin + " :: " + where + ".shot no es un mapa -- se ignora");
        }

        WeaponPackSpec.Guard guard = null;
        if (map.get("guard") instanceof Map<?, ?> guardMap) {
            unknownKeys(origin, where + ".guard", guardMap, GUARD_KEYS, warnings);
            guard = new WeaponPackSpec.Guard(
                    whole(origin, where + ".guard", guardMap, "points", warnings),
                    number(origin, where + ".guard", guardMap, "arc", warnings),
                    whole(origin, where + ".guard", guardMap, "regen", warnings),
                    whole(origin, where + ".guard", guardMap, "stun", warnings));
        } else if (map.get("guard") != null) {
            warnings.add(origin + " :: " + where + ".guard no es un mapa -- se ignora");
        }

        WeaponPackSpec.Sounds sounds = null;
        if (map.get("sounds") instanceof Map<?, ?> soundMap) {
            unknownKeys(origin, where + ".sounds", soundMap, SOUND_KEYS, warnings);
            sounds = new WeaponPackSpec.Sounds(
                    text(origin, where + ".sounds", soundMap, "swing", warnings),
                    text(origin, where + ".sounds", soundMap, "hit", warnings),
                    text(origin, where + ".sounds", soundMap, "charged", warnings));
        } else if (map.get("sounds") != null) {
            warnings.add(origin + " :: " + where + ".sounds no es un mapa -- se ignora");
        }

        String slash = null;
        if (map.get("texture") instanceof Map<?, ?> textureMap) {
            unknownKeys(origin, where + ".texture", textureMap, TEXTURE_KEYS, warnings);
            slash = text(origin, where + ".texture", textureMap, "slash", warnings);
        } else if (map.get("texture") != null) {
            warnings.add(origin + " :: " + where + ".texture no es un mapa -- se ignora");
        }

        String pose = null;
        if (map.get("animation") instanceof Map<?, ?> animationMap) {
            unknownKeys(origin, where + ".animation", animationMap, ANIMATION_KEYS, warnings);
            pose = text(origin, where + ".animation", animationMap, "pose", warnings);
        } else if (map.get("animation") != null) {
            warnings.add(origin + " :: " + where + ".animation no es un mapa -- se ignora");
        }

        WeaponPackSpec.Display display = null;
        if (map.get("display") instanceof Map<?, ?> displayMap) {
            unknownKeys(origin, where + ".display", displayMap, DISPLAY_KEYS, warnings);
            display = new WeaponPackSpec.Display(
                    text(origin, where + ".display", displayMap, "name", warnings),
                    text(origin, where + ".display", displayMap, "tab", warnings));
        } else if (map.get("display") != null) {
            warnings.add(origin + " :: " + where + ".display no es un mapa -- se ignora");
        }

        if (creates == null && tag == null && id == null) {
            warnings.add(origin + " :: " + where
                    + " sin identidad (creates, match.tag o match.id) -- nunca va a matchear");
        }

        return new WeaponPackSpec.Entry(creates, id, tag,
                text(origin, where, map, "archetype", warnings),
                text(origin, where, map, "profile", warnings),
                stats,
                text(origin, where, map, "special", warnings),
                cone, reach, shot, slash, sounds, pose, display, guard);
    }

    private static ArchetypeSpec parseArchetype(String origin, String name, Object raw,
                                                List<String> warnings) {
        if (!(raw instanceof Map<?, ?>)) {
            warnings.add(origin + " :: arquetipo '" + name + "' no es un mapa -- se ignora");
            return null;
        }
        Map<?, ?> map = cast(raw);
        unknownKeys(origin, "archetypes." + name, map, ARCHETYPE_KEYS, warnings);
        String profile = text(origin, "archetypes." + name, map, "profile", warnings);
        String special = text(origin, "archetypes." + name, map, "special", warnings);
        return new ArchetypeSpec(
                profile != null ? PackResolver.profileById(profile, origin, warnings) : null,
                special != null ? PackResolver.specialById(special, origin, warnings) : null,
                bool(origin, "archetypes." + name, map, "charge", warnings),
                text(origin, "archetypes." + name, map, "slash", warnings),
                text(origin, "archetypes." + name, map, "pose", warnings));
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private static Map<?, ?> cast(Object value) {
        return (Map<?, ?>) value;
    }

    private static void unknownKeys(String origin, String where, Map<?, ?> map, Set<String> known,
                                    List<String> warnings) {
        for (Object key : map.keySet()) {
            if (!known.contains(String.valueOf(key))) {
                warnings.add(origin + " :: " + where + " :: campo desconocido '" + key
                        + "' -- se ignora");
            }
        }
    }

    private static String text(String origin, String where, Map<?, ?> map, String key,
                               List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        // Un número o un bool escrito sin comillas es texto válido en YAML de todas formas;
        // lo que NO aceptamos es una lista o un mapa donde se pide un escalar.
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        warnings.add(origin + " :: " + where + "." + key + " debería ser texto -- se ignora");
        return null;
    }

    private static Float number(String origin, String where, Map<?, ?> map, String key,
                                List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        warnings.add(origin + " :: " + where + "." + key + " debería ser un número, llegó "
                + value.getClass().getSimpleName() + " -- se ignora");
        return null;
    }

    private static Integer whole(String origin, String where, Map<?, ?> map, String key,
                                 List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        warnings.add(origin + " :: " + where + "." + key + " debería ser un número entero -- se ignora");
        return null;
    }

    private static Boolean bool(String origin, String where, Map<?, ?> map, String key,
                                List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        warnings.add(origin + " :: " + where + "." + key + " debería ser true/false -- se ignora");
        return null;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }
}
