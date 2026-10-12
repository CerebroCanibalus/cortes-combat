package com.cortes.combat.sound;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The pools in {@code sounds.json} have to agree with the files on disk, in both directions.
 *
 * <p>Neither failure is loud. A name with no file behind it is the one the game does complain
 * about -- thirty lines of "does not exist, cannot add it to event", easy to filter out by eye --
 * and a file with no name in front of it is completely silent, which is the same thing the game
 * says about a sound that is simply not there. So the check that matters is the second one and
 * nothing in the game performs it.</p>
 *
 * <p>This is the sound twin of the particle test that reads the description JSON against the
 * sprite list. Both exist because a list in one file and a folder in another can only be checked
 * by a program.</p>
 *
 * <p><b>Parsed with a regex, not with a JSON library.</b> There is no JSON parser on the test
 * classpath and adding one for a file this shape would be a dependency that exists to serve a
 * test. The reader below is twelve lines and only understands the two things this file contains:
 * an event key and a list of names.</p>
 */
class SoundPoolTest {

    private static final Path SOUNDS_JSON =
            Path.of("src/main/resources/assets/cortescombat/sounds.json");
    private static final Path SOUND_DIR =
            Path.of("src/main/resources/assets/cortescombat/sounds");

    private static final Pattern LEGAL = Pattern.compile("[a-z0-9/._-]+");
    private static final Pattern EVENT = Pattern.compile("\"([a-z0-9_]+)\"\\s*:\\s*\\{");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    /**
     * Lo que una ruta de recurso de Minecraft admite: {@code namespace:path}, y en cada mitad
     * {@code [a-z0-9/._-]}. El `:` separa las dos y es legal; lo que no lo es, una mayuscula.
     */
    private static boolean isLegalLocation(String id) {
        String[] parts = id.split(":", -1);
        if (parts.length > 2) {
            return false;
        }
        for (String part : parts) {
            if (!LEGAL.matcher(part).matches()) {
                return false;
            }
        }
        return true;
    }

    /** The pools that are supposed to have more than one take, and therefore to sound varied. */
    private static final Set<String> SHOULD_RANDOMISE = Set.of(
            "sword_hit", "sword_miss",
            "axe_hit",
            "daga_hit",
            "ropera_hit", "ropera_miss",
            "alabarda_hit",
            "martillo_hit", "martillo_miss");

    /** Event key -> the bare file names it points at, in declaration order. */
    private static Map<String, List<String>> pools() throws IOException {
        String json = Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8);
        Map<String, List<String>> pools = new LinkedHashMap<>();

        Matcher matcher = EVENT.matcher(json);
        while (matcher.find()) {
            int open = json.indexOf('{', matcher.start());
            int depth = 0;
            int close = open;
            for (int i = open; i < json.length(); i++) {
                if (json.charAt(i) == '{') {
                    depth++;
                } else if (json.charAt(i) == '}') {
                    depth--;
                    if (depth == 0) {
                        close = i;
                        break;
                    }
                }
            }
            String body = json.substring(open + 1, close);

            List<String> files = new ArrayList<>();
            Matcher names = NAME.matcher(body);
            while (names.find()) {
                String id = names.group(1);
                int slash = id.lastIndexOf('/');
                files.add(id.substring(slash + 1));
            }
            pools.put(matcher.group(1), files);
        }
        assertTrue(pools.size() > 10, "el parser no leio casi nada: " + pools.size() + " eventos");
        return pools;
    }

    private static Set<String> filesOnDisk() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(SOUND_DIR)) {
            walk.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".ogg"))
                    .forEach(n -> names.add(n.substring(0, n.length() - 4)));
        }
        return names;
    }

    @Test
    @DisplayName("every name in sounds.json is a LEGAL resource path, or the whole file is dropped")
    void theNamesAreLegalLocations() throws IOException {
        // **Este es el test que faltaba, y su ausencia costo TODOS los sonidos del mod.**
        //
        // `everyNameResolves` compara el nombre contra los .ogg del disco, asi que
        // `sweeping_edgeV2` coincidia con `sweeping_edgeV2.ogg` y el test pasaba. Lo que nadie
        // preguntaba es si el nombre era una **ruta de recurso valida**: Minecraft solo admite
        // `[a-z0-9/._-]`, y una V mayuscula en el `name` hace que el juego tire
        // `ResourceLocationException` y **descarte sounds.json completo**.
        //
        // El sintoma del playtest fue "un monton de armas no tienen sonidos, estan totalmente
        // rotos", y suena a que se rompio el sistema de audio. No: se rompio un archivo, y lo rompio
        // una letra. Un guard que compara nombres contra nombres no ve un nombre invalido -- hay que
        // preguntar por la GRAMATICA del nombre, que es la misma leccion de los encantamientos.
        String json = Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8);
        List<String> illegal = new ArrayList<>();
        Matcher names = NAME.matcher(json);
        while (names.find()) {
            String id = names.group(1);
            if (!isLegalLocation(id)) {
                illegal.add(id);
            }
        }
        assertTrue(illegal.isEmpty(),
                "**estos nombres no son rutas de recurso validas** (`[a-z0-9/._-]`), y UNO SOLO hace "
                        + "que el juego descarte sounds.json ENTERO y que ningun sonido del mod suene: "
                        + illegal);
    }

    @Test
    @DisplayName("every .ogg on disk has a lowercase name, because a resource path is lowercase")
    void theFilesAreLowercase() throws IOException {
        List<String> illegal = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SOUND_DIR)) {
            walk.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".ogg"))
                    .filter(n -> !n.equals(n.toLowerCase(java.util.Locale.ROOT)))
                    .forEach(illegal::add);
        }
        assertTrue(illegal.isEmpty(),
                "un .ogg con mayusculas no se puede nombrar desde sounds.json -- la ruta que lo apunta "
                        + "seria invalida y tiraria el archivo entero. Renombralo en minusculas: "
                        + illegal);
    }

    @Test
    @DisplayName("every name in sounds.json has a file behind it")
    void everyNameResolves() throws IOException {
        Set<String> onDisk = filesOnDisk();
        List<String> missing = new ArrayList<>();
        for (var pool : pools().entrySet()) {
            for (String file : pool.getValue()) {
                // **A vanilla name is resolved by the game and not by this jar.** The check exists
                // because sounds.json naming a file nobody shipped is silence, and the arcabuz's
                // fuse is exactly that kind of silence: `minecraft:entity.creeper.primed` is the
                // closest thing the game has to a match lighting and there is no reason to ship a
                // worse copy of it. Skipping namespaced-other entries here is not a loosening of
                // the test -- the sibling below still catches every orphan FILE on disk, and a
                // vanilla name cannot be an orphan file because the jar does not carry it.
                if (file.contains(":")) {
                    continue;
                }
                if (!onDisk.contains(file)) {
                    missing.add(pool.getKey() + " -> " + file);
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "estos nombres no tienen archivo: " + missing
                        + ". El juego se queja de un archivo que no esta, pero no de un sonido que "
                        + "no se nombra nunca, y el sintoma de eso es silencio y nada mas");
    }

    @Test
    @DisplayName("every file on disk is named by sounds.json, or it is dead weight in the jar")
    void everyFileIsReachable() throws IOException {
        Set<String> named = new LinkedHashSet<>();
        pools().values().forEach(named::addAll);
        List<String> orphans = filesOnDisk().stream().filter(n -> !named.contains(n)).toList();
        assertTrue(orphans.isEmpty(),
                "estos .ogg no los nombra ningun evento, asi que no suenan nunca: " + orphans);
    }

    @Test
    @DisplayName("the pools that should sound varied really do have more than one take")
    void poolsAreActuallyPools() throws IOException {
        Map<String, List<String>> pools = pools();
        List<String> thin = new ArrayList<>();
        for (String key : SHOULD_RANDOMISE) {
            List<String> takes = pools.get(key);
            if (takes == null) {
                fail("sounds.json no tiene el evento " + key);
            }
            if (takes.size() < 2) {
                thin.add(key + " (" + takes.size() + ")");
            }
        }
        assertTrue(thin.isEmpty(),
                "estos eventos tienen una sola toma, asi que el pool no sortea nada: " + thin);
    }

    @Test
    @DisplayName("no mod sound declares replace, which is for overriding another pack")
    void noReplaceFlag() throws IOException {
        String json = Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8);
        assertTrue(!json.contains("\"replace\""),
                "replace significa 'piso la definicion de otro pack con este nombre'. Estos son "
                        + "eventos NUEVOS de cortescombat y nadie mas los define, asi que la "
                        + "bandera no hace nada -- y si algun dia un evento nosso se llamara como "
                        + "uno de vanilla, esto lo pisaria de verdad");
    }

    @Test
    @DisplayName("every event ModSounds can ask for is in sounds.json or is vanilla's")
    void everyAskResolves() throws IOException {
        Map<String, List<String>> pools = pools();
        for (WeaponProfile profile : WeaponProfile.values()) {
            for (ModSounds.Kind kind : ModSounds.Kind.values()) {
                String name = ModSounds.of(profile, kind);
                if (name.startsWith("minecraft:")) {
                    continue;
                }
                String bare = name.substring(name.indexOf(':') + 1);
                String key = bare.substring(bare.lastIndexOf('/') + 1);
                assertTrue(pools.containsKey(key),
                        profile + " pide " + name + " y sounds.json no lo tiene. Seria un "
                                + "silencio sin error en ninguna parte");
            }
        }
    }

    @Test
    @DisplayName("the parry is its own event, not the halberd's sweep")
    void parryIsNotTheSweep() {
        assertEquals("cortescombat:shotel_parry",
                ModSounds.of(WeaponProfile.SABRE, ModSounds.Kind.PARRY),
                "el parry necesita su propio Kind: SPECIAL cae en el barrido de la alabarda, y "
                        + "un parriado que suena como una barrida no se reconoce");
        assertEquals("cortescombat:alabarda_wide_swing",
                ModSounds.of(WeaponProfile.SABRE, ModSounds.Kind.SPECIAL),
                "el sable no tiene barrido, asi que SPECIAL le resuelve al evento de la alabarda");
    }
}
